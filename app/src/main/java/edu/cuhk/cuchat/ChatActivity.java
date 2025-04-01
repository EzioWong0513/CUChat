package edu.cuhk.cuchat;

import android.os.Bundle;
import android.text.TextUtils;
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
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;
import edu.cuhk.cuchat.adapters.MessagesAdapter;
import edu.cuhk.cuchat.models.Chat;
import edu.cuhk.cuchat.models.Message;
import edu.cuhk.cuchat.models.User;

import com.google.firebase.firestore.SetOptions;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";

    private Toolbar toolbar;
    private CircleImageView ivUserProfilePic;
    private TextView tvUsername;
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
                                    // Handle modified messages if needed
                                    break;
                                case REMOVED:
                                    // Handle removed messages if needed
                                    break;
                            }
                        }

                        messagesAdapter.notifyDataSetChanged();
                        rvMessages.scrollToPosition(messageList.size() - 1);

                        // Mark messages as seen if they are from the chat user
                        markMessagesAsSeen();
                    }
                });
    }

    private void markMessagesAsSeen() {
        for (Message message : messageList) {
            if (message.getSenderId().equals(chatUserId) && !message.isSeen()) {
                db.collection("chats").document(chatId).collection("messages")
                        .document(message.getMessageId())
                        .update("seen", true);
            }
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

                    // Update chat summary
                    updateChatSummary(messageContent, timestamp);

                    // Look up the receiver's FCM token to potentially send a notification
                    db.collection("users").document(chatUserId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                String receiverToken = documentSnapshot.getString("fcmToken");
                                boolean isOnline = Boolean.TRUE.equals(documentSnapshot.getBoolean("isOnline"));

                                // For demonstration - mark that we checked for notification
                                // In a real implementation, you would send this from a server
                                documentReference.update("notificationSent", true);

                                Log.d(TAG, "Receiver online status: " + isOnline + ", has token: " + (receiverToken != null));
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

    @Override
    protected void onPause() {
        super.onPause();
        // Update user's online status when leaving chat
        if (currentUser != null) {
            db.collection("users").document(currentUserId)
                    .update("isOnline", false);
        }
        updateUserStatus(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update user's online status when entering chat
        if (currentUser != null) {
            db.collection("users").document(currentUserId)
                    .update("isOnline", true);
        }

        // Mark this chat as seen
        updateChatSeenStatus();

        // Update user status to online
        updateUserStatus(true);
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
}