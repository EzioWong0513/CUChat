package edu.cuhk.cuchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;

    private TextInputEditText etEmail, etPassword;
    private Button btnLogin, btnGoogleSignIn;
    private TextView tvRegisterPrompt;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Initialize UI elements
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        tvRegisterPrompt = findViewById(R.id.tvRegisterPrompt);

        // Set click listeners
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginUser();
            }
        });

        tvRegisterPrompt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to RegisterActivity
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            }
        });

        btnGoogleSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signInWithGoogle();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // User is already signed in, redirect to MainActivity
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Validate inputs
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            return;
        }

        // Authenticate user
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success
                            Log.d(TAG, "signInWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();

                            // Update FCM token after successful login
                            updateFCMToken();

                            // Navigate to MainActivity
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            // If sign in fails, display a message to the user
                            Log.w(TAG, "signInWithEmail:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                // Google Sign In failed, update UI appropriately
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Google Sign In failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = mAuth.getCurrentUser();

                            // Check if this is a new user
                            checkIfUserExists(user);
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkIfUserExists(FirebaseUser user) {
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                // User exists in Firestore, just update FCM token
                                updateFCMToken();

                                // Navigate to MainActivity
                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                finish();
                            } else {
                                // New user, create profile in Firestore
                                createUserInFirestore(user);
                            }
                        } else {
                            Log.w(TAG, "Error checking if user exists.", task.getException());
                            Toast.makeText(LoginActivity.this, "Error checking user data. Please try again.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void createUserInFirestore(FirebaseUser user) {
        updateFCMToken(); // Get FCM token

        // Create basic user profile
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("userId", user.getUid());
        userMap.put("username", user.getDisplayName() != null ? user.getDisplayName() : "User");
        userMap.put("email", user.getEmail());
        userMap.put("bio", "");
        userMap.put("status", "Hey, I'm using CUChat!");
        userMap.put("profileImageUrl", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
        userMap.put("createdAt", System.currentTimeMillis());
        userMap.put("isOnline", true);

        db.collection("users").document(user.getUid())
                .set(userMap)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "User profile created for Google sign-in user.");

                            // Navigate to MainActivity
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            Log.w(TAG, "Error adding user to Firestore", task.getException());
                            Toast.makeText(LoginActivity.this, "Error creating profile. Please try again.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void updateFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    // Get new FCM registration token
                    String token = task.getResult();
                    Log.d(TAG, "FCM Token: " + token);

                    // Save the token to Firestore for this user
                    if (mAuth.getCurrentUser() != null) {
                        String userId = mAuth.getCurrentUser().getUid();
                        Map<String, Object> tokenData = new HashMap<>();
                        tokenData.put("fcmToken", token);
                        tokenData.put("isOnline", true);
                        tokenData.put("lastSeen", System.currentTimeMillis());

                        db.collection("users").document(userId)
                                .update(tokenData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "FCM token and online status updated successfully");

                                    // Also update the Realtime Database
                                    setupOnlineStatusTracking(userId);
                                })
                                .addOnFailureListener(e -> Log.w(TAG, "Error updating FCM token", e));
                    }
                });
    }

    private void setupOnlineStatusTracking(String userId) {
        // Update user's online status in Firestore
        db.collection("users").document(userId)
                .update("isOnline", true,
                        "lastSeen", System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User marked as online in Firestore");

                    // Set up a tracker in Firebase Realtime Database
                    DatabaseReference userStatusRef = FirebaseDatabase.getInstance().getReference()
                            .child("status").child(userId);

                    Map<String, Object> onlineState = new HashMap<>();
                    onlineState.put("state", "online");
                    onlineState.put("lastChanged", ServerValue.TIMESTAMP);
                    userStatusRef.setValue(onlineState)
                            .addOnSuccessListener(aVoid2 -> {
                                Log.d(TAG, "User marked as online in Realtime Database");

                                // Set up disconnect handler
                                Map<String, Object> offlineState = new HashMap<>();
                                offlineState.put("state", "offline");
                                offlineState.put("lastChanged", ServerValue.TIMESTAMP);

                                userStatusRef.onDisconnect().setValue(offlineState)
                                        .addOnSuccessListener(aVoid3 -> {
                                            Log.d(TAG, "Disconnect handler set up successfully");
                                        });
                            });
                });
    }

    private void createUserInFirestore(FirebaseUser user, String username, String email, String fcmToken) {
        // Store additional user info in Firestore
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("userId", user.getUid());
        userMap.put("username", username);
        userMap.put("email", email);
        userMap.put("bio", "");
        userMap.put("status", "Hey, I'm using CUChat!");
        userMap.put("profileImageUrl", "");
        userMap.put("createdAt", System.currentTimeMillis());
        userMap.put("isOnline", true);
        userMap.put("lastSeen", System.currentTimeMillis());

        // Add FCM token if available
        if (fcmToken != null) {
            userMap.put("fcmToken", fcmToken);
        }

        db.collection("users").document(user.getUid())
                .set(userMap)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            // Set up online status tracking in Realtime Database
                            setupOnlineStatusTracking(user.getUid());

                            Toast.makeText(LoginActivity.this, "Registration successful",
                                    Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            Log.w(TAG, "Error adding user to Firestore", task.getException());
                        }
                    }
                });
    }

}