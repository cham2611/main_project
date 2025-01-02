package com.cookandroid.ex2_1;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.ViewHolder> {
    private List<ChatSession> sessions;
    private OnSessionClickListener listener;
    private int selectedPosition = -1;

    public interface OnSessionClickListener {
        void onSessionClick(ChatSession session);
    }

    public SessionAdapter(List<ChatSession> sessions, OnSessionClickListener listener) {
        this.sessions = sessions;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_session, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (sessions == null || position >= sessions.size()) {
            return;
        }
        
        ChatSession session = sessions.get(position);
        if (session == null) {
            return;
        }
        
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = isoFormat.parse(session.startTime);
            holder.sessionName.setText("상담 " + sdf.format(date));
            holder.sessionDate.setText(sdf.format(date));
            
            holder.itemView.setBackgroundColor(selectedPosition == position ? 
                Color.parseColor("#E3F2FD") : Color.TRANSPARENT);
            
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int previousSelected = selectedPosition;
                    selectedPosition = position;
                    notifyItemChanged(previousSelected);
                    notifyItemChanged(selectedPosition);
                    listener.onSessionClick(session);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            holder.sessionName.setText("상담 기록");
            holder.sessionDate.setText("날짜 없음");
        }
    }

    @Override
    public int getItemCount() {
        return sessions != null ? sessions.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView sessionName;
        TextView sessionDate;

        ViewHolder(View view) {
            super(view);
            sessionName = view.findViewById(R.id.sessionName);
            sessionDate = view.findViewById(R.id.sessionDate);
        }
    }
}
