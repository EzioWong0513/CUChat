package edu.cuhk.cuchat.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import edu.cuhk.cuchat.fragments.ChatsFragment;
import edu.cuhk.cuchat.fragments.MapFragment;
import edu.cuhk.cuchat.fragments.ProfileFragment;

public class ViewPagerAdapter extends FragmentStateAdapter {

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new MapFragment();
            case 1:
                return new ChatsFragment();
            case 2:
                return new ProfileFragment();
            default:
                return new MapFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;  // Map, Chats, and Profile tabs
    }
}