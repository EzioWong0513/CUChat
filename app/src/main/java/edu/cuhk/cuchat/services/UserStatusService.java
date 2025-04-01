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
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class UserStatusService extends Service {
    private static final String TAG = "UserStatusService";

    private FirebaseUser currentUser;
    private ValueEventListener statusListener;
    private DatabaseReference statusRef;

    @Override
    public void onCreate() {
        super.onCreate();

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            // Watch status changes in Realtime Database and propagate to Firestore
            statusRef = FirebaseDatabase.getInstance().getReference()
                    .child("status").child(currentUser.getUid());

            statusListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String status = snapshot.getValue(String.class);

                        // Update Firestore with current status
                        FirebaseFirestore db = FirebaseFirestore.getInstance();
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("isOnline", "online".equals(status));

                        if ("offline".equals(status)) {
                            // When going offline, update lastSeen timestamp
                            updates.put("lastSeen", System.currentTimeMillis());
                        }

                        db.collection("users").document(currentUser.getUid())
                                .update(updates)
                                .addOnSuccessListener(aVoid ->
                                        Log.d(TAG, "Status updated in Firestore: " + status));
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Error watching status", error.toException());
                }
            };

            statusRef.addValueEventListener(statusListener);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Remove listener and mark user as offline when service is destroyed
        if (statusRef != null && statusListener != null) {
            statusRef.removeEventListener(statusListener);
        }

        if (currentUser != null) {
            // Explicitly mark user as offline in Firestore
            FirebaseFirestore.getInstance().collection("users")
                    .document(currentUser.getUid())
                    .update("isOnline", false,
                            "lastSeen", System.currentTimeMillis());

            // Also mark in Realtime Database
            FirebaseDatabase.getInstance().getReference()
                    .child("status").child(currentUser.getUid())
                    .setValue("offline");
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}