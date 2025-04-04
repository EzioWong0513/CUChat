package edu.cuhk.cuchat;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;
import edu.cuhk.cuchat.adapters.MessagesAdapter;
import edu.cuhk.cuchat.models.Message;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";

    private Toolbar toolbar;
    private CircleImageView ivUserProfilePic;
    private TextView tvUsername;
    private TextView tvUserStatus; // Add this field to track the user status TextView
    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageButton btnSendMessage;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String currentUserId;
    private String chatUserId; // ID of the user we're chatting with
    private String chatId; // ID of the chat document in Firestore

    private MessagesAdapter messagesAdapter;
    private List<Message> messageList;

    private ListenerRegistration userStatusListener; // Add this to track the user status listener

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Get chatting user's ID from intent
        chatUserId = getIntent().getStringExtra("userId");
        String username = getIntent().getStringExtra("username");
        String profileImageUrl = getIntent().getStringExtra("profileImageUrl");

        if (chatUserId == null) {
            finish();
            return;
        }

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();
        currentUserId = currentUser.getUid();

        // Initialize UI components
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        ivUserProfilePic = findViewById(R.id.ivUserProfilePic);
        tvUsername = findViewById(R.id.tvUsername);
        tvUserStatus = findViewById(R.id.tvUserStatus); // Initialize the user status TextView
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSendMessage = findViewById(R.id.btnSendMessage);

        // Set user info in toolbar
        tvUsername.setText(username);
        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
            Glide.with(this)
                    .load(profileImageUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(ivUserProfilePic);
        }

        // Initialize RecyclerView
        messageList = new ArrayList<>();
        messagesAdapter = new MessagesAdapter(this, messageList, currentUserId);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(messagesAdapter);

        // Create or get chat ID
        if (currentUserId.compareTo(chatUserId) < 0) {
            chatId = currentUserId + "_" + chatUserId;
        } else {
            chatId = chatUserId + "_" + currentUserId;
        }

        // Set click listener for send button
        btnSendMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        // Load messages
        loadMessages();

        // Start listening for user status changes
        listenForUserStatusChanges();

        setupMessageSeenListener();
    }

    // Add this method to listen for user status changes
    private void listenForUserStatusChanges() {
        DocumentReference userRef = db.collection("users").document(chatUserId);

        userStatusListener = userRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e);
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    // Check if user is online
                    Boolean isOnline = snapshot.getBoolean("isOnline");
                    Long lastSeen = snapshot.getLong("lastSeen");

                    // Update UI with status
                    updateUserStatusUI(isOnline, lastSeen);
                }
            }
        });
    }

    // Add this method to update the user status UI
    private void updateUserStatusUI(Boolean isOnline, Long lastSeen) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tvUserStatus != null) {
                    if (isOnline != null && isOnline) {
                        tvUserStatus.setText("Online");
                        tvUserStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    } else {
                        // Format last seen time if available
                        if (lastSeen != null && lastSeen > 0) {
                            String formattedTime = formatLastSeen(lastSeen);
                            tvUserStatus.setText("Last seen " + formattedTime);
                        } else {
                            tvUserStatus.setText("Offline");
                        }
                        tvUserStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
                    }
                }
            }
        });
    }

    private void loadMessages() {
        db.collection("chats").document(chatId).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            Log.w(TAG, "Listen failed.", error);
                            return;
                        }

                        for (DocumentChange dc : value.getDocumentChanges()) {
                            switch (dc.getType()) {
                                case ADDED:
                                    Message message = dc.getDocument().toObject(Message.class);
                                    message.setMessageId(dc.getDocument().getId());
                                    messageList.add(message);
                                    break;
                                case MODIFIED:
                                    // Handle modified messages - update the seen status
                                    Message updatedMessage = dc.getDocument().toObject(Message.class);
                                    updatedMessage.setMessageId(dc.getDocument().getId());

                                    // Find and replace the modified message
                                    for (int i = 0; i < messageList.size(); i++) {
                                        if (messageList.get(i).getMessageId().equals(updatedMessage.getMessageId())) {
                                            messageList.set(i, updatedMessage);
                                            messagesAdapter.notifyItemChanged(i);
                                            break;
                                        }
                                    }
                                    break;
                                case REMOVED:
                                    // Handle removed messages if needed
                                    break;
                            }
                        }

                        messagesAdapter.notifyDataSetChanged();
                        if (messageList.size() > 0) {
                            rvMessages.scrollToPosition(messageList.size() - 1);
                        }

                        // Mark messages as seen if they are from the chat user
                        markMessagesAsSeen();
                    }
                });
    }

    private void setupMessageSeenListener() {
        // Listen for changes to the messages in this chat to update seen status in real-time
        db.collection("chats").document(chatId).collection("messages")
                .whereEqualTo("senderId", currentUserId)  // Only listen for messages sent by the current user
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.MODIFIED) {
                            // Message was modified - check if seen status changed
                            Message updatedMessage = dc.getDocument().toObject(Message.class);
                            updatedMessage.setMessageId(dc.getDocument().getId());

                            // Update the seen status in the adapter
                            boolean seen = updatedMessage.isSeen();
                            if (seen && messagesAdapter != null) {
                                messagesAdapter.updateMessageSeenStatus(updatedMessage.getMessageId(), true);
                            }
                        }
                    }
                });
    }

    private void markMessagesAsSeen() {
        boolean updatedAny = false;
        WriteBatch batch = db.batch();

        for (Message message : messageList) {
            if (message.getSenderId().equals(chatUserId) && !message.isSeen()) {
                DocumentReference messageRef = db.collection("chats").document(chatId)
                        .collection("messages").document(message.getMessageId());
                batch.update(messageRef, "seen", true);
                updatedAny = true;
            }
        }

        if (updatedAny) {
            batch.commit().addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Batch update successful - messages marked as seen");
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Error updating messages seen status", e);
            });
        }
    }

    private void sendMessage() {
        String messageContent = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(messageContent)) {
            return;
        }

        long timestamp = System.currentTimeMillis();

        // Create a new message
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("senderId", currentUserId);
        messageMap.put("receiverId", chatUserId);
        messageMap.put("content", messageContent);
        messageMap.put("timestamp", timestamp);
        messageMap.put("seen", false);
        messageMap.put("notificationSent", false);

        // Add message to the chat
        db.collection("chats").document(chatId).collection("messages")
                .add(messageMap)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Message sent with ID: " + documentReference.getId());
                    etMessage.setText("");

                    // THIS LINE IS CRUCIAL - Make sure it's here and not commented out!
                    updateChatSummary(messageContent, timestamp);

                    // Look up the receiver's FCM token for notifications
                    db.collection("users").document(chatUserId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                String receiverToken = documentSnapshot.getString("fcmToken");
                                boolean isOnline = Boolean.TRUE.equals(documentSnapshot.getBoolean("isOnline"));

                                // Mark that we checked for notification
                                documentReference.update("notificationSent", true);
                            });
                })
                .addOnFailureListener(e -> Log.w(TAG, "Error sending message", e));
    }

    private void updateChatSummary(String lastMessage, long timestamp) {
        // Determine user1Id and user2Id based on lexicographical ordering
        String user1Id, user2Id;
        if (currentUserId.compareTo(chatUserId) < 0) {
            user1Id = currentUserId;
            user2Id = chatUserId;
        } else {
            user1Id = chatUserId;
            user2Id = currentUserId;
        }

        // Create chat summary
        Map<String, Object> chatUpdates = new HashMap<>();
        chatUpdates.put("chatId", chatId);
        chatUpdates.put("user1Id", user1Id);
        chatUpdates.put("user2Id", user2Id);
        chatUpdates.put("lastMessageContent", lastMessage);
        chatUpdates.put("lastMessageTimestamp", timestamp);
        chatUpdates.put("lastMessageSenderId", currentUserId); // Add this line to track who sent the last message

        // Create participants array
        List<String> participants = new ArrayList<>();
        participants.add(user1Id);
        participants.add(user2Id);
        chatUpdates.put("participants", participants);

        // Set seen status - the sender has seen it, the receiver hasn't
        chatUpdates.put("user1Seen", currentUserId.equals(user1Id));
        chatUpdates.put("user2Seen", currentUserId.equals(user2Id));

        // Update or create chat document
        db.collection("chats").document(chatId)
                .set(chatUpdates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Chat summary updated successfully");
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error updating chat summary", e);
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateChatSeenStatus() {
        db.collection("chats").document(chatId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> updates = new HashMap<>();
                        if (currentUserId.equals(documentSnapshot.getString("user1Id"))) {
                            updates.put("user1Seen", true);
                        } else if (currentUserId.equals(documentSnapshot.getString("user2Id"))) {
                            updates.put("user2Seen", true);
                        }

                        // Update the chat document
                        if (!updates.isEmpty()) {
                            db.collection("chats").document(chatId)
                                    .update(updates);
                        }
                    }
                });
    }

    private void updateUserStatus(boolean isOnline) {
        if (currentUserId != null) {
            db.collection("users").document(currentUserId)
                    .update("isOnline", isOnline);
        }
    }

    private void updateUserStatusUI(Boolean isOnline, String status, Long lastSeen) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tvUserStatus != null) {
                    if (isOnline != null && isOnline) {
                        tvUserStatus.setText("Online");
                        tvUserStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    } else {
                        // Format last seen time if available
                        if (lastSeen != null && lastSeen > 0) {
                            String formattedTime = formatLastSeen(lastSeen);
                            tvUserStatus.setText("Last seen " + formattedTime);
                        } else if (status != null && !status.isEmpty()) {
                            tvUserStatus.setText(status);
                        } else {
                            tvUserStatus.setText("Offline");
                        }
                        tvUserStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
                    }
                }
            }
        });
    }

    private String formatLastSeen(long lastSeen) {
        long now = System.currentTimeMillis();
        long diff = now - lastSeen;

        // Less than a minute
        if (diff < 60 * 1000) {
            return "just now";
        }
        // Less than an hour
        else if (diff < 60 * 60 * 1000) {
            int minutes = (int) (diff / (60 * 1000));
            return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        }
        // Less than a day
        else if (diff < 24 * 60 * 60 * 1000) {
            int hours = (int) (diff / (60 * 60 * 1000));
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        }
        // Less than a week
        else if (diff < 7 * 24 * 60 * 60 * 1000) {
            int days = (int) (diff / (24 * 60 * 60 * 1000));
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        }
        // Format as date
        else {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(lastSeen);
            return DateFormat.format("MMM d, yyyy", cal).toString();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateUserStatus(false);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Update online status when leaving chat
        // But only if finishing (actually leaving the activity)
        if (isFinishing()) {
            if (currentUser != null) {
                db.collection("users").document(currentUserId)
                        .update("isOnline", true);  // Keep as online since app is still running
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update online status
        if (currentUser != null) {
            db.collection("users").document(currentUserId)
                    .update("isOnline", true);
        }

        // Mark this chat as seen
        updateChatSeenStatus();

        // Mark all messages as seen
        markMessagesAsSeen();

        // Update user status to online
        updateUserStatus(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove the user status listener to prevent memory leaks
        if (userStatusListener != null) {
            userStatusListener.remove();
        }
    }

}