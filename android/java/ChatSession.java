package com.cookandroid.ex2_1;

import com.google.gson.annotations.SerializedName;

public class ChatSession {
    @SerializedName("id")
    public int id;

    @SerializedName("user_id")
    public int userId;

    @SerializedName("start_time")
    public String startTime;

    @SerializedName("end_time")
    public String endTime;
}