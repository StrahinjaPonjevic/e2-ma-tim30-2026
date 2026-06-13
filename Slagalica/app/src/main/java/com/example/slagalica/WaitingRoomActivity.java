package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.auth.SessionManager;

public class WaitingRoomActivity extends AppCompatActivity {

    private TextView tvCode;
    private TextView tvWaitingMessage;
    private Button btnCancel;
    private SessionManager sessionManager;
    private String sessionId;
    private boolean isOwner;
    private com.google.firebase.firestore.ListenerRegistration listenerRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting_room);

        sessionManager = new SessionManager();

        sessionId = getIntent().getStringExtra("sessionId");
        isOwner = getIntent().getBooleanExtra("isOwner", true);

        tvCode = findViewById(R.id.tvCode);
        tvWaitingMessage = findViewById(R.id.tvWaitingMessage);
        btnCancel = findViewById(R.id.btnCancel);

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sessionManager.endSession(sessionId);
                finish();
            }
        });

        loadSessionCode();
        listenForOpponent();
    }

    private void loadSessionCode() {
        sessionManager.listenSession(sessionId, new SessionManager.SessionListener() {
            @Override
            public void onSessionUpdated(SessionManager.SessionData data) {
                if (data.code != null) {
                    tvCode.setText(data.code);
                    tvWaitingMessage.setText("Čekanje protivnika...\nKod: " + data.code);
                }
                if (data.guestId != null && !data.guestId.isEmpty() && "joined".equals(data.status)) {
                    listenerRegistration.remove();
                    startActivity(new Intent(WaitingRoomActivity.this, GameSelectionActivity.class)
                            .putExtra("sessionId", sessionId)
                            .putExtra("isOwner", true));
                    finish();
                }
            }

            @Override
            public void onError(String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(WaitingRoomActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void listenForOpponent() {
        listenerRegistration = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("sessions").document(sessionId)
                .addSnapshotListener(new com.google.firebase.firestore.EventListener<com.google.firebase.firestore.DocumentSnapshot>() {
                    @Override
                    public void onEvent(com.google.firebase.firestore.DocumentSnapshot snapshot,
                                        com.google.firebase.firestore.FirebaseFirestoreException e) {
                        if (e != null) return;
                        if (snapshot != null && snapshot.exists()) {
                            String status = snapshot.getString("status");
                            String code = snapshot.getString("code");
                            String guestId = snapshot.getString("guestId");

                            if (code != null) {
                                tvCode.setText(code);
                            }

                            if ("joined".equals(status) && guestId != null && !guestId.isEmpty()) {
                                Toast.makeText(WaitingRoomActivity.this, "Protivnik se pridružio!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(WaitingRoomActivity.this, GameSelectionActivity.class)
                                        .putExtra("sessionId", sessionId)
                                        .putExtra("isOwner", true));
                                finish();
                            }
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }
}
