package edu.cuhk.cuchat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;
import edu.cuhk.cuchat.models.User;
import edu.cuhk.cuchat.services.UserStatusService;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";
    private static final int PICK_IMAGE_REQUEST = 1;

    private Toolbar toolbar;
    private CircleImageView ivProfilePicture;
    private Button btnChangeProfilePicture, btnSaveProfile, btnLogout;
    private TextInputEditText etUsername, etEmail, etBio, etStatus;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();

        // Initialize UI components
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ivProfilePicture = findViewById(R.id.ivProfilePicture);
        btnChangeProfilePicture = findViewById(R.id.btnChangeProfilePicture);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnLogout = findViewById(R.id.btnLogout);
        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        etBio = findViewById(R.id.etBio);
        etStatus = findViewById(R.id.etStatus);

        // Load user data
        loadUserData();

        // Set click listeners
        btnChangeProfilePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileChooser();
            }
        });

        btnSaveProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveProfile();
            }
        });

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logoutUser();
            }
        });
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Set email (not editable)
            etEmail.setText(user.getEmail());

            // Get additional user data from Firestore
            db.collection("users").document(user.getUid())
                    .get()
                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                            if (task.isSuccessful()) {
                                DocumentSnapshot document = task.getResult();
                                if (document.exists()) {
                                    User userProfile = document.toObject(User.class);

                                    // Set user data to UI
                                    etUsername.setText(userProfile.getUsername());
                                    etBio.setText(userProfile.getBio());
                                    etStatus.setText(userProfile.getStatus());

                                    // Load profile image if exists
                                    if (userProfile.getProfileImageUrl() != null && !userProfile.getProfileImageUrl().isEmpty()) {
                                        Glide.with(ProfileActivity.this)
                                                .load(userProfile.getProfileImageUrl())
                                                .placeholder(R.drawable.ic_launcher_foreground)
                                                .into(ivProfilePicture);
                                    }
                                } else {
                                    Log.d(TAG, "No such document");
                                }
                            } else {
                                Log.d(TAG, "get failed with ", task.getException());
                            }
                        }
                    });
        }
    }

    private void openFileChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            imageUri = data.getData();

            // Display the selected image
            Glide.with(this)
                    .load(imageUri)
                    .into(ivProfilePicture);
        }
    }

    private void saveProfile() {
        final FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            final String username = etUsername.getText().toString().trim();
            final String bio = etBio.getText().toString().trim();
            final String status = etStatus.getText().toString().trim();

            // Update username in Firebase Auth
            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                    .setDisplayName(username)
                    .build();

            user.updateProfile(profileUpdates);

            // Create a map with updated user data
            final Map<String, Object> userUpdates = new HashMap<>();
            userUpdates.put("username", username);
            userUpdates.put("bio", bio);
            userUpdates.put("status", status);

            // Upload image if selected
            if (imageUri != null) {
                final StorageReference fileRef = storageRef.child("profile_images/" + user.getUid());

                fileRef.putFile(imageUri)
                        .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                fileRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                    @Override
                                    public void onSuccess(Uri uri) {
                                        userUpdates.put("profileImageUrl", uri.toString());
                                        updateFirestore(user.getUid(), userUpdates);
                                    }
                                });
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(ProfileActivity.this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                                updateFirestore(user.getUid(), userUpdates);
                            }
                        });
            } else {
                updateFirestore(user.getUid(), userUpdates);
            }
        }
    }

    private void updateFirestore(String userId, Map<String, Object> updates) {
        db.collection("users").document(userId)
                .update(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(ProfileActivity.this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(ProfileActivity.this, "Error updating profile", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error updating document", e);
                    }
                });
    }

    private void logoutUser() {
        // Mark user as offline in Firestore
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            FirebaseFirestore.getInstance().collection("users")
                    .document(currentUser.getUid())
                    .update("isOnline", false,
                            "lastSeen", System.currentTimeMillis());

            // Also mark in Realtime Database
            FirebaseDatabase.getInstance().getReference()
                    .child("status").child(currentUser.getUid())
                    .setValue("offline");
        }

        // Stop the status service
        stopService(new Intent(this, UserStatusService.class));

        // Sign out from Firebase
        mAuth.signOut();

        // Navigate to login screen
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}