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
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SessionListAdapter extends RecyclerView.Adapter<SessionListAdapter.ViewHolder> {
    private List<ChatSession> sessions;
    private OnSessionClickListener listener;
    private int selectedPosition = -1;

    public interface OnSessionClickListener {
        void onSessionClick(ChatSession session);
    }

    public SessionListAdapter(List<ChatSession> sessions, OnSessionClickListener listener) {
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
            // 날짜 표시
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = isoFormat.parse(session.startTime);
            
            // 세션 제목 (날짜와 시간)
            holder.sessionName.setText("상담 " + sdf.format(date));
            
            // 첫 메시지 미리보기 (서버에서 가져오기)
            ApiService service = RetrofitClient.getInstance().create(ApiService.class);
            service.getSessionMessages(session.id).enqueue(new Callback<List<ChatMessage>>() {
                @Override
                public void onResponse(Call<List<ChatMessage>> call, Response<List<ChatMessage>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        ChatMessage firstMessage = response.body().get(0);
                        String preview = firstMessage.message;
                        if (preview.length() > 30) {
                            preview = preview.substring(0, 30) + "...";
                        }
                        holder.sessionDate.setText(preview);
                    }
                }
                
                @Override
                public void onFailure(Call<List<ChatMessage>> call, Throwable t) {
                    holder.sessionDate.setText("새로운 상담");
                }
            });
            
            // 클릭 리스너 설정
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int previousSelected = selectedPosition;
                    selectedPosition = position;
                    notifyItemChanged(previousSelected);
                    notifyItemChanged(selectedPosition);
                    listener.onSessionClick(session);
                }
            });
            
            // 선택된 항목 강조
            holder.itemView.setBackgroundColor(selectedPosition == position ? 
                Color.parseColor("#E3F2FD") : Color.TRANSPARENT);
            
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