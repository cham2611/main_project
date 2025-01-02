package com.cookandroid.ex2_1;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    private boolean isActivityStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // 로고 애니메이션
        setupLogoAnimation();

        // 화면 터치 시 전환
        setupScreenTouchListener();
    }

    private void setupLogoAnimation() {
        ImageView logoImage = findViewById(R.id.logoImage);
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1500);
        logoImage.startAnimation(fadeIn);
    }

    private void setupScreenTouchListener() {
        View rootView = findViewById(android.R.id.content);
        rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNextActivity();
            }
        });
    }

    private void startNextActivity() {
        if (!isActivityStarted) {
            isActivityStarted = true;
            // Ex2_1Activity로 이동
            Intent intent = new Intent(SplashActivity.this, Ex2_1Activity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }
    }
}