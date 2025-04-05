package edu.cuhk.cuchat.adapters;

import android.content.Context;
import android.graphics.Color;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import edu.cuhk.cuchat.R;
import edu.cuhk.cuchat.models.Message;

public class GroupMessagesAdapter extends RecyclerView.Adapter {
    private static final int VIEW_TYPE_MESSAGE_SENT = 1;
    private static final int VIEW_TYPE_MESSAGE_RECEIVED = 2;
    private static final int VIEW_TYPE_SYSTEM_MESSAGE = 3;

    private Context context;
    private List<Message> messageList;
    private String currentUserId;

    // Cache usernames to avoid repeated Firestore queries
    private Map<String, String> usernameCache = new HashMap<>();

    public GroupMessagesAdapter(Context context, List<Message> messageList, String currentUserId) {
        this.context = context;
        this.messageList = messageList;
        this.currentUserId = currentUserId;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messageList.get(position);

        // Safely check for system messages
        if (message == null) {
            return VIEW_TYPE_SYSTEM_MESSAGE; // Default to system message if null
        }

        // Check for null senderId
        String senderId = message.getSenderId();
        if (senderId == null) {
            return VIEW_TYPE_SYSTEM_MESSAGE;
        }

        // Check if it's a system message - handle potential NPE
        Boolean isSystemMsg = false;
        try {
            isSystemMsg = message.isSystemMessage();
        } catch (Exception e) {
            Log.e("GroupMessagesAdapter", "Error checking if system message", e);
        }

        if ("system".equals(senderId) || Boolean.TRUE.equals(isSystemMsg)) {
            return VIEW_TYPE_SYSTEM_MESSAGE;
        }

        // Otherwise, check if it's sent by the current user or received
        if (senderId.equals(currentUserId)) {
            return VIEW_TYPE_MESSAGE_SENT;
        } else {
            return VIEW_TYPE_MESSAGE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;

        if (viewType == VIEW_TYPE_MESSAGE_SENT) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_sent, parent, false);
            return new SentMessageHolder(view);
        } else if (viewType == VIEW_TYPE_MESSAGE_RECEIVED) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_group_message_received, parent, false);
            return new ReceivedMessageHolder(view);
        } else { // VIEW_TYPE_SYSTEM_MESSAGE
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_system_message, parent, false);
            return new SystemMessageHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        try {
            // Safety check to prevent index out of bounds
            if (position < 0 || position >= messageList.size()) {
                return;
            }

            Message message = messageList.get(position);
            if (message == null) {
                return;
            }

            switch (holder.getItemViewType()) {
                case VIEW_TYPE_MESSAGE_SENT:
                    ((SentMessageHolder) holder).bind(message);
                    break;
                case VIEW_TYPE_MESSAGE_RECEIVED:
                    ((ReceivedMessageHolder) holder).bind(message);
                    break;
                case VIEW_TYPE_SYSTEM_MESSAGE:
                    ((SystemMessageHolder) holder).bind(message);
                    break;
            }
        } catch (Exception e) {
            Log.e("GroupMessagesAdapter", "Error binding view holder", e);
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    private class SentMessageHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText, statusText;

        SentMessageHolder(View itemView) {
            super(itemView);

            messageText = itemView.findViewById(R.id.tvMessage);
            timeText = itemView.findViewById(R.id.tvTimestamp);
            statusText = itemView.findViewById(R.id.tvMessageStatus); // Add this TextView to your item_message_sent.xml
        }

        void bind(Message message) {
            try {
                messageText.setText(message.getContent());
                timeText.setText(formatTime(message.getTimestamp()));

                // Handle seen/delivered status
                if (statusText != null) {
                    Map<String, Boolean> seenBy = message.getSeenBy();
                    if (seenBy != null && !seenBy.isEmpty()) {
                        // Count how many people have seen the message (excluding sender)
                        int seenCount = 0;
                        for (Map.Entry<String, Boolean> entry : seenBy.entrySet()) {
                            if (!entry.getKey().equals(currentUserId) && Boolean.TRUE.equals(entry.getValue())) {
                                seenCount++;
                            }
                        }

                        if (seenCount > 0) {
                            // At least one person has seen it
                            statusText.setText("Seen");
                            statusText.setTextColor(context.getResources().getColor(R.color.purple_500));
                        } else {
                            // No one has seen it yet
                            statusText.setText("Delivered");
                            statusText.setTextColor(Color.GRAY);
                        }
                        statusText.setVisibility(View.VISIBLE);
                    } else {
                        statusText.setText("Sent");
                        statusText.setTextColor(Color.GRAY);
                        statusText.setVisibility(View.VISIBLE);
                    }
                }
            } catch (Exception e) {
                Log.e("GroupMessagesAdapter", "Error binding sent message", e);
            }
        }
    }

    private class ReceivedMessageHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText, nameText;

        ReceivedMessageHolder(View itemView) {
            super(itemView);

            messageText = itemView.findViewById(R.id.tvMessage);
            timeText = itemView.findViewById(R.id.tvTimestamp);
            nameText = itemView.findViewById(R.id.tvUsername);
        }

        void bind(Message message) {
            try {
                String content = message.getContent();
                if (content != null) {
                    messageText.setText(content);
                } else {
                    messageText.setText("");
                }

                long timestamp = message.getTimestamp();
                timeText.setText(formatTime(timestamp));

                // Get and display the sender's username
                String senderId = message.getSenderId();
                if (senderId != null) {
                    loadUsername(senderId, username -> {
                        if (username != null) {
                            nameText.setText(username);
                        } else {
                            nameText.setText("Unknown User");
                        }
                    });
                } else {
                    nameText.setText("Unknown User");
                }
            } catch (Exception e) {
                Log.e("GroupMessagesAdapter", "Error binding received message", e);
            }
        }

    }

    private class SystemMessageHolder extends RecyclerView.ViewHolder {
        TextView messageText;

        SystemMessageHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.tvSystemMessage);
        }

        void bind(Message message) {
            try {
                String content = message.getContent();
                if (content != null) {
                    messageText.setText(content);
                } else {
                    messageText.setText("System message");
                }
            } catch (Exception e) {
                Log.e("GroupMessagesAdapter", "Error binding system message", e);
            }
        }
    }

    private String formatTime(long timestamp) {
        Calendar cal = Calendar.getInstance(Locale.ENGLISH);
        cal.setTimeInMillis(timestamp);
        return DateFormat.format("hh:mm a", cal).toString();
    }

    private void loadUsername(String userId, final UsernameCallback callback) {
        // First check if the username is already cached
        if (usernameCache.containsKey(userId)) {
            callback.onUsernameLoaded(usernameCache.get(userId));
            return;
        }

        // If not cached, query Firestore
        FirebaseFirestore.getInstance().collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String username = documentSnapshot.getString("username");
                        // Cache the username for future use
                        usernameCache.put(userId, username);
                        callback.onUsernameLoaded(username);
                    } else {
                        callback.onUsernameLoaded(null);
                    }
                })
                .addOnFailureListener(e -> callback.onUsernameLoaded(null));
    }

    // Interface for the username callback
    public interface UsernameCallback {
        void onUsernameLoaded(String username);
    }
}