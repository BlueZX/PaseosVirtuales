package com.example.paseosvirtuales.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import com.example.paseosvirtuales.R;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private ImageButton closeButton;
    private Button signInButton;
    private TextInputEditText emailET;
    private TextInputEditText passwordET;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        closeButton = findViewById(R.id.btn_close_login);
        signInButton = findViewById(R.id.btn_login);
        emailET = findViewById(R.id.et_login_email);
        passwordET = findViewById(R.id.et_login_password);

        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("login", emailET.getText() + ", " + passwordET.getText());
            }
        });
    }
}