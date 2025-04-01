package edu.cuhk.cuchat.models;

public class User {
    private String userId;
    private String username;
    private String email;
    private String profileImageUrl;
    private String bio;
    private String status;
    private long createdAt;
    private boolean isOnline;
    private double distanceInKm;

    private String fcmToken;

    // Empty constructor for Firestore
    public User() {
    }

    public User(String userId, String username, String email, String profileImageUrl, String bio, String status, long createdAt, boolean isOnline) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.bio = bio;
        this.status = status;
        this.createdAt = createdAt;
        this.isOnline = isOnline;
        this.distanceInKm = 0.0; // Initialize with default value
    }

    // Getters and setters
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public double getDistanceInKm() {
        return distanceInKm;
    }

    public void setDistanceInKm(double distanceInKm) {
        this.distanceInKm = distanceInKm;
    }

    // Add getter and setter
    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}