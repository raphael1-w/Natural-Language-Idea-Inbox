package com.example.myapplication.ui.dashboard;

import android.os.Bundle;
import android.os.TokenWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.Manifest;
import android.content.pm.PackageManager;
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

public class DashboardFragment extends Fragment {
    private FragmentDashboardBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        // Show the action bar when the fragment is visible
        ((AppCompatActivity) getActivity()).getSupportActionBar().show();

        DashboardViewModel dashboardViewModel =
                new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        MaterialButton recordButton = binding.getRoot().findViewById(R.id.record_button);
        recordButton.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, 200);
            } else {
                Toast.makeText(requireContext(), "Starting", Toast.LENGTH_SHORT).show();
                startRecordingFragment();
            }
        });
        return root;
    }

    private void startRecordingFragment() {
        // Start recording fragment
        try {
            NavController navcontroller = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_activity_main);
            navcontroller.navigate(R.id.navigation_recording);
        } catch (Exception e) {
            Log.e("FragmentError", "Error starting recording fragment: " + e.getMessage());
            // Handle the error appropriately
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}