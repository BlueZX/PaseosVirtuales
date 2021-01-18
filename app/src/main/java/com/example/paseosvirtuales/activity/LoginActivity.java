package com.example.paseosvirtuales.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
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
    private Button signUpButton;
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
        signUpButton = findViewById(R.id.btn_go_signup);

        emailET.setText("ej@gmail.com");
        passwordET.setText("hola1234");

        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(emailET.getText().toString().equals("ej@gmail.com") && passwordET.getText().toString().equals("hola1234")){
                    Log.d("login", emailET.getText() + ", " + passwordET.getText());
                    Intent intent = new Intent(view.getContext(), ARActivity.class);
                    startActivity(intent);
                }
            }
        });

        signUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), SignUpActivity.class);
                startActivity(intent);
            }
        });

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), InitActivity.class);
                startActivity(intent);
            }
        });
    }
}