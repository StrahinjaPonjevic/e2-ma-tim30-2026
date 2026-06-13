package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.auth.FirebaseManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ResetPasswordActivity extends AppCompatActivity {

    private EditText etResetEmail;
    private EditText etOldPassword;
    private EditText etNewPassword;
    private EditText etRepeatedNewPassword;
    private Button btnConfirmResetPassword;
    private Button btnResetPasswordBack;
    private FirebaseManager firebaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        firebaseManager = new FirebaseManager();

        etResetEmail = findViewById(R.id.etResetEmail);
        etOldPassword = findViewById(R.id.etOldPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etRepeatedNewPassword = findViewById(R.id.etRepeatedNewPassword);
        btnConfirmResetPassword = findViewById(R.id.btnConfirmResetPassword);
        btnResetPasswordBack = findViewById(R.id.btnResetPasswordBack);

        String intentEmail = getIntent().getStringExtra("email");
        if (intentEmail != null && !intentEmail.isEmpty()) {
            etResetEmail.setText(intentEmail);
        } else {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getEmail() != null) {
                etResetEmail.setText(user.getEmail());
            }
        }

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
        String email = etResetEmail.getText().toString().trim();
        String oldPassword = etOldPassword.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString().trim();
        String repeatedNewPassword = etRepeatedNewPassword.getText().toString().trim();

        if (email.isEmpty()) {
            etResetEmail.setError("Unesite email");
            return;
        }

        if (oldPassword.isEmpty()) {
            etOldPassword.setError("Unesite staru lozinku");
            return;
        }

        if (newPassword.isEmpty()) {
            etNewPassword.setError("Unesite novu lozinku");
            return;
        }

        if (newPassword.length() < 6) {
            etNewPassword.setError("Lozinka mora imati najmanje 6 karaktera");
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

        btnConfirmResetPassword.setEnabled(false);

        firebaseManager.resetPassword(email, oldPassword, newPassword, new FirebaseManager.AuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnConfirmResetPassword.setEnabled(true);
                        Toast.makeText(ResetPasswordActivity.this,
                                "Lozinka je uspešno promenjena. Prijavite se ponovo.",
                                Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnConfirmResetPassword.setEnabled(true);
                        Toast.makeText(ResetPasswordActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}
