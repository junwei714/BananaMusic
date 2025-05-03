package my.edu.utar.bananamusic.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
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
import my.edu.utar.bananamusic.auth.LoginActivity;

/**
 * Dialog that appears when a user tries to access features that require authentication
 */
public class AuthRequiredDialog extends DialogFragment {
    private static final String ARG_MESSAGE = "message";
    
    public interface AuthRequiredCallback {
        void onLoginSelected();
        void onCancel();
    }
    
    private AuthRequiredCallback callback;
    
    public static AuthRequiredDialog newInstance(String message) {
        AuthRequiredDialog dialog = new AuthRequiredDialog();
        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE, message);
        dialog.setArguments(args);
        return dialog;
    }
    
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            callback = (AuthRequiredCallback) context;
        } catch (ClassCastException e) {
            // If the context doesn't implement the callback,
            // the dialog will still work with default actions
        }
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        
        // Inflate the custom layout for the dialog
        View view = inflater.inflate(R.layout.dialog_auth_required, null);
        
        Bundle args = getArguments();
        String message = args != null ? args.getString(ARG_MESSAGE) : 
                         "You need to be logged in to access this feature.";
        
        TextView messageTextView = view.findViewById(R.id.auth_message);
        Button loginButton = view.findViewById(R.id.login_button);
        Button cancelButton = view.findViewById(R.id.cancel_button);
        
        messageTextView.setText(message);
        
        loginButton.setOnClickListener(v -> {
            dismiss();
            if (callback != null) {
                callback.onLoginSelected();
            } else {
                // Default behavior if no callback is implemented
                Intent intent = new Intent(requireContext(), LoginActivity.class);
                startActivity(intent);
            }
        });
        
        cancelButton.setOnClickListener(v -> {
            dismiss();
            if (callback != null) {
                callback.onCancel();
            }
        });
        
        builder.setView(view);
        return builder.create();
    }
    
    public void show(FragmentManager manager) {
        show(manager, "auth_required_dialog");
    }
} 
 