package com.cookandroid.ex2_1;

import java.util.List;

public class ChatHistoryGroup {
    String date;
    List<ChatMessage> messages;

    public ChatHistoryGroup(String date, List<ChatMessage> messages) {
        this.date = date;
        this.messages = messages;
    }
}
