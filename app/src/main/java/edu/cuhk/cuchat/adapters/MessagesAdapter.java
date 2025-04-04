package edu.cuhk.cuchat.adapters;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import edu.cuhk.cuchat.R;
import edu.cuhk.cuchat.models.Message;

public class MessagesAdapter extends RecyclerView.Adapter {
    private static final int VIEW_TYPE_MESSAGE_SENT = 1;
    private static final int VIEW_TYPE_MESSAGE_RECEIVED = 2;

    private Context context;
    private List<Message> messageList;
    private String currentUserId;

    public MessagesAdapter(Context context, List<Message> messageList, String currentUserId) {
        this.context = context;
        this.messageList = messageList;
        this.currentUserId = currentUserId;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messageList.get(position);

        if (message.getSenderId().equals(currentUserId)) {
            // If the current user is the sender of the message
            return VIEW_TYPE_MESSAGE_SENT;
        } else {
            // If some other user sent the message
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
                    .inflate(R.layout.item_message_received, parent, false);
            return new ReceivedMessageHolder(view);
        }

        return null;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messageList.get(position);

        switch (holder.getItemViewType()) {
            case VIEW_TYPE_MESSAGE_SENT:
                ((SentMessageHolder) holder).bind(message);
                break;
            case VIEW_TYPE_MESSAGE_RECEIVED:
                ((ReceivedMessageHolder) holder).bind(message);
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
            statusText = itemView.findViewById(R.id.tvMessageStatus);
        }

        void bind(Message message) {
            messageText.setText(message.getContent());

            // Format the stored timestamp into a readable String
            timeText.setText(formatTime(message.getTimestamp()));

            // Set the status text based on the 'seen' field
            if (statusText != null) {
                if (message.isSeen()) {
                    statusText.setText("Seen");
                    statusText.setTextColor(context.getResources().getColor(R.color.purple_500));
                } else {
                    statusText.setText("Delivered");
                    statusText.setTextColor(context.getResources().getColor(R.color.gray));
                }
            }
        }
    }

    private class ReceivedMessageHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText;

        ReceivedMessageHolder(View itemView) {
            super(itemView);

            messageText = itemView.findViewById(R.id.tvMessage);
            timeText = itemView.findViewById(R.id.tvTimestamp);
        }

        void bind(Message message) {
            messageText.setText(message.getContent());

            // Format the stored timestamp into a readable String
            timeText.setText(formatTime(message.getTimestamp()));
        }
    }

    private String formatTime(long timestamp) {
        Calendar cal = Calendar.getInstance(Locale.ENGLISH);
        cal.setTimeInMillis(timestamp);
        return DateFormat.format("hh:mm a", cal).toString();
    }

    // Method to update a single message's seen status
    public void updateMessageSeenStatus(String messageId, boolean seen) {
        for (int i = 0; i < messageList.size(); i++) {
            Message message = messageList.get(i);
            if (message.getMessageId().equals(messageId) && message.getSenderId().equals(currentUserId)) {
                message.setSeen(seen);
                notifyItemChanged(i);
                break;
            }
        }
    }

    // Method to update all messages to seen
    public void updateAllMessagesSeenStatus(boolean seen) {
        boolean updated = false;
        for (int i = 0; i < messageList.size(); i++) {
            Message message = messageList.get(i);
            if (message.getSenderId().equals(currentUserId) && !message.isSeen()) {
                message.setSeen(seen);
                updated = true;
                notifyItemChanged(i);
            }
        }
    }
}