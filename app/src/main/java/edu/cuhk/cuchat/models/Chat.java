package edu.cuhk.cuchat.models;

import java.util.ArrayList;
import java.util.List;

public class Chat {
    private String chatId;
    private String user1Id;
    private String user2Id;
    private String lastMessageContent;
    private long lastMessageTimestamp;
    private boolean user1Seen;
    private boolean user2Seen;
    private List<String> participants;

    // Empty constructor for Firestore
    public Chat() {
    }

    public Chat(String chatId, String user1Id, String user2Id, String lastMessageContent,
                long lastMessageTimestamp, boolean user1Seen, boolean user2Seen) {
        this.chatId = chatId;
        this.user1Id = user1Id;
        this.user2Id = user2Id;
        this.lastMessageContent = lastMessageContent;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.user1Seen = user1Seen;
        this.user2Seen = user2Seen;

        // Initialize participants array
        this.participants = new ArrayList<>();
        this.participants.add(user1Id);
        this.participants.add(user2Id);
    }

    public List<String> getParticipants() {
        return participants;
    }

    public void setParticipants(List<String> participants) {
        this.participants = participants;
    }

    // Getters and setters
    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getUser1Id() {
        return user1Id;
    }

    public void setUser1Id(String user1Id) {
        this.user1Id = user1Id;
    }

    public String getUser2Id() {
        return user2Id;
    }

    public void setUser2Id(String user2Id) {
        this.user2Id = user2Id;
    }

    public String getLastMessageContent() {
        return lastMessageContent;
    }

    public void setLastMessageContent(String lastMessageContent) {
        this.lastMessageContent = lastMessageContent;
    }

    public long getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }

    public void setLastMessageTimestamp(long lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    public boolean isUser1Seen() {
        return user1Seen;
    }

    public void setUser1Seen(boolean user1Seen) {
        this.user1Seen = user1Seen;
    }

    public boolean isUser2Seen() {
        return user2Seen;
    }

    public void setUser2Seen(boolean user2Seen) {
        this.user2Seen = user2Seen;
    }
}