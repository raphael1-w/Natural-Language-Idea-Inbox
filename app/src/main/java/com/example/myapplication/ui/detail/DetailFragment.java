package com.example.myapplication.ui.detail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.room.Room;

import com.example.myapplication.R;
import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.AttachmentsDao;
import com.example.myapplication.database.IdeasDao;
import com.example.myapplication.database.Ideas_table;
import com.example.myapplication.databinding.FragmentDetailBinding;
import com.example.myapplication.summarizationService.SummarizeService;
import com.example.myapplication.transcriptionService.TranscribeService;
import com.example.myapplication.ui.home.HomeViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DetailFragment extends Fragment implements TranscribeService.TranscriptionCallback, SummarizeService.SummarizationCallback {
    private TextView typeView, tagsView, dateView, durationView;
    private FragmentDetailBinding binding;
    private TranscribeService transcribeService;
    private SummarizeService summarizeService;
    private boolean isTranscribeServiceBound = false;
    private boolean isSummarizeServiceBound = false;
    private boolean isTextIdea;
    private MaterialToolbar topAppBar;
    private AppDatabase db;
    private IdeasDao ideasDao;
    private AttachmentsDao attachmentsDao;
    private Ideas_table thisIdea;
    private String transcriptFilePath, textFilePath, summaryFilePath, recordingFilePath;
    private String currentShownFile;
    private HashMap<String, String> fileCache = new HashMap<String, String>();
    private boolean hasChanges = false;
    private MediaPlayer mediaPlayer;
    private int recordingDuration;
    private Handler handler = new Handler();
    private Runnable updateSeekBar;
    private boolean ignoreTextChanges = false;
    public static boolean isTranscriptionServiceStarted = false;
    public static boolean isSummarizationServiceStarted = false;
    private boolean generatingSummary = false;

    public static DetailFragment newInstance(Ideas_table idea) {
        DetailFragment fragment = new DetailFragment();
        Bundle args = new Bundle();
        args.putInt("idea_id", idea.id);
        args.putString("type", idea.type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = inflater.inflate(R.layout.fragment_detail, container, false);
        binding = FragmentDetailBinding.bind(view);

        // Initialize database and DAOs
        db = Room.databaseBuilder(requireContext(), AppDatabase.class, "app-database").build();
        ideasDao = db.ideasDao();
        attachmentsDao = db.attachmentsDao();

        topAppBar = view.findViewById(R.id.topAppBar);

        assert getArguments() != null;
        if (Objects.equals(getArguments().getString("type"), "text")) {
            isTextIdea = true;
        }

        // Start thread to fetch data from the database and update the UI
        Thread dbThread = getDbThread();
        dbThread.start();

        try { // Wait for the thread to finish before proceeding
            dbThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Set the path of the required text files
        transcriptFilePath = thisIdea.transcript_file_path;
        textFilePath = thisIdea.text_file_path;
        summaryFilePath = thisIdea.summary_file_path;
        recordingFilePath = thisIdea.recording_file_path;

        // Set up click listeners for the top app bar
        topAppBar.setNavigationOnClickListener(v -> handleNavigationWithUnsavedChanges());

        // Set up back button callback to handle unsaved changes
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleNavigationWithUnsavedChanges();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), callback);

        topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                // Save the changes to the database
                saveChanges();
                return true;
            }
            if (item.getItemId() == R.id.action_delete) {
                // Delete the idea from the database
                showDeleteDialog();
                return true;
            }
            return false;
        });

        // Set the save button to not be visible by default
        if (!hasChanges) {
            // make the save button not visible
            topAppBar.findViewById(R.id.action_save).setVisibility(View.GONE);
        }

        // Set up listener for changes in the text area
        binding.editableTextArea.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (ignoreTextChanges) {
                    return;
                }
                // Update the cache with the new text when the user edits the text
                hasChanges = true;
                fileCache.put(currentShownFile, s.toString());

            }

            @Override
            public void afterTextChanged(Editable s) {
                // Show the save button
                if (hasChanges) {
                    topAppBar.findViewById(R.id.action_save).setVisibility(View.VISIBLE);
                    topAppBar.setSubtitle(getResources().getString(R.string.unsaved_changes));
                }
            }
        });

        // Set up the segmented buttons for selecting the file to show
        MaterialButtonToggleGroup toggleButton = view.findViewById(R.id.segmentedButtons);
        toggleButton.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_transcript) {
                    showTextFiles(transcriptFilePath, "transcript");
                } else if (checkedId == R.id.btn_userText) {
                    showTextFiles(textFilePath, "userText");
                } else {
                    showTextFiles(summaryFilePath, "summary");
                }
            }
        });

        // Set up the start service button
        binding.startServiceButton.setOnClickListener(v -> {

            // Check which button is selected to determine the service to start
            if (binding.segmentedButtons.getCheckedButtonId() == R.id.btn_transcript) {
                Log.d("DetailFragment", "Transcribe button clicked, starting transcription service");

                isTranscriptionServiceStarted = true;
                binding.startServiceButton.setVisibility(View.GONE);
                binding.EmptyFileText.setText(getResources().getString(R.string.transcription_in_progress_message));

                // Bind to service first
                Intent bindIntent = new Intent(requireContext(), TranscribeService.class);
                requireContext().bindService(bindIntent, serviceConnectionTranscribe, Context.BIND_AUTO_CREATE);

                // Start the transcription service
                Intent intent = new Intent(requireContext(), TranscribeService.class);
                intent.putExtra("audioFilePath", recordingFilePath);
                intent.putExtra("id", thisIdea.id);
                requireContext().startForegroundService(intent);
            } else {
                Log.d("DetailFragment", "Summarize button clicked, starting summarization service");

                isSummarizationServiceStarted = true;
                binding.startServiceButton.setVisibility(View.GONE);
                binding.EmptyFileText.setText(getResources().getString(R.string.summarization_in_progress_message));

                // Bind to service first
                Intent bindIntent = new Intent(requireContext(), SummarizeService.class);
                requireContext().bindService(bindIntent, serviceConnectionSummarize, Context.BIND_AUTO_CREATE);

                // Start the summarization service
                Intent intent = new Intent(requireContext(), SummarizeService.class);
                intent.putExtra("isTextIdea", isTextIdea);
                intent.putExtra("transcriptFilePath", transcriptFilePath);
                intent.putExtra("textFilePath", textFilePath);
                intent.putExtra("id", thisIdea.id);

                requireContext().startForegroundService(intent);
            }

        });

        if (!isTextIdea) {
            try {
                prepareMediaPLayerAndControls();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return view;
    }

    @NonNull
    private Thread getDbThread() {
        final Handler handler = new Handler(Looper.getMainLooper());

        // Fetch data on a background thread
        // Update UI on main thread
        return new Thread(() -> {
            try {
                assert getArguments() != null;
                // Fetch the idea from the database
                thisIdea = ideasDao.getIdeaById(getArguments().getInt("idea_id"));

                // Update UI on main thread
                handler.post(this::updateUI);
            } catch (Exception e) {
                Log.e("DetailFragment", "Error fetching idea: ", e);
                handler.post(() -> Toast.makeText(requireContext(), "Error loading idea", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateUI() { // Method to update UI with the fetched data
        // Set the title of the top app bar
        topAppBar.setTitle(thisIdea.title);

        // Configure UI for text ideas
        if (isTextIdea) {
            // Remove transcript button, then selecting the user text button
            binding.btnTranscript.setVisibility(View.GONE);
            binding.btnUserText.setChecked(true);

            // Hide audio controls
            binding.audioControls.setVisibility(View.GONE);
            binding.segmentedButtons.check(R.id.btn_userText);

            showTextFiles(textFilePath, "userText");
        } else {
            showTextFiles(transcriptFilePath, "transcript");
        }
    }

    private void showTextFiles(String filePath, String fileType) {
        currentShownFile = fileType;
        Log.d("DetailFragment", "cache file: " + fileCache);
        if (fileCache.containsKey(fileType)) {
            binding.EmptyFileText.setVisibility(View.GONE);
            binding.startServiceButton.setVisibility(View.GONE);
            binding.SummaryTempText.setVisibility(View.GONE);

            ignoreTextChanges = true;
            binding.editableTextArea.setText(fileCache.get(fileType));
            binding.editableTextArea.setVisibility(View.VISIBLE);
            ignoreTextChanges = false;

            Log.d("DetailFragment", "Loaded from cache: " + filePath);

        } else if (filePath != null) { // If the text file is available but not in cache
            binding.EmptyFileText.setVisibility(View.GONE);
            binding.startServiceButton.setVisibility(View.GONE);
            binding.SummaryTempText.setVisibility(View.GONE);
            // Open the file and show the text in the editableTextArea
            StringBuilder text = getStringBuilder(filePath);

            ignoreTextChanges = true;
            binding.editableTextArea.setText(text.toString());
            binding.editableTextArea.setVisibility(View.VISIBLE);
            ignoreTextChanges = false;

            // Cache the file
            fileCache.put(fileType, text.toString());

            Log.d("DetailFragment", "Showing text file: " + filePath);

        } else { // If the text file is not available
            binding.editableTextArea.setVisibility(View.GONE);

            // Show no files available in EmptyFileText
            String currentFileText = "";
            switch (currentShownFile) {
                case "transcript":
                    if (!isTranscriptionServiceStarted) { // If transcription service is not running
                        binding.startServiceButton.setVisibility(View.VISIBLE);
                        binding.startServiceButton.setText(getResources().getString(R.string.start_transcribe_button));
                        currentFileText = getResources().getString(R.string.transcript_file_tab);
                    } else {
                        binding.startServiceButton.setVisibility(View.GONE);
                        binding.EmptyFileText.setText(getResources().getString(R.string.transcription_in_progress_message));
                    }

                    break;
                case "userText":
                    currentFileText = getResources().getString(R.string.notes_file_tab);
                     binding.startServiceButton.setVisibility(View.GONE);
                    // Don't think the above is needed cuz the text box already fills up the whole box?
                    break;
                case "summary":
                    if (!isSummarizationServiceStarted) { // If transcription service is not running
                        binding.startServiceButton.setVisibility(View.VISIBLE);
                        binding.startServiceButton.setText(getResources().getString(R.string.start_summarize_button));
                        currentFileText = getResources().getString(R.string.summary_file_tab);
                    } else {
                        binding.startServiceButton.setVisibility(View.GONE);
                        binding.EmptyFileText.setText(getResources().getString(R.string.summarization_in_progress_message));
                    }
                    break;
            }
            binding.EmptyFileText.setVisibility(View.VISIBLE);
            binding.EmptyFileText.setText(getResources().getString(R.string.file_not_available, currentFileText));
        }

        Log.d("DetailFragment", "cache file after: " + fileCache);
    }

    @NonNull
    private static StringBuilder getStringBuilder(String filePath) {
        File file = new File(filePath);
        // Read the file and show the text in the editableTextArea
        StringBuilder text = new StringBuilder();
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                text.append(scanner.nextLine()).append("\n");
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return text;
    }

    private void saveChanges() { // Save changes to text files (transcripts/notes/summaries)
        topAppBar.setSubtitle(getResources().getString(R.string.saving_changes));

        // Save the text changes from the cache to the files
        for (String fileType : fileCache.keySet()) {
            String filePath = null;
            switch (fileType) {
                case "transcript":
                    filePath = transcriptFilePath;
                    break;
                case "userText":
                    filePath = textFilePath;
                    break;
                case "summary":
                    filePath = summaryFilePath;
                    break;
            }

            if (filePath != null) {
                try (FileWriter writer = new FileWriter(filePath)) {
                    writer.write(fileCache.get(fileType));
                } catch (IOException e) {
                    Log.e("DetailFragment", "Error saving file: " + filePath, e);
                    Toast.makeText(requireContext(), "Error saving file: " + filePath, Toast.LENGTH_SHORT).show();
                }
            }
        }

        topAppBar.setSubtitle(getResources().getString(R.string.all_changes_saved));
        hasChanges = false;
        topAppBar.findViewById(R.id.action_save).setVisibility(View.GONE);
    }

    private void prepareMediaPLayerAndControls() throws IOException {
        // Set up the media player
        mediaPlayer = new MediaPlayer();
        mediaPlayer.reset();
        assert recordingFilePath != null;
        mediaPlayer.setDataSource(recordingFilePath);
        mediaPlayer.prepare();
        mediaPlayer.setLooping(false);

        // Set up listeners for audio controls
        MaterialButton playPauseButton = binding.getRoot().findViewById(R.id.playPauseButton);
        MaterialButton rewindButton = binding.getRoot().findViewById(R.id.rewindButton);
        MaterialButton forwardButton = binding.getRoot().findViewById(R.id.forwardButton);
        Slider audioProgressSlider = binding.getRoot().findViewById(R.id.audioProgressSlider);
        TextView currentDurationText = binding.getRoot().findViewById(R.id.currentDurationText);
        TextView totalDurationText = binding.getRoot().findViewById(R.id.totalDurationText);

        // Set max value of the slider and the toto the duration of the audio
        recordingDuration = mediaPlayer.getDuration();
        audioProgressSlider.setValueTo(recordingDuration);
        totalDurationText.setText(formatDuration(recordingDuration));


        // Runnable to update slider position
        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    audioProgressSlider.setValue(mediaPlayer.getCurrentPosition());
                    currentDurationText.setText(formatDuration(mediaPlayer.getCurrentPosition()));
                    handler.postDelayed(this, 100); // Update every 100ms
                }
            }
        };

        mediaPlayer.setOnCompletionListener(mp -> {
            playPauseButton.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_play, null));
            handler.removeCallbacks(updateSeekBar);
            // Set the slider and current duration text to start
            audioProgressSlider.setValue(0);
            currentDurationText.setText(formatDuration(0));
        });

        playPauseButton.setOnClickListener(v -> {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                playPauseButton.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_play, null));
                handler.removeCallbacks(updateSeekBar);
            } else {
                mediaPlayer.start();
                playPauseButton.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_pause, null));
                handler.post(updateSeekBar);
            }
        });

        rewindButton.setOnClickListener(v -> {
            // Rewind in 5 second intervals
            mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() - 5000);
            audioProgressSlider.setValue(mediaPlayer.getCurrentPosition());
        });

        forwardButton.setOnClickListener(v -> {
            // Fast forward in 5 second intervals
            mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() + 5000);
            audioProgressSlider.setValue(mediaPlayer.getCurrentPosition());
        });

        // Seek to position when user moves the slider and change the current duration text
        audioProgressSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                mediaPlayer.seekTo((int) value);
                currentDurationText.setText(formatDuration((int) value));
            }
        });
    }

    private String formatDuration(long durationMs) {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / 1000) / 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private void handleNavigationWithUnsavedChanges() {
        if (hasChanges) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
            builder.setTitle(requireContext().getResources().getString(R.string.unsaved_changes_title));
            builder.setMessage(requireContext().getResources().getString(R.string.unsaved_changes_message));
            builder.setPositiveButton(requireContext().getResources().getString(R.string.unsaved_changes_positive_dialog),
                    (dialog, which) -> {
                        requireActivity().getSupportFragmentManager().popBackStack();
                    });
            builder.setNegativeButton(requireContext().getResources().getString(R.string.unsaved_changes_negative_dialog), null);
            builder.show();
        } else {
            requireActivity().getSupportFragmentManager().popBackStack();
        }
    }

    private void showDeleteDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(requireContext().getResources().getString(R.string.delete_dialog_title));
        builder.setMessage(requireContext().getResources().getString(R.string.delete_dialog_message));
        builder.setPositiveButton(requireContext().getResources().getString(R.string.delete_dialog_positive_dialog),
                (dialog, which) -> {
                    deleteIdea();
                });
        builder.setNegativeButton(requireContext().getResources().getString(R.string.delete_dialog_negative_dialog), null);
        builder.show();
    }

    private void deleteIdea() {
        // TODO: Accommodate attachments deletion

        logAllFilesInDirectories(transcriptFilePath, textFilePath, summaryFilePath, recordingFilePath);

        // Delete files
        AtomicBoolean allFilesDeleted = new AtomicBoolean(true); // Track overall success

        String[] filePaths = {transcriptFilePath, textFilePath, summaryFilePath, recordingFilePath};

        // Create a thread pool (adjust the number of threads as needed)
        ExecutorService executorService = Executors.newFixedThreadPool(4);

        for (String filePath : filePaths) {
            if (filePath != null && !filePath.isEmpty()) { // Check for null AND empty string
                // Create a Runnable task for file deletion
                Runnable fileDeletionTask = () -> {
                    File file = new File(filePath);
                    if (file.exists()) { // Check if the file exists
                        boolean deleted = file.delete();
                        if (!deleted) {
                            Log.e("deleteIdea", "Failed to delete file: " + filePath);
                            allFilesDeleted.set(false); // Mark overall failure
                            // Consider:  Collect failed file paths for later retry/cleanup
                        } else {
                            Log.d("deleteIdea", "File deleted successfully: " + filePath);
                        }
                    } else {
                        Log.w("deleteIdea", "File not found: " + filePath);
                    }
                };

                // Submit the task to the executor service
                executorService.execute(fileDeletionTask);
            }
        }

        // Shutdown the executor service after all tasks are submitted
        executorService.shutdown();

        Handler mainHandler = new Handler(Looper.getMainLooper()); // Handler to run code on the UI thread
        HomeViewModel viewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        new Thread(() -> {
            boolean dbDeleteSuccess = true; // Track database deletion success

            try {
                // Delete the idea from the database
                ideasDao.delete(thisIdea);
            } catch (Exception e) {
                Log.e("deleteIdea", "Error deleting idea: " + e.getMessage(), e);
                dbDeleteSuccess = false;

                // Update UI on the main thread to show the error
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "Error deleting idea", Toast.LENGTH_SHORT).show();
                });
            } finally {
                // Always run this code on the main thread after background task completes
                boolean finalDbDeleteSuccess = dbDeleteSuccess;
                mainHandler.post(() -> {

                    String snackbarMessage = "";

                    if (!allFilesDeleted.get()) {
                        // Display a warning to the user (optional)
                        Log.w("deleteIdea", "Some files were not deleted.");
                        snackbarMessage = "Idea deleted, but some files may not have been removed.";
                    } else if (!finalDbDeleteSuccess) {
                        // Display a warning to the user (optional)
                        Log.w("deleteIdea", "Idea was deleted, but an error occurred while deleting from the database.");
                        snackbarMessage = "Idea deleted, but an error occurred while deleting from the database.";
                    } else {
                        // Show a snackbar to confirm deletion
                        snackbarMessage = "Idea deleted";
                    }

                    viewModel.setDeletionResult(snackbarMessage);

                    // Navigate back to the list fragment
                    requireActivity().getSupportFragmentManager().popBackStack();

                    logAllFilesInDirectories(transcriptFilePath, textFilePath, summaryFilePath, recordingFilePath);
                });
            }
        }).start();
    }

    public static void logAllFilesInDirectories(String transcriptFilePath, String textFilePath, String summaryFilePath, String recordingFilePath) {
        Log.d("DetailFragment", "Logging all files in directories");

        final String TAG = "FileLogger"; // For Logcat filtering

        logFiles(TAG, "Transcript Files", transcriptFilePath);
        logFiles(TAG, "Text Files", textFilePath);
        logFiles(TAG, "Summary Files", summaryFilePath);
        logFiles(TAG, "Recording Files", recordingFilePath);
    }

    private static void logFiles(String TAG, String fileType, String filePath) {
        if (filePath != null && !filePath.isEmpty()) {
            File file = new File(filePath);
            File directory = file.getParentFile(); // Get the parent directory
            if (directory != null && directory.exists() && directory.isDirectory()) {
                String directoryPath = directory.getAbsolutePath();

                Log.d(TAG, "--- " + fileType + " (Directory: " + directoryPath + ") ---");
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File f : files) {
                        Log.d(TAG, "File: " + f.getAbsolutePath() + ", Size: " + f.length() + " bytes");
                    }
                } else {
                    Log.w(TAG, "Directory is empty or cannot be read: " + directoryPath);
                }
            } else {
                Log.w(TAG, "Directory does not exist or is not a directory: " + (directory != null ? directory.getAbsolutePath() : "null"));
            }
        } else {
            Log.w(TAG, "File path is null or empty for: " + fileType);
        }
    }

    private final ServiceConnection serviceConnectionTranscribe = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TranscribeService.LocalBinder binder = (TranscribeService.LocalBinder) service;
            transcribeService = binder.getService();
            transcribeService.setTranscriptionCallback(DetailFragment.this);
            isTranscribeServiceBound = true;
            Log.d("DetailFragment", "Service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            transcribeService = null;
            isTranscribeServiceBound = false;
            Log.d("DetailFragment", "Service disconnected");
        }
    };

    private final ServiceConnection serviceConnectionSummarize = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SummarizeService.LocalBinder binder = (SummarizeService.LocalBinder) service;
            summarizeService = binder.getService();
            summarizeService.setSummarizationCallback((SummarizeService.SummarizationCallback) DetailFragment.this);
            isSummarizeServiceBound = true;
            Log.d("DetailFragment", "Service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            summarizeService = null;
            isSummarizeServiceBound = false;
            Log.d("DetailFragment", "Service disconnected");
        }
    };

    @Override
    public void onTranscriptionProgress(String partialTranscript, float progress) {
        Log.d("DetailFragmentCallback", "Transcription progress callback: " + progress);
    }

    @Override
    public void onTranscriptionComplete(String transcriptFilePath) {
        requireActivity().runOnUiThread(() -> {
            // Update the UI to show the transcribed text
            this.transcriptFilePath = transcriptFilePath;

            // If the transcript button is selected, show the transcribed text
            if (binding.segmentedButtons.getCheckedButtonId() == R.id.btn_transcript) {
                showTextFiles(transcriptFilePath, "transcript");
            }

            isTranscriptionServiceStarted = false;
        });
    }

    @Override
    public void onSummarizationProgress(String partialResult) {
        requireActivity().runOnUiThread(() -> {
            Log.d("DetailFragmentCallback", "Summarization progress callback: " + partialResult);
            TextView summaryTempText = binding.getRoot().findViewById(R.id.SummaryTempText);

            generatingSummary = true;
            isTranscribeServiceBound = true;
            binding.getRoot().findViewById(R.id.EmptyFileText).setVisibility(View.GONE);
            summaryTempText.setVisibility(View.VISIBLE);
            summaryTempText.setText(partialResult);
        });

    }

    @Override
    public void onSummarizationComplete(String summaryFilePath) {
        requireActivity().runOnUiThread(() -> {
            // Update the UI to show the transcribed text
            this.summaryFilePath = summaryFilePath;

            // If the transcript button is selected, show the transcribed text
            if (binding.segmentedButtons.getCheckedButtonId() == R.id.btn_summary) {
                showTextFiles(summaryFilePath, "summary");
            }

            generatingSummary = false;
            isSummarizationServiceStarted = false;
        });
    }

    @Override
    public void onSummarizationError(String error) {

    }

    @Override
    public void onTranscriptionError(String error) {
        Log.e("DetailFragmentCallback", "Transcription error callback: " + error);
    }

    @Override
    public void onDestroyView() {
        if (isTranscribeServiceBound) {
            try {
                requireContext().unbindService(serviceConnectionTranscribe);
            } catch (IllegalArgumentException e) {
                Log.e("DetailFragment", "Error unbinding transcribe service", e);
            }
            isTranscribeServiceBound = false;
        }

        if (isSummarizeServiceBound) {
            try {
                requireContext().unbindService(serviceConnectionSummarize);
            } catch (IllegalArgumentException e) {
                Log.e("DetailFragment", "Error unbinding summarize service", e);
            }
            isSummarizeServiceBound = false;
        }

        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        db.close();
        binding = null;

        super.onDestroyView();
    }
}
