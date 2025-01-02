package com.cookandroid.ex2_1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import android.media.MediaRecorder;
import android.content.pm.PackageManager;
import android.Manifest;
import android.widget.ImageButton;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.http.Part;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import android.media.MediaPlayer;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Handler;
import android.util.Log;

public class Ex2_1Activity extends AppCompatActivity {
    private EditText messageInput;
    private Button sendButton;
    private LinearLayout chatLayout;
    private ScrollView scrollView;
    private Button loginButton;
    private TextView titleUserName;
    private int currentSessionId = -1;
    private List<ChatSession> sessions = new ArrayList<>();
    private LinearLayout chatHistoryButtons;
    private static final int RECORD_AUDIO_PERMISSION = 1;
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecording = false;
    private ImageButton voiceInputButton;
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int RECORDING_DURATION = 10000; // 10초
    private Handler handler = new Handler();
    private boolean isListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ex2_1);

        initializeViews();
        setupButtons();
        checkLoginStatus();

        voiceInputButton = findViewById(R.id.voiceInputButton);
        setupVoiceButton();
    }

    private void initializeViews() {
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        chatLayout = findViewById(R.id.chatLayout);
        scrollView = findViewById(R.id.scrollView);
        loginButton = findViewById(R.id.loginButton);
        titleUserName = findViewById(R.id.titleUserName);
        chatHistoryButtons = findViewById(R.id.chatHistoryButtons);

        // 초기 메시지 표시
        addMessage("안녕하세요, 만나뵙게 되어 반갑습니다! 저는 여러분의 진로상담 선생님이에요~", false);
    }

    private void checkLoginStatus() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userName = prefs.getString("name", null);
        if (userName != null) {
            // 로그인 상태
            loginButton.setText("로그아웃");
            titleUserName.setText(userName + "님");
            titleUserName.setVisibility(View.VISIBLE);
            chatHistoryButtons.setVisibility(View.VISIBLE);
            loadSessions();  // 세션 목록 로드
        } else {
            // 비로그인 상태
            loginButton.setText("로그인");
            titleUserName.setVisibility(View.GONE);
            chatHistoryButtons.setVisibility(View.GONE);
            currentSessionId = -1;
        }
    }

    private void setupButtons() {
        sendButton.setOnClickListener(v -> {
            sendMessageAndSpeak();
        });

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
                if (prefs.getString("name", null) != null) {
                    // 현재 세션이 있다면 종료
                    if (currentSessionId != -1) {
                        endCurrentSession();
                    }

                    // 로그아웃
                    prefs.edit().clear().apply();

                    // 화면 초기화
                    chatLayout.removeAllViews();
                    currentSessionId = -1;
                    sessions.clear();

                    // 초기 메시지 다시 표시
                    addMessage("안녕하세요, 만나뵙게 되어 반갑습니다! 저는 여러분의 진로상담 선생님이에요~", false);

                    // UI 상태 업데이트
                    checkLoginStatus();
                    showToast("로그아웃되었습니다");
                } else {
                    // 로그인 화면으로 이동
                    Intent intent = new Intent(Ex2_1Activity.this, LoginActivity.class);
                    startActivity(intent);
                }
            }
        });

        // 새 대화 시작 버튼
        Button newChatButton = findViewById(R.id.newChatButton);
        newChatButton.setOnClickListener(v -> {
            // 현재 세션이 있다면 먼저 종료
            if (currentSessionId != -1) {
                endCurrentSession();
            }
            currentSessionId = -1;
            chatLayout.removeAllViews();
            addMessage("새로운 상담을 시작합니다.", false);
        });

        // 이전 상담 기록 버튼
        Button historyButton = findViewById(R.id.historyButton);
        historyButton.setOnClickListener(v -> {
            // 현재 세션이 있다면 먼저 종료
            if (currentSessionId != -1) {
                endCurrentSession();
            }
            Intent intent = new Intent(this, ChatHistoryActivity.class);
            startActivityForResult(intent, 1001);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            int sessionId = data.getIntExtra("session_id", -1);
            if (sessionId != -1) {
                currentSessionId = sessionId;
                loadSessionMessages(sessionId);
                // 세션 시간 갱신
                ApiService service = RetrofitClient.getInstance().create(ApiService.class);
                service.updateSessionTimes(sessionId).enqueue(new Callback<ChatSession>() {
                    @Override
                    public void onResponse(Call<ChatSession> call, Response<ChatSession> response) {
                        if (!response.isSuccessful()) {
                            showToast("세션 시간 갱신 실패");
                        }
                    }

                    @Override
                    public void onFailure(Call<ChatSession> call, Throwable t) {
                        showToast("세션 시간 갱신 실패");
                    }
                });
            }
        }
    }

    private void addMessage(String message, boolean isUser) {
        // 메시지를 감싸는 LinearLayout 생성
        LinearLayout messageContainer = new LinearLayout(this);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                isUser ? LinearLayout.LayoutParams.MATCH_PARENT : LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        messageContainer.setLayoutParams(containerParams);
        messageContainer.setOrientation(LinearLayout.HORIZONTAL);

        if (isUser) {
            messageContainer.setGravity(android.view.Gravity.END);
            containerParams.setMargins(80, 0, 0, 8);
        } else {
            containerParams.setMargins(0, 0, 80, 8);
        }

        // 메시지 TextView 생성
        TextView textView = new TextView(this);
        textView.setText(message);
        textView.setPadding(12, 12, 12, 12);
        textView.setTextSize(16);

        // 텍스트 색상 설정 - 모든 메시지를 검정색으로 변경
        textView.setTextColor(getResources().getColor(android.R.color.black));

        // 배경 설정
        textView.setBackground(getDrawable(
                isUser ? R.drawable.user_message_background : R.drawable.bot_message_background));

        // TextView를 컨테이너에 추가
        messageContainer.addView(textView);

        // 컨테이너를 채팅 레이아웃에 추가
        chatLayout.addView(messageContainer);

        // 스롤을 가장 아래로 이동
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void sendMessageAndSpeak() {
        String message = messageInput.getText().toString().trim();
        if (!message.isEmpty()) {
            // 사용자 메시지 표시
            addMessage(message, true);
            messageInput.setText("");

            // 로그인 상태 확인
            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            int userId = prefs.getInt("user_id", -1);

            ApiService service = RetrofitClient.getInstance().create(ApiService.class);
            ChatRequest request = new ChatRequest(message);

            service.chatWithRag(request).enqueue(new Callback<ChatResponse>() {
                @Override
                public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String botResponse = response.body().response;

                        runOnUiThread(() -> {
                            if (userId != -1) {
                                // 로그인된 경우 세션 관리
                                if (currentSessionId == -1) {
                                    startNewSession(userId, message, botResponse);
                                } else {
                                    saveMessages(userId, message, botResponse, currentSessionId);
                                }
                            } else {
                                // 비로그인 상태
                                addMessage(botResponse, false);
                            }

                            // 스크롤을 가장 아래로
                            scrollToBottom();

                            // 응답을 음성으로 변환하여 재생
                            speakResponse(botResponse);
                        });
                    } else {
                        runOnUiThread(() -> {
                            showToast("응답 실패");
                            addMessage("죄송합니다. 일시적인 오류가 발생했습니다.", false);
                        });
                    }
                }

                @Override
                public void onFailure(Call<ChatResponse> call, Throwable t) {
                    runOnUiThread(() -> {
                        showToast("네트워크 오류: " + t.getMessage());
                        addMessage("죄송합니다. 네트워크 오류가 발생했습니다.", false);
                    });
                }
            });
        }
    }

    private void startNewSession(int userId, String message, String botResponse) {
        ChatSessionRequest sessionRequest = new ChatSessionRequest(userId);
        ApiService service = RetrofitClient.getInstance().create(ApiService.class);

        service.startChatSession(sessionRequest).enqueue(new Callback<ChatSession>() {
            @Override
            public void onResponse(Call<ChatSession> call, Response<ChatSession> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentSessionId = response.body().id;
                    saveMessages(userId, message, botResponse, currentSessionId);
                } else {
                    showToast("세션 생성 실패");
                    addMessage(botResponse, false);
                }
            }

            @Override
            public void onFailure(Call<ChatSession> call, Throwable t) {
                showToast("세션 생성 실패");
                addMessage(botResponse, false);
            }
        });
    }

    private void saveMessages(int userId, String userMessage, String botResponse, int sessionId) {
        ApiService service = RetrofitClient.getInstance().create(ApiService.class);

        // 사용자 메시지 저장
        ChatMessageRequest userRequest = new ChatMessageRequest(userMessage, true, sessionId);
        service.createChatMessage(userId, userRequest).enqueue(new Callback<ChatMessage>() {
            @Override
            public void onResponse(Call<ChatMessage> call, Response<ChatMessage> response) {
                if (response.isSuccessful()) {
                    // AI 응답 저장
                    ChatMessageRequest botRequest = new ChatMessageRequest(botResponse, false, sessionId);
                    service.createChatMessage(userId, botRequest).enqueue(new Callback<ChatMessage>() {
                        @Override
                        public void onResponse(Call<ChatMessage> call, Response<ChatMessage> response) {
                            if (response.isSuccessful()) {
                                addMessage(botResponse, false);
                                // 세션 목록 갱신
                                loadSessions();
                            } else {
                                showToast("AI 응답 저장 실패: " + response.code());
                                addMessage(botResponse, false);
                            }
                        }

                        @Override
                        public void onFailure(Call<ChatMessage> call, Throwable t) {
                            showToast("AI 응답 저장 실패: " + t.getMessage());
                            addMessage(botResponse, false);
                        }
                    });
                } else {
                    showToast("메시지 저장 실패: " + response.code());
                    addMessage(botResponse, false);
                }
            }

            @Override
            public void onFailure(Call<ChatMessage> call, Throwable t) {
                showToast("메시지 저장 실패: " + t.getMessage());
                addMessage(botResponse, false);
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void updateSessionTimes(int sessionId) {
        ApiService service = RetrofitClient.getInstance().create(ApiService.class);
        try {
            // 세션 시간 갱신을 동기적으로 처리
            Response<ChatSession> response = service.updateSessionTimes(sessionId).execute();
            if (response.isSuccessful()) {
                loadSessions();
            } else {
                showToast("세션 시간 갱신 실패");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showToast("세션 시간 갱신 실패");
        }
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
                    } else {
                        showToast("세션 목록 로드 실패: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<List<ChatSession>> call, Throwable t) {
                    showToast("세션 목록 로드 실패: " + t.getMessage());
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkLoginStatus();
        loadSessions();  // 세션 목록 로드

        // 새 세션이 필요한지 확인
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("need_new_session", false)) {
            if (currentSessionId != -1) {
                endCurrentSession();
            }
            currentSessionId = -1;
            prefs.edit().putBoolean("need_new_session", false).apply();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 앱이 백그라운드로 갈 때 현재 세션 종료
        if (currentSessionId != -1) {
            new Thread(() -> {
                endCurrentSession();
            }).start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 앱이 종료될 때 현재 세션 종료
        if (currentSessionId != -1) {
            new Thread(() -> {
                endCurrentSession();
            }).start();
        }
    }

    private void endCurrentSession() {
        if (currentSessionId != -1) {
            ApiService service = RetrofitClient.getInstance().create(ApiService.class);
            service.endChatSession(currentSessionId).enqueue(new Callback<ChatSession>() {
                @Override
                public void onResponse(Call<ChatSession> call, Response<ChatSession> response) {
                    if (response.isSuccessful()) {
                        currentSessionId = -1;
                    }
                }

                @Override
                public void onFailure(Call<ChatSession> call, Throwable t) {
                    showToast("세션 종료 실패");
                }
            });
        }
    }

    private void loadSessionMessages(int sessionId) {
        ApiService service = RetrofitClient.getInstance().create(ApiService.class);
        service.getSessionMessages(sessionId).enqueue(new Callback<List<ChatMessage>>() {
            @Override
            public void onResponse(Call<List<ChatMessage>> call, Response<List<ChatMessage>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    runOnUiThread(() -> {
                        chatLayout.removeAllViews();
                        for (ChatMessage message : response.body()) {
                            addMessage(message.message, message.is_user);
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<List<ChatMessage>> call, Throwable t) {
                showToast("메시지 로드 실패");
            }
        });
    }

    // ChatGPT 응답을 음성으로 변환
    private void speakResponse(String text) {
        ApiService service = RetrofitClient.getInstance().create(ApiService.class);
        TTSRequest request = new TTSRequest(text);

        service.generateSpeech(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        File audioFile = new File(getCacheDir(), "tts_response.wav");
                        FileOutputStream fos = new FileOutputStream(audioFile);
                        fos.write(response.body().bytes());
                        fos.close();

                        MediaPlayer mediaPlayer = new MediaPlayer();
                        mediaPlayer.setDataSource(audioFile.getAbsolutePath());
                        mediaPlayer.prepare();
                        mediaPlayer.start();

                        mediaPlayer.setOnCompletionListener(mp -> {
                            mp.release();
                            audioFile.delete();
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("TTS", "Error playing audio: " + e.getMessage());
                        showToast("음성 재생 실패: " + e.getMessage());
                    }
                } else {
                    try {
                        String errorBody = response.errorBody().string();
                        Log.e("TTS", "Error: " + errorBody);
                        showToast("음성 합성 실패: " + errorBody);
                    } catch (IOException e) {
                        showToast("음성 합성 실패: " + response.code());
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                Log.e("TTS", "Failure: " + t.getMessage());
                showToast("음성 합성 실패: " + t.getMessage());
            }
        });
    }

    private void setupVoiceButton() {
        voiceInputButton = findViewById(R.id.voiceInputButton);
        voiceInputButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        PERMISSION_REQUEST_CODE);
            } else {
                if (!isListening) {
                    startVoiceConsultation();
                } else {
                    stopVoiceConsultation();
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                showToast("음성 입력을 위해서는 마이크 권한이 필요합니다");
            }
        }
    }

    private void startVoiceConsultation() {
        isListening = true;
        voiceInputButton.setImageResource(android.R.drawable.ic_media_pause);
        voiceInputButton.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light));
        showToast("음성 상담을 시작합니다. 10초 동안 말씀해 주세요.");

        startRecording();

        // 10초 후 자동으로 녹음 중지
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isListening) {
                    stopRecording();
                }
            }
        }, RECORDING_DURATION);
    }

    private void stopVoiceConsultation() {
        isListening = false;
        voiceInputButton.setImageResource(android.R.drawable.ic_btn_speak_now);
        voiceInputButton.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary));
        stopRecording();
    }

    private void startRecording() {
        try {
            isRecording = true;
            voiceInputButton.setImageResource(android.R.drawable.ic_media_pause);
            voiceInputButton.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light));

            File audioFile = new File(getCacheDir(), "audio_record.mp4");
            audioFilePath = audioFile.getAbsolutePath();

            mediaRecorder = new MediaRecorder();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                mediaRecorder = new MediaRecorder(this);
            } else {
                mediaRecorder = new MediaRecorder();
            }

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setOutputFile(audioFilePath);

            mediaRecorder.prepare();
            mediaRecorder.start();

            showToast("음성 입력을 시작합니다");
        } catch (Exception e) {
            e.printStackTrace();
            showToast("음성 입력 시작 실패: " + e.getMessage());
            isListening = false;
        }
    }

    private void stopRecording() {
        try {
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.stop();
                } catch (RuntimeException stopException) {
                    showToast("음성 입력이 너무 짧습니다");
                    return;
                }

                mediaRecorder.release();
                mediaRecorder = null;

                // 음성 파��� 전 및 처리
                processVoiceInput();
            }
        } catch (Exception e) {
            e.printStackTrace();
            showToast("음성 입력 중지 실패: " + e.getMessage());
        }
    }

    private void processVoiceInput() {
        File audioFile = new File(audioFilePath);
        if (!audioFile.exists()) {
            showToast("녹음된 파일이 없습니다");
            return;
        }

        Log.d("AudioFile", "File size: " + audioFile.length());

        RequestBody requestFile = RequestBody.create(MediaType.parse("audio/mp4"), audioFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", audioFile.getName(), requestFile);

        ApiService service = RetrofitClient.getInstance().create(ApiService.class);
        service.transcribeAudio(body).enqueue(new Callback<TranscriptionResponse>() {
            @Override
            public void onResponse(Call<TranscriptionResponse> call, Response<TranscriptionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String transcribedText = response.body().text;
                    if (!transcribedText.trim().isEmpty()) {
                        runOnUiThread(() -> {
                            messageInput.setText(transcribedText);
                            sendMessageAndSpeak();
                        });
                    } else {
                        showToast("음성을 인식하지 못했습니다");
                    }
                } else {
                    try {
                        String errorBody = response.errorBody().string();
                        Log.e("AudioTranscription", "Error: " + errorBody);
                        showToast("음성 인식 실패: " + errorBody);
                    } catch (IOException e) {
                        e.printStackTrace();
                        showToast("음성 인식 실패: " + response.code());
                    }
                }
            }

            @Override
            public void onFailure(Call<TranscriptionResponse> call, Throwable t) {
                t.printStackTrace();
                Log.e("AudioTranscription", "Failure: " + t.getMessage());
                showToast("음성 인식 실패: " + t.getMessage());
            }
        });
    }

    private void sendAudioFile() {
        File audioFile = new File(audioFilePath);
        if (!audioFile.exists()) {
            showToast("녹음된 파일이 없습니다");
            return;
        }

        RequestBody requestFile = RequestBody.create(MediaType.parse("audio/mp4"), audioFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", audioFile.getName(), requestFile);

        ApiService service = RetrofitClient.getInstance().create(ApiService.class);
        service.transcribeAudio(body).enqueue(new Callback<TranscriptionResponse>() {
            @Override
            public void onResponse(Call<TranscriptionResponse> call, Response<TranscriptionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String transcribedText = response.body().text;
                    if (!transcribedText.trim().isEmpty()) {
                        messageInput.setText(transcribedText);
                        sendMessageAndSpeak();
                    } else {
                        showToast("음성을 인식하지 못했습니다");
                    }
                } else {
                    try {
                        String errorBody = response.errorBody().string();
                        showToast("음성 인식 실패: " + errorBody);
                        Log.e("AudioTranscription", "Error: " + errorBody);
                    } catch (IOException e) {
                        e.printStackTrace();
                        showToast("음성 인식 실패: " + response.code());
                    }
                }
            }

            @Override
            public void onFailure(Call<TranscriptionResponse> call, Throwable t) {
                t.printStackTrace();
                showToast("음성 인식 실패: " + t.getMessage());
            }
        });
    }

    private void scrollToBottom() {
        scrollView.post(() -> {
            scrollView.fullScroll(View.FOCUS_DOWN);
        });
    }
}