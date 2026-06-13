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
import com.google.firebase.auth.FirebaseUser;

public class SessionActivity extends AppCompatActivity {

    private Button btnCreateGame;
    private Button btnJoinGame;
    private EditText etJoinCode;
    private Button btnBack;
    private SessionManager sessionManager;
    private FirebaseManager firebaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        sessionManager = new SessionManager();
        firebaseManager = new FirebaseManager();

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
