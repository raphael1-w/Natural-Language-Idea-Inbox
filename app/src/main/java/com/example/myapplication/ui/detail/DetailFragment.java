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

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.room.Room;

import com.example.myapplication.R;
import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.AttachmentsDao;
import com.example.myapplication.database.IdeasDao;
import com.example.myapplication.database.Ideas_table;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class DetailFragment extends Fragment {
    private TextView typeView, tagsView, dateView, durationView;
    private MaterialToolbar topAppBar;
    private AppDatabase db;
    private IdeasDao ideasDao;
    private AttachmentsDao attachmentsDao;
    private Ideas_table thisIdea;

    public static DetailFragment newInstance(Ideas_table idea) {
        DetailFragment fragment = new DetailFragment();
        Bundle args = new Bundle();
        args.putInt("idea_id", idea.id);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_detail, container, false);

        // Initialize database and DAOs
        db = Room.databaseBuilder(requireContext(), AppDatabase.class, "app-database").build();
        ideasDao = db.ideasDao();
        attachmentsDao = db.attachmentsDao();

        topAppBar = view.findViewById(R.id.topAppBar);
//        typeView = view.findViewById(R.id.detailType);
//        tagsView = view.findViewById(R.id.detailTags);
//        dateView = view.findViewById(R.id.detailDate);
//        durationView = view.findViewById(R.id.detailDuration);

        if (getArguments() != null) {
            // Get the Handler for UI updates
            Thread dbThread = getDbThread();
            dbThread.start();
        }

        // Set up click listeners and other UI configurations
        topAppBar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

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
                thisIdea = ideasDao.getIdeaById(getArguments().getInt("idea_id"));

                // Update UI on main thread
                handler.post(this::updateUI);
            } catch (Exception e) {
                Log.e("DetailFragment", "Error fetching idea: ", e);
                handler.post(() -> Toast.makeText(requireContext(), "Error loading idea", Toast.LENGTH_SHORT).show());
            }
        });
    }

    // Method to update UI with the fetched data
    private void updateUI() {
        topAppBar.setTitle(thisIdea.title);
//        typeView.setText("Type: " + thisIdea.type);
//        tagsView.setText("Tags: " + thisIdea.tags);

//        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
//
//        dateView.setText("Date: " + sdf.format(thisIdea.created_at));
//
//        if (thisIdea.type.equals("audio")) {
//            durationView.setText("Duration: " + formatDuration(thisIdea.recording_duration));
//        } else {
//            durationView.setVisibility(View.GONE);
//        }
    }

    private String formatDuration(long durationMs) {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / 1000) / 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }
}
