package com.example.myapplication.ui.detail;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.room.Room;

import com.example.myapplication.R;
import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.AttachmentsDao;
import com.example.myapplication.database.IdeasDao;
import com.example.myapplication.database.Ideas_table;
import com.example.myapplication.databinding.FragmentDetailBinding;
import com.example.myapplication.databinding.FragmentHomeBinding;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;

public class DetailFragment extends Fragment {
    private TextView typeView, tagsView, dateView, durationView;
    private FragmentDetailBinding binding;
    private MaterialToolbar topAppBar;
    private AppDatabase db;
    private IdeasDao ideasDao;
    private AttachmentsDao attachmentsDao;
    private Ideas_table thisIdea;
    private String transcriptFilePath, textFilePath, summaryFilePath;

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

        if (getArguments() != null) {
            // Get the Handler for UI updates
            Thread dbThread = getDbThread();
            dbThread.start();
        }

        // Set up click listeners and other UI configurations
        topAppBar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        // Set up the segmented buttons for selecting the file to show
        MaterialButtonToggleGroup toggleButton = view.findViewById(R.id.segmentedButtons);
        toggleButton.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_transcript) {
                    showTextFiles(transcriptFilePath);
                } else if (checkedId == R.id.btn_userText) {
                    showTextFiles(textFilePath);
                } else {
                    showTextFiles(summaryFilePath);
                }
            };
        });

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

        // Set the path of the required text files
        transcriptFilePath = thisIdea.transcript_file_path;
        textFilePath = thisIdea.text_file_path;
        summaryFilePath = thisIdea.summary_file_path;

        // Configure UI for text ideas
        if (getArguments() != null && Objects.equals(getArguments().getString("type"), "text")) {
            // Remove transcript button, then selecting the user text button
            binding.btnTranscript.setVisibility(View.GONE);
            binding.btnUserText.setChecked(true);

            // Hide audio controls
            binding.audioControls.setVisibility(View.GONE);

            binding.segmentedButtons.check(R.id.btn_userText);
        } else {
            showTextFiles(transcriptFilePath);
        }
    }

    private void showTextFiles(String filePath) {
        Log.d("DetailFragment", "Showing text file: " + filePath);
        if (filePath != null) {
            binding.EmptyFileText.setVisibility(View.GONE);
            // Open the file and show the text in the editableTextArea
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
            binding.editableTextArea.setVisibility(View.VISIBLE);
            binding.editableTextArea.setText(text.toString());

        } else {
            binding.editableTextArea.setVisibility(View.GONE);

            // Show no files available in EmptyFileText
            binding.EmptyFileText.setVisibility(View.VISIBLE);
            binding.EmptyFileText.setText("File not yet avaliable");
        }
    }

    private String formatDuration(long durationMs) {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / 1000) / 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }
}
