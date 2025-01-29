package com.example.myapplication.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.databinding.FragmentHomeBinding;

import java.io.File;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textHome;

        // Get the list of files in the 'recording' directory
        File recordingDir = new File(requireContext().getFilesDir(), "/recordings");

        // Generate a string of all file names
        StringBuilder fileList = new StringBuilder("Files in recording directory:\n");
        File[] files = recordingDir.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                fileList.append(file.getName()).append("\n");
            }
        } else {
            fileList.append("No files found.");
        }

        // Update the ViewModel with the list of files
        homeViewModel.setText(fileList.toString());

        // Observe the ViewModel's text and set it to the TextView
        homeViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
