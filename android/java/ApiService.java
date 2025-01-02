package com.cookandroid.ex2_1;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.PUT;
import java.util.List;
import com.google.gson.annotations.SerializedName;
import retrofit2.http.Multipart;
import retrofit2.http.Part;
import okhttp3.ResponseBody;
import okhttp3.MultipartBody;
import retrofit2.http.Headers;

public interface ApiService {
    @POST("signup/")
    Call<UserResponse> signup(@Body UserRequest request);

    @POST("login/")
    Call<UserResponse> login(@Body LoginRequest request);

    @POST("chat/{user_id}/")
    Call<ChatMessage> createChatMessage(
            @Path("user_id") int userId,
            @Body ChatMessageRequest message
    );

    @GET("chat/{user_id}/")
    Call<List<ChatMessage>> getChatHistory(
            @Path("user_id") int userId,
            @Query("skip") int skip,
            @Query("limit") int limit
    );

    @POST("chat/sessions/start")
    Call<ChatSession> startChatSession(@Body ChatSessionRequest request);

    @POST("chat/sessions/{session_id}/end")
    Call<ChatSession> endChatSession(@Path("session_id") int sessionId);

    @GET("chat/sessions/{user_id}")
    Call<List<ChatSession>> getUserSessions(@Path("user_id") int userId);

    @GET("chat/sessions/{session_id}/messages")
    Call<List<ChatMessage>> getSessionMessages(@Path("session_id") int sessionId);

    @PUT("chat/sessions/{session_id}/update_times")
    Call<ChatSession> updateSessionTimes(@Path("session_id") int sessionId);

    @POST("chat/rag")
    Call<ChatResponse> chatWithRag(@Body ChatRequest request);

    @Multipart
    @POST("transcribe/")
    Call<TranscriptionResponse> transcribeAudio(@Part MultipartBody.Part file);

    @POST("tts/generate")
    @Headers({
            "Content-Type: application/json",
            "Accept: audio/wav"
    })
    Call<ResponseBody> generateSpeech(@Body TTSRequest request);
}

class UserRequest {
    String email;
    String password;
    String name;

    public UserRequest(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
    }
}

class LoginRequest {
    String email;
    String password;

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
}

class UserResponse {
    int id;
    String email;
    String name;
}

class ChatMessageRequest {
    @SerializedName("message")
    String message;

    @SerializedName("is_user")
    boolean is_user;

    @SerializedName("session_id")
    Integer session_id;

    public ChatMessageRequest(String message, boolean is_user, Integer sessionId) {
        this.message = message;
        this.is_user = is_user;
        this.session_id = sessionId;
    }
}

class ChatSessionRequest {
    @SerializedName("user_id")
    int user_id;

    public ChatSessionRequest(int userId) {
        this.user_id = userId;
    }
}

class ChatRequest {
    @SerializedName("message")
    String message;

    public ChatRequest(String message) {
        this.message = message;
    }
}

class ChatResponse {
    @SerializedName("response")
    String response;
}

class TranscriptionResponse {
    @SerializedName("text")
    String text;
}

class TTSRequest {
    @SerializedName("text")
    String text;

    @SerializedName("noise_scale")
    float noise_scale = 0.667f;

    @SerializedName("noise_scale_w")
    float noise_scale_w = 0.8f;

    @SerializedName("length_scale")
    float length_scale = 1.0f;

    public TTSRequest(String text) {
        this.text = text;
    }
}