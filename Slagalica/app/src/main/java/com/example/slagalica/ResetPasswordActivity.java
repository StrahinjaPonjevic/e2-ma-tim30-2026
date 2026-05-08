package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ResetPasswordActivity extends AppCompatActivity {

    private EditText etOldPassword;
    private EditText etNewPassword;
    private EditText etRepeatedNewPassword;
    private Button btnConfirmResetPassword;
    private Button btnResetPasswordBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        etOldPassword = findViewById(R.id.etOldPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etRepeatedNewPassword = findViewById(R.id.etRepeatedNewPassword);
        btnConfirmResetPassword = findViewById(R.id.btnConfirmResetPassword);
        btnResetPasswordBack = findViewById(R.id.btnResetPasswordBack);

        btnConfirmResetPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetPassword();
            }
        });

        btnResetPasswordBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    private void resetPassword() {
        String oldPassword = etOldPassword.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString().trim();
        String repeatedNewPassword = etRepeatedNewPassword.getText().toString().trim();

        if (oldPassword.isEmpty()) {
            etOldPassword.setError("Unesite staru lozinku");
            return;
        }

        if (newPassword.isEmpty()) {
            etNewPassword.setError("Unesite novu lozinku");
            return;
        }

        if (repeatedNewPassword.isEmpty()) {
            etRepeatedNewPassword.setError("Ponovite novu lozinku");
            return;
        }

        if (!newPassword.equals(repeatedNewPassword)) {
            etRepeatedNewPassword.setError("Nove lozinke se ne poklapaju");
            return;
        }

        Toast.makeText(this, "Lozinka je uspesno promenjena.", Toast.LENGTH_SHORT).show();
        finish();
    }
}
