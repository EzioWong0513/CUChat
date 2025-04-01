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
import com.google.firebase.firestore.DocumentChange;
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
    private static final String CHANNEL_ID = "cuchat_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "NotificationService created");
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

            if (data != null && !data.isEmpty()) {
                clickAction = data.get("click_action");
                chatUserId = data.get("user_id");
                Log.d(TAG, "Message data: clickAction=" + clickAction + ", chatUserId=" + chatUserId);
            }

            sendNotification(title, body, clickAction, chatUserId);
        } else if (remoteMessage.getData().size() > 0) {
            // Handle data messages
            Map<String, String> data = remoteMessage.getData();
            Log.d(TAG, "Message data payload: " + data);

            String title = data.get("title");
            String body = data.get("body");
            String clickAction = data.get("click_action");
            String chatUserId = data.get("user_id");

            if (title != null && body != null) {
                sendNotification(title, body, clickAction, chatUserId);
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        sendRegistrationToServer(token);
    }

    private void sendRegistrationToServer(String token) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();

            // Update the FCM token in Firestore
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("fcmToken", token);

            FirebaseFirestore.getInstance().collection("users").document(userId)
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

            // Get the username and profile image for the chat
            FirebaseFirestore.getInstance().collection("users").document(chatUserId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String username = documentSnapshot.getString("username");
                            String profileImageUrl = documentSnapshot.getString("profileImageUrl");

                            intent.putExtra("username", username);
                            intent.putExtra("profileImageUrl", profileImageUrl);

                            // Now continue with showing the notification
                            showNotificationWithIntent(title, messageBody, intent, chatUserId.hashCode());
                        } else {
                            showNotificationWithIntent(title, messageBody, intent, chatUserId.hashCode());
                        }
                    })
                    .addOnFailureListener(e -> {
                        showNotificationWithIntent(title, messageBody, intent, chatUserId.hashCode());
                    });

            return; // Exit early as we'll show notification from the callback
        } else {
            // Otherwise, open the main activity
            intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }

        showNotificationWithIntent(title, messageBody, intent, 0);
    }

    private void showNotificationWithIntent(String title, String messageBody, Intent intent, int notificationId) {
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
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    "CUChat Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.enableLights(true);
            channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(notificationId, notificationBuilder.build());
    }
}