package com.cookandroid.ex2_1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.List;

public class ChatHistoryActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private List<ChatSession> sessions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_history);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadSessions();
    }

    private void loadSessions() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        int userId = prefs.getInt("user_id", -1);

        if (userId != -1) {
            ApiService service = RetrofitClient.getInstance().create(ApiService.class);
            service.getUserSessions(userId).enqueue(new Callback<List<ChatSession>>() {
                @Override
                public void onResponse(Call<List<ChatSession>> call, Response<List<ChatSession>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        sessions.clear();
                        sessions.addAll(response.body());
                        setupAdapter();
                    } else {
                        showToast("세션 목록 로드 실패");
                    }
                }

                @Override
                public void onFailure(Call<List<ChatSession>> call, Throwable t) {
                    showToast("세션 목록 로드 실패");
                }
            });
        }
    }

    private void setupAdapter() {
        SessionAdapter adapter = new SessionAdapter(sessions, session -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("session_id", session.id);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
        recyclerView.setAdapter(adapter);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}