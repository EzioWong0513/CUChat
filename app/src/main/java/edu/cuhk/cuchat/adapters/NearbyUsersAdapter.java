package edu.cuhk.cuchat.adapters;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.GlideException;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;
import edu.cuhk.cuchat.ChatActivity;
import edu.cuhk.cuchat.R;
import edu.cuhk.cuchat.models.User;

public class NearbyUsersAdapter extends RecyclerView.Adapter<NearbyUsersAdapter.NearbyUserViewHolder> {

    private static final String TAG = "NearbyUsersAdapter";
    private List<User> userList;
    private Context context;

    public NearbyUsersAdapter(List<User> userList, Context context) {
        this.userList = userList;
        this.context = context;
    }

    @NonNull
    @Override
    public NearbyUserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        try {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_nearby_user, parent, false);
            return new NearbyUserViewHolder(view);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating item_nearby_user layout", e);
            // Create a fallback view if the layout inflation fails
            View fallbackView = new View(context);
            fallbackView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new NearbyUserViewHolder(fallbackView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull NearbyUserViewHolder holder, int position) {
        try {
            if (userList == null || position < 0 || position >= userList.size()) {
                Log.e(TAG, "Invalid position or null userList");
                return;
            }

            User user = userList.get(position);
            if (user == null) {
                Log.e(TAG, "Null user at position " + position);
                return;
            }

            // Check if views exist before trying to use them
            if (holder.tvUsername != null) {
                holder.tvUsername.setText(user.getUsername() != null ? user.getUsername() : "Unknown User");
            }

            if (holder.tvStatus != null) {
                holder.tvStatus.setText(user.getStatus() != null ? user.getStatus() : "");
            }

            if (holder.tvDistance != null) {
                // Format distance
                if (user.getDistanceInKm() < 1.0) {
                    // Convert to meters for distances less than 1km
                    int distanceInMeters = (int) (user.getDistanceInKm() * 1000);
                    holder.tvDistance.setText(distanceInMeters + "m away");
                } else {
                    holder.tvDistance.setText(String.format("%.1f km away", user.getDistanceInKm()));
                }
            }

            // Load profile image if available
            if (holder.ivUserProfilePic != null) {
                if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
                    try {
                        Glide.with(context)
                                .load(user.getProfileImageUrl())
                                .placeholder(R.drawable.ic_launcher_foreground)
                                .into(holder.ivUserProfilePic);
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading profile image", e);
                        holder.ivUserProfilePic.setImageResource(R.drawable.ic_launcher_foreground);
                    }
                } else {
                    holder.ivUserProfilePic.setImageResource(R.drawable.ic_launcher_foreground);
                }
            }

            // Set chat button click listener
            if (holder.btnChat != null) {
                holder.btnChat.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            Intent intent = new Intent(context, ChatActivity.class);
                            intent.putExtra("userId", user.getUserId());
                            intent.putExtra("username", user.getUsername());
                            intent.putExtra("profileImageUrl", user.getProfileImageUrl());
                            context.startActivity(intent);
                        } catch (Exception e) {
                            Log.e(TAG, "Error starting ChatActivity", e);
                            Toast.makeText(context, "Error opening chat", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error binding view holder at position " + position, e);
        }
    }

    @Override
    public int getItemCount() {
        return userList != null ? userList.size() : 0;
    }

    public static class NearbyUserViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivUserProfilePic;
        TextView tvUsername, tvStatus, tvDistance;
        View btnChat; // Could be Button or ImageButton

        public NearbyUserViewHolder(@NonNull View itemView) {
            super(itemView);
            try {
                ivUserProfilePic = itemView.findViewById(R.id.ivUserProfilePic);

                // Try both potential IDs for username
                tvUsername = itemView.findViewById(R.id.tvUsername);
                if (tvUsername == null) {
                    tvUsername = itemView.findViewById(R.id.tvNewFriendName);
                }

                // Try both potential IDs for status
                tvStatus = itemView.findViewById(R.id.tvStatus);
                if (tvStatus == null) {
                    tvStatus = itemView.findViewById(R.id.tvNewFriendStatus);
                }

                tvDistance = itemView.findViewById(R.id.tvDistance);

                // Try both potential types of chat buttons
                btnChat = itemView.findViewById(R.id.btnChat);
                if (btnChat == null) {
                    btnChat = itemView.findViewById(R.id.btnChatWithUser);
                }
            } catch (Exception e) {
                Log.e("NearbyUserViewHolder", "Error finding views", e);
            }
        }
    }
}