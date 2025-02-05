package com.example.myapplication.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.R;
import com.example.myapplication.Ideas_table;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class IdeasAdapter extends RecyclerView.Adapter<IdeasAdapter.ViewHolder> {
    private final List<Ideas_table> ideas;

    public IdeasAdapter(List<Ideas_table> ideas) {
        this.ideas = ideas;
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
    }

    @Override
    public int getItemCount() {
        return ideas.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, type, tags, date;

        public ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.ideaTitle);
            type = itemView.findViewById(R.id.ideaType);
            tags = itemView.findViewById(R.id.ideaTags);
            date = itemView.findViewById(R.id.ideaDate);
        }
    }
}
