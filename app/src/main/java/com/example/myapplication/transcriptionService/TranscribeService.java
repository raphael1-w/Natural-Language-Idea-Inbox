package com.example.myapplication.transcriptionService;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TranscribeService extends Service {
    private static final String TAG = "TranscribeService";
    private static final String CHANNEL_ID = "TranscribeServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    // Audio processing parameters
    private static final int SAMPLE_RATE = 16000;
    private static final int N_FFT = 400;
    private static final int N_MELS = 80;
    private static final int HOP_LENGTH = 160;
    private static final int CHUNK_LENGTH = 30;
    private static final int N_SAMPLES = SAMPLE_RATE * CHUNK_LENGTH;
    private static final int CHUNK_OVERLAP = 6;
    private static final int N_SAMPLES_OVERLAP = SAMPLE_RATE * CHUNK_OVERLAP;
    private final IBinder binder = new LocalBinder();
    private ExecutorService executor;
    private Interpreter tfliteInterpreter;
    private WhisperTokenizer tokenizer;
    private TranscriptionCallback callback;
    private int id;
    private String audioFilePath;


    public class LocalBinder extends Binder {
        TranscribeService getService() {
            return TranscribeService.this;
        }
    }

    public interface TranscriptionCallback {
        void onTranscriptionProgress(String partialTranscript, float progress);
        void onTranscriptionComplete(String finalTranscript);
        void onTranscriptionError(String error);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            id = intent.getIntExtra("id", -1);
            audioFilePath = intent.getStringExtra("audioFilePath");
        }

        // Start transcription
        if (audioFilePath != null) {
            Log.d(TAG, "Starting transcription...");
            startTranscription(audioFilePath);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("TranscribeService", "Service created");
        executor = Executors.newSingleThreadExecutor();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Initializing transcription service..."));
        initializeModel();
    }

    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void initializeModel() {
        try {
            // Load TFLite model
            Log.d(TAG, "Loading TFLite model...");
            Interpreter.Options options = new Interpreter.Options();
            tfliteInterpreter = new Interpreter(loadModelFile(getAssets(),"whisper.tflite"), options);

            // Initialize tokenizer
            Log.d(TAG, "Initializing tokenizer...");
            tokenizer = new WhisperTokenizer(getAssets());
        } catch (IOException e) {
            Log.e(TAG, "Error initializing model/tokenizer", e);
            if (callback != null) {
                callback.onTranscriptionError(e.getMessage());
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void setTranscriptionCallback(TranscriptionCallback callback) {
        this.callback = callback;
    }

    public void startTranscription(String audioPath) {
        executor.execute(() -> {
            try {
                String transcript = transcribeAudio(audioPath);
                if (callback != null) {
                    callback.onTranscriptionComplete(transcript);
                }
                Log.d("TranscribeService", "Transcription complete: " + transcript);
            } catch (Exception e) {
                Log.e(TAG, "Transcription error", e);
                if (callback != null) {
                    callback.onTranscriptionError(e.getMessage());
                }
            }
        });
    }

    private String transcribeAudio(String audioPath) throws IOException {
        updateNotification("Loading audio file...");
        Log.d("TranscribeService", "Load and resample audio file: " + audioPath);
        float[] audio = loadAndResampleAudio(audioPath);

        List<float[]> chunks = calculateAndCreateChunks(audio);
        List<String> transcriptions = new ArrayList<>();
        String previousTranscript = "";

        for (int i = 0; i < chunks.size(); i++) {
            updateNotification(String.format("Processing chunk %d/%d", i + 1, chunks.size()));
            float progress = (float) i / chunks.size();

            String transcript = processAudioChunk(chunks.get(i));
            if (!transcript.isEmpty()) {
                if (!previousTranscript.isEmpty()) {
                    String cleanedTranscript = findOverlapFuzzy(previousTranscript, transcript);
                    if (!cleanedTranscript.isEmpty()) {
                        transcriptions.add(cleanedTranscript);
                    }
                } else {
                    transcriptions.add(transcript);
                }
                previousTranscript = transcript;
            }

            if (callback != null) {
                String partialTranscript = String.join(" ", transcriptions);
                callback.onTranscriptionProgress(partialTranscript, progress);
            }
        }

        String finalTranscript = String.join(" ", transcriptions);
        saveTranscription(finalTranscript);
        return finalTranscript;
    }

    private float[] loadAndResampleAudio(String audioPath) {
        try {
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(audioPath);

            // Log original file duration
//            MediaFormat format_ = extractor.getTrackFormat(0);
//            long durationUs = format_.getLong(MediaFormat.KEY_DURATION);
//            float durationSec = durationUs / 1_000_000f;
//            Log.d(TAG, String.format("Original audio duration: %.2f seconds", durationSec));

            // Select the first audio track
            int audioTrackIndex = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    break;
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                throw new IOException("No audio track found");
            }

            extractor.selectTrack(audioTrackIndex);

            // Get audio properties
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int originalSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int maxBufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

            // Create a list to store audio samples
            List<Float> audioSamples = new ArrayList<>();

            // Read audio data in chunks
            ByteBuffer buffer = ByteBuffer.allocate(maxBufferSize);
            while (true) {
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) break;

                buffer.rewind();
                for (int i = 0; i < sampleSize; i += 2) {
                    if (buffer.remaining() >= 2) {
                        short sample = buffer.getShort();
                        audioSamples.add(sample / 32768.0f);
                    }
                }
                buffer.clear();
                extractor.advance();
            }

            extractor.release();

            // Convert List<Float> to float[]
            float[] audioData = new float[audioSamples.size()];
            for (int i = 0; i < audioSamples.size(); i++) {
                audioData[i] = audioSamples.get(i);
            }

            // Resample if necessary
            if (originalSampleRate != SAMPLE_RATE) {
                audioData = resampleAudio(audioData, originalSampleRate, SAMPLE_RATE);
            }

            // Convert stereo to mono if necessary
            if (channels == 2) {
                audioData = convertStereoToMono(audioData);
            }

            // Log audio properties
            Log.d(TAG, "Audio loaded: " + audioData.length + " samples, " +
                    "sample rate: " + SAMPLE_RATE + ", channels: " + channels);

            return audioData;

        } catch (IOException e) {
            Log.e(TAG, "Error loading audio file", e);
            return new float[0];
        }
    }

    private float[] resampleAudio(float[] input, int originalRate, int targetRate) {
        // Simple linear interpolation resampling
        int outputLength = (int) ((long) input.length * targetRate / originalRate);
        float[] output = new float[outputLength];

        for (int i = 0; i < outputLength; i++) {
            float position = i * ((float) originalRate / targetRate);
            int index = (int) position;
            float fraction = position - index;

            if (index >= input.length - 1) {
                output[i] = input[input.length - 1];
            } else {
                output[i] = input[index] * (1 - fraction) + input[index + 1] * fraction;
            }
        }

        return output;
    }

    private float[] convertStereoToMono(float[] stereoData) {
        float[] monoData = new float[stereoData.length / 2];
        for (int i = 0; i < monoData.length; i++) {
            monoData[i] = (stereoData[i * 2] + stereoData[i * 2 + 1]) / 2.0f;
        }
        return monoData;
    }

    private List<float[]> calculateAndCreateChunks(float[] audio) {
        List<float[]> chunks = new ArrayList<>();
        int start = 0;

        while (start < audio.length) {
            int end = Math.min(start + N_SAMPLES, audio.length);
            float[] chunk = Arrays.copyOfRange(audio, start, end);

            chunks.add(chunk);

            start += (N_SAMPLES - N_SAMPLES_OVERLAP);
        }

        // Log chunking results
        Log.d(TAG, "Audio chunking: " + chunks.size() + " chunks created");

        return chunks;
    }

    private float[] padAudio(float[] audio) {
        float[] padded = new float[N_SAMPLES];
        System.arraycopy(audio, 0, padded, 0, audio.length);
        return padded;
    }

    private String processAudioChunk(float[] audioChunk) {
        try {
            // Convert audio to mel spectrogram
            float[][] melSpec = computeMelSpectrogram(audioChunk);

            // Pad or trim to exactly 3000 frames
            int targetLength = 3000;
            float[][] paddedMelSpec = new float[N_MELS][targetLength];

            // Copy and pad/trim to target length
            for (int i = 0; i < N_MELS; i++) {
                if (Math.min(melSpec[i].length, targetLength) >= 0)
                    System.arraycopy(melSpec[i], 0, paddedMelSpec[i], 0, Math.min(melSpec[i].length, targetLength));
            }

            // Convert to log mel spectrogram and normalize
            float[][][] logMelSpec = new float[1][N_MELS][targetLength];
            for (int i = 0; i < N_MELS; i++) {
                for (int j = 0; j < targetLength; j++) {
                    // Apply log10 with clipping to avoid -inf
                    float value = Math.max(paddedMelSpec[i][j], 1e-10f);
                    float logValue = (float)Math.log10(value);
                    // Normalize with the same formula as Python
                    logMelSpec[0][i][j] = (logValue + 4.0f) / 4.0f;
                }
            }

            // Prepare input tensor (add channel dimension)
            float[][][][] inputTensor = new float[1][N_MELS][targetLength][1];
            for (int i = 0; i < N_MELS; i++) {
                for (int j = 0; j < targetLength; j++) {
                    inputTensor[0][i][j][0] = logMelSpec[0][i][j];
                }
            }

            // Run inference
            int[][] outputTensor = new int[1][448];
            tfliteInterpreter.run(inputTensor, outputTensor);

            // Process tokens
            List<Integer> validTokens = new ArrayList<>();
            for (int token : outputTensor[0]) {
                if (token >= 0 && token < 50258) {
                    validTokens.add(token);
                }
            }

            return tokenizer.decode(validTokens, true);
        } catch (Exception e) {
            Log.e(TAG, "Error processing audio chunk", e);
            return "";
        }
    }

    private float[][] computeMelSpectrogram(float[] audio) {
        // Note: You'll need to implement mel spectrogram computation
        // This could use a signal processing library or native code
        // For now, this is a placeholder
        throw new UnsupportedOperationException("Mel spectrogram computation not implemented");
    }

    private String findOverlapFuzzy(String text1, String text2) {
        // Implement fuzzy matching similar to Python's SequenceMatcher
        // For now, using a simplified approach
        String[] words1 = text1.split("\\s+");
        String[] words2 = text2.split("\\s+");

        int maxWindow = Math.min(words1.length, words2.length);
        int bestOverlapLen = 0;
        double bestRatio = 0.85; // threshold

        for (int window = maxWindow; window > 3; window--) {
            if (words1.length >= window && words2.length >= window) {
                String text1End = String.join(" ",
                        Arrays.copyOfRange(words1, words1.length - window, words1.length));
                String text2Start = String.join(" ",
                        Arrays.copyOfRange(words2, 0, window));

                double ratio = calculateSimilarity(text1End.toLowerCase(), text2Start.toLowerCase());
                if (ratio > bestRatio) {
                    bestRatio = ratio;
                    bestOverlapLen = window;
                }
            }
        }

        if (bestOverlapLen > 0) {
            return String.join(" ",
                    Arrays.copyOfRange(words2, bestOverlapLen, words2.length));
        }
        return text2;
    }

    private double calculateSimilarity(String s1, String s2) {
        // Implement a simple similarity metric
        // This is a simplified version of SequenceMatcher
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;
        return (double) (maxLength - levenshteinDistance(s1, s2)) / maxLength;
    }

    private int levenshteinDistance(String s1, String s2) {
        int[] prev = new int[s2.length() + 1];
        int[] curr = new int[s2.length() + 1];

        for (int j = 0; j <= s2.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[s2.length()];
    }

    private void saveTranscription(String transcript) {
        try {
            File transcriptionFile = new File(getFilesDir(), "transcription.txt");
            try (FileOutputStream fos = new FileOutputStream(transcriptionFile)) {
                fos.write(transcript.getBytes());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving transcription", e);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Transcription Service",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

    }

    private Notification createNotification(String message) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Audio Transcription")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
    }

    private void updateNotification(String message) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createNotification(message));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        Log.d("TranscribeService", "Service destroyed");

        super.onDestroy();
        if (tfliteInterpreter != null) {
            tfliteInterpreter.close();
        }
        if (executor != null) {
            executor.shutdown();
        }

    }
}