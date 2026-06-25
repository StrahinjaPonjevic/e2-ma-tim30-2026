package com.example.slagalica.chat;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.auth.FirebaseManager;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    private static String activeRegion;

    private TextView tvTitle;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private Button btnSend;
    private Button btnBack;

    private FirebaseManager firebaseManager;
    private ChatRepository chatRepository;
    private FirebaseUser currentUser;
    private ListenerRegistration messagesListener;
    private MessageAdapter adapter;
    private String username;
    private String region;

    public static boolean isActiveForRegion(String region) {
        return activeRegion != null && activeRegion.equals(region);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        firebaseManager = new FirebaseManager();
        chatRepository = new ChatRepository();
        currentUser = firebaseManager.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "Morate biti prijavljeni za cet.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        setupRecycler();
        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());
        loadUserAndListen();
    }

    private void bindViews() {
        tvTitle = findViewById(R.id.tvChatTitle);
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSendMessage);
        btnBack = findViewById(R.id.btnChatBack);
    }

    private void setupRecycler() {
        adapter = new MessageAdapter(currentUser.getUid());
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);
    }

    private void loadUserAndListen() {
        firebaseManager.loadUserData(currentUser.getUid(), new FirebaseManager.UserDataCallback() {
            @Override
            public void onSuccess(String loadedUsername, String loadedRegion) {
                username = loadedUsername;
                region = loadedRegion;
                runOnUiThread(() -> {
                    activeRegion = region;
                    tvTitle.setText("Cet - " + region);
                    listenMessages();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void listenMessages() {
        if (messagesListener != null) {
            messagesListener.remove();
        }

        messagesListener = chatRepository.listenMessages(region, new ChatRepository.MessagesCallback() {
            @Override
            public void onMessages(List<ChatMessage> messages) {
                runOnUiThread(() -> {
                    adapter.submit(messages);
                    rvMessages.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendMessage() {
        if (region == null) {
            return;
        }

        String text = etMessage.getText().toString();
        btnSend.setEnabled(false);
        chatRepository.sendMessage(region, currentUser.getUid(), username, text, new ChatRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    etMessage.setText("");
                    btnSend.setEnabled(true);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnSend.setEnabled(true);
                    Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (region != null) {
            activeRegion = region;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (region != null && region.equals(activeRegion)) {
            activeRegion = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null) {
            messagesListener.remove();
        }
    }

    private static final class MessageAdapter extends RecyclerView.Adapter<MessageViewHolder> {
        private final String currentUserId;
        private final List<ChatMessage> messages = new ArrayList<>();
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        MessageAdapter(String currentUserId) {
            this.currentUserId = currentUserId;
        }

        void submit(List<ChatMessage> newMessages) {
            messages.clear();
            messages.addAll(newMessages);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message, parent, false);
            return new MessageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            ChatMessage message = messages.get(position);
            boolean mine = currentUserId.equals(message.senderId);
            holder.root.setGravity(mine ? Gravity.END : Gravity.START);
            holder.bubble.setBackground(makeBubble(mine ? "#DDEBFF" : "#FFFFFF"));
            holder.tvSender.setText((message.senderName != null ? message.senderName : "Igrac")
                    + " • " + formatTime(message.createdAt));
            holder.tvText.setText(message.text != null ? message.text : "");
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        private String formatTime(Timestamp timestamp) {
            Date date = timestamp != null ? timestamp.toDate() : new Date();
            return timeFormat.format(date);
        }

        private GradientDrawable makeBubble(String color) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.parseColor(color));
            drawable.setCornerRadius(22f);
            return drawable;
        }
    }

    private static final class MessageViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout root;
        final LinearLayout bubble;
        final TextView tvSender;
        final TextView tvText;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.messageRoot);
            bubble = itemView.findViewById(R.id.messageBubble);
            tvSender = itemView.findViewById(R.id.tvMessageSender);
            tvText = itemView.findViewById(R.id.tvMessageText);
        }
    }
}
