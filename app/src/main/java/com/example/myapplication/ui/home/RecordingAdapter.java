package com.example.myapplication.ui.home;

import android.media.MediaMetadataRetriever;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.io.File;
import java.util.List;

public class RecordingAdapter extends RecyclerView.Adapter<RecordingAdapter.ViewHolder> {
    private List<File> recordings;

    public RecordingAdapter(List<File> files) {
        this.recordings = files;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recording_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = recordings.get(position);
        holder.fileName.setText(file.getName());

        // Retrieve audio duration
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(file.getPath());
        String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

        if (duration != null) {
            int durationMs = Integer.parseInt(duration);
            String formattedDuration = String.format("%02d:%02d:%03d",
                    durationMs / 60000,
                    (durationMs % 60000) / 1000,
                    durationMs % 1000);
            holder.fileDuration.setText("Duration: " + formattedDuration);
        } else {
            holder.fileDuration.setText("Duration: Unknown");
        }
    }

    @Override
    public int getItemCount() {
        return recordings.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView fileName, fileDuration;

        public ViewHolder(View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileName);
            fileDuration = itemView.findViewById(R.id.fileDuration);
        }
    }
}

