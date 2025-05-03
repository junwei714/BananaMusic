package my.edu.utar.bananamusic.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import my.edu.utar.bananamusic.R;

public class PlaybackErrorDialog extends DialogFragment {
    private static final String ARG_ERROR_MESSAGE = "error_message";
    private static final String ARG_CAN_RETRY = "can_retry";
    private static final String ARG_HAS_FALLBACK = "has_fallback";
    
    public interface PlaybackErrorCallback {
        void onRetry();
        void onUseFallback();
        void onDismiss();
    }
    
    private PlaybackErrorCallback callback;
    
    public static PlaybackErrorDialog newInstance(String errorMessage, boolean canRetry, boolean hasFallback) {
        PlaybackErrorDialog dialog = new PlaybackErrorDialog();
        Bundle args = new Bundle();
        args.putString(ARG_ERROR_MESSAGE, errorMessage);
        args.putBoolean(ARG_CAN_RETRY, canRetry);
        args.putBoolean(ARG_HAS_FALLBACK, hasFallback);
        dialog.setArguments(args);
        return dialog;
    }
    
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            callback = (PlaybackErrorCallback) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement PlaybackErrorCallback");
        }
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_playback_error, null);
        
        Bundle args = getArguments();
        String errorMessage = args != null ? args.getString(ARG_ERROR_MESSAGE) : "Unknown error";
        boolean canRetry = args != null && args.getBoolean(ARG_CAN_RETRY);
        boolean hasFallback = args != null && args.getBoolean(ARG_HAS_FALLBACK);
        
        TextView messageTextView = view.findViewById(R.id.error_message);
        Button retryButton = view.findViewById(R.id.retry_button);
        Button fallbackButton = view.findViewById(R.id.fallback_button);
        Button dismissButton = view.findViewById(R.id.dismiss_button);
        
        messageTextView.setText(errorMessage);
        
        retryButton.setVisibility(canRetry ? View.VISIBLE : View.GONE);
        fallbackButton.setVisibility(hasFallback ? View.VISIBLE : View.GONE);
        
        retryButton.setOnClickListener(v -> {
            dismiss();
            if (callback != null) {
                callback.onRetry();
            }
        });
        
        fallbackButton.setOnClickListener(v -> {
            dismiss();
            if (callback != null) {
                callback.onUseFallback();
            }
        });
        
        dismissButton.setOnClickListener(v -> {
            dismiss();
            if (callback != null) {
                callback.onDismiss();
            }
        });
        
        builder.setView(view);
        return builder.create();
    }
    
    public void show(FragmentManager manager) {
        show(manager, "playback_error_dialog");
    }
}