package com.cookandroid.ex2_1;

import com.google.gson.annotations.SerializedName;

public class ChatMessage {
    @SerializedName("id")
    public int id;

    @SerializedName("session_id")
    public int session_id;

    @SerializedName("user_id")
    public int user_id;

    @SerializedName("message")
    public String message;

    @SerializedName("is_user")
    public boolean is_user;

    @SerializedName("timestamp")
    public String timestamp;
}
