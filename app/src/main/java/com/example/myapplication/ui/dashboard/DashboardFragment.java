package com.example.myapplication.ui.dashboard;

import android.media.MediaRecorder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import java.io.IOException;
import java.util.Objects;

public class DashboardFragment extends Fragment {
    private FragmentDashboardBinding binding;
    private MediaRecorder mediaRecorder;
    boolean commitButtonIsRecord = true;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        DashboardViewModel dashboardViewModel =
                new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // The following section allows the capture bar to have the correct bottom margin regardless
        // of the height of the BottomNavigationView

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
            String audioFilePath = Objects.requireNonNull(requireContext().getExternalFilesDir(null)).getAbsolutePath() + "/recording.3gp";
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(audioFilePath);
            try {
                mediaRecorder.prepare();
                mediaRecorder.start();
                Toast.makeText(getContext(), "Recording started", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(getContext(), "Recording failed", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getContext(), "Recording stopped", Toast.LENGTH_SHORT).show();
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private void send() {
        //TODO: Implement sending the message
        Toast.makeText(getContext(), "Message sent", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Release the media recorder if it is currently active
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }

        binding = null;
    }
}