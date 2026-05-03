package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginIdentifier;
    private EditText etLoginPassword;
    private Button btnLogin;
    private Button btnGoToRegister;
    private Button btnForgotPassword;
    private Button btnLoginBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

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
                Toast.makeText(
                        LoginActivity.this,
                        "Reset lozinke ćemo implementirati na posebnom ekranu.",
                        Toast.LENGTH_SHORT
                ).show();
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
            etLoginIdentifier.setError("Unesite email ili korisničko ime");
            return;
        }

        if (password.isEmpty()) {
            etLoginPassword.setError("Unesite lozinku");
            return;
        }

        Toast.makeText(
                this,
                "Uspešna prijava.",
                Toast.LENGTH_SHORT
        ).show();
        finish();
    }
}