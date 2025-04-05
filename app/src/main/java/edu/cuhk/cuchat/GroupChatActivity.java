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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.Toast;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;

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

        // Initialize participants list
        participants = new ArrayList<>();

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
        }

        // Initialize with a loading state
        tvParticipantsCount.setText("Loading members...");
        tvParticipantsCount.setVisibility(View.VISIBLE);

        // Initialize RecyclerView
        messageList = new ArrayList<>();
        messagesAdapter = new GroupMessagesAdapter(this, messageList, currentUserId);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messagesAdapter);

        // Set click listener for send button
        btnSendMessage.setOnClickListener(v -> sendMessage());

        // Set click listener for group info button
        ivGroupInfo.setOnClickListener(v -> showGroupInfoDialog());

        // First, load group info (name, participants)
        loadGroupInfo();

        // Then load messages
        loadMessages();

        // Mark messages as seen when entering the chat
        updateGroupSeenStatus();
    }

    private void loadGroupInfo() {
        try {
            // Add direct debug logging
            Log.d(TAG, "loadGroupInfo called for chatId: " + chatId);

            if (chatId == null) {
                Log.e(TAG, "chatId is null in loadGroupInfo");
                return;
            }

            groupInfoListener = db.collection("chats").document(chatId)
                    .addSnapshotListener((snapshot, e) -> {
                        if (e != null) {
                            Log.e(TAG, "Listen failed in loadGroupInfo", e);
                            return;
                        }

                        if (snapshot == null) {
                            Log.e(TAG, "Snapshot is null in loadGroupInfo");
                            return;
                        }

                        if (!snapshot.exists()) {
                            Log.e(TAG, "Document doesn't exist for chatId: " + chatId);
                            return;
                        }

                        // Get group name
                        String groupNameFromDb = snapshot.getString("groupName");
                        if (groupNameFromDb != null) {
                            groupName = groupNameFromDb;
                            tvGroupName.setText(groupName);
                            Log.d(TAG, "Group name loaded: " + groupName);
                        } else {
                            Log.w(TAG, "Group name is null in document");
                        }

                        // Get participants list
                        try {
                            // Explicitly log all fields in the document for debugging
                            Map<String, Object> data = snapshot.getData();
                            if (data != null) {
                                Log.d(TAG, "Document data: " + data.toString());
                            }

                            // Try to get participants in different ways
                            Object participantsObj = snapshot.get("participants");
                            Log.d(TAG, "Participants object type: " +
                                    (participantsObj != null ? participantsObj.getClass().getName() : "null"));

                            if (participantsObj instanceof List) {
                                List<String> participantsList = (List<String>) participantsObj;
                                participants = participantsList;

                                int memberCount = participantsList.size();
                                Log.d(TAG, "Participant count: " + memberCount);

                                // Update the participant count text
                                if (memberCount > 0) {
                                    String memberText = memberCount + " member" + (memberCount > 1 ? "s" : "");
                                    tvParticipantsCount.setText(memberText);
                                    tvParticipantsCount.setVisibility(View.VISIBLE);
                                    Log.d(TAG, "Setting participant count text: " + memberText);
                                } else {
                                    tvParticipantsCount.setText("No members");
                                    tvParticipantsCount.setVisibility(View.VISIBLE);
                                    Log.w(TAG, "No participants found in list");
                                }
                            } else {
                                Log.e(TAG, "Participants field is not a List");
                                tvParticipantsCount.setText("Error loading members");
                                tvParticipantsCount.setVisibility(View.VISIBLE);
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Error processing participants", ex);
                            tvParticipantsCount.setText("Error loading members");
                            tvParticipantsCount.setVisibility(View.VISIBLE);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in loadGroupInfo", e);
        }
    }

    private void loadMessages() {
        try {
            Log.d(TAG, "loadMessages called for chatId: " + chatId);

            if (chatId == null) {
                Log.e(TAG, "chatId is null in loadMessages");
                return;
            }

            // Remove previous listener if exists
            if (messagesListener != null) {
                messagesListener.remove();
            }

            // Clear the current message list
            messageList.clear();
            if (messagesAdapter != null) {
                messagesAdapter.notifyDataSetChanged();
            }

            // Set up real-time listener for messages
            messagesListener = db.collection("chats").document(chatId).collection("messages")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .addSnapshotListener((value, error) -> {
                        if (error != null) {
                            Log.e(TAG, "Listen failed in loadMessages", error);
                            return;
                        }

                        if (value == null) {
                            Log.e(TAG, "QuerySnapshot is null in loadMessages");
                            return;
                        }

                        try {
                            for (DocumentChange dc : value.getDocumentChanges()) {
                                switch (dc.getType()) {
                                    case ADDED:
                                        try {
                                            // Add new message
                                            Message message = dc.getDocument().toObject(Message.class);
                                            message.setMessageId(dc.getDocument().getId());
                                            messageList.add(message);
                                            Log.d(TAG, "Message added: " + message.getContent());
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error adding message", e);
                                        }
                                        break;

                                    case MODIFIED:
                                        try {
                                            // Handle message modification
                                            String modifiedId = dc.getDocument().getId();
                                            Message updatedMessage = dc.getDocument().toObject(Message.class);
                                            updatedMessage.setMessageId(modifiedId);

                                            // Find and replace the message in our list
                                            for (int i = 0; i < messageList.size(); i++) {
                                                if (messageList.get(i).getMessageId().equals(modifiedId)) {
                                                    messageList.set(i, updatedMessage);
                                                    Log.d(TAG, "Message updated: " + modifiedId);
                                                    break;
                                                }
                                            }
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error updating message", e);
                                        }
                                        break;

                                    case REMOVED:
                                        try {
                                            // Handle message removal
                                            String removedId = dc.getDocument().getId();
                                            for (int i = 0; i < messageList.size(); i++) {
                                                if (messageList.get(i).getMessageId().equals(removedId)) {
                                                    messageList.remove(i);
                                                    Log.d(TAG, "Message removed: " + removedId);
                                                    break;
                                                }
                                            }
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error removing message", e);
                                        }
                                        break;
                                }
                            }

                            // Update the adapter
                            messagesAdapter.notifyDataSetChanged();

                            // Scroll to bottom if we have messages
                            if (messageList.size() > 0) {
                                rvMessages.scrollToPosition(messageList.size() - 1);
                            }

                            // Update seen status for all messages
                            updateGroupSeenStatus();

                        } catch (Exception e) {
                            Log.e(TAG, "Error processing message changes", e);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in loadMessages", e);
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

        // Temporarily disable the send button to prevent duplicate messages
        btnSendMessage.setEnabled(false);

        long timestamp = System.currentTimeMillis();

        try {
            // Get current participants list if needed
            if (participants == null || participants.isEmpty()) {
                // Load participants from Firestore if we don't have them
                db.collection("chats").document(chatId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                List<String> loadedParticipants = (List<String>) documentSnapshot.get("participants");
                                if (loadedParticipants != null && !loadedParticipants.isEmpty()) {
                                    participants = loadedParticipants;
                                    Log.d(TAG, "Loaded " + participants.size() + " participants before sending message");
                                    // Now that we have participants, proceed with sending
                                    sendMessageWithParticipants(messageContent, timestamp);
                                } else {
                                    // Fall back to just adding the current user
                                    participants = new ArrayList<>();
                                    participants.add(currentUserId);
                                    Log.w(TAG, "Could not load participants, using only current user");
                                    sendMessageWithParticipants(messageContent, timestamp);
                                }
                            } else {
                                Log.e(TAG, "Chat document not found");
                                btnSendMessage.setEnabled(true);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error loading participants", e);
                            btnSendMessage.setEnabled(true);
                        });
            } else {
                // We already have participants, proceed with sending
                sendMessageWithParticipants(messageContent, timestamp);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in sendMessage", e);
            btnSendMessage.setEnabled(true);
            Toast.makeText(this, "Error sending message", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendMessageWithParticipants(String messageContent, long timestamp) {
        try {
            // Create a new message
            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("senderId", currentUserId);
            messageMap.put("content", messageContent);
            messageMap.put("timestamp", timestamp);
            messageMap.put("seen", false); // Legacy field, keeping for compatibility
            messageMap.put("isSystemMessage", false);

            // Create the seenBy map for participants
            Map<String, Boolean> seenByMap = new HashMap<>();
            for (String participantId : participants) {
                // Only the sender has seen the message initially
                seenByMap.put(participantId, participantId.equals(currentUserId));
            }
            messageMap.put("seenBy", seenByMap);

            Log.d(TAG, "Sending message with " + participants.size() + " participants in seenBy");

            // Add message to the chat
            db.collection("chats").document(chatId).collection("messages")
                    .add(messageMap)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "Message sent with ID: " + documentReference.getId());
                        etMessage.setText("");
                        btnSendMessage.setEnabled(true);

                        // Update chat summary
                        updateGroupChatSummary(messageContent, timestamp);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error sending message", e);
                        btnSendMessage.setEnabled(true);
                        Toast.makeText(GroupChatActivity.this, "Failed to send message", Toast.LENGTH_SHORT).show();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in sendMessageWithParticipants", e);
            btnSendMessage.setEnabled(true);
            Toast.makeText(this, "Error preparing message", Toast.LENGTH_SHORT).show();
        }
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
                Log.e(TAG, "Cannot update seen status: chatId or currentUserId is null");
                return;
            }

            Log.d(TAG, "updateGroupSeenStatus called for user: " + currentUserId);

            // First update the main chat document seenStatus
            DocumentReference chatRef = db.collection("chats").document(chatId);
            chatRef.get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    try {
                        // Update the seenStatus map
                        Map<String, Object> updates = new HashMap<>();

                        // Get or create seenStatus map
                        Map<String, Boolean> seenStatus;
                        Object existingSeenStatus = documentSnapshot.get("seenStatus");

                        if (existingSeenStatus instanceof Map) {
                            seenStatus = (Map<String, Boolean>) existingSeenStatus;
                        } else {
                            seenStatus = new HashMap<>();
                        }

                        // Update current user's status
                        seenStatus.put(currentUserId, true);
                        updates.put("seenStatus", seenStatus);

                        // Update the document
                        chatRef.update(updates)
                                .addOnSuccessListener(aVoid ->
                                        Log.d(TAG, "Chat seenStatus updated for user: " + currentUserId))
                                .addOnFailureListener(e ->
                                        Log.e(TAG, "Error updating chat seenStatus", e));

                    } catch (Exception e) {
                        Log.e(TAG, "Error processing chat seenStatus", e);
                    }
                } else {
                    Log.e(TAG, "Chat document not found");
                }
            }).addOnFailureListener(e -> Log.e(TAG, "Error fetching chat document", e));

            // Now update all message seenBy fields
            // Instead of using a transaction (which was causing the error),
            // first get all messages and then batch update them
            db.collection("chats").document(chatId).collection("messages")
                    .get()
                    .addOnSuccessListener(messagesSnapshot -> {
                        // Create a batch for all updates
                        WriteBatch batch = db.batch();
                        boolean hasPendingWrites = false;

                        for (DocumentSnapshot doc : messagesSnapshot.getDocuments()) {
                            try {
                                Message message = doc.toObject(Message.class);
                                if (message != null) {
                                    // Get or create the seenBy map
                                    Map<String, Boolean> seenBy = message.getSeenBy();
                                    if (seenBy == null) {
                                        seenBy = new HashMap<>();
                                    }

                                    // Only update if not already set to true
                                    if (!Boolean.TRUE.equals(seenBy.get(currentUserId))) {
                                        seenBy.put(currentUserId, true);

                                        // Add to the batch
                                        batch.update(doc.getReference(), "seenBy", seenBy);
                                        hasPendingWrites = true;
                                        Log.d(TAG, "Adding seenBy update for message: " + message.getMessageId());
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing message for seenBy update", e);
                            }
                        }

                        // Only commit if we have changes
                        if (hasPendingWrites) {
                            batch.commit()
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Batch update success - updated message seenBy fields");
                                        // Refresh UI
                                        messagesAdapter.notifyDataSetChanged();
                                    })
                                    .addOnFailureListener(e ->
                                            Log.e(TAG, "Batch update failed - could not update message seenBy fields", e));
                        } else {
                            Log.d(TAG, "No seenBy updates needed");
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error fetching messages for seenBy update", e));
        } catch (Exception e) {
            Log.e(TAG, "Exception in updateGroupSeenStatus", e);
        }
    }

    private void showGroupInfoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(groupName);

        // Create message with participants count
        String message = "";
        if (participants != null) {
            message = participants.size() + " members in this group";
        }
        builder.setMessage(message);

        // Only show delete button if user is the group creator
        db.collection("chats").document(chatId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String createdBy = documentSnapshot.getString("createdBy");

                        // Dialog actions
                        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());

                        // Only show delete option if user is the creator
                        if (currentUserId.equals(createdBy)) {
                            builder.setNegativeButton("Delete Group", (dialog, which) -> {
                                // Confirm deletion
                                showDeleteConfirmationDialog();
                            });
                        }

                        // Show the dialog
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                })
                .addOnFailureListener(e -> {
                    // Show dialog without delete option if there's an error
                    builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
                    AlertDialog dialog = builder.create();
                    dialog.show();
                });
    }

    private void showDeleteConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Group");
        builder.setMessage("Are you sure you want to delete this group? This action cannot be undone.");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            deleteGroup();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void deleteGroup() {
        if (chatId == null) {
            Toast.makeText(this, "Error: Chat ID is missing", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show a progress dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Deleting group...");
        builder.setCancelable(false);
        AlertDialog progressDialog = builder.create();
        progressDialog.show();

        try {
            // 1. Get a reference to the messages collection
            CollectionReference messagesRef = db.collection("chats").document(chatId).collection("messages");

            // 2. Get all messages in the group
            messagesRef.get().addOnSuccessListener(querySnapshot -> {
                // Use a batch to delete all messages
                WriteBatch batch = db.batch();

                // Add each message document to the batch
                querySnapshot.getDocuments().forEach(doc -> {
                    batch.delete(doc.getReference());
                });

                // Execute the batch
                batch.commit().addOnSuccessListener(aVoid -> {
                    // 3. Now that all messages are deleted, delete the group chat document
                    db.collection("chats").document(chatId).delete()
                            .addOnSuccessListener(aVoid2 -> {
                                progressDialog.dismiss();
                                Toast.makeText(GroupChatActivity.this, "Group deleted successfully", Toast.LENGTH_SHORT).show();
                                finish(); // Close the activity
                            })
                            .addOnFailureListener(e -> {
                                progressDialog.dismiss();
                                Log.e(TAG, "Error deleting group document", e);
                                Toast.makeText(GroupChatActivity.this, "Error deleting group", Toast.LENGTH_SHORT).show();
                            });
                }).addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Log.e(TAG, "Error deleting messages", e);
                    Toast.makeText(GroupChatActivity.this, "Error deleting group messages", Toast.LENGTH_SHORT).show();
                });
            }).addOnFailureListener(e -> {
                progressDialog.dismiss();
                Log.e(TAG, "Error getting messages", e);
                Toast.makeText(GroupChatActivity.this, "Error accessing group messages", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            progressDialog.dismiss();
            Log.e(TAG, "Error in deleteGroup", e);
            Toast.makeText(this, "Error deleting group", Toast.LENGTH_SHORT).show();
        }
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