package edu.cuhk.cuchat;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cuhk.cuchat.adapters.GroupMessagesAdapter;
import edu.cuhk.cuchat.models.Message;

public class GroupChatActivity extends AppCompatActivity {

    private static final String TAG = "GroupChatActivity";

    private Toolbar toolbar;
    private TextView tvGroupName;
    private TextView tvParticipantsCount;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageButton btnSendMessage;
    private ImageView ivGroupInfo;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String currentUserId;
    private String chatId;
    private String groupName;
    private List<String> participants;

    private GroupMessagesAdapter messagesAdapter;
    private List<Message> messageList;

    private ListenerRegistration messagesListener;
    private ListenerRegistration groupInfoListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        // Get chat ID from intent
        chatId = getIntent().getStringExtra("chatId");
        groupName = getIntent().getStringExtra("groupName");

        if (TextUtils.isEmpty(chatId)) {
            Toast.makeText(this, "Chat ID is missing", Toast.LENGTH_SHORT).show();
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

        tvGroupName = findViewById(R.id.tvGroupName);
        tvParticipantsCount = findViewById(R.id.tvParticipantsCount);
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSendMessage = findViewById(R.id.btnSendMessage);
        ivGroupInfo = findViewById(R.id.ivGroupInfo);

        // Set group name in toolbar
        if (groupName != null) {
            tvGroupName.setText(groupName);
        } else {
            // If group name is not provided, fetch it from Firestore
            loadGroupInfo();
        }

        // Initialize RecyclerView
        messageList = new ArrayList<>();
        messagesAdapter = new GroupMessagesAdapter(this, messageList, currentUserId);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(messagesAdapter);

        // Set click listener for send button
        btnSendMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        // Set click listener for group info button
        ivGroupInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showGroupInfoDialog();
            }
        });

        // Load messages
        loadMessages();

        // Update the group's seen status for the current user
        updateGroupSeenStatus();
    }

    private void loadGroupInfo() {
        groupInfoListener = db.collection("chats").document(chatId)
                .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.w(TAG, "Listen failed.", e);
                            return;
                        }

                        if (snapshot != null && snapshot.exists()) {
                            // Get group name
                            groupName = snapshot.getString("groupName");
                            if (groupName != null) {
                                tvGroupName.setText(groupName);
                            }

                            // Get participants list - fixed to correctly retrieve participants array
                            try {
                                participants = (List<String>) snapshot.get("participants");
                                if (participants != null) {
                                    // Update the participant count text
                                    int memberCount = participants.size();
                                    tvParticipantsCount.setText(memberCount + " members");

                                    // Make sure the UI is updated on the main thread
                                    runOnUiThread(() -> {
                                        tvParticipantsCount.setVisibility(View.VISIBLE);
                                    });

                                    Log.d(TAG, "Loaded " + memberCount + " group members");
                                } else {
                                    Log.w(TAG, "No participants found in group chat");
                                    tvParticipantsCount.setText("0 members");
                                }
                            } catch (Exception ex) {
                                Log.e(TAG, "Error loading participants", ex);
                                tvParticipantsCount.setText("? members");
                            }
                        }
                    }
                });
    }

    private void loadMessages() {
        try {
            messagesListener = db.collection("chats").document(chatId).collection("messages")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .addSnapshotListener(new EventListener<QuerySnapshot>() {
                        @Override
                        public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                            if (error != null) {
                                Log.w(TAG, "Listen failed.", error);
                                return;
                            }

                            if (value == null) {
                                return;
                            }

                            try {
                                for (DocumentChange dc : value.getDocumentChanges()) {
                                    switch (dc.getType()) {
                                        case ADDED:
                                            try {
                                                Message message = dc.getDocument().toObject(Message.class);

                                                // Make sure all critical fields are set
                                                ensureMessageFields(message, dc.getDocument());

                                                messageList.add(message);
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error processing message", e);
                                            }
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
                                if (messageList.size() > 0) {
                                    rvMessages.scrollToPosition(messageList.size() - 1);
                                }

                                // Mark messages as seen
                                updateGroupSeenStatus();

                            } catch (Exception e) {
                                Log.e(TAG, "Error processing message changes", e);
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up messages listener", e);
        }
    }

    private void ensureMessageFields(Message message, DocumentSnapshot doc) {
        try {
            // Set message ID if not already set
            if (message.getMessageId() == null) {
                message.setMessageId(doc.getId());
            }

            // Make sure senderId is set
            if (message.getSenderId() == null) {
                String senderId = doc.getString("senderId");
                if (senderId != null) {
                    message.setSenderId(senderId);
                } else {
                    message.setSenderId("unknown");
                }
            }

            // Check for null content
            if (message.getContent() == null) {
                message.setContent("");
            }

            // Set isSystemMessage if missing
            try {
                message.isSystemMessage(); // Just checking if this works
            } catch (Exception e) {
                // Field doesn't exist or error occurred
                Boolean isSystemMsg = doc.getBoolean("isSystemMessage");
                message.setSystemMessage(isSystemMsg != null && isSystemMsg);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring message fields", e);
        }
    }


    private void sendMessage() {
        String messageContent = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(messageContent)) {
            return;
        }

        long timestamp = System.currentTimeMillis();

        // Create a new message with all required fields properly initialized
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("senderId", currentUserId);
        messageMap.put("receiverId", ""); // For group chats, we don't have a specific receiver
        messageMap.put("content", messageContent);
        messageMap.put("timestamp", timestamp);
        messageMap.put("seen", false);
        messageMap.put("isSystemMessage", false); // Make sure this field is always set

        // Show progress indicator or disable send button temporarily
        btnSendMessage.setEnabled(false);

        // Add message to the chat
        db.collection("chats").document(chatId).collection("messages")
                .add(messageMap)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Message sent with ID: " + documentReference.getId());
                    etMessage.setText("");
                    btnSendMessage.setEnabled(true);

                    // Update chat summary with proper error handling
                    try {
                        updateGroupChatSummary(messageContent, timestamp);
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating group chat summary", e);
                        // Don't let this crash the app
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error sending message", e);
                    Toast.makeText(GroupChatActivity.this, "Failed to send message", Toast.LENGTH_SHORT).show();
                    btnSendMessage.setEnabled(true);
                });
    }

    private void updateGroupChatSummary(String lastMessage, long timestamp) {
        try {
            // Fetch the current user's username
            db.collection("users").document(currentUserId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        try {
                            String username = "User";
                            if (documentSnapshot != null && documentSnapshot.exists()) {
                                String fetchedUsername = documentSnapshot.getString("username");
                                if (fetchedUsername != null) {
                                    username = fetchedUsername;
                                }
                            }

                            final String finalUsername = username;

                            // Create chat summary updates
                            Map<String, Object> chatUpdates = new HashMap<>();
                            chatUpdates.put("lastMessageContent", finalUsername + ": " + lastMessage);
                            chatUpdates.put("lastMessageTimestamp", timestamp);
                            chatUpdates.put("lastMessageSenderId", currentUserId);

                            // Safely handle participants and seen status
                            if (participants == null) {
                                // If participants list is null, fetch it first
                                db.collection("chats").document(chatId)
                                        .get()
                                        .addOnSuccessListener(chatDoc -> {
                                            if (chatDoc != null && chatDoc.exists()) {
                                                List<String> fetchedParticipants = (List<String>) chatDoc.get("participants");
                                                if (fetchedParticipants != null && !fetchedParticipants.isEmpty()) {
                                                    updateSeenStatus(chatUpdates, fetchedParticipants);
                                                }
                                            }
                                        });
                            } else {
                                // Use the existing participants list
                                updateSeenStatus(chatUpdates, participants);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing username for chat summary", e);
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error fetching username", e));
        } catch (Exception e) {
            Log.e(TAG, "Error in updateGroupChatSummary", e);
        }
    }

    // Helper method to update seen status
    private void updateSeenStatus(Map<String, Object> chatUpdates, List<String> participantsList) {
        try {
            // Reset seen status for all participants except the sender
            Map<String, Boolean> seenStatus = new HashMap<>();
            for (String participantId : participantsList) {
                seenStatus.put(participantId, participantId.equals(currentUserId));
            }
            chatUpdates.put("seenStatus", seenStatus);

            // Update the chat document
            db.collection("chats").document(chatId)
                    .update(chatUpdates)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Group chat summary updated successfully"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error updating group chat summary", e));
        } catch (Exception e) {
            Log.e(TAG, "Error updating seen status", e);
        }
    }

    private void updateGroupSeenStatus() {
        try {
            if (chatId == null || currentUserId == null) {
                Log.w(TAG, "Cannot update seen status: chatId or currentUserId is null");
                return;
            }

            // Reference to the chat document
            DocumentReference chatRef = db.collection("chats").document(chatId);

            // First fetch the current seenStatus map
            chatRef.get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    try {
                        // Get the current seenStatus map or create a new one if it doesn't exist
                        Map<String, Boolean> seenStatus = (Map<String, Boolean>) documentSnapshot.get("seenStatus");
                        if (seenStatus == null) {
                            // If no seenStatus exists, create a new one
                            seenStatus = new HashMap<>();

                            // Get participants to initialize seenStatus for every member
                            List<String> members = (List<String>) documentSnapshot.get("participants");
                            if (members != null) {
                                for (String member : members) {
                                    // Only current user has seen the messages
                                    seenStatus.put(member, member.equals(currentUserId));
                                }
                            } else {
                                // If no participants, at least mark current user
                                seenStatus.put(currentUserId, true);
                            }
                        } else {
                            // Update just the current user's status
                            seenStatus.put(currentUserId, true);
                        }

                        // Create a field update to set the current user's seen status to true
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("seenStatus", seenStatus);

                        // Update the document
                        chatRef.update(updates)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Group seen status updated for user " + currentUserId))
                                .addOnFailureListener(e -> Log.w(TAG, "Error updating group seen status", e));

                    } catch (Exception e) {
                        Log.e(TAG, "Error processing seenStatus", e);
                    }
                }
            }).addOnFailureListener(e -> Log.e(TAG, "Error fetching chat document", e));

            // Additionally, mark all messages as seen
            db.collection("chats").document(chatId)
                    .collection("messages")
                    .whereEqualTo("seen", false)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        WriteBatch batch = db.batch();
                        boolean hasPendingWrites = false;

                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            // Update each message
                            batch.update(doc.getReference(), "seen", true);
                            hasPendingWrites = true;
                        }

                        if (hasPendingWrites) {
                            batch.commit()
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Marked messages as seen"))
                                    .addOnFailureListener(e -> Log.e(TAG, "Error marking messages as seen", e));
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in updateGroupSeenStatus", e);
        }
    }

    private void showGroupInfoDialog() {
        // Here you would implement the group info dialog
        // This could show all members, allow adding new members, etc.
        Toast.makeText(this, "Group info not implemented yet", Toast.LENGTH_SHORT).show();
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
        // Update seen status when leaving chat
        updateGroupSeenStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove listeners to prevent memory leaks
        if (messagesListener != null) {
            messagesListener.remove();
        }
        if (groupInfoListener != null) {
            groupInfoListener.remove();
        }
    }
}