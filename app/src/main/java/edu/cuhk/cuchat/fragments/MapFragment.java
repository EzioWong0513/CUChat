package edu.cuhk.cuchat.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cuhk.cuchat.R;
import edu.cuhk.cuchat.adapters.NearbyUsersAdapter;
import edu.cuhk.cuchat.models.User;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "MapFragment";
    private static final int PERMISSIONS_REQUEST_LOCATION = 99;
    private static final float DEFAULT_ZOOM = 15f;
    private static final double MAX_DISTANCE_KM = 5.0; // 5 kilometers radius
    private static final int MAX_MAP_READY_RETRIES = 3;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private FloatingActionButton fabMyLocation, fabNearbyUsers;
    private LinearLayout llNearbyUsersList;
    private RecyclerView rvNearbyUsers;
    private NearbyUsersAdapter nearbyUsersAdapter;
    private List<User> nearbyUsersList;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private Location lastKnownLocation;
    private boolean mapReady = false;
    private int mapReadyRetryCount = 0;
    private Handler handler = new Handler(Looper.getMainLooper());

    private ImageButton btnDismissNearbyUsers;

    public MapFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            // Inflate the layout for this fragment
            return inflater.inflate(R.layout.fragment_map, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout", e);
            Toast.makeText(requireContext(), "Error loading map view", Toast.LENGTH_SHORT).show();
            return new View(requireContext());
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize Firebase instances
            mAuth = FirebaseAuth.getInstance();
            db = FirebaseFirestore.getInstance();

            // Initialize UI components
            initializeViews(view);

            // Initialize location services
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

            // Initialize RecyclerView for nearby users
            initializeNearbyUsersList();

            // Set up the map fragment
            SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                    .findFragmentById(R.id.map);
            if (mapFragment != null) {
                mapFragment.getMapAsync(this);
            } else {
                Log.e(TAG, "Map fragment not found");
                Toast.makeText(requireContext(), "Error loading map", Toast.LENGTH_SHORT).show();
            }

            // Set click listeners
            setClickListeners();

        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated", e);
            Toast.makeText(requireContext(), "Error initializing map", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeViews(View view) {
        try {
            fabMyLocation = view.findViewById(R.id.fabMyLocation);
            fabNearbyUsers = view.findViewById(R.id.fabNearbyUsers);
            llNearbyUsersList = view.findViewById(R.id.llNearbyUsersList);
            rvNearbyUsers = view.findViewById(R.id.rvNearbyUsers);
            btnDismissNearbyUsers = view.findViewById(R.id.btnDismissNearbyUsers);

            // Check if views were found
            if (fabMyLocation == null || fabNearbyUsers == null ||
                    llNearbyUsersList == null || rvNearbyUsers == null) {
                Log.e(TAG, "One or more views not found in layout");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
        }
    }

    private void initializeNearbyUsersList() {
        try {
            nearbyUsersList = new ArrayList<>();
            nearbyUsersAdapter = new NearbyUsersAdapter(nearbyUsersList, requireContext());

            if (rvNearbyUsers != null) {
                rvNearbyUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
                rvNearbyUsers.setAdapter(nearbyUsersAdapter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing nearby users list", e);
        }
    }

    private void setClickListeners() {
        try {
            if (fabMyLocation != null) {
                fabMyLocation.setOnClickListener(v -> {
                    if (lastKnownLocation != null && mMap != null) {
                        LatLng latLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM));
                    } else {
                        getDeviceLocation();
                    }
                });
            }

            if (fabNearbyUsers != null && llNearbyUsersList != null) {
                fabNearbyUsers.setOnClickListener(v -> {
                    if (llNearbyUsersList.getVisibility() == View.VISIBLE) {
                        llNearbyUsersList.setVisibility(View.GONE);
                    } else {
                        findNearbyUsers();
                    }
                });
            }

            if (btnDismissNearbyUsers != null) {
                btnDismissNearbyUsers.setOnClickListener(v -> {
                    // Hide the nearby users panel
                    if (llNearbyUsersList != null) {
                        llNearbyUsersList.setVisibility(View.GONE);
                    }

                    // Clear map markers if needed
                    if (mMap != null) {
                        mMap.clear();

                        // Add back current user marker if location is available
                        if (lastKnownLocation != null) {
                            LatLng currentUserLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                            mMap.addMarker(new MarkerOptions()
                                    .position(currentUserLatLng)
                                    .title("You are here")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
                        }
                    }

                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting click listeners", e);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        try {
            mMap = googleMap;
            mapReady = true;

            // Check and request location permissions
            if (ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSIONS_REQUEST_LOCATION);
                return;
            }

            // Enable location layer on map
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);

            // Get current location and update map
            getDeviceLocation();
        } catch (Exception e) {
            Log.e(TAG, "Error in onMapReady", e);
            Toast.makeText(requireContext(), "Error setting up map", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission")
    private void getDeviceLocation() {
        try {
            if (fusedLocationClient == null) {
                Log.e(TAG, "FusedLocationProviderClient is null");
                initializeDefaultLocation();
                return;
            }

            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(requireActivity(), new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                lastKnownLocation = location;

                                // Update location in Firestore
                                updateUserLocation(location);

                                // Move camera to current location if map is ready
                                if (mMap != null && mapReady) {
                                    LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, DEFAULT_ZOOM));
                                }
                            } else {
                                Log.w(TAG, "Location is null");
                                Toast.makeText(requireContext(), "Unable to get current location", Toast.LENGTH_SHORT).show();
                                initializeDefaultLocation();
                            }
                        }
                    })
                    .addOnFailureListener(requireActivity(), new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "Error getting location", e);
                            Toast.makeText(requireContext(), "Error getting location", Toast.LENGTH_SHORT).show();
                            initializeDefaultLocation();
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception getting device location: " + e.getMessage(), e);
            initializeDefaultLocation();
        }
    }

    private void updateUserLocation(Location location) {
        if (mAuth == null || mAuth.getCurrentUser() == null || db == null) {
            Log.w(TAG, "Cannot update user location: Auth or Firestore is null");
            return;
        }

        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("location", new GeoPoint(location.getLatitude(), location.getLongitude()));
            updates.put("lastLocationUpdate", System.currentTimeMillis());

            db.collection("users").document(mAuth.getCurrentUser().getUid())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "User location updated"))
                    .addOnFailureListener(e -> Log.w(TAG, "Error updating user location", e));
        } catch (Exception e) {
            Log.e(TAG, "Error updating user location", e);
        }
    }

    // Improved nearby user search that works regardless of map state
    private void findNearbyUsers() {
        try {
            // Show the nearby users list regardless of map state
            if (llNearbyUsersList != null) {
                llNearbyUsersList.setVisibility(View.VISIBLE);
            }

            // Check if we have a location
            if (lastKnownLocation == null) {
                // Try to get location first
                getDeviceLocation();

                // If still null, show a message and create mock users
                if (lastKnownLocation == null) {
                    Toast.makeText(requireContext(), "Location not available yet", Toast.LENGTH_SHORT).show();
                    createMockUsersIfNeeded(true); // Force create mock users
                    return;
                }
            }

            // Clear previous data
            nearbyUsersList.clear();

            // Clear map markers if map is ready
            if (mMap != null && mapReady) {
                mMap.clear();

                // Add current user marker
                LatLng currentUserLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                mMap.addMarker(new MarkerOptions()
                        .position(currentUserLatLng)
                        .title("You are here")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            } else {
                // Map isn't ready, but we can still search for users and show the list
                Log.d(TAG, "Map is not ready yet, but still searching for nearby users");

                // Attempt to get the map ready
                retryGetMapReady();
            }

            // Query Firestore for all users
            if (db == null) {
                Log.e(TAG, "Firestore instance is null");
                createMockUsersIfNeeded(true);
                return;
            }

            db.collection("users")
                    .get()
                    .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                        @Override
                        public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                            processUsersQueryResult(queryDocumentSnapshots);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.w(TAG, "Error finding nearby users", e);
                            Toast.makeText(requireContext(), "Error finding nearby users", Toast.LENGTH_SHORT).show();
                            createMockUsersIfNeeded(true);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error finding nearby users", e);
            Toast.makeText(requireContext(), "Error searching for nearby users", Toast.LENGTH_SHORT).show();
            createMockUsersIfNeeded(true);
        }
    }

    private void processUsersQueryResult(QuerySnapshot queryDocumentSnapshots) {
        try {
            if (mAuth == null || mAuth.getCurrentUser() == null) {
                Log.w(TAG, "Auth is null or no current user");
                return;
            }

            String currentUserId = mAuth.getCurrentUser().getUid();
            boolean foundNearbyUsers = false;

            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                try {
                    // Skip the current user
                    if (document.getId().equals(currentUserId)) {
                        continue;
                    }

                    User user = document.toObject(User.class);
                    if (user == null || user.getUserId() == null) {
                        Log.w(TAG, "Null user or user ID for document: " + document.getId());
                        continue;
                    }

                    // Get user's location
                    GeoPoint geoPoint = document.getGeoPoint("location");
                    if (geoPoint == null) {
                        // Skip users without location
                        continue;
                    }

                    // Calculate distance
                    Location userLocation = new Location("");
                    userLocation.setLatitude(geoPoint.getLatitude());
                    userLocation.setLongitude(geoPoint.getLongitude());

                    float distanceInMeters = lastKnownLocation.distanceTo(userLocation);
                    double distanceInKm = distanceInMeters / 1000.0;

                    // Only show users within the specified distance
                    if (distanceInKm <= MAX_DISTANCE_KM) {
                        // Add user to list
                        user.setDistanceInKm(distanceInKm);
                        nearbyUsersList.add(user);
                        foundNearbyUsers = true;

                        // Add marker on map if map is ready
                        if (mMap != null && mapReady) {
                            LatLng userLatLng = new LatLng(geoPoint.getLatitude(), geoPoint.getLongitude());
                            mMap.addMarker(new MarkerOptions()
                                    .position(userLatLng)
                                    .title(user.getUsername())
                                    .snippet(String.format("%.1f km away", distanceInKm)));
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing user document: " + document.getId(), e);
                }
            }

            // If no nearby users found, add mock users
            if (!foundNearbyUsers) {
                createMockUsersIfNeeded(true);
            }

            // Update adapter
            if (nearbyUsersAdapter != null) {
                nearbyUsersAdapter.notifyDataSetChanged();
            }

            if (nearbyUsersList.isEmpty()) {
                Toast.makeText(requireContext(), "No users found nearby", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing users query result", e);
        }
    }

    // Try to get the map ready if it's not ready yet
    private void retryGetMapReady() {
        if (mapReady || mMap != null || mapReadyRetryCount >= MAX_MAP_READY_RETRIES) {
            return;
        }

        mapReadyRetryCount++;

        // Try to get map fragment again
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);

        if (mapFragment != null) {
            Log.d(TAG, "Retrying to get map ready (attempt " + mapReadyRetryCount + ")");
            mapFragment.getMapAsync(this);
        }
    }

    private void createMockUsersIfNeeded(boolean forceCreate) {
        try {
            if ((nearbyUsersList.isEmpty() || forceCreate) && lastKnownLocation != null) {
                Log.d(TAG, "Creating mock users for testing");

                // Create mock users with locations near current user
                for (int i = 1; i <= 5; i++) {
                    User mockUser = new User();
                    mockUser.setUserId("mock-user-" + i);
                    mockUser.setUsername("Test User " + i);
                    mockUser.setStatus("This is a test user");

                    // Create a location that's slightly offset from current user
                    // Each user will be at a different distance
                    double latOffset = (Math.random() - 0.5) * 0.02; // Random offset within ~1-2km
                    double lngOffset = (Math.random() - 0.5) * 0.02;

                    double mockLat = lastKnownLocation.getLatitude() + latOffset;
                    double mockLng = lastKnownLocation.getLongitude() + lngOffset;

                    // Calculate distance
                    Location mockLocation = new Location("");
                    mockLocation.setLatitude(mockLat);
                    mockLocation.setLongitude(mockLng);

                    float distanceInMeters = lastKnownLocation.distanceTo(mockLocation);
                    double distanceInKm = distanceInMeters / 1000.0;

                    mockUser.setDistanceInKm(distanceInKm);
                    nearbyUsersList.add(mockUser);

                    // Add marker on map if map is ready
                    if (mMap != null && mapReady) {
                        LatLng mockLatLng = new LatLng(mockLat, mockLng);
                        mMap.addMarker(new MarkerOptions()
                                .position(mockLatLng)
                                .title(mockUser.getUsername())
                                .snippet(String.format("%.1f km away", distanceInKm)));
                    }
                }

                // Update adapter
                if (nearbyUsersAdapter != null) {
                    nearbyUsersAdapter.notifyDataSetChanged();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating mock users", e);
        }
    }

    // For simplicity, let's assume this method initializes a default mock location
    // if we can't get the actual location
    private void initializeDefaultLocation() {
        if (lastKnownLocation == null) {
            // Use CUHK as default location (22.4195, 114.2067)
            lastKnownLocation = new Location("");
            lastKnownLocation.setLatitude(22.4195);
            lastKnownLocation.setLongitude(114.2067);
            Log.d(TAG, "Using default location (CUHK)");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, initialize map
                if (mMap != null) {
                    onMapReady(mMap);
                }
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Initialize a default location if needed
        initializeDefaultLocation();

        // Reset map ready retry count
        mapReadyRetryCount = 0;
    }

    @Override
    public void onPause() {
        super.onPause();
        // Clean up resources
        mapReady = false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up map resources
        mMap = null;
        mapReady = false;
    }
}