package com.example.myapplication.ui.dashboard;

import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.TokenWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentDashboardBinding;
import com.example.myapplication.ui.recording.RecordingFragment;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.Objects;

public class DashboardFragment extends Fragment {
    private FragmentDashboardBinding binding;
    private MediaRecorder mediaRecorder;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        DashboardViewModel dashboardViewModel =
                new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.promptText;
        dashboardViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        MaterialButton recordButton = binding.getRoot().findViewById(R.id.record_button);
        recordButton.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, 200);
            } else {
                if (mediaRecorder != null) {
                    stopRecording();
                } else {
                    startRecording();
                }
            }
        });
        return root;
    }

    private void startRecording() {
        String audioFilePath = Objects.requireNonNull(requireContext().getExternalFilesDir(null)).getAbsolutePath() + "/recording.3gp";
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(audioFilePath);
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            Toast.makeText(getContext(), "Recording started", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Recording failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        Toast.makeText(getContext(), "Recording stopped", Toast.LENGTH_SHORT).show();
        mediaRecorder.release();
        mediaRecorder = null;

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mediaRecorder != null) {
            stopRecording();
        }
        binding = null;
    }
}