package com.example.myapplication.ui.dashboard;

import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.Manifest;
import android.content.pm.PackageManager;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.room.Room;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.AttachmentsDao;
import com.example.myapplication.database.IdeasDao;
import com.example.myapplication.database.Ideas_table;
import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentDashboardBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public class DashboardFragment extends Fragment {
    private FragmentDashboardBinding binding;
    private MediaRecorder mediaRecorder;
    boolean commitButtonIsRecord = true;
    String audioFilePath;
    Date date = new Date();
    File recordingDir;
    private AppDatabase db;
    IdeasDao ideasDao;
    AttachmentsDao attachmentsDao;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        DashboardViewModel dashboardViewModel =
                new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize database instance
        db = Room.databaseBuilder(requireContext(), AppDatabase.class, "app-database").build();
        ideasDao = db.ideasDao();
        attachmentsDao = db.attachmentsDao();

        // Log output the whole ideas table when the fragment is created
//        new Thread(() -> {
//            for (Ideas_table idea : ideasDao.getAll()) {
//                Log.d("Database", "Idea: " + idea.title + " created at " + idea.created_at + "type: " + idea.type);
//            }
//        }).start();

        // Calculate the bottom margin for the capture bar, adjusting for the BottomNavigationView
        calculateBottomMargin();

        // Set the greeting text
        final TextView textView = binding.promptText;
        dashboardViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        // Get the commit button
        MaterialButton commitButton = binding.getRoot().findViewById(R.id.commit_button);
        // React to the commit button being clicked
        commitButton.setOnClickListener(v -> {
            if (commitButtonIsRecord) {
                // If the commit button is set to record, start recording
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, 200);
                } else {
                    record();
                }
            } else {
                // If the commit button is set to send, send the message
                send();
            }
        });

        // Get the input field
        EditText editText = binding.getRoot().findViewById(R.id.inputField);
        // Listen for changes to the input field
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Determine the new icon
                int newIconRes = (s.length() > 0) ? R.drawable.ic_arrow_upward : R.drawable.ic_mic;

                // Only update the icon if amount of text has changed between 0 and 1
                if ((s.length() > 0 && commitButtonIsRecord) || (s.length() == 0 && !commitButtonIsRecord)) {
                    // Update the commit button state to determine the action
                    commitButtonIsRecord = (s.length() == 0);

                    // Animate the icon change
                    commitButton.animate()
                            .alpha(0f) // Fade out
                            .setDuration(50) // Animation duration
                            .withEndAction(() -> {
                                commitButton.setIcon(ResourcesCompat.getDrawable(getResources(), newIconRes, null));
                                commitButton.animate().alpha(1f).setDuration(100).start(); // Fade in
                            })
                            .start();
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
        });
        return root;
    }

    private void record() {
        if (mediaRecorder == null) {
            // Set the current date-time for database entry
            date.setTime(System.currentTimeMillis());

            // Get current date-time with alphanumeric values to name the audio file
            String currentDateTime = java.time.LocalDateTime.now().toString().replaceAll("[^a-zA-Z0-9]", "");

            // Create the audio file
            recordingDir = new File(requireContext().getFilesDir(), "/recordings");
            File audioFile = new File(recordingDir, currentDateTime + "_audio.m4a");
            audioFilePath = audioFile.getAbsolutePath();
            Log.d("Files", "Audio file created at " + audioFilePath);

            // Prepare the media recorder
            mediaRecorder = new MediaRecorder(requireContext());
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(16 * 44100);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setOutputFile(audioFilePath);

            try { // Start recording
                mediaRecorder.prepare();
                mediaRecorder.start();
                Toast.makeText(getContext(), "Recording started", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e("Recording", "Recording failed", e);
            }
        } else { // Stop recording
            try {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;

                boolean isAudioFileSaved = cleanUp(audioFilePath); // Automatically delete the recording if it is too short

                // Add audio idea entry to database if audio file is saved
                if (isAudioFileSaved) {
                    // Get the file name from the audio file path
                    String fileName = "AUD_" + audioFilePath.replaceAll(".*/|[^0-9]", "").substring(0, 12);

                    insertToDatabase(fileName, date, "audio", audioFilePath,false);
                }
            } catch (RuntimeException e) {
                // if no valid audio data has been received when stop() is called
                // This happens if stop() is called immediately after start()

                // Clean up the output file
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
                new File(audioFilePath).delete();

                Log.e("Recording", "Deleted recording", e);
            }
            Toast.makeText(getContext(), "Recording stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean cleanUp(String audioFilePath) {
        long durationLong = getRecordingDuration(audioFilePath);

        // If recording is less than 600 milliseconds, delete the file
        if (durationLong < 600) {
            new File(audioFilePath).delete();
            Log.d("Recording", "Deleted recording: " + audioFilePath);
            return false;
        } else {
            Log.d("Files", "Audio file saved to " + audioFilePath);
            return true;
        }
    }

    private static long getRecordingDuration(String audioFilePath) {
        // Get the duration of the recording
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(audioFilePath);
        String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        return Long.parseLong(duration);
    }

    private void insertToDatabase(String title, Date date, String type, String filePath, boolean hasAttachments) {

        // Create a new idea object
        Ideas_table idea = new Ideas_table();
        idea.title = title;
        idea.type = type;
        idea.created_at = date;
        idea.updated_at = date;

        if (type.equals("audio")) {
            idea.recording_file_path = filePath;
            idea.recording_duration = getRecordingDuration(filePath);
        } else { // Type is text
            idea.text_file_path = filePath;
        }

        idea.has_attachments = hasAttachments;

        // Insert the idea into the database in a new thread
        new Thread(() -> {
            ideasDao.insertAll(idea);
            Log.d("Database", "Inserted idea: " + idea.title);
        }).start();
    }

    private void send() {
        // Get the input field
        EditText editText = binding.getRoot().findViewById(R.id.inputField);
        String text = editText.getText().toString();

        // If the input field is empty, do nothing
        if (text.isEmpty()) {
            return;
        }

        // Set the current date-time for database entry
        date.setTime(System.currentTimeMillis());

        // Get current date-time with alphanumeric values to name the text file
        String currentDateTime = java.time.LocalDateTime.now().toString().replaceAll("[^a-zA-Z0-9]", "");

        // Create the text file
        File textDir = new File(requireContext().getFilesDir(), "/texts");
        File textFile = new File(textDir, currentDateTime + "_text.txt");
        String textFilePath = textFile.getAbsolutePath();
        Log.d("Files", "Text file created at " + textFilePath);

        // Write the text to the file
        try {
            textFile.createNewFile();
            java.io.FileWriter writer = new java.io.FileWriter(textFile);
            writer.write(text);
            writer.close();
        } catch (IOException e) {
            Log.e("Files", "Error writing text file", e);
        }

        // Get the file name from the audio file path
        String fileName = "TXT_" + textFilePath.replaceAll(".*/|[^0-9]", "").substring(0, 12);

        // Add text idea entry to database
        insertToDatabase(fileName, date, "text", textFilePath, false);

        // Clear the input field
        editText.setText("");
    }

    private void calculateBottomMargin() { // Calculate the bottom margin for the capture bar

        // Find your BottomNavigationView in the Activity
        BottomNavigationView bottomNavigationView = requireActivity().findViewById(R.id.nav_view);

        // Find your element in the Fragment's layout
        View yourElement = binding.getRoot().findViewById(R.id.main_bar);

        // Add a listener to calculate the height of the BottomNavigationView
        bottomNavigationView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Get the height of the BottomNavigationView
                int navBarHeight = bottomNavigationView.getHeight();

                // Set the bottom margin for your element
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) yourElement.getLayoutParams();
                params.bottomMargin = navBarHeight + Math.round(8 * getResources().getDisplayMetrics().density); // 8dp above the nav bar
                yourElement.setLayoutParams(params);

                // Remove the listener to avoid multiple calls
                bottomNavigationView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // TODO: Warn the user if they are about to lose their recording
        // Release the media recorder if it is currently active
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }

        // Release the database instance
        db.close();

        binding = null;
    }
}