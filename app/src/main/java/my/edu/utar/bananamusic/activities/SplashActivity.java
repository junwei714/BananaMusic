package my.edu.utar.bananamusic.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.auth.LoginActivity;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {
    private static final long SPLASH_DELAY = 2000; // 2 seconds

    private ImageView ivLogo;
    private TextView tvAppName;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize views
        ivLogo = findViewById(R.id.ivLogo);
        tvAppName = findViewById(R.id.tvAppName);
        mAuth = FirebaseAuth.getInstance();

        // Start animations
        startAnimations();

        // Navigate after delay
        new Handler(Looper.getMainLooper()).postDelayed(this::checkUserAndNavigate, SPLASH_DELAY);
    }

    private void startAnimations() {
        // Fade in animation
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1000);
        fadeIn.setFillAfter(true);

        // Start animation for logo and app name
        ivLogo.startAnimation(fadeIn);
        
        // Start app name animation with a small delay
        Animation fadeInDelayed = new AlphaAnimation(0.0f, 1.0f);
        fadeInDelayed.setDuration(1000);
        fadeInDelayed.setStartOffset(500);
        fadeInDelayed.setFillAfter(true);
        tvAppName.startAnimation(fadeInDelayed);
    }

    private void checkUserAndNavigate() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        Intent intent;

        if (currentUser != null) {
            // User is signed in, go to main activity
            intent = new Intent(this, MainActivity.class);
        } else {
            // No user is signed in, go to login activity
            intent = new Intent(this, LoginActivity.class);
        }

        startActivity(intent);
        finish();
        
        // Add transition animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
} 