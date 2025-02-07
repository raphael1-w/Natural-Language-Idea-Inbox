package com.example.myapplication.ui.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.example.myapplication.database.Ideas_table;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class DetailFragment extends Fragment {
    private TextView typeView, tagsView, dateView, durationView;
    private MaterialToolbar topAppBar;

    public static DetailFragment newInstance(Ideas_table idea) {
        DetailFragment fragment = new DetailFragment();
        Bundle args = new Bundle();
        args.putString("title", idea.title);
        args.putString("type", idea.type);
        args.putString("tags", idea.tags);
        args.putLong("created_at", idea.created_at.getTime());

        if (idea.recording_file_path != null && idea.recording_duration != null) {
            args.putLong("duration", idea.recording_duration);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_detail, container, false);

        topAppBar = view.findViewById(R.id.topAppBar);
        typeView = view.findViewById(R.id.detailType);
        tagsView = view.findViewById(R.id.detailTags);
        dateView = view.findViewById(R.id.detailDate);
        durationView = view.findViewById(R.id.detailDuration);

        if (getArguments() != null) {
            topAppBar.setTitle(getArguments().getString("title"));
            typeView.setText("Type: " + getArguments().getString("type"));
            tagsView.setText("Tags: " + getArguments().getString("tags"));

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            dateView.setText("Created: " + sdf.format(getArguments().getLong("created_at")));

            if (getArguments().containsKey("duration")) {
                long duration = getArguments().getLong("duration");
                durationView.setText("Duration: " + formatDuration(duration));
            } else {
                durationView.setVisibility(View.GONE);
            }
        }

        // Handle top bar close button by popping the fragment from the back stack
        topAppBar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        // Handle opt bar menu
        topAppBar.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_delete) {
                // Handle delete action
                Toast.makeText(requireContext(), "Delete action", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        return view;
    }

    private String formatDuration(long durationMs) {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / 1000) / 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }
}
