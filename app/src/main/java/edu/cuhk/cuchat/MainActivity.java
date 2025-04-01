package edu.cuhk.cuchat;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import edu.cuhk.cuchat.adapters.ViewPagerAdapter;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigation;
    private ViewPagerAdapter viewPagerAdapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize Views
        viewPager = findViewById(R.id.viewPager);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Set up ViewPager
        viewPagerAdapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(viewPagerAdapter);
        viewPager.setUserInputEnabled(false); // Disable swiping

        // Set up BottomNavigationView
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_map) {
                viewPager.setCurrentItem(0);
                return true;
            } else if (id == R.id.navigation_chats) {
                viewPager.setCurrentItem(1);
                return true;
            } else if (id == R.id.navigation_profile) {
                viewPager.setCurrentItem(2);
                return true;
            }
            return false;
        });

        // Sync ViewPager with BottomNavigationView
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 0:
                        bottomNavigation.setSelectedItemId(R.id.navigation_map);
                        break;
                    case 1:
                        bottomNavigation.setSelectedItemId(R.id.navigation_chats);
                        break;
                    case 2:
                        bottomNavigation.setSelectedItemId(R.id.navigation_profile);
                        break;
                }
            }
        });
    }
}