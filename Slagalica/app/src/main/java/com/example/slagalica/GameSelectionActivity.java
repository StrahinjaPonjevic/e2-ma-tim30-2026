package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.auth.SessionManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class GameSelectionActivity extends AppCompatActivity {

    private Button btnKorakPoKorak;
    private Button btnMojBroj;
    private Button btnKoZnaZna;
    private Button btnSpojnice;
    private Button btnAsocijacije;
    private Button btnSkocko;
    private Button btnBack;
    private SessionManager sessionManager;
    private String sessionId;
    private boolean isOwner;
    private com.google.firebase.firestore.ListenerRegistration listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_selection);

        sessionManager = new SessionManager();
        sessionId = getIntent().getStringExtra("sessionId");
        isOwner = getIntent().getBooleanExtra("isOwner", true);

        btnKorakPoKorak = findViewById(R.id.btnOpenKorakPoKorak);
        btnMojBroj = findViewById(R.id.btnOpenMojBroj);
        btnKoZnaZna = findViewById(R.id.btnOpenKoZnaZna);
        btnSpojnice = findViewById(R.id.btnOpenSpojnice);
        btnAsocijacije = findViewById(R.id.btnOpenAsocijacije);
        btnSkocko = findViewById(R.id.btnOpenSkocko);
        btnBack = findViewById(R.id.btnBack);

        if (sessionId != null && !isOwner) {
            setTitle("Čekanje izbora protivnika...");
            disableAllButtons();
            listenForGameSelection();
        }

        btnKorakPoKorak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectGameOrStart("korak_po_korak", KorakPoKorakActivity.class);
            }
        });

        btnMojBroj.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectGameOrStart("moj_broj", MojBrojActivity.class);
            }
        });

        btnKoZnaZna.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectGameOrStart("ko_zna_zna", KoZnaZnaActivity.class);
            }
        });

        btnSpojnice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectGameOrStart("spojnice", SpojniceActivity.class);
            }
        });

        btnAsocijacije.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectGameOrStart("asocijacije", AsocijacijeActivity.class);
            }
        });

        btnSkocko.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectGameOrStart("skocko", SkockoActivity.class);
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (sessionId != null && isOwner) {
                    sessionManager.endSession(sessionId);
                }
                finish();
            }
        });
    }

    private void selectGameOrStart(String gameName, Class<?> activityClass) {
        if (sessionId != null && isOwner) {
            sessionManager.selectGame(sessionId, gameName);
            Toast.makeText(this, "Igra pokrenuta!", Toast.LENGTH_SHORT).show();
        }
        Intent intent = new Intent(GameSelectionActivity.this, activityClass);
        if (sessionId != null) {
            intent.putExtra("sessionId", sessionId);
            intent.putExtra("isOwner", isOwner);
        }
        startActivity(intent);
        if (sessionId != null) {
            finish();
        }
    }

    private void listenForGameSelection() {
        listener = FirebaseFirestore.getInstance()
                .collection("sessions").document(sessionId)
                .addSnapshotListener(new com.google.firebase.firestore.EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(DocumentSnapshot snapshot,
                                        com.google.firebase.firestore.FirebaseFirestoreException e) {
                        if (e != null || snapshot == null || !snapshot.exists()) return;

                        String selectedGame = snapshot.getString("selectedGame");
                        if (selectedGame != null && !selectedGame.isEmpty()) {
                            if (listener != null) listener.remove();
                            openGame(selectedGame);
                        }
                    }
                });
    }

    private void openGame(String gameName) {
        Intent intent = null;
        switch (gameName) {
            case "korak_po_korak":
                intent = new Intent(this, KorakPoKorakActivity.class);
                break;
            case "moj_broj":
                intent = new Intent(this, MojBrojActivity.class);
                break;
            case "ko_zna_zna":
                intent = new Intent(this, KoZnaZnaActivity.class);
                break;
            case "spojnice":
                intent = new Intent(this, SpojniceActivity.class);
                break;
            case "asocijacije":
                intent = new Intent(this, AsocijacijeActivity.class);
                break;
            case "skocko":
                intent = new Intent(this, SkockoActivity.class);
                break;
        }
        if (intent != null) {
            intent.putExtra("sessionId", sessionId);
            intent.putExtra("isOwner", false);
            startActivity(intent);
            finish();
        }
    }

    private void disableAllButtons() {
        btnKorakPoKorak.setEnabled(false);
        btnMojBroj.setEnabled(false);
        btnKoZnaZna.setEnabled(false);
        btnSpojnice.setEnabled(false);
        btnAsocijacije.setEnabled(false);
        btnSkocko.setEnabled(false);
        btnBack.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) {
            listener.remove();
        }
    }
}
