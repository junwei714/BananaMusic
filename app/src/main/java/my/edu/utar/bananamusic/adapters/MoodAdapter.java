package my.edu.utar.bananamusic.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import my.edu.utar.bananamusic.R;

public class MoodAdapter extends RecyclerView.Adapter<MoodAdapter.MoodViewHolder> {

    private final List<String> moodList;
    private final OnMoodClickListener listener;
    private final Map<String, Integer> moodColors = new HashMap<>();
    private final Random random = new Random();

    public interface OnMoodClickListener {
        void onMoodClick(String mood);
    }

    public MoodAdapter(List<String> moodList, OnMoodClickListener listener) {
        this.moodList = moodList;
        this.listener = listener;
        generateMoodColors();
    }

    private void generateMoodColors() {
        // Assign consistent colors for each mood
        // Happy - Yellow
        moodColors.put("Happy", Color.parseColor("#FFCE00"));
        // Sad - Blue
        moodColors.put("Sad", Color.parseColor("#1E88E5"));
        // Energetic - Red
        moodColors.put("Energetic", Color.parseColor("#E53935"));
        // Relaxed - Green
        moodColors.put("Relaxed", Color.parseColor("#43A047"));
        // Romantic - Pink
        moodColors.put("Romantic", Color.parseColor("#EC407A"));
        // Focused - Purple
        moodColors.put("Focused", Color.parseColor("#7B1FA2"));
    }

    @NonNull
    @Override
    public MoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mood, parent, false);
        return new MoodViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MoodViewHolder holder, int position) {
        String mood = moodList.get(position);
        holder.bind(mood);
    }

    @Override
    public int getItemCount() {
        return moodList.size();
    }

    class MoodViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvMoodName;

        MoodViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            tvMoodName = itemView.findViewById(R.id.tv_mood_name);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onMoodClick(moodList.get(position));
                }
            });
        }

        void bind(String mood) {
            tvMoodName.setText(mood);
            
            // Set a special color for the mood card based on the mood type
            if (moodColors.containsKey(mood)) {
                cardView.setCardBackgroundColor(moodColors.get(mood));
            } else {
                // Generate random color for any other mood
                int color = Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256));
                cardView.setCardBackgroundColor(color);
            }
        }
    }
} 