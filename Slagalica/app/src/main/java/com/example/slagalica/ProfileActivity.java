package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {

    private Button btnEditAvatar;
    private Button btnLogout;
    private Button btnBackToMain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        btnEditAvatar = findViewById(R.id.btnEditAvatar);
        btnLogout = findViewById(R.id.btnLogout);
        btnBackToMain = findViewById(R.id.btnBackToMain);

        btnEditAvatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(ProfileActivity.this, "Izmena avatara će biti dodata kasnije", Toast.LENGTH_SHORT).show();
            }
        });

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(ProfileActivity.this, "Logout će biti dodat kasnije", Toast.LENGTH_SHORT).show();
            }
        });

        btnBackToMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }
}