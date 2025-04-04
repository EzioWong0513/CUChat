package edu.cuhk.cuchat.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class UserStatusService extends Service {
    private static final String TAG = "UserStatusService";

    private FirebaseUser currentUser;
    private ValueEventListener statusListener;
    private DatabaseReference userStatusDatabaseRef;
    private DatabaseReference connectedRef;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "UserStatusService started");

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            setupStatusTracking();
        }
    }

    private void setupStatusTracking() {
        // Reference to the user's status node in Realtime Database
        userStatusDatabaseRef = FirebaseDatabase.getInstance().getReference()
                .child("status").child(currentUser.getUid());

        // Reference to the connected node (used to detect connection state)
        connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");

        // Listen for connection state changes
        statusListener = connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class);
                Log.d(TAG, "Connection state changed: " + (connected ? "online" : "offline"));

                if (connected) {
                    // User is online, update status

                    // 1. Set up disconnect handler - this will update status to offline when connection is lost
                    Map<String, Object> onDisconnectValues = new HashMap<>();
                    onDisconnectValues.put("state", "offline");
                    onDisconnectValues.put("lastChanged", ServerValue.TIMESTAMP);
                    userStatusDatabaseRef.onDisconnect().updateChildren(onDisconnectValues);

                    // 2. Set the current state to online
                    Map<String, Object> onlineValues = new HashMap<>();
                    onlineValues.put("state", "online");
                    onlineValues.put("lastChanged", ServerValue.TIMESTAMP);
                    userStatusDatabaseRef.updateChildren(onlineValues);

                    // 3. Also update Firestore
                    updateFirestoreStatus(true);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error in connection listener", error.toException());
            }
        });

        // Add a listener to Realtime Database to track changes and sync to Firestore
        userStatusDatabaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String state = snapshot.child("state").getValue(String.class);
                    Object lastChanged = snapshot.child("lastChanged").getValue();

                    Log.d(TAG, "Status in Realtime DB changed: " + state);

                    // Sync status to Firestore
                    boolean isOnline = "online".equals(state);
                    updateFirestoreStatus(isOnline);

                    if (lastChanged != null && !isOnline) {
                        // If we have a timestamp and user is offline, update lastSeen in Firestore
                        if (lastChanged instanceof Long) {
                            updateLastSeen((Long) lastChanged);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error in status listener", error.toException());
            }
        });
    }

    private void updateFirestoreStatus(boolean isOnline) {
        if (currentUser == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> updates = new HashMap<>();
        updates.put("isOnline", isOnline);

        if (!isOnline) {
            // If going offline, also update lastSeen timestamp
            updates.put("lastSeen", System.currentTimeMillis());
        }

        db.collection("users").document(currentUser.getUid())
                .update(updates)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "Firestore status updated to: " + (isOnline ? "online" : "offline")))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error updating Firestore status", e));
    }

    private void updateLastSeen(long timestamp) {
        if (currentUser == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastSeen", timestamp);

        db.collection("users").document(currentUser.getUid())
                .update(updates)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "Firestore lastSeen updated to: " + timestamp))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error updating Firestore lastSeen", e));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "UserStatusService being destroyed");

        // Explicitly mark user as offline when service is destroyed
        if (currentUser != null) {
            // 1. Update Realtime Database
            Map<String, Object> offlineValues = new HashMap<>();
            offlineValues.put("state", "offline");
            offlineValues.put("lastChanged", ServerValue.TIMESTAMP);

            if (userStatusDatabaseRef != null) {
                userStatusDatabaseRef.updateChildren(offlineValues);
            }

            // 2. Update Firestore
            updateFirestoreStatus(false);

            // 3. Remove listeners
            if (connectedRef != null && statusListener != null) {
                connectedRef.removeEventListener(statusListener);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Static helper methods that can be called from activities

    public static void setUserOnline(FirebaseUser user) {
        if (user == null) return;

        // Update Firestore
        FirebaseFirestore.getInstance().collection("users")
                .document(user.getUid())
                .update("isOnline", true);

        // Update Realtime Database
        DatabaseReference userStatusRef = FirebaseDatabase.getInstance().getReference()
                .child("status").child(user.getUid());

        Map<String, Object> onlineValues = new HashMap<>();
        onlineValues.put("state", "online");
        onlineValues.put("lastChanged", ServerValue.TIMESTAMP);
        userStatusRef.updateChildren(onlineValues);
    }

    public static void setUserOffline(FirebaseUser user) {
        if (user == null) return;

        // Update Firestore
        Map<String, Object> updates = new HashMap<>();
        updates.put("isOnline", false);
        updates.put("lastSeen", System.currentTimeMillis());

        FirebaseFirestore.getInstance().collection("users")
                .document(user.getUid())
                .update(updates);

        // Update Realtime Database
        DatabaseReference userStatusRef = FirebaseDatabase.getInstance().getReference()
                .child("status").child(user.getUid());

        Map<String, Object> offlineValues = new HashMap<>();
        offlineValues.put("state", "offline");
        offlineValues.put("lastChanged", ServerValue.TIMESTAMP);
        userStatusRef.updateChildren(offlineValues);
    }
}