package com.example.myapplication.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.R;
import com.example.myapplication.database.Ideas_table;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class IdeasAdapter extends RecyclerView.Adapter<IdeasAdapter.ViewHolder> {
    private final List<Ideas_table> ideas;
    private final onItemClickListener listener;

    public interface onItemClickListener {
        void onItemClick(Ideas_table idea);
    }

    public IdeasAdapter(List<Ideas_table> ideas, onItemClickListener listener) {
        this.ideas = ideas;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.idea_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Ideas_table idea = ideas.get(position);
        holder.title.setText(idea.title);
        holder.type.setText("Type: " + idea.type);
        holder.tags.setText("Tags: " + idea.tags);

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        holder.date.setText("Created: " + sdf.format(idea.created_at));

        // Check if the idea type is audio and display duration
        if ("audio".equalsIgnoreCase(idea.type) && idea.recording_file_path != null && idea.recording_duration != null) {
            String duration = formatDuration(idea.recording_duration);
            holder.duration.setText("Duration: " + duration);
        }

        // Set click listener for the whole item
        holder.itemView.setOnClickListener(v -> listener.onItemClick(idea));
    }

    @Override
    public int getItemCount() {
        return ideas.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, type, duration, tags, date;

        public ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.ideaTitle);
            type = itemView.findViewById(R.id.ideaType);
            duration = itemView.findViewById(R.id.ideaDuration);
            tags = itemView.findViewById(R.id.ideaTags);
            date = itemView.findViewById(R.id.ideaDate);
        }
    }

    private String formatDuration(long durationMs) {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / 1000) / 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }
}
