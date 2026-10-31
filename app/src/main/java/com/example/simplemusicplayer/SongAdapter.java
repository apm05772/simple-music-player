package com.example.simplemusicplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    private List<Song> songList;
    private OnItemClickListener clickListener;
    private OnDeleteClickListener deleteClickListener;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteClickListener = listener;
    }

    public SongAdapter(List<Song> songList) {
        this.songList = songList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.song_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Song song = songList.get(position);
        holder.textView.setText(song.getTitle());

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                int current = holder.getBindingAdapterPosition();
                if (current != RecyclerView.NO_POSITION) clickListener.onItemClick(current);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteClickListener != null) {
                int current = holder.getBindingAdapterPosition();
                if (current != RecyclerView.NO_POSITION) deleteClickListener.onDeleteClick(current);
            }
        });
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    public void updateList(List<Song> newList) {
        songList = newList;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ImageButton btnDelete;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.songTitle);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}