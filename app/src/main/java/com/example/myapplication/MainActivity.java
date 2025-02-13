package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.myapplication.databinding.ActivityMainBinding;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.navigation.NavigationBarView;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Enable edge to edge
        EdgeToEdge.enable(this);

        super.onCreate(savedInstanceState);

        // Apply dynamic colors to the activity
        DynamicColors.applyToActivitiesIfAvailable(this.getApplication());

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Create required dirs for the app if not already created
        createDir();

        // Set up the bottom navigation bar
        BottomNavigationView bottomNavigationView = findViewById(R.id.nav_view);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_activity_main);
        assert navHostFragment != null;
        NavController navController = navHostFragment.getNavController();

        NavigationUI.setupWithNavController(bottomNavigationView, navController);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                navController.navigate(R.id.navigation_home);
                return true;
            } else if (itemId == R.id.navigation_dashboard) {
                navController.navigate(R.id.navigation_dashboard);
                return true;
            } else if (itemId == R.id.navigation_notifications) {
                navController.navigate(R.id.navigation_notifications);
                return true;
            }
            return false;
        });

        // Do nothing when the same item is reselected
        bottomNavigationView.setOnItemReselectedListener(item -> {
        });

        // Hide bottom navigation bar when recording fragment is visible
        navController.addOnDestinationChangedListener((navController1, navDestination, bundle) -> {
            {
                if(navDestination.getId() == R.id.navigation_detail) {
                    bottomNavigationView.setVisibility(View.GONE);
                } else {
                    bottomNavigationView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void createDir() {
        // Create the 'recordings' directory in internal storage
        File recordingDir = new File(getFilesDir(), "/recordings");
        if (!recordingDir.exists()) {
            boolean makeRecordingDirSuccess = recordingDir.mkdirs(); // Create the directory if it doesn't exist
            if (makeRecordingDirSuccess) {
                Log.d("Files", "Directory created at " + recordingDir.getAbsolutePath());
            }
        }

        // Create the 'transcripts' directory in internal storage
        File transcriptDir = new File(getFilesDir(), "/transcripts");
        if (!transcriptDir.exists()) {
            boolean makeTranscriptDirSuccess = transcriptDir.mkdirs(); // Create the directory if it doesn't exist
            if (makeTranscriptDirSuccess) {
                Log.d("Files", "Directory created at " + transcriptDir.getAbsolutePath());
            }
        }

        // Create the 'texts' directory in internal storage
        File textDir = new File(getFilesDir(), "/texts");
        if (!textDir.exists()) {
            boolean makeTextDirSuccess = textDir.mkdirs(); // Create the directory if it doesn't exist
            if (makeTextDirSuccess) {
                Log.d("Files", "Directory created at " + textDir.getAbsolutePath());
            }
        }

        // Create the 'summaries' directory in internal storage
        File summaryDir = new File(getFilesDir(), "/summaries");
        if (!summaryDir.exists()) {
            boolean makeSummaryDirSuccess = summaryDir.mkdirs(); // Create the directory if it doesn't exist
            if (makeSummaryDirSuccess) {
                Log.d("Files", "Directory created at " + summaryDir.getAbsolutePath());
            }
        }

        // Create the 'attachments' directory in internal storage
        File attachmentDir = new File(getFilesDir(), "/attachments");
        if (!attachmentDir.exists()) {
            boolean makeAttachmentDirSuccess = attachmentDir.mkdirs(); // Create the directory if it doesn't exist
            if (makeAttachmentDirSuccess) {
                Log.d("Files", "Directory created at " + attachmentDir.getAbsolutePath());
            }
        }
    }
}