package edu.cuhk.cuchat.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;  // Add this import
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

import edu.cuhk.cuchat.ChatActivity;
import edu.cuhk.cuchat.MainActivity;
import edu.cuhk.cuchat.R;

public class NotificationService extends FirebaseMessagingService {

    private static final String TAG = "NotificationService";
    private FirebaseFirestore db;

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();

        setupMessageListener();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());

            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();

            // Get data payload if any
            Map<String, String> data = remoteMessage.getData();
            String clickAction = null;
            String chatUserId = null;

            if (data != null) {
                clickAction = data.get("click_action");
                chatUserId = data.get("user_id");
            }

            sendNotification(title, body, clickAction, chatUserId);

        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);

        // If you want to send messages to this application instance or
        // manage this app's subscriptions on the server side, send the
        // FCM registration token to your app server.
        sendRegistrationToServer(token);
    }

    private void sendRegistrationToServer(String token) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();

            // Update the FCM token in Firestore
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("fcmToken", token);

            db.collection("users").document(userId)
                    .update(tokenData)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "FCM token updated in Firestore");
                            } else {
                                Log.w(TAG, "Failed to update FCM token", task.getException());
                            }
                        }
                    });
        }
    }

    private void sendNotification(String title, String messageBody, String clickAction, String chatUserId) {
        Intent intent;

        if (clickAction != null && clickAction.equals("CHAT_ACTIVITY") && chatUserId != null) {
            // If notification is for a chat message, open the specific chat
            intent = new Intent(this, ChatActivity.class);
            intent.putExtra("userId", chatUserId);
        } else {
            // Otherwise, open the main activity
            intent = new Intent(this, MainActivity.class);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        String channelId = getString(R.string.default_notification_channel_id);
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    "CUChat Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(0, notificationBuilder.build());
    }

    // Add this method to your NotificationService class
    public void setupMessageListener() {
        Log.d(TAG, "setupMessageListener() called");
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "currentUser is null, exiting setupMessageListener()");
            return;
        }

        String currentUserId = currentUser.getUid();
        Log.d(TAG, "Setting up listener for user: " + currentUserId);

        // Listen for messages where the current user is the receiver
        FirebaseFirestore.getInstance().collectionGroup("messages")
                .whereEqualTo("receiverId", currentUserId)
                .whereEqualTo("seen", false)
                .whereEqualTo("notificationSent", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }

                    Log.d(TAG, "Snapshot listener triggered. Empty? " +
                            (snapshots == null || snapshots.isEmpty()));


                    if (snapshots != null && !snapshots.isEmpty()) {
                        Log.d(TAG, "Number of documents: " + snapshots.size());
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                // Get message data
                                String senderId = dc.getDocument().getString("senderId");
                                String content = dc.getDocument().getString("content");

                                // Mark notification as sent
                                dc.getDocument().getReference().update("notificationSent", true);

                                // Get sender's name
                                FirebaseFirestore.getInstance().collection("users")
                                        .document(senderId)
                                        .get()
                                        .addOnSuccessListener(document -> {
                                            if (document.exists()) {
                                                String senderName = document.getString("username");
                                                // Show local notification
                                                showLocalNotification(senderName, content, senderId);
                                            }
                                        });
                            }
                        }
                    }
                });
    }

    private void showLocalNotification(String title, String body, String senderId) {
        // Create notification
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("userId", senderId);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        String channelId = getString(R.string.default_notification_channel_id);
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    "CUChat Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(0, notificationBuilder.build());
    }
}