package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.auth.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvUsername;
    private TextView tvEmail;
    private TextView tvRegion;
    private Button btnEditAvatar;
    private Button btnLogout;
    private Button btnBackToMain;
    private FirebaseManager firebaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        firebaseManager = new FirebaseManager();

        tvUsername = findViewById(R.id.tvUsername);
        tvEmail = findViewById(R.id.tvEmail);
        tvRegion = findViewById(R.id.tvRegion);
        btnEditAvatar = findViewById(R.id.btnEditAvatar);
        btnLogout = findViewById(R.id.btnLogout);
        btnBackToMain = findViewById(R.id.btnBackToMain);

        loadUserProfile();

        btnEditAvatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(ProfileActivity.this, "Izmena avatara će biti dodata kasnije", Toast.LENGTH_SHORT).show();
            }
        });

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                firebaseManager.logout();
                Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });

        btnBackToMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    private void loadUserProfile() {
        FirebaseUser user = firebaseManager.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Morate biti prijavljeni", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvEmail.setText("Email: " + user.getEmail());

        firebaseManager.loadUserData(user.getUid(), new FirebaseManager.UserDataCallback() {
            @Override
            public void onSuccess(final String username, final String region) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvUsername.setText("Korisničko ime: " + username);
                        tvRegion.setText("Region: " + region);
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}