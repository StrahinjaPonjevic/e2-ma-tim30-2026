package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.auth.FirebaseManager;

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginIdentifier;
    private EditText etLoginPassword;
    private Button btnLogin;
    private Button btnGoToRegister;
    private Button btnForgotPassword;
    private Button btnLoginBack;
    private FirebaseManager firebaseManager;
    private String resolvedEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        firebaseManager = new FirebaseManager();

        etLoginIdentifier = findViewById(R.id.etLoginIdentifier);
        etLoginPassword = findViewById(R.id.etLoginPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoToRegister = findViewById(R.id.btnGoToRegister);
        btnForgotPassword = findViewById(R.id.btnForgotPassword);
        btnLoginBack = findViewById(R.id.btnLoginBack);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loginUser();
            }
        });

        btnGoToRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });

        btnForgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LoginActivity.this, ResetPasswordActivity.class);
                String typed = etLoginIdentifier.getText().toString().trim();
                if (!typed.isEmpty() && typed.contains("@")) {
                    intent.putExtra("email", typed);
                }
                startActivity(intent);
            }
        });

        btnLoginBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    private void loginUser() {
        String identifier = etLoginIdentifier.getText().toString().trim();
        String password = etLoginPassword.getText().toString().trim();

        if (identifier.isEmpty()) {
            etLoginIdentifier.setError("Unesite email ili korisnicko ime");
            return;
        }

        if (password.isEmpty()) {
            etLoginPassword.setError("Unesite lozinku");
            return;
        }

        btnLogin.setEnabled(false);

        boolean isEmail = identifier.contains("@");

        FirebaseManager.LoginCallback callback = new FirebaseManager.LoginCallback() {
            @Override
            public void onSuccess(final String email) {
                resolvedEmail = email;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnLogin.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "Uspešna prijava.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnLogin.setEnabled(true);
                        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        };

        if (isEmail) {
            firebaseManager.loginWithEmail(identifier, password, callback);
        } else {
            firebaseManager.loginWithUsername(identifier, password, callback);
        }
    }
}
