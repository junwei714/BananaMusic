package my.edu.utar.bananamusic.adapters;

import android.animation.ObjectAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import my.edu.utar.bananamusic.R;

public class MoodSelectionAdapter extends RecyclerView.Adapter<MoodSelectionAdapter.MoodViewHolder> {

    public interface OnMoodSelectedListener {
        void onMoodSelected(String mood);
    }

    private final String[] moods = {
        "Happy", "Sad", "Chill", "Party", "Focus", "Romantic"
    };

    private final String[] descriptions = {
        "Upbeat and cheerful tunes",
        "Melancholic and emotional songs",
        "Laid-back and relaxing beats",
        "High-energy dance music",
        "Music for concentration",
        "Love songs and ballads"
    };

    private final int[] backgrounds = {
        R.drawable.mood_item_happy,
        R.drawable.mood_item_sad,
        R.drawable.mood_item_chill,
        R.drawable.mood_item_party,
        R.drawable.mood_item_focus,
        R.drawable.mood_item_romantic
    };

    private final OnMoodSelectedListener listener;

    public MoodSelectionAdapter(OnMoodSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public MoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_mood_selection, parent, false);
        return new MoodViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MoodViewHolder holder, int position) {
        String mood = moods[position];
        String description = descriptions[position];
        
        holder.tvMoodName.setText(mood);
        holder.tvMoodDescription.setText(description);
        holder.itemView.setBackgroundResource(backgrounds[position]);

        holder.itemView.setOnClickListener(v -> {
            // Add click animation with smoother interpolation
            ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 0.95f);
            ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 0.95f);
            scaleDownX.setDuration(150);
            scaleDownY.setDuration(150);
            scaleDownX.setInterpolator(new DecelerateInterpolator());
            scaleDownY.setInterpolator(new DecelerateInterpolator());
            scaleDownX.start();
            scaleDownY.start();

            // Restore original scale with bounce effect
            v.postDelayed(() -> {
                ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1.0f);
                ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1.0f);
                scaleUpX.setDuration(300);
                scaleUpY.setDuration(300);
                scaleUpX.setInterpolator(new DecelerateInterpolator(1.5f));
                scaleUpY.setInterpolator(new DecelerateInterpolator(1.5f));
                scaleUpX.start();
                scaleUpY.start();
            }, 150);

            if (listener != null) {
                listener.onMoodSelected(mood.toLowerCase());
            }
        });
    }

    @Override
    public int getItemCount() {
        return moods.length;
    }

    static class MoodViewHolder extends RecyclerView.ViewHolder {
        TextView tvMoodName;
        TextView tvMoodDescription;

        MoodViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMoodName = itemView.findViewById(R.id.tvMoodName);
            tvMoodDescription = itemView.findViewById(R.id.tvMoodDescription);
        }
    }
} 