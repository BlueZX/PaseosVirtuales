package com.example.paseosvirtuales;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;

import com.google.ar.core.ArCoreApk;

public class MainActivity extends AppCompatActivity {

    private Button mArButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable AR related functionality on ARCore supported devices only.

        mArButton = (Button) findViewById(R.id.mArButton);

        setContentView(R.layout.activity_main);
    }

}