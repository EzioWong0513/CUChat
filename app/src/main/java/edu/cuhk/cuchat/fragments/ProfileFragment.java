package edu.cuhk.cuchat.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;
import edu.cuhk.cuchat.LoginActivity;
import edu.cuhk.cuchat.R;
import edu.cuhk.cuchat.models.User;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private static final int PICK_IMAGE_REQUEST = 1;

    private CircleImageView ivProfilePicture;
    private Button btnChangeProfilePicture, btnSaveProfile, btnLogout;
    private TextInputEditText etUsername, etEmail, etBio, etStatus;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private Uri imageUri;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize UI components
        ivProfilePicture = view.findViewById(R.id.ivProfilePicture);
        btnChangeProfilePicture = view.findViewById(R.id.btnChangeProfilePicture);
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile);
        btnLogout = view.findViewById(R.id.btnLogout);
        etUsername = view.findViewById(R.id.etUsername);
        etEmail = view.findViewById(R.id.etEmail);
        etBio = view.findViewById(R.id.etBio);
        etStatus = view.findViewById(R.id.etStatus);

        // Load user data
        loadUserData();

        // Set click listeners
        btnChangeProfilePicture.setOnClickListener(v -> openFileChooser());

        btnSaveProfile.setOnClickListener(v -> saveProfile());

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(getActivity(), LoginActivity.class));
            getActivity().finish();
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
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            if (task.getResult().exists()) {
                                User userProfile = task.getResult().toObject(User.class);

                                // Set user data to UI
                                etUsername.setText(userProfile.getUsername());
                                etBio.setText(userProfile.getBio());
                                etStatus.setText(userProfile.getStatus());

                                // Load profile image if exists
                                if (userProfile.getProfileImageUrl() != null && !userProfile.getProfileImageUrl().isEmpty()) {
                                    Glide.with(requireContext())
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
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == getActivity().RESULT_OK
                && data != null && data.getData() != null) {
            imageUri = data.getData();

            // Display the selected image
            Glide.with(this)
                    .load(imageUri)
                    .into(ivProfilePicture);
        }
    }

    private void saveProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String username = etUsername.getText().toString().trim();
            String bio = etBio.getText().toString().trim();
            String status = etStatus.getText().toString().trim();

            // Create a map with updated user data
            Map<String, Object> userUpdates = new HashMap<>();
            userUpdates.put("username", username);
            userUpdates.put("bio", bio);
            userUpdates.put("status", status);

            // Update user data in Firestore
            db.collection("users").document(user.getUid())
                    .update(userUpdates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getActivity(), "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getActivity(), "Error updating profile", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error updating profile", e);
                    });

            // Upload image if selected (Implementation similar to your ProfileActivity)
            // For brevity, I've omitted the image upload code here
        }
    }

    private void logoutUser() {
        // Mark user as offline in Firestore
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

}