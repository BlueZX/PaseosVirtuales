package com.example.paseosvirtuales.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import com.example.paseosvirtuales.R;
import com.example.paseosvirtuales.activity.AR.ARFragment;
import com.example.paseosvirtuales.activity.Home.HomeFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainMenuActivity extends AppCompatActivity {

    private boolean viewIsAtHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        BottomNavigationView navigation = findViewById(R.id.bottom_navigation);

        navigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                int itemId = menuItem.getItemId();
                switch (itemId){
                    case R.id.home:
                        addFragment(new HomeFragment());
                        viewIsAtHome = true;
                        break;
                    case R.id.initRA:
                        addFragment(new ARFragment());
                        viewIsAtHome = false;
                        break;
                }
                return true;
            }
        });

        navigation.setSelectedItemId(R.id.home);

    }

    private void addFragment(Fragment fragment){
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onBackPressed() {
        if (!viewIsAtHome) {
            BottomNavigationView bottomNavigationView = (BottomNavigationView) findViewById(R.id.bottom_navigation);
            bottomNavigationView.setSelectedItemId(R.id.home);
        } else {
            moveTaskToBack(true);
        }
    }

}