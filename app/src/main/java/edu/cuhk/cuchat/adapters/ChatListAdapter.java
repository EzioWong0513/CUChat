package edu.cuhk.cuchat.adapters;

import android.content.Context;
import android.content.Intent;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;
import edu.cuhk.cuchat.ChatActivity;
import edu.cuhk.cuchat.R;
import edu.cuhk.cuchat.models.ChatListItem;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatViewHolder> {

    private static final String TAG = "ChatListAdapter";
    private Context context;
    private List<ChatListItem> chatList;

    public ChatListAdapter(Context context, List<ChatListItem> chatList) {
        this.context = context;
        this.chatList = chatList;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        try {
            View view = LayoutInflater.from(context).inflate(R.layout.item_chat, parent, false);
            return new ChatViewHolder(view);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating view", e);
            // Fallback to a simple layout if there's an issue
            View fallbackView = new View(context);
            fallbackView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ChatViewHolder(fallbackView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        try {
            if (position < 0 || position >= chatList.size()) {
                Log.e(TAG, "Position out of bounds: " + position + ", list size: " + chatList.size());
                return;
            }

            ChatListItem chat = chatList.get(position);
            if (chat == null) {
                Log.e(TAG, "Null chat item at position " + position);
                return;
            }

            // Handle tvUsername - might be tvChatTitle in the layout
            if (holder.tvUsername != null) {
                holder.tvUsername.setText(chat.getUsername() != null ? chat.getUsername() : "Unknown User");
            }

            // Handle tvLastMessage
            if (holder.tvLastMessage != null) {
                if (chat.getLastMessage() != null && !chat.getLastMessage().isEmpty()) {
                    holder.tvLastMessage.setText(chat.getLastMessage());
                } else {
                    holder.tvLastMessage.setText("No messages yet");
                }
            }

            // Handle tvTimestamp
            if (holder.tvTimestamp != null) {
                holder.tvTimestamp.setText(formatTime(chat.getLastMessageTime()));
            }

            // Handle unread indicator
            if (holder.ivUnread != null) {
                holder.ivUnread.setVisibility(chat.isUnread() ? View.VISIBLE : View.INVISIBLE);
            }

            // Handle online status indicator - this is the key part that needs fixing
            if (holder.onlineIndicator != null) {
                // Only show the indicator if the user is actually online
                holder.onlineIndicator.setVisibility(chat.isUserOnline() ? View.VISIBLE : View.GONE);
            }

            // Handle profile image
            if (holder.ivProfilePic != null) {
                if (chat.getProfileImageUrl() != null && !chat.getProfileImageUrl().isEmpty()) {
                    try {
                        Glide.with(context)
                                .load(chat.getProfileImageUrl())
                                .placeholder(R.drawable.ic_launcher_foreground)
                                .into(holder.ivProfilePic);
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading image", e);
                        holder.ivProfilePic.setImageResource(R.drawable.ic_launcher_foreground);
                    }
                } else {
                    holder.ivProfilePic.setImageResource(R.drawable.ic_launcher_foreground);
                }
            }

            // Set click listener
            holder.itemView.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(context, ChatActivity.class);
                    intent.putExtra("userId", chat.getUserId());
                    intent.putExtra("username", chat.getUsername());
                    intent.putExtra("profileImageUrl", chat.getProfileImageUrl());
                    context.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error starting ChatActivity", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error binding view holder at position " + position, e);
        }
    }

    @Override
    public int getItemCount() {
        return chatList != null ? chatList.size() : 0;
    }

    public void updateData(List<ChatListItem> newData) {
        if (newData == null) {
            Log.w(TAG, "Tried to update with null data");
            return;
        }

        Log.d(TAG, "updateData called with " + newData.size() + " items");

        try {
            this.chatList.clear();
            this.chatList.addAll(newData);

            // Sort by timestamp
            Collections.sort(this.chatList, (item1, item2) -> {
                try {
                    return Long.compare(item2.getLastMessageTime(), item1.getLastMessageTime());
                } catch (Exception e) {
                    Log.e(TAG, "Error comparing chat items", e);
                    return 0;
                }
            });

            notifyDataSetChanged();
        } catch (Exception e) {
            Log.e(TAG, "Error updating data", e);
        }
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) {
            return "";
        }

        try {
            Calendar cal = Calendar.getInstance(Locale.ENGLISH);
            cal.setTimeInMillis(timestamp);

            Calendar now = Calendar.getInstance();

            // Check if the message is from today
            if (now.get(Calendar.DATE) == cal.get(Calendar.DATE) &&
                    now.get(Calendar.MONTH) == cal.get(Calendar.MONTH) &&
                    now.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) {
                return DateFormat.format("hh:mm a", cal).toString();
            }
            // Check if the message is from this week
            else if (now.get(Calendar.WEEK_OF_YEAR) == cal.get(Calendar.WEEK_OF_YEAR) &&
                    now.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) {
                return DateFormat.format("EEE", cal).toString();
            }
            // Otherwise show date
            else {
                return DateFormat.format("MMM d", cal).toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error formatting time", e);
            return "";
        }
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivProfilePic;
        TextView tvUsername, tvLastMessage, tvTimestamp;
        View ivUnread;
        View onlineIndicator; // Make sure this is properly declared

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            try {
                // Try to find the views - handle both possible ID names
                ivProfilePic = itemView.findViewById(R.id.ivProfilePic);

                // Try to find username TextView with either ID
                tvUsername = itemView.findViewById(R.id.tvUsername);
                if (tvUsername == null) {
                    tvUsername = itemView.findViewById(R.id.tvChatTitle);
                }

                tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
                tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
                ivUnread = itemView.findViewById(R.id.btnUnread);

                // tvTime might be used instead of tvTimestamp
                if (tvTimestamp == null) {
                    tvTimestamp = itemView.findViewById(R.id.tvTime);
                }

                // Make sure to find the online indicator view
                onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
            } catch (Exception e) {
                Log.e("ChatViewHolder", "Error finding views", e);
            }
        }
    }
}