package edu.cuhk.cuchat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ServerValue;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;

import edu.cuhk.cuchat.adapters.ViewPagerAdapter;
import edu.cuhk.cuchat.services.UserStatusService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

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

        // Start user status service
        startService(new Intent(this, UserStatusService.class));

        // Check notification permission on Android 13+
        checkNotificationPermission();

        // Set up notification listener
        setupLocalNotificationListener();
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        100);
            }
        }
    }

    private void setupLocalNotificationListener() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();

        // Listen for chats where current user is a participant
        db.collection("chats")
                .whereArrayContains("participants", userId)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    for (DocumentChange change : snapshots.getDocumentChanges()) {
                        // Only handle modifications (new messages)
                        if (change.getType() != DocumentChange.Type.MODIFIED) continue;

                        String chatId = change.getDocument().getId();
                        String lastMessageSenderId = change.getDocument().getString("lastMessageSenderId");
                        String lastMessageContent = change.getDocument().getString("lastMessageContent");

                        // Don't notify for own messages
                        if (userId.equals(lastMessageSenderId)) continue;

                        // Check if message is unread for current user
                        boolean isUnread = false;
                        String user1Id = change.getDocument().getString("user1Id");
                        if (userId.equals(user1Id)) {
                            isUnread = !Boolean.TRUE.equals(change.getDocument().getBoolean("user1Seen"));
                        } else {
                            isUnread = !Boolean.TRUE.equals(change.getDocument().getBoolean("user2Seen"));
                        }

                        if (isUnread && lastMessageSenderId != null && lastMessageContent != null) {
                            // Get sender's name and show notification
                            db.collection("users").document(lastMessageSenderId)
                                    .get()
                                    .addOnSuccessListener(userDoc -> {
                                        if (userDoc.exists()) {
                                            String senderName = userDoc.getString("username");
                                            showLocalNotification(senderName, lastMessageContent, lastMessageSenderId);
                                        }
                                    });
                        }
                    }
                });
    }

    private void showLocalNotification(String title, String message, String senderId) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = getString(R.string.default_notification_channel_id);

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Chat Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        // Create intent to open chat
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("userId", senderId);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // Show notification
        notificationManager.notify(senderId.hashCode(), builder.build());
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Update user's online status when app comes to foreground
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            UserStatusService.setUserOnline(currentUser);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update online status
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            UserStatusService.setUserOnline(currentUser);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Do not update status to offline in onPause
        // Only do it in onStop - this keeps the user online
        // during brief pauses (like rotating the device)
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Update online status to offline when app goes to background
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Don't set offline if just changing activities within the app
            if (isFinishing()) {
                UserStatusService.setUserOffline(currentUser);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Make sure user is marked as offline when app is killed
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            UserStatusService.setUserOffline(currentUser);
        }
    }
}