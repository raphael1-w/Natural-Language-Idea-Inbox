package com.example.myapplication.summarizationService;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.room.Room;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.IdeasDao;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SummarizeService extends Service {
    private static final String TAG = "SummarizeService";
    private static final String CHANNEL_ID = "SummarizationServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private final IBinder binder = new LocalBinder();
    private ExecutorService executor;
    private SummarizationCallback callback;
    private int id;
    private String transcriptFilePath;
    private String summaryFilePath;



    public class LocalBinder extends Binder {
        public SummarizeService getService() {
            return SummarizeService.this;
        }
    }

    public interface SummarizationCallback {
        void onSummarizationProgress();
        void onSummarizationComplete(String transcriptFilePath);
        void onSummarizationError(String error);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification("Initializing summarization service..."));

        if (intent != null) {
            id = intent.getIntExtra("id", -1);
            transcriptFilePath = intent.getStringExtra("transcriptFilePath");
        }

        // Start summarization
        if (transcriptFilePath != null) {
            Log.d(TAG, "Starting transcription...");
            startSummarization(transcriptFilePath);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("TranscribeService", "Service created");
        executor = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }

    public void setSummarizationCallback(SummarizationCallback callback) {
        this.callback = callback;
    }

    public void startSummarization(String transcriptPath) {
        executor.execute(() -> {
            try {
                String summary = SummarizeTranscript(transcriptPath);
                Log.d(TAG, "Summarization complete: " + summary);
//                saveSummary(summary);
                if (callback != null) {
                    updateNotification("Summarization complete! ");
                    callback.onSummarizationComplete(transcriptFilePath);
                }
                stopSelf();
            } catch (Exception e) {
                Log.e(TAG, "Summarization error", e);
                if (callback != null) {
                    callback.onSummarizationComplete(transcriptFilePath);
                }
                stopSelf(); // stop service either way
            }
        });
    }

    private String SummarizeTranscript(String transcriptPath) {
        updateNotification("Summarizing transcript...");

        String transcript = getTranscriptText(transcriptPath);

        String modelPath = "/data/local/tmp/llm_model/gemma2-2b-it-cpu-int8.task";
        String modelName = "gemma-2-2b-it-cpu-int8.task";

        String summarizationPrompt = "Summarize the following text: ";

        String results = null;

        LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .setResultListener( (partialResult, done) ->
                        Log.d("SummarizationService", "Partial result: " + partialResult))
                .build();

        Log.d(TAG, "Summarization prompt: " + summarizationPrompt + transcript);

        try {
            Log.d(TAG, "Creating LLM inference...");
            LlmInference llmInference = LlmInference.createFromOptions(getApplicationContext(), options);

            Log.d(TAG, "Generating response...");
            results = llmInference.generateResponse(summarizationPrompt + transcript);

            Log.d(TAG, "Summarization results: " + results);

            return results;

        } catch (Exception e) {
            Log.e(TAG, "Error creating LLM inference", e);
            return null;
        }
    }

    private String getTranscriptText(String transcriptPath) {
        Log.d(TAG, "Reading transcript file: " + transcriptPath);

        File transcriptFile = new File(transcriptPath);
        StringBuilder transcript = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(transcriptFile));
            String line;
            while ((line = reader.readLine()) != null) {
                transcript.append(line).append("\n");
            }
            reader.close();
            Log.d(TAG, "Transcript: " + transcript);
            return transcript.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error reading transcript file", e);
        }
        return "";
    }


    private void saveSummary(String summary) {
        updateNotification("Saving transcription...");

        AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "app-database").build();
        IdeasDao ideasDao = db.ideasDao();

        summaryFilePath = transcriptFilePath.replace("transcripts", "summaries")
                .replace("_transcript.txt", "_summary.txt");

        File summaryFile = new File(summaryFilePath);
        try {
            // Save transcription to file
            summaryFile.createNewFile();
            FileWriter writer = new FileWriter(summaryFile);
            writer.write(summary);
            writer.close();
            Log.d(TAG, "Saved summary to file: " + summaryFilePath);

            // Update database
            new Thread (() -> {
                ideasDao.updateSummary(id, summaryFilePath);
                Log.d(TAG, "Updated summary in database: " + id);
            }).start();

        } catch (IOException e) {
            Log.e(TAG, "Error saving summary", e);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Summarization Service",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

    }

    private Notification createNotification(String message) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Idea Summarization")
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
        Log.d(TAG, "Service destroyed");

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.cancel(NOTIFICATION_ID);

        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }

    }
}