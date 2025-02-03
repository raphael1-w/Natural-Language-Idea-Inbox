package com.example.myapplication.ui.dashboard;

import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
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

import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentDashboardBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class DashboardFragment extends Fragment {
    private FragmentDashboardBinding binding;
    private MediaRecorder mediaRecorder;
    boolean commitButtonIsRecord = true;
    String audioFilePath;
    File recordingDir;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        DashboardViewModel dashboardViewModel =
                new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

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
            };
        });

        // Get the input field
        EditText editText = binding.getRoot().findViewById(R.id.inputField);
        // Listen for changes to the input field
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Change the icon of the commit button based if the input field has text
                if (s.length() > 0) {
                    commitButtonIsRecord = false;
                    commitButton.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_send, null));
                } else {
                    commitButtonIsRecord = true;
                    commitButton.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_mic, null));
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

                cleanUp(audioFilePath); // Automatically delete the recording if it is too short
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

    private void cleanUp(String audioFilePath) {
        // Get the duration of the recording
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(audioFilePath);
        String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

        // If recording is less than 600 milliseconds, delete the file
        if (Integer.parseInt(duration) < 600) {
            new File(audioFilePath).delete();
            Log.d("Recording", "Deleted recording: " + audioFilePath);
        } else {
            Log.d("Files", "Audio file saved to " + audioFilePath);
        }
    }

    private void send() {
        //TODO: Implement sending the message
        Toast.makeText(getContext(), "Message sent", Toast.LENGTH_SHORT).show();
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

        binding = null;
    }
}