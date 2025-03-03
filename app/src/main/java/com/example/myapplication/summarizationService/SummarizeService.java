package com.example.myapplication.summarizationService;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.room.Room;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.IdeasDao;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.genai.llminference.*;
import com.google.mediapipe.tasks.core.BaseOptions;

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
    private String textFilePath;
    private boolean isTextIdea;
    private String summaryFilePath;
    private LlmInference llmInference;
    private LlmInferenceSession llmInferenceSession;
    private String summary;



    public class LocalBinder extends Binder {
        public SummarizeService getService() {
            return SummarizeService.this;
        }
    }

    public interface SummarizationCallback {
        void onSummarizationProgress(String partialResult);
        void onSummarizationComplete(String summaryFilePath);
        void onSummarizationError(String error);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification("Initializing summarization service..."));

        if (intent != null) {
            id = intent.getIntExtra("id", -1);
            transcriptFilePath = intent.getStringExtra("transcriptFilePath");
            textFilePath = intent.getStringExtra("textFilePath");
            isTextIdea = intent.getBooleanExtra("isTextIdea", false);
        }

        // Start summarization
        Log.d(TAG, "Starting summarization...");
        startSummarization(transcriptFilePath, textFilePath, isTextIdea);

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

    public void startSummarization(String transcriptPath, String textPath, boolean isTextIdea) {
        executor.execute(() -> {
            try {
                SummarizeIdea(transcriptPath, textPath, isTextIdea);
                stopSelf();
            } catch (Exception e) {
                Log.e(TAG, "Summarization error", e);
                if (callback != null) {
                    callback.onSummarizationError(String.valueOf(e));
                }
                stopSelf(); // stop service either way
            }
        });
    }

    private void SummarizeIdea(String transcriptPath, String textPath, boolean isTextIdea) {
        updateNotification("Summarizing idea...");

        Log.d(TAG, "Files: " + transcriptPath + ", " + textPath);

        String transcript = "";
        String userText = "";

        if (transcriptPath != null) {
            transcript = getText(transcriptPath);
        }

        if (textPath != null) {
            userText = getText(textPath);
        }

        String modelPath = "/data/local/tmp/llm_model/gemma2-2b-it-cpu-int8.task";
        String modelPathGPU = "/data/local/tmp/llm_model/gemma2-2b-it-gpu-int8.bin";

        String summarizationPrompt = "Generate a detailed summary of the following idea by the user. " +
                "An idea may contain a voice transcription and the user's written note." +
                "List out the key information in point form. " +
                "Only generate the summary, nothing else. Start the message with **Summary**. " +
                "Your message will be directly used in a summary text file. Be mindful of the formatting requirements. \n";
        if (!isTextIdea) summarizationPrompt += "Transcription of voice note:\n" + userText + "\n";
        summarizationPrompt += "User's written note: " + transcript;

        StringBuilder results = new StringBuilder();

        BaseOptions baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelPathGPU)
                .setDelegate(Delegate.GPU)
                .build();

        LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPathGPU)
                .setMaxTokens(1024)
                .setResultListener((partialResult, done) -> {
                    results.append(partialResult);
                    if (done) {
                        Log.d(TAG, "Complete result: " + results);
                        summary = results.toString();
                        Log.d(TAG, "Summarization complete: " + summary);
                        saveSummary(summary);
                    } else {
                        Log.d(TAG, "Partial result: " + results);
                        callback.onSummarizationProgress(results.toString());
                    }
                })
                .build();

        LlmInferenceSession.LlmInferenceSessionOptions sessionOption = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.6f)
                .setTopK(40)
                .setTopP(0.9f)
                .setRandomSeed(101)
                .build();

        try {
            Log.d(TAG, "Creating LLM inference...");
            llmInference = LlmInference.createFromOptions(getApplicationContext(), options);

            llmInferenceSession = LlmInferenceSession.createFromOptions(llmInference, sessionOption);

            Log.d(TAG, "Generating response...");
            llmInferenceSession.addQueryChunk(summarizationPrompt);
            llmInferenceSession.generateResponseAsync();


        } catch (Exception e) {
            Log.e(TAG, "Error creating LLM inference", e);
        }

    }

    private String getText(String transcriptPath) {
        Log.d(TAG, "Reading file: " + transcriptPath);

        File transcriptFile = new File(transcriptPath);
        StringBuilder transcript = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(transcriptFile));
            String line;
            while ((line = reader.readLine()) != null) {
                transcript.append(line).append("\n");
            }
            reader.close();
            Log.d(TAG, "Text: " + transcript);
            return transcript.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error reading text file", e);
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

            // Update database and wait for completion
            executor.execute(() -> {
                try {
                    ideasDao.updateSummary(id, summaryFilePath);
                    Log.d(TAG, "Updated summary in database: " + id);

                    // Only notify callback after database update is complete
                    if (callback != null) {
                        updateNotification("Summarization complete!");
                        callback.onSummarizationComplete(summaryFilePath);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error updating database", e);
                    if (callback != null) {
                        callback.onSummarizationError("Failed to update database: " + e.getMessage());
                    }
                }
            });

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