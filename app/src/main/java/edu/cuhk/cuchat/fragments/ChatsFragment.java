package edu.cuhk.cuchat.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cuhk.cuchat.GroupCreationActivity;
import edu.cuhk.cuchat.R;
import edu.cuhk.cuchat.adapters.ChatListAdapter;
import edu.cuhk.cuchat.models.ChatListItem;

public class ChatsFragment extends Fragment {

    private static final String TAG = "ChatsFragment";

    private RecyclerView rvChats;
    private TextView tvNoChats;
    private ChatListAdapter chatListAdapter;
    private List<ChatListItem> chatList;
    private Map<String, ChatListItem> chatMap;
    private TabLayout tabLayoutChatFilter;
    private FloatingActionButton fabCreateGroupChat;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String currentUserId;
    private ListenerRegistration chatListener1;
    private ListenerRegistration chatListener2;
    private ListenerRegistration groupChatsListener;
    private Map<String, ListenerRegistration> userStatusListeners = new HashMap<>();

    private int currentTabPosition = 0;

    public ChatsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            // Inflate the activity_chat_list layout
            return inflater.inflate(R.layout.activity_chat_list, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout", e);
            Toast.makeText(getContext(), "Error loading chats view", Toast.LENGTH_SHORT).show();
            // Fallback to the original fragment layout if there's an issue
            return inflater.inflate(R.layout.fragment_chats, container, false);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize Firebase
            mAuth = FirebaseAuth.getInstance();
            db = FirebaseFirestore.getInstance();
            currentUser = mAuth.getCurrentUser();

            if (currentUser == null) {
                Log.e(TAG, "No current user found");
                Toast.makeText(getContext(), "Please log in to view chats", Toast.LENGTH_SHORT).show();
                return;
            }

            currentUserId = currentUser.getUid();

            // Initialize views
            initializeViews(view);

            // Setup tab layout for filtering
            setupTabLayout();

            // Initialize chat list
            initializeAdapter();

            // Set click listener for create group chat button
            if (fabCreateGroupChat != null) {
                fabCreateGroupChat.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(getActivity(), GroupCreationActivity.class);
                        startActivity(intent);
                    }
                });
            }

            // Load chats
            loadChats();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated", e);
            Toast.makeText(getContext(), "Error initializing chats", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeViews(View view) {
        try {
            rvChats = view.findViewById(R.id.rvChats);

            // Try to find the no chats text view - it might be in the fragment_chats layout
            // but not in activity_chat_list, so we need to handle its potential absence
            tvNoChats = view.findViewById(R.id.tvNoChats);

            // Find tab layout for filtering
            tabLayoutChatFilter = view.findViewById(R.id.tabLayoutChatFilter);

            // Find the create group FAB
            fabCreateGroupChat = view.findViewById(R.id.fabCreateGroupChat);

            if (rvChats == null) {
                Log.e(TAG, "rvChats view not found");
                Toast.makeText(getContext(), "Error: Chat list view not found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
        }
    }

    private void setupTabLayout() {
        try {
            if (tabLayoutChatFilter != null) {
                // Make sure tab layout has the correct number of tabs
                if (tabLayoutChatFilter.getTabCount() != 3) {
                    Log.w(TAG, "TabLayout doesn't have expected number of tabs");
                }

                tabLayoutChatFilter.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                    @Override
                    public void onTabSelected(TabLayout.Tab tab) {
                        currentTabPosition = tab.getPosition();
                        filterChats();
                    }

                    @Override
                    public void onTabUnselected(TabLayout.Tab tab) {
                        // Not needed
                    }

                    @Override
                    public void onTabReselected(TabLayout.Tab tab) {
                        // Refresh the same filter
                        filterChats();
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up tab layout", e);
        }
    }

    private void initializeAdapter() {
        try {
            chatList = new ArrayList<>();
            chatMap = new HashMap<>();
            chatListAdapter = new ChatListAdapter(requireContext(), chatList);

            if (rvChats != null) {
                rvChats.setLayoutManager(new LinearLayoutManager(getContext()));
                rvChats.setAdapter(chatListAdapter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing adapter", e);
        }
    }

    private void loadChats() {
        if (currentUserId == null || db == null) {
            Log.e(TAG, "Cannot load chats: user ID or database is null");
            updateEmptyView();
            return;
        }

        try {
            // Remove any existing listeners
            if (chatListener1 != null) {
                chatListener1.remove();
            }
            if (chatListener2 != null) {
                chatListener2.remove();
            }
            if (groupChatsListener != null) {
                groupChatsListener.remove();
            }

            // Clear existing data
            chatList.clear();
            chatMap.clear();
            if (chatListAdapter != null) {
                chatListAdapter.notifyDataSetChanged();
            }

            // Set up the first listener - for chats where current user is user1Id
            chatListener1 = db.collection("chats")
                    .whereEqualTo("user1Id", currentUserId)
                    .addSnapshotListener((snapshots, error) -> {
                        handleChatSnapshot(snapshots, error);
                    });

            // Set up the second listener - for chats where current user is user2Id
            chatListener2 = db.collection("chats")
                    .whereEqualTo("user2Id", currentUserId)
                    .addSnapshotListener((snapshots, error) -> {
                        handleChatSnapshot(snapshots, error);
                    });

            // Set up the third listener - for group chats where current user is a participant
            groupChatsListener = db.collection("chats")
                    .whereEqualTo("isGroupChat", true)
                    .whereArrayContains("participants", currentUserId)
                    .addSnapshotListener((snapshots, error) -> {
                        handleChatSnapshot(snapshots, error);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up chat listeners", e);
            updateEmptyView();
        }
    }

    private void handleChatSnapshot(QuerySnapshot snapshots, FirebaseFirestoreException error) {
        if (error != null) {
            Log.e(TAG, "Listen failed", error);
            return;
        }

        if (snapshots == null) {
            Log.w(TAG, "No snapshot data");
            updateEmptyView();
            return;
        }

        Log.d(TAG, "Got " + snapshots.size() + " chat documents");

        for (DocumentChange dc : snapshots.getDocumentChanges()) {
            try {
                QueryDocumentSnapshot document = dc.getDocument();

                // Check if it's a group chat
                Boolean isGroupChat = document.getBoolean("isGroupChat");
                if (Boolean.TRUE.equals(isGroupChat)) {
                    processGroupChat(document);
                } else {
                    processOneToOneChat(document);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing chat document change", e);
            }
        }

        // Apply current filter after processing changes
        filterChats();
    }

    private void processGroupChat(QueryDocumentSnapshot document) {
        try {
            String chatId = document.getId();

            // Check if current user is a participant
            List<String> participants = (List<String>) document.get("participants");
            if (participants == null || !participants.contains(currentUserId)) {
                return;
            }

            String groupName = document.getString("groupName");
            String lastMessage = document.getString("lastMessageContent");
            Long timestamp = document.getLong("lastMessageTimestamp");
            if (timestamp == null) timestamp = 0L;

            // Determine if there are unread messages for current user
            boolean isUnread = false;
            Map<String, Boolean> seenStatus = (Map<String, Boolean>) document.get("seenStatus");
            if (seenStatus != null && seenStatus.containsKey(currentUserId)) {
                isUnread = !Boolean.TRUE.equals(seenStatus.get(currentUserId));
                // Log the seen status for debugging
                Log.d(TAG, "Group chat: " + groupName + " seen status for current user: " + !isUnread);
            } else {
                Log.d(TAG, "Group chat: " + groupName + " has no seen status for current user");
                isUnread = true; // Default to unread if no status exists
            }

            // Create a ChatListItem for the group chat
            final String groupChatId = chatId;
            final String finalGroupName = groupName != null ? groupName : "Group Chat";
            final String finalLastMessage = lastMessage != null ? lastMessage : "";
            final Long finalTimestamp = timestamp;
            final boolean finalIsUnread = isUnread;
            final int participantCount = participants != null ? participants.size() : 0;

            // Create the chat list item for group chat
            ChatListItem groupChatItem = new ChatListItem(
                    groupChatId,
                    "group_" + groupChatId, // Use a prefix to indicate it's a group
                    finalGroupName,
                    "", // No profile image for groups yet
                    finalLastMessage,
                    finalTimestamp,
                    finalIsUnread,
                    false // Default to not online for groups
            );

            // Set special flag for group chats and participant count
            groupChatItem.setGroupChat(true);
            groupChatItem.setGroupParticipants(participantCount);
            Log.d(TAG, "Group chat item created: " + finalGroupName + " with " + participantCount + " members");

            // Update UI with this chat item
            addOrUpdateChat(groupChatItem);
        } catch (Exception e) {
            Log.e(TAG, "Error processing group chat", e);
        }
    }

    private void processOneToOneChat(QueryDocumentSnapshot document) {
        try {
            String chatId = document.getId();

            // Check both user IDs
            String user1Id = document.getString("user1Id");
            String user2Id = document.getString("user2Id");

            if (user1Id == null || user2Id == null) {
                Log.w(TAG, "Chat document missing user IDs: " + chatId);
                return;
            }

            String lastMessage = document.getString("lastMessageContent");
            Long timestamp = document.getLong("lastMessageTimestamp");

            if (timestamp == null) timestamp = 0L;

            // Determine which user is the other participant
            String otherUserId;
            if (currentUserId.equals(user1Id)) {
                otherUserId = user2Id;
            } else if (currentUserId.equals(user2Id)) {
                otherUserId = user1Id;
            } else {
                Log.w(TAG, "Current user not found in chat participants");
                return;
            }

            // Determine if there are unread messages
            boolean isUnread = false;
            if (currentUserId.equals(user1Id) && document.contains("user1Seen")) {
                isUnread = !document.getBoolean("user1Seen");
            } else if (currentUserId.equals(user2Id) && document.contains("user2Seen")) {
                isUnread = !document.getBoolean("user2Seen");
            }

            final String finalChatId = chatId;
            final String finalOtherUserId = otherUserId;
            final String finalLastMessage = lastMessage;
            final Long finalTimestamp = timestamp;
            final boolean finalIsUnread = isUnread;

            // Get the other user's data
            db.collection("users").document(otherUserId)
                    .get()
                    .addOnSuccessListener(userDocument -> {
                        if (userDocument.exists()) {
                            try {
                                String username = userDocument.getString("username");
                                String profileImageUrl = userDocument.getString("profileImageUrl");

                                // Check if user is online
                                Boolean isOnline = userDocument.getBoolean("isOnline");
                                boolean userOnlineStatus = isOnline != null && isOnline;

                                if (username == null) username = "Unknown User";

                                ChatListItem chatItem = new ChatListItem(
                                        finalChatId,
                                        finalOtherUserId,
                                        username,
                                        profileImageUrl,
                                        finalLastMessage,
                                        finalTimestamp,
                                        finalIsUnread,
                                        userOnlineStatus
                                );

                                // Debug log for online status
                                Log.d(TAG, "User " + username + " online status: " + userOnlineStatus);

                                addOrUpdateChat(chatItem);
                            } catch (Exception e) {
                                Log.e(TAG, "Error creating chat item", e);
                            }
                        } else {
                            Log.w(TAG, "User document does not exist: " + finalOtherUserId);
                            // Create a fallback chat item
                            ChatListItem fallbackItem = new ChatListItem(
                                    finalChatId,
                                    finalOtherUserId,
                                    "Unknown User",
                                    null,
                                    finalLastMessage,
                                    finalTimestamp,
                                    finalIsUnread,
                                    false
                            );
                            addOrUpdateChat(fallbackItem);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching user document: " + finalOtherUserId, e);
                        // Create a fallback chat item
                        ChatListItem fallbackItem = new ChatListItem(
                                finalChatId,
                                finalOtherUserId,
                                "Unknown User",
                                null,
                                finalLastMessage,
                                finalTimestamp,
                                finalIsUnread,
                                false
                        );
                        addOrUpdateChat(fallbackItem);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error processing chat document", e);
        }
    }

    private void addOrUpdateChat(ChatListItem chatItem) {
        if (chatItem == null || chatItem.getChatId() == null) {
            Log.w(TAG, "Tried to add null chat item");
            return;
        }

        try {
            // Use the map to avoid duplicates
            chatMap.put(chatItem.getChatId(), chatItem);

            // Apply filter based on current tab
            filterChats();
        } catch (Exception e) {
            Log.e(TAG, "Error adding/updating chat", e);
        }
    }

    private void filterChats() {
        try {
            if (tabLayoutChatFilter == null) {
                // If there's no tab layout, just show all chats
                updateChatListFromMap(new ArrayList<>(chatMap.values()));
                return;
            }

            // Apply filter based on selected tab
            switch (currentTabPosition) {
                case 0: // All chats
                    // Show all chats
                    updateChatListFromMap(new ArrayList<>(chatMap.values()));
                    break;
                case 1: // Unread chats
                    // Show only unread chats
                    filterUnreadChats();
                    break;
                case 2: // Read chats
                    // Show only read chats
                    filterReadChats();
                    break;
                default:
                    // Default to all chats
                    updateChatListFromMap(new ArrayList<>(chatMap.values()));
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error filtering chats", e);
        }
    }

    private void filterUnreadChats() {
        List<ChatListItem> unreadChats = new ArrayList<>();
        for (ChatListItem chat : chatMap.values()) {
            // Debug log to help diagnose unread status
            Log.d(TAG, "Filtering - Chat with " + chat.getUsername() + " isUnread: " + chat.isUnread());

            if (chat.isUnread()) {
                unreadChats.add(chat);
            }
        }

        Log.d(TAG, "Found " + unreadChats.size() + " unread chats");
        updateChatListFromMap(unreadChats);
    }

    private void filterReadChats() {
        List<ChatListItem> readChats = new ArrayList<>();
        for (ChatListItem chat : chatMap.values()) {
            // Only add chats that are marked as read (NOT unread)
            if (!chat.isUnread()) {
                readChats.add(chat);
            }
        }
        Log.d(TAG, "Found " + readChats.size() + " read chats");
        updateChatListFromMap(readChats);
    }

    private void updateChatListFromMap(List<ChatListItem> filteredList) {
        try {
            if (chatList == null || filteredList == null) {
                Log.e(TAG, "Chat list or filtered list is null");
                return;
            }

            // Sort the filtered list by timestamp (newest first)
            Collections.sort(filteredList, (item1, item2) ->
                    Long.compare(item2.getLastMessageTime(), item1.getLastMessageTime()));

            // Update the adapter's data
            chatList.clear();
            chatList.addAll(filteredList);

            // Log the filtered results
            Log.d(TAG, "Displaying " + chatList.size() + " chats after filtering");

            // Update the adapter
            if (chatListAdapter != null) {
                chatListAdapter.notifyDataSetChanged();
            }

            // Update empty view visibility
            updateEmptyView();
        } catch (Exception e) {
            Log.e(TAG, "Error updating chat list from map", e);
        }
    }

    private void updateEmptyView() {
        try {
            if (tvNoChats == null || rvChats == null) {
                return;
            }

            if (chatList == null || chatList.isEmpty()) {
                tvNoChats.setVisibility(View.VISIBLE);
                rvChats.setVisibility(View.GONE);
            } else {
                tvNoChats.setVisibility(View.GONE);
                rvChats.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating empty view", e);
        }
    }

    private void setupUserStatusListeners() {
        // Clear any existing listeners
        for (ListenerRegistration listener : userStatusListeners.values()) {
            listener.remove();
        }
        userStatusListeners.clear();

        // Add listeners for each user in the chat list
        for (ChatListItem chat : chatList) {
            // Skip group chats
            if (chat.isGroupChat()) {
                continue;
            }

            String userId = chat.getUserId();
            if (userId != null && !userStatusListeners.containsKey(userId)) {
                ListenerRegistration listener = db.collection("users").document(userId)
                        .addSnapshotListener((snapshot, e) -> {
                            if (e != null || snapshot == null || !snapshot.exists()) {
                                return;
                            }

                            Boolean isOnline = snapshot.getBoolean("isOnline");
                            boolean userOnlineStatus = isOnline != null && isOnline;

                            // Update the chat item with the new online status
                            for (ChatListItem item : chatList) {
                                if (item.getUserId().equals(userId)) {
                                    item.setUserOnline(userOnlineStatus);
                                    break;
                                }
                            }

                            // Notify the adapter to refresh the view
                            if (chatListAdapter != null) {
                                chatListAdapter.notifyDataSetChanged();
                            }
                        });

                userStatusListeners.put(userId, listener);
            }
        }
    }

    // Call this in onPause or onDestroy
    private void removeUserStatusListeners() {
        for (ListenerRegistration listener : userStatusListeners.values()) {
            listener.remove();
        }
        userStatusListeners.clear();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Only reload if we have a valid user
        if (currentUser != null) {
            loadChats();
            setupUserStatusListeners(); // Add this
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Clean up listeners when fragment is paused
        if (chatListener1 != null) {
            chatListener1.remove();
            chatListener1 = null;
        }
        if (chatListener2 != null) {
            chatListener2.remove();
            chatListener2 = null;
        }
        if (groupChatsListener != null) {
            groupChatsListener.remove();
            groupChatsListener = null;
        }
        removeUserStatusListeners(); // Add this
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            // Make sure listeners are removed
            if (chatListener1 != null) {
                chatListener1.remove();
                chatListener1 = null;
            }
            if (chatListener2 != null) {
                chatListener2.remove();
                chatListener2 = null;
            }
            if (groupChatsListener != null) {
                groupChatsListener.remove();
                groupChatsListener = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroyView", e);
        }
    }
}