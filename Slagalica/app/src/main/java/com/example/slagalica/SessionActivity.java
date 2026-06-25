package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.auth.FirebaseManager;
import com.example.slagalica.auth.SessionManager;
import com.example.slagalica.party.PartyActivity;
import com.example.slagalica.party.PartyRepository;
import com.google.firebase.auth.FirebaseUser;

public class SessionActivity extends AppCompatActivity {

    private Button btnCreateGame;
    private Button btnJoinGame;
    private EditText etJoinCode;
    private Button btnBack;
    private SessionManager sessionManager;
    private FirebaseManager firebaseManager;
    private PartyRepository partyRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        sessionManager = new SessionManager();
        firebaseManager = new FirebaseManager();
        partyRepository = new PartyRepository();

        btnCreateGame = findViewById(R.id.btnCreateGame);
        btnJoinGame = findViewById(R.id.btnJoinGame);
        etJoinCode = findViewById(R.id.etJoinCode);
        btnBack = findViewById(R.id.btnBack);

        btnCreateGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createGame();
            }
        });

        btnJoinGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                joinGame();
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        FirebaseUser currentUser = firebaseManager.getCurrentUser();
        if (currentUser == null) {
            btnCreateGame.setEnabled(false);
            btnJoinGame.setEnabled(false);
            firebaseManager.signInAnonymously(new FirebaseManager.AuthCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnCreateGame.setEnabled(true);
                            btnJoinGame.setEnabled(true);
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnCreateGame.setEnabled(true);
                            btnJoinGame.setEnabled(true);
                            Toast.makeText(SessionActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        }
    }

    private void createGame() {
        FirebaseUser registeredUser = firebaseManager.getCurrentUser();
        if (registeredUser != null && !registeredUser.isAnonymous()) {
            startRegularMatchmaking(registeredUser);
            return;
        }

        btnCreateGame.setEnabled(false);

        ensureSignedIn(new Runnable() {
            @Override
            public void run() {
                FirebaseUser user = firebaseManager.getCurrentUser();
                resolveUsername(user, new FirebaseManager.UserDataCallback() {
                    @Override
                    public void onSuccess(final String username, String region) {
                        sessionManager.createSession(user.getUid(), username, new SessionManager.SessionCallback() {
                            @Override
                            public void onSuccess(String sessionId) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        btnCreateGame.setEnabled(true);
                                        Intent intent = new Intent(SessionActivity.this, WaitingRoomActivity.class);
                                        intent.putExtra("sessionId", sessionId);
                                        intent.putExtra("isOwner", true);
                                        startActivity(intent);
                                    }
                                });
                            }

                            @Override
                            public void onError(final String message) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        btnCreateGame.setEnabled(true);
                                        Toast.makeText(SessionActivity.this, message, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                    }

                    @Override
                    public void onError(final String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btnCreateGame.setEnabled(true);
                                sessionManager.createSession(user.getUid(), "Igrač", new SessionManager.SessionCallback() {
                                    @Override
                                    public void onSuccess(String sessionId) {
                                        Intent intent = new Intent(SessionActivity.this, WaitingRoomActivity.class);
                                        intent.putExtra("sessionId", sessionId);
                                        intent.putExtra("isOwner", true);
                                        startActivity(intent);
                                    }

                                    @Override
                                    public void onError(String msg) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                btnCreateGame.setEnabled(true);
                                                Toast.makeText(SessionActivity.this, msg, Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    private void startRegularMatchmaking(FirebaseUser user) {
        btnCreateGame.setEnabled(false);
        btnJoinGame.setEnabled(false);

        resolveUsername(user, new FirebaseManager.UserDataCallback() {
            @Override
            public void onSuccess(String username, String region) {
                partyRepository.findRandomOpponentOrWait(user.getUid(), username, new PartyRepository.MatchmakingCallback() {
                    @Override
                    public void onPartyReady(String partyId) {
                        runOnUiThread(() -> {
                            btnCreateGame.setEnabled(true);
                            btnJoinGame.setEnabled(true);
                            Intent intent = new Intent(SessionActivity.this, PartyActivity.class);
                            intent.putExtra(PartyActivity.EXTRA_PARTY_ID, partyId);
                            startActivity(intent);
                            finish();
                        });
                    }

                    @Override
                    public void onWaiting() {
                        runOnUiThread(() -> {
                            btnCreateGame.setEnabled(true);
                            btnJoinGame.setEnabled(true);
                            Intent intent = new Intent(SessionActivity.this, PartyActivity.class);
                            intent.putExtra(PartyActivity.EXTRA_QUEUE_WAITING, true);
                            startActivity(intent);
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            btnCreateGame.setEnabled(true);
                            btnJoinGame.setEnabled(true);
                            Toast.makeText(SessionActivity.this, message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnCreateGame.setEnabled(true);
                    btnJoinGame.setEnabled(true);
                    Toast.makeText(SessionActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void joinGame() {
        final String code = etJoinCode.getText().toString().trim().toUpperCase();

        if (code.isEmpty()) {
            etJoinCode.setError("Unesite kod");
            return;
        }

        btnJoinGame.setEnabled(false);

        ensureSignedIn(new Runnable() {
            @Override
            public void run() {
                FirebaseUser user = firebaseManager.getCurrentUser();
                resolveUsername(user, new FirebaseManager.UserDataCallback() {
                    @Override
                    public void onSuccess(final String username, String region) {
                        doJoin(code, user.getUid(), username);
                    }

                    @Override
                    public void onError(final String message) {
                        doJoin(code, user.getUid(), "Igrač");
                    }
                });
            }
        });
    }

    private void doJoin(String code, String uid, String username) {
        sessionManager.joinSession(code, uid, username, new SessionManager.JoinCallback() {
            @Override
            public void onSuccess(final String sessionId) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnJoinGame.setEnabled(true);
                        Toast.makeText(SessionActivity.this, "Pridružili ste se igri!", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(SessionActivity.this, GameSelectionActivity.class);
                        intent.putExtra("sessionId", sessionId);
                        intent.putExtra("isOwner", false);
                        startActivity(intent);
                        finish();
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnJoinGame.setEnabled(true);
                        Toast.makeText(SessionActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void ensureSignedIn(final Runnable action) {
        FirebaseUser user = firebaseManager.getCurrentUser();
        if (user != null) {
            action.run();
        } else {
            firebaseManager.signInAnonymously(new FirebaseManager.AuthCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(action);
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnCreateGame.setEnabled(true);
                            btnJoinGame.setEnabled(true);
                            Toast.makeText(SessionActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        }
    }

    private void resolveUsername(FirebaseUser user, FirebaseManager.UserDataCallback callback) {
        if (user.isAnonymous()) {
            String uid = user.getUid();
            String name = "Gost" + uid.substring(Math.max(0, uid.length() - 4));
            callback.onSuccess(name, "");
        } else {
            firebaseManager.loadUserData(user.getUid(), callback);
        }
    }
}
