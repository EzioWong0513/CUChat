package edu.cuhk.cuchat.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;
import edu.cuhk.cuchat.R;
import edu.cuhk.cuchat.models.User;

public class UserSelectionAdapter extends RecyclerView.Adapter<UserSelectionAdapter.UserSelectionViewHolder> {

    private Context context;
    private List<User> userList;
    private OnUserSelectedListener listener;

    public interface OnUserSelectedListener {
        void onUserSelected(User user, boolean isSelected);
    }

    public UserSelectionAdapter(Context context, List<User> userList, OnUserSelectedListener listener) {
        this.context = context;
        this.userList = userList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserSelectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user_selection, parent, false);
        return new UserSelectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserSelectionViewHolder holder, int position) {
        User user = userList.get(position);

        holder.tvUsername.setText(user.getUsername());
        holder.tvStatus.setText(user.getStatus());

        // Load profile image if available
        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
            Glide.with(context)
                    .load(user.getProfileImageUrl())
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(holder.ivProfilePic);
        } else {
            holder.ivProfilePic.setImageResource(R.drawable.ic_launcher_foreground);
        }

        // Show online status indicator
        holder.ivOnlineStatus.setVisibility(user.isOnline() ? View.VISIBLE : View.GONE);

        // Set checkbox click listener
        holder.checkBox.setOnCheckedChangeListener(null); // Clear previous listener
        holder.checkBox.setChecked(false); // Default unselected

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onUserSelected(user, isChecked);
            }
        });

        // Make the whole item clickable to toggle selection
        holder.itemView.setOnClickListener(v -> {
            holder.checkBox.setChecked(!holder.checkBox.isChecked());
        });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    static class UserSelectionViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivProfilePic;
        TextView tvUsername;
        TextView tvStatus;
        CheckBox checkBox;
        View ivOnlineStatus;

        UserSelectionViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfilePic = itemView.findViewById(R.id.ivUserProfilePic);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            checkBox = itemView.findViewById(R.id.checkboxSelectUser);
            ivOnlineStatus = itemView.findViewById(R.id.onlineIndicator);
        }
    }
}