package com.example.myapplication.ui.detail;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.room.Room;

import com.example.myapplication.R;
import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.AttachmentsDao;
import com.example.myapplication.database.IdeasDao;
import com.example.myapplication.database.Ideas_table;
import com.example.myapplication.databinding.FragmentDetailBinding;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;

public class DetailFragment extends Fragment {
    private TextView typeView, tagsView, dateView, durationView;
    private FragmentDetailBinding binding;
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
        topAppBar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                // Save the changes to the database
                saveChanges();
                return true;
            }
            if (item.getItemId() == R.id.action_delete) {
                // Delete the idea from the database
                // TODO: Implement delete functionality
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
            ignoreTextChanges = true;
            binding.editableTextArea.setText(fileCache.get(fileType));
            binding.editableTextArea.setVisibility(View.VISIBLE);
            ignoreTextChanges = false;

            Log.d("DetailFragment", "Loaded from cache: " + filePath);

        } else if (filePath != null) { // If the text file is available but not in cache
            binding.EmptyFileText.setVisibility(View.GONE);
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
                    currentFileText = getResources().getString(R.string.transcript_file_tab);
                    break;
                case "userText":
                    currentFileText = getResources().getString(R.string.notes_file_tab);
                    break;
                case "summary":
                    currentFileText = getResources().getString(R.string.summary_file_tab);
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

    @Override
    public void onDestroyView() {
        //TODO Warn user of unsaved changes
        super.onDestroyView();
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        db.close();
        binding = null;
    }
}
