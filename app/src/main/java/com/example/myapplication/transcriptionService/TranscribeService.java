package com.example.myapplication.transcriptionService;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaCodec;
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
import java.nio.ByteOrder;
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
    private static final int N_FFT = 512;
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
            tfliteInterpreter = new Interpreter(loadModelFile(getAssets(),"whisper-tiny.tflite"), options);

            // Log model input/output shapes
            int[] inputShape = tfliteInterpreter.getInputTensor(0).shape();
            int[] outputShape = tfliteInterpreter.getOutputTensor(0).shape();

            Log.d(TAG, "Model input shape: " + Arrays.toString(inputShape));
            Log.d(TAG, "Model output shape: " + Arrays.toString(outputShape));

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

        Log.d("TranscribeService", "Processing audio chunks...");

        for (int i = 0; i < chunks.size(); i++) {
            updateNotification(String.format("Processing chunk %d/%d", i + 1, chunks.size()));
            float progress = (float) i / chunks.size();

            String transcript = processAudioChunk(chunks.get(i));
            Log.d("TranscribeService", "Transcript for current chunk: " + transcript);
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
            MediaFormat format_ = extractor.getTrackFormat(0);
            long durationUs = format_.getLong(MediaFormat.KEY_DURATION);
            float durationSec = durationUs / 1_000_000f;
            Log.d(TAG, String.format("Original audio duration: %.2f seconds", durationSec));

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

            // Check for audio encoding
            String mime = format.getString(MediaFormat.KEY_MIME);
            Log.d(TAG, "Audio mime type: " + mime);

            // For compressed formats, we need to use MediaCodec to decode
            if (!mime.equals("audio/raw")) {
                // This is what librosa would do - decode and get all samples
                return decodeCompressedAudio(extractor, format, durationSec, originalSampleRate, channels);
            }

            // Rest of code for uncompressed audio (unlikely path)
            // ...

            Log.e(TAG, "Uncompressed audio format detected, which is unusual. Using fallback method.");
            return new float[0];
        } catch (IOException e) {
            Log.e(TAG, "Error loading audio file", e);
            return new float[0];
        }
    }

    private float[] decodeCompressedAudio(MediaExtractor extractor, MediaFormat format,
                                          float durationSec, int originalSampleRate, int channels) {
        try {
            // Calculate expected frame count based on duration
            long expectedFrames = (long)(durationSec * originalSampleRate);
            Log.d(TAG, "Expected frames before decoding: " + expectedFrames);

            // Create decoder
            String mime = format.getString(MediaFormat.KEY_MIME);
            MediaCodec decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            // Prepare buffers
            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean isEOS = false;

            // Allocate buffer for decoded PCM data - estimate based on duration
            // 2 bytes per sample * channels * expected frames with some extra room
            ByteBuffer pcmData = ByteBuffer.allocate((int)(2 * channels * expectedFrames * 1.1));
            pcmData.order(ByteOrder.LITTLE_ENDIAN);

            // Decode loop
            while (!isEOS) {
                // Feed input buffer with encoded data
                int inIndex = decoder.dequeueInputBuffer(100000);
                if (inIndex >= 0) {
                    ByteBuffer buffer = inputBuffers[inIndex];
                    buffer.clear();

                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        long sampleTime = extractor.getSampleTime();
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0);
                        extractor.advance();
                    }
                }

                // Get decoded output
                int outIndex = decoder.dequeueOutputBuffer(info, 100000);
                if (outIndex >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEOS = true;
                    }

                    ByteBuffer buffer = outputBuffers[outIndex];
                    buffer.position(info.offset);
                    buffer.limit(info.offset + info.size);

                    // Copy decoded PCM data
                    pcmData.put(buffer);

                    decoder.releaseOutputBuffer(outIndex, false);
                } else if (outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = decoder.getOutputBuffers();
                }
            }

            // Clean up
            decoder.stop();
            decoder.release();
            extractor.release();

            // Prepare PCM data for processing
            pcmData.flip();
            int totalSamples = pcmData.limit() / 2;  // 16-bit samples = 2 bytes per sample

            Log.d(TAG, "Decoded PCM data size: " + pcmData.limit() + " bytes");
            Log.d(TAG, "Total decoded samples: " + totalSamples);

            // Convert to float array (normalized to [-1.0, 1.0])
            float[] audioData = new float[totalSamples];
            for (int i = 0; i < totalSamples; i++) {
                if (pcmData.remaining() >= 2) {
                    short sample = pcmData.getShort();
                    audioData[i] = sample / 32768.0f;
                }
            }

            Log.d(TAG, String.format("Loaded %d samples at %d Hz (%d channels)",
                    audioData.length, originalSampleRate, channels));

            // Resample if necessary
            if (originalSampleRate != SAMPLE_RATE) {
                audioData = resampleAudio(audioData, originalSampleRate, SAMPLE_RATE, channels);
            }

            // Convert stereo to mono if necessary
            if (channels == 2) {
                audioData = convertStereoToMono(audioData);
            }

            // Log expected samples after resampling
            Log.d(TAG, "Expected samples after resampling: " + (int)(SAMPLE_RATE * durationSec));

            // Check if we need to match the exact expected duration (librosa behavior)
            float[] finalAudio = matchLibrosaDuration(audioData, durationSec, SAMPLE_RATE);

            // Log audio properties
            Log.d(TAG, "Audio loaded: " + finalAudio.length + " samples, " +
                    "sample rate: " + SAMPLE_RATE + ", channels: 1");

            return finalAudio;
        } catch (Exception e) {
            Log.e(TAG, "Error decoding compressed audio", e);
            return new float[0];
        }
    }

    private float[] matchLibrosaDuration(float[] audio, float durationSec, int sampleRate) {
        // Calculate the expected number of samples for the full duration
        int expectedSamples = (int)(sampleRate * durationSec);

        if (audio.length >= expectedSamples) {
            // We already have enough samples, just return the correct length
            Log.d(TAG, "Audio already matches expected duration");
            return audio;
        }

        // We need to extend the audio to match the expected duration
        Log.d(TAG, String.format("Extending audio from %d to %d samples to match expected duration",
                audio.length, expectedSamples));

        float[] extendedAudio = new float[expectedSamples];

        // Copy existing samples
        System.arraycopy(audio, 0, extendedAudio, 0, audio.length);

        // Fill the rest with zeros (silence) - this matches librosa's zero-padding behavior
        // This is usually not the case with real audio, but ensures we get the expected duration

        return extendedAudio;
    }

    private float[] resampleAudio(float[] input, int originalRate, int targetRate, int channels) {
        // Handle multi-channel audio correctly
        int framesIn = input.length / channels;

        // Calculate output length based on ratio of sample rates
        int framesOut = (int) Math.ceil(((double) framesIn * targetRate) / originalRate);
        float[] output = new float[framesOut * channels];

        if (channels == 1) {
            // Simple case - mono audio
            resampleChannel(input, output, framesIn, framesOut, originalRate, targetRate);
        } else {
            // Handle multi-channel audio (typically stereo)
            for (int ch = 0; ch < channels; ch++) {
                // Extract single channel from input
                float[] inputChannel = new float[framesIn];
                for (int i = 0; i < framesIn; i++) {
                    inputChannel[i] = input[i * channels + ch];
                }

                // Create buffer for resampled channel
                float[] outputChannel = new float[framesOut];

                // Resample single channel
                resampleChannel(inputChannel, outputChannel, framesIn, framesOut, originalRate, targetRate);

                // Copy back to interleaved output
                for (int i = 0; i < framesOut; i++) {
                    output[i * channels + ch] = outputChannel[i];
                }
            }
        }

        Log.d(TAG, String.format("Resampling from %dHz to %dHz", originalRate, targetRate));
        Log.d(TAG, String.format("Input frames: %d, Output frames: %d",
                framesIn, framesOut));
        Log.d(TAG, String.format("Expected duration: %.2f seconds",
                (double) framesIn / originalRate));
        Log.d(TAG, String.format("Actual duration: %.2f seconds",
                (double) framesOut / targetRate));

        return output;
    }

    private void resampleChannel(float[] input, float[] output, int framesIn, int framesOut, int originalRate, int targetRate) {
        // Similar to librosa's resample functionality, using linear interpolation
        double ratio = (double) originalRate / targetRate;

        for (int i = 0; i < framesOut; i++) {
            double sourceIdx = i * ratio;
            int sourceIdxInt = (int) sourceIdx;
            double frac = sourceIdx - sourceIdxInt;

            // Linear interpolation
            if (sourceIdxInt < framesIn - 1) {
                output[i] = (float) ((1.0 - frac) * input[sourceIdxInt] +
                        frac * input[sourceIdxInt + 1]);
            } else if (sourceIdxInt < framesIn) {
                output[i] = input[sourceIdxInt];
            }
        }
    }

    private float[] convertStereoToMono(float[] stereoData) {
        if (stereoData.length % 2 != 0) {
            Log.w(TAG, "Warning: Audio data length is not even, might not be stereo");
            return stereoData;
        }

        float[] monoData = new float[stereoData.length / 2];
        for (int i = 0; i < monoData.length; i++) {
            monoData[i] = (stereoData[i * 2] + stereoData[i * 2 + 1]) / 2.0f;
        }

        Log.d(TAG, String.format("Converted %d stereo samples to %d mono samples",
                stereoData.length, monoData.length));

        return monoData;
    }

    private List<float[]> calculateAndCreateChunks(float[] audio) {
        List<float[]> chunks = new ArrayList<>();
        int start = 0;

        while (start < audio.length) {
            int end = Math.min(start + N_SAMPLES, audio.length);
            float[] chunk = Arrays.copyOfRange(audio, start, end);

            chunks.add(chunk);

            // Log chunking results
            Log.d(TAG, String.format("Created chunk %d: start=%d, end=%d, length=%d",
                    chunks.size(), start, end, chunk.length));

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
            Log.d(TAG, "Computing mel spectrogram, length of audio chunk: " + audioChunk.length);
            MelSpectrogram melSpectrogram = new MelSpectrogram();
            double[][] melSpec = melSpectrogram.melSpectrogram(audioChunk);

            Log.d(TAG, "Mel spectrogram: " + Arrays.toString(melSpec[0]));

            // Pad or trim to exactly 3000 frames
            int targetFrames = 3000;
            float[][][] inputTensor = new float[1][N_MELS][targetFrames];

            // Copy and normalize mel spectrogram into input tensor
            for (int i = 0; i < N_MELS; i++) {
                for (int j = 0; j < targetFrames; j++) {
                    if (j < melSpec[i].length) {
                        double value = Math.max(melSpec[i][j], 1e-10);
                        double logValue = Math.log10(value);
                        inputTensor[0][i][j] = (float)((logValue + 4.0) / 4.0);
                    }
                }
            }

            // Log normalized values
            Log.d(TAG, "Normalized mel spectrogram: " + Arrays.deepToString(inputTensor[0]));

            // Run inference
            Log.d(TAG, "Running inference...");
            int[][] outputTensor = new int[1][448];
            tfliteInterpreter.run(inputTensor, outputTensor);

            // Log output tokens
            Log.d(TAG, "Output tokens: " + Arrays.toString(outputTensor[0]));

            // Process tokens
            Log.d(TAG, "Processing output tokens...");
            List<Integer> validTokens = new ArrayList<>();
            for (int token : outputTensor[0]) {
                if (token >= 0 && token < 50258) {
                    validTokens.add(token);
                }
            }

            Log.d(TAG, "Decoding tokens...");
            return tokenizer.decode(validTokens, true);
        } catch (Exception e) {
            Log.e(TAG, "Error processing audio chunk", e);
            return "";
        }
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