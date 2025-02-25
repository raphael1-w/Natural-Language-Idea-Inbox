package com.example.myapplication;

import static android.Manifest.permission.FOREGROUND_SERVICE;
import static android.Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC;
import static android.Manifest.permission.POST_NOTIFICATIONS;

import static androidx.core.content.ContentProviderCompat.requireContext;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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

public class MainActivity extends AppCompatActivity implements KeyboardLayoutListener.OnKeyboardVisibilityListener {

    private ActivityMainBinding binding;
    private KeyboardLayoutListener keyboardLayoutListener;

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

        // Hide bottom navigation bar when ideas detail fragment is visible
        navController.addOnDestinationChangedListener((navController1, navDestination, bundle) -> {
            {
                if(navDestination.getId() == R.id.navigation_detail) {
                    bottomNavigationView.setVisibility(View.GONE);
                } else {
                    bottomNavigationView.setVisibility(View.VISIBLE);
                }
            }
        });

        // Setup keyboard listener
        keyboardLayoutListener = new KeyboardLayoutListener(binding.getRoot(), this);
        keyboardLayoutListener.attach();

        // Request permissions, if not granted quit the app
        if (ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{POST_NOTIFICATIONS}, 101);
        }
        if (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(this, FOREGROUND_SERVICE) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{FOREGROUND_SERVICE}, 102);
        }

    }

    @Override
    public void onKeyboardVisible(int keyboardHeight) {
        // Handle keyboard shown
        Log.d("Keyboard", "Keyboard visible");

        // If current fragment is dashboard
        // hide bottom navigation bar and remove all bottom margin

        // else if current fragment is detail
        // hide bottom controls (linear layout)

        // Get current fragment ID from NavController
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_activity_main);
        assert navHostFragment != null;
        NavController navController = navHostFragment.getNavController();
        int currentDestinationId = navController.getCurrentDestination().getId();

        // Get views
        BottomNavigationView bottomNavigationView = findViewById(R.id.nav_view);

        if (currentDestinationId == R.id.navigation_dashboard) {
            // Hide bottom navigation bar
            bottomNavigationView.setVisibility(View.GONE);

            // Find the main bar in dashboard fragment and remove its bottom margin
            View mainBar = findViewById(R.id.main_bar);
            if (mainBar != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mainBar.getLayoutParams();
                params.bottomMargin = 0;
                mainBar.setLayoutParams(params);
            }
        } else if (currentDestinationId == R.id.navigation_detail) {
            // Hide bottom controls in detail fragment
            LinearLayout bottomControls = findViewById(R.id.bottomControls);
            if (bottomControls != null) {
                bottomControls.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onKeyboardHidden() {
        // Handle keyboard hidden
        Log.d("Keyboard", "Keyboard hidden");

        // Get current fragment ID from NavController
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_activity_main);
        assert navHostFragment != null;
        NavController navController = navHostFragment.getNavController();
        int currentDestinationId = navController.getCurrentDestination().getId();

        // Get views
        BottomNavigationView bottomNavigationView = findViewById(R.id.nav_view);

        if (currentDestinationId == R.id.navigation_dashboard) {
            // Show bottom navigation bar
            bottomNavigationView.setVisibility(View.VISIBLE);

            // Find the main bar in dashboard fragment and restore its bottom margin
            View mainBar = findViewById(R.id.main_bar);
            if (mainBar != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mainBar.getLayoutParams();
                // Add bottom navigation height plus 8dp margin
                float density = getResources().getDisplayMetrics().density;
                calculateBottomMargin();
            }
        } else if (currentDestinationId == R.id.navigation_detail) {
            // Show bottom controls in detail fragment
            LinearLayout bottomControls = findViewById(R.id.bottomControls);
            if (bottomControls != null) {
                bottomControls.setVisibility(View.VISIBLE);
            }
        }
    }

    private void calculateBottomMargin() { // Calculate the bottom margin for the capture bar

        // Find your BottomNavigationView in the Activity
        BottomNavigationView bottomNavigationView = findViewById(R.id.nav_view);

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

    @Override
    protected void onDestroy() {
        if (keyboardLayoutListener != null) {
            keyboardLayoutListener.detach();
        }
        super.onDestroy();
    }
}