package com.example.myapplication.transcriptionService;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
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
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Initializing transcription service..."));
        initializeModel();
    }

    private void initializeModel() {
        try {
            // Load TFLite model
            MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(this, "whisper-base-external.tflite");
            Interpreter.Options options = new Interpreter.Options();
            tfliteInterpreter = new Interpreter(tfliteModel, options);

            // Initialize tokenizer
            String tokenizerPath = new File(getFilesDir(), "whisper-base").getAbsolutePath();
            tokenizer = new WhisperTokenizer(tokenizerPath);
        } catch (IOException e) {
            Log.e(TAG, "Error initializing model", e);
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
        float[] audio = loadAndResampleAudio(audioPath);

        List<float[]> chunks = createChunks(audio);
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

    private float[] loadAndResampleAudio(String audioPath) throws IOException {
        // Note: You'll need to implement audio loading and resampling
        // This could use Android's MediaExtractor or a library like FFmpeg
        // For now, this is a placeholder
        throw new UnsupportedOperationException("Audio loading not implemented");
    }

    private List<float[]> createChunks(float[] audio) {
        List<float[]> chunks = new ArrayList<>();
        int start = 0;

        while (start < audio.length) {
            int end = Math.min(start + N_SAMPLES, audio.length);
            float[] chunk = Arrays.copyOfRange(audio, start, end);

            if (chunk.length > N_SAMPLES * 0.25) {
                if (chunk.length < N_SAMPLES) {
                    chunk = padAudio(chunk);
                }
                chunks.add(chunk);
            }

            start = start + N_SAMPLES - N_SAMPLES_OVERLAP;
        }

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

            // Prepare input tensor
            float[][][][] inputTensor = new float[1][N_MELS][3000][1];
            for (int i = 0; i < N_MELS; i++) {
                for (int j = 0; j < Math.min(melSpec[i].length, 3000); j++) {
                    inputTensor[0][i][j][0] = melSpec[i][j];
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
        super.onDestroy();
        if (tfliteInterpreter != null) {
            tfliteInterpreter.close();
        }
        if (executor != null) {
            executor.shutdown();
        }
    }
}