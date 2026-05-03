package com.example.slagalica;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail;
    private EditText etUsername;
    private EditText etPassword;
    private EditText etRepeatedPassword;
    private Spinner spinnerRegion;
    private Button btnRegister;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etEmail = findViewById(R.id.etEmail);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etRepeatedPassword = findViewById(R.id.etRepeatedPassword);
        spinnerRegion = findViewById(R.id.spinnerRegion);
        btnRegister = findViewById(R.id.btnRegister);
        btnBack = findViewById(R.id.btnBack);

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                registerUser();
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String repeatedPassword = etRepeatedPassword.getText().toString().trim();
        String region = spinnerRegion.getSelectedItem().toString();

        if (email.isEmpty()) {
            etEmail.setError("Unesite email");
            return;
        }

        if (username.isEmpty()) {
            etUsername.setError("Unesite korisničko ime");
            return;
        }

        if (region.equals("Izaberi region")) {
            Toast.makeText(this, "Izaberite region", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Unesite lozinku");
            return;
        }

        if (repeatedPassword.isEmpty()) {
            etRepeatedPassword.setError("Ponovite lozinku");
            return;
        }

        if (!password.equals(repeatedPassword)) {
            etRepeatedPassword.setError("Lozinke se ne poklapaju");
            return;
        }

        Toast.makeText(
                this,
                "Registracija uspešna. Link za potvrdu je poslat na email.",
                Toast.LENGTH_LONG
        ).show();

        finish();
    }
}