package edu.cuhk.cuchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import edu.cuhk.cuchat.adapters.UserSelectionAdapter;
import edu.cuhk.cuchat.models.User;

public class GroupCreationActivity extends AppCompatActivity {

    private static final String TAG = "GroupCreationActivity";

    private Toolbar toolbar;
    private EditText etGroupName;
    private RecyclerView rvUsers;
    private Button btnCreateGroup;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String currentUserId;

    private List<User> userList;
    private List<String> selectedUserIds;
    private UserSelectionAdapter userSelectionAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_creation);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();
        currentUserId = currentUser.getUid();

        // Initialize UI components
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Create Group Chat");

        etGroupName = findViewById(R.id.etGroupName);
        rvUsers = findViewById(R.id.rvSelectUsers);
        btnCreateGroup = findViewById(R.id.btnCreateGroup);

        // Initialize lists
        userList = new ArrayList<>();
        selectedUserIds = new ArrayList<>();

        // Add current user to selected users list by default
        selectedUserIds.add(currentUserId);

        // Initialize the adapter
        userSelectionAdapter = new UserSelectionAdapter(this, userList, new UserSelectionAdapter.OnUserSelectedListener() {
            @Override
            public void onUserSelected(User user, boolean isSelected) {
                if (isSelected) {
                    if (!selectedUserIds.contains(user.getUserId())) {
                        selectedUserIds.add(user.getUserId());
                    }
                } else {
                    selectedUserIds.remove(user.getUserId());
                }

                // Update button state based on selection
                updateCreateButtonState();
            }
        });

        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(userSelectionAdapter);

        // Set click listener for create button
        btnCreateGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createGroupChat();
            }
        });

        // Load users from Firestore
        loadUsers();

        // Initially disable the create button until we have selections
        updateCreateButtonState();
    }

    private void updateCreateButtonState() {
        // Enable button only if we have a group name and at least one other member besides current user
        boolean hasGroupName = !TextUtils.isEmpty(etGroupName.getText().toString().trim());
        boolean hasMembers = selectedUserIds.size() > 1; // Current user + at least one more

        btnCreateGroup.setEnabled(hasGroupName && hasMembers);
    }

    private void loadUsers() {
        db.collection("users")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            userList.clear();
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                // Skip current user
                                if (document.getId().equals(currentUserId)) {
                                    continue;
                                }

                                User user = document.toObject(User.class);
                                userList.add(user);
                            }

                            userSelectionAdapter.notifyDataSetChanged();
                        } else {
                            Log.d(TAG, "Error getting users: ", task.getException());
                            Toast.makeText(GroupCreationActivity.this, "Error loading users", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void createGroupChat() {
        final String groupName = etGroupName.getText().toString().trim();

        if (TextUtils.isEmpty(groupName)) {
            etGroupName.setError("Group name is required");
            return;
        }

        if (selectedUserIds.size() < 2) {
            Toast.makeText(this, "Please select at least one user", Toast.LENGTH_SHORT).show();
            return;
        }

        // Generate a unique ID for the group chat
        final String groupId = "group_" + UUID.randomUUID().toString();

        // Create a group chat document in Firestore
        Map<String, Object> groupChatData = new HashMap<>();
        groupChatData.put("chatId", groupId);
        groupChatData.put("isGroupChat", true);
        groupChatData.put("groupName", groupName);
        groupChatData.put("createdBy", currentUserId);
        groupChatData.put("createdAt", System.currentTimeMillis());
        groupChatData.put("lastMessageContent", "Group created");
        groupChatData.put("lastMessageTimestamp", System.currentTimeMillis());
        groupChatData.put("lastMessageSenderId", currentUserId);
        groupChatData.put("participants", selectedUserIds);

        // Create a map to track who has seen the last message
        Map<String, Boolean> seenStatus = new HashMap<>();
        for (String userId : selectedUserIds) {
            // Current user has seen it, others haven't
            seenStatus.put(userId, userId.equals(currentUserId));
        }
        groupChatData.put("seenStatus", seenStatus);

        // Save the group chat to Firestore
        db.collection("chats").document(groupId)
                .set(groupChatData)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Group chat created with ID: " + groupId);

                            // Get group members' information
                            db.collection("users").document(currentUserId)
                                    .get()
                                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                            if (task.isSuccessful() && task.getResult() != null) {
                                                String creatorName = task.getResult().getString("username");

                                                // Create a system message for the group creation
                                                Map<String, Object> systemMessage = new HashMap<>();
                                                systemMessage.put("senderId", "system");
                                                systemMessage.put("content", creatorName + " created the group");
                                                systemMessage.put("timestamp", System.currentTimeMillis());
                                                systemMessage.put("seen", false);
                                                systemMessage.put("isSystemMessage", true);

                                                db.collection("chats").document(groupId)
                                                        .collection("messages")
                                                        .add(systemMessage);

                                                // Open the chat activity for this group
                                                Intent intent = new Intent(GroupCreationActivity.this, GroupChatActivity.class);
                                                intent.putExtra("chatId", groupId);
                                                intent.putExtra("groupName", groupName);
                                                startActivity(intent);
                                                finish();
                                            }
                                        }
                                    });
                        } else {
                            Log.w(TAG, "Error creating group chat", task.getException());
                            Toast.makeText(GroupCreationActivity.this, "Error creating group chat", Toast.LENGTH_SHORT).show();
                        }
                    }
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
}