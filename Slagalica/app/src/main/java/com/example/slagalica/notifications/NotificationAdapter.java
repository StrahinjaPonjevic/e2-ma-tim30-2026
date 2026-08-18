package com.example.slagalica.notifications;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    public interface OnNotificationClickListener {
        void onNotificationClicked(NotificationItem item);
    }

    private final List<NotificationItem> items = new ArrayList<>();
    private final OnNotificationClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    public NotificationAdapter(OnNotificationClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<NotificationItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationItem item = items.get(position);

        holder.tvTitle.setText(item.title != null ? item.title : "");
        holder.tvMessage.setText(item.message != null ? item.message : "");
        holder.tvChannel.setText(channelDisplayName(item.channel));
        holder.tvDate.setText(item.createdAtMs != null ? dateFormat.format(new Date(item.createdAtMs)) : "");

        if (item.read) {
            holder.itemView.setBackgroundColor(Color.WHITE);
            holder.tvTitle.setTypeface(null, Typeface.NORMAL);
            holder.vUnreadDot.setVisibility(View.INVISIBLE);
        } else {
            holder.itemView.setBackgroundColor(Color.parseColor("#E8EAF6"));
            holder.tvTitle.setTypeface(null, Typeface.BOLD);
            holder.vUnreadDot.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNotificationClicked(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String channelDisplayName(String channel) {
        if (NotificationChannelManager.CHANNEL_CHAT.equals(channel)) {
            return "Cet";
        }
        if (NotificationChannelManager.CHANNEL_RANKING.equals(channel)) {
            return "Rangiranje";
        }
        if (NotificationChannelManager.CHANNEL_REWARDS.equals(channel)) {
            return "Nagrade";
        }
        return "Ostalo";
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvMessage;
        final TextView tvChannel;
        final TextView tvDate;
        final View vUnreadDot;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNotifTitle);
            tvMessage = itemView.findViewById(R.id.tvNotifMessage);
            tvChannel = itemView.findViewById(R.id.tvNotifChannel);
            tvDate = itemView.findViewById(R.id.tvNotifDate);
            vUnreadDot = itemView.findViewById(R.id.vUnreadDot);
        }
    }
}
