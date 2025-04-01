package edu.cuhk.cuchat.models;

public class ChatListItem {
    private String chatId;
    private String userId;  // ID of the other user in the chat
    private String username;
    private String profileImageUrl;
    private String lastMessage;
    private long lastMessageTime;
    private boolean unread;
    private boolean isPinned;

    // Empty constructor for Firestore
    public ChatListItem() {
    }

    public ChatListItem(String chatId, String userId, String username, String profileImageUrl,
                        String lastMessage, long lastMessageTime, boolean unread) {
        this.chatId = chatId;
        this.userId = userId;
        this.username = username;
        this.profileImageUrl = profileImageUrl;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.unread = unread;
    }

    // Getters and setters
    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public long getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(long lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public boolean isUnread() {
        return unread;
    }

    public void setUnread(boolean unread) {
        this.unread = unread;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }
}