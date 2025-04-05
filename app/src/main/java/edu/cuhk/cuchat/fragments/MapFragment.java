package edu.cuhk.cuchat.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
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
import androidx.cardview.widget.CardView;
import com.google.android.material.slider.Slider;
import android.graphics.Color;
import com.google.android.gms.maps.model.CircleOptions;

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
    private ConstraintLayout rootLayout;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private Location lastKnownLocation;
    private boolean mapReady = false;
    private int mapReadyRetryCount = 0;
    private Handler handler = new Handler(Looper.getMainLooper());

    private ImageButton btnDismissNearbyUsers;

    // Store the original height of the nearby users list
    private int originalNearbyUsersHeight;
    private boolean isNearbyUsersExpanded = false;

    // Store the original and expanded heights for the list
    private float nearbyListExpandedHeight = 0.3f; // 30% of screen height
    private float nearbyListCollapsedHeight = 0.6f; // 60% of screen height
    private static final double DEFAULT_SEARCH_DISTANCE_KM = 1.0; // Default to 1km
    private double searchDistanceKm = DEFAULT_SEARCH_DISTANCE_KM;

    private CardView cardRangeSlider;
    private TextView tvRangeLabel;
    private Slider sliderRange;
    private Button btnApplyRange;
    private FloatingActionButton fabSetRange;

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
            fabSetRange = view.findViewById(R.id.fabSetRange);
            llNearbyUsersList = view.findViewById(R.id.llNearbyUsersList);
            rvNearbyUsers = view.findViewById(R.id.rvNearbyUsers);
            btnDismissNearbyUsers = view.findViewById(R.id.btnDismissNearbyUsers);
            rootLayout = view.findViewById(R.id.rootConstraintLayout);

            // Initialize range slider card and components
            cardRangeSlider = view.findViewById(R.id.cardRangeSlider);
            tvRangeLabel = view.findViewById(R.id.tvRangeLabel);
            sliderRange = view.findViewById(R.id.sliderRange);
            btnApplyRange = view.findViewById(R.id.btnApplyRange);

            // Set initial range label
            updateRangeLabel(searchDistanceKm);

            // Check if views were found
            if (fabMyLocation == null || fabNearbyUsers == null ||
                    llNearbyUsersList == null || rvNearbyUsers == null ||
                    fabSetRange == null || cardRangeSlider == null) {
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

                // Save the original height for later
                rvNearbyUsers.post(() -> {
                    if (rvNearbyUsers != null) {
                        originalNearbyUsersHeight = rvNearbyUsers.getLayoutParams().height;
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing nearby users list", e);
        }
    }

    private void setClickListeners() {
        try {
            // Existing click listeners...
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
                        isNearbyUsersExpanded = false;

                        // Reset the constraint to original
                        resetMapToFullSize();
                    } else {
                        findNearbyUsers();
                    }
                });
            }

            // New click listener for range setting
            if (fabSetRange != null && cardRangeSlider != null) {
                fabSetRange.setOnClickListener(v -> {
                    if (cardRangeSlider.getVisibility() == View.VISIBLE) {
                        cardRangeSlider.setVisibility(View.GONE);
                    } else {
                        // Show the slider card
                        cardRangeSlider.setVisibility(View.VISIBLE);
                    }
                });
            }

            // Add range slider change listener
            if (sliderRange != null) {
                sliderRange.addOnChangeListener((slider, value, fromUser) -> {
                    if (fromUser) {
                        updateRangeLabel(value);
                    }
                });
            }

            // Add apply button click listener
            if (btnApplyRange != null) {
                btnApplyRange.setOnClickListener(v -> {
                    // Get the current slider value
                    searchDistanceKm = sliderRange.getValue();

                    // Update the display
                    updateRangeLabel(searchDistanceKm);

                    // Hide the slider card
                    cardRangeSlider.setVisibility(View.GONE);

                    // If the nearby users list is already visible, refresh it with the new range
                    if (llNearbyUsersList.getVisibility() == View.VISIBLE) {
                        findNearbyUsers();
                    } else {
                        // Show a toast to confirm the range change
                        Toast.makeText(requireContext(),
                                "Search range set to " + formatDistance(searchDistanceKm),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Existing click listeners...
            if (btnDismissNearbyUsers != null) {
                btnDismissNearbyUsers.setOnClickListener(v -> {
                    // Hide the nearby users panel
                    if (llNearbyUsersList != null) {
                        llNearbyUsersList.setVisibility(View.GONE);
                        isNearbyUsersExpanded = false;

                        // Reset the map to full size
                        resetMapToFullSize();
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
                                    .icon(getColoredMarkerIcon(getResources().getColor(R.color.purple_500))));

                            // Center the map on the user's location
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentUserLatLng, DEFAULT_ZOOM));
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting click listeners", e);
        }
    }

    private void updateRangeLabel(double distanceKm) {
        if (tvRangeLabel != null) {
            tvRangeLabel.setText("Search Range: " + formatDistance(distanceKm));
        }
    }

    private String formatDistance(double distanceKm) {
        if (distanceKm < 1.0) {
            // Display in meters for distances less than 1km
            int meters = (int) (distanceKm * 1000);
            return meters + " m";
        } else {
            // Display in kilometers with one decimal place
            return String.format("%.1f km", distanceKm);
        }
    }

    // Toggle between expanded and collapsed view for nearby users list
    private void toggleNearbyUsersListSize() {
        if (rootLayout == null || llNearbyUsersList == null) return;

        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(rootLayout);

        if (isNearbyUsersExpanded) {
            // Collapse - more map, less list
            constraintSet.constrainPercentHeight(llNearbyUsersList.getId(), nearbyListExpandedHeight);
            isNearbyUsersExpanded = false;
        } else {
            // Expand - less map, more list
            constraintSet.constrainPercentHeight(llNearbyUsersList.getId(), nearbyListCollapsedHeight);
            isNearbyUsersExpanded = true;
        }

        // Apply changes with animation
        constraintSet.applyTo(rootLayout);
    }

    // Reset the map to full size
    private void resetMapToFullSize() {
        if (rootLayout == null) return;

        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(rootLayout);

        // Remove any height constraints on the nearby users list
        if (llNearbyUsersList != null) {
            llNearbyUsersList.setVisibility(View.GONE);
        }

        // Apply changes
        constraintSet.applyTo(rootLayout);
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

                                    // Add marker for current user with purple color
                                    mMap.addMarker(new MarkerOptions()
                                            .position(currentLatLng)
                                            .title("You are here")
                                            .icon(getColoredMarkerIcon(getResources().getColor(R.color.purple_500))));
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

    // Method to get a colored marker icon
    private BitmapDescriptor getColoredMarkerIcon(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return BitmapDescriptorFactory.defaultMarker(hsv[0]);
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

    // Improved nearby user search that makes the map larger
    private void findNearbyUsers() {
        try {
            // Show the nearby users list
            if (llNearbyUsersList != null) {
                llNearbyUsersList.setVisibility(View.VISIBLE);

                // Set initial height constraint for nearby users list (30% of screen)
                if (rootLayout != null) {
                    ConstraintSet constraintSet = new ConstraintSet();
                    constraintSet.clone(rootLayout);
                    constraintSet.constrainPercentHeight(llNearbyUsersList.getId(), nearbyListExpandedHeight);
                    constraintSet.applyTo(rootLayout);
                }

                isNearbyUsersExpanded = false;
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

                // Add current user marker with purple color
                LatLng currentUserLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                mMap.addMarker(new MarkerOptions()
                        .position(currentUserLatLng)
                        .title("You are here")
                        .icon(getColoredMarkerIcon(getResources().getColor(R.color.purple_500))));

                // Center the map on the user's location
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentUserLatLng, DEFAULT_ZOOM));

                // Add a circle to show the search radius
                mMap.addCircle(new CircleOptions()
                        .center(currentUserLatLng)
                        .radius(searchDistanceKm * 1000) // Convert km to meters
                        .strokeColor(Color.argb(100, 0, 0, 255))
                        .fillColor(Color.argb(30, 0, 0, 255)));
            } else {
                // Map isn't ready, but we can still search for users and show the list
                Log.d(TAG, "Map is not ready yet, but still searching for nearby users");

                // Attempt to get the map ready
                retryGetMapReady();
            }

            // Show searching message in the title
            View tvNearbyUsersTitle = getView().findViewById(R.id.tvNearbyUsersTitle);
            if (tvNearbyUsersTitle instanceof TextView) {
                ((TextView) tvNearbyUsersTitle).setText("Searching within " + formatDistance(searchDistanceKm) + "...");
            }

            // Query Firestore for all users
            if (db == null) {
                Log.e(TAG, "Firestore instance is null");
                createMockUsersIfNeeded(true);
                return;
            }

            db.collection("users")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        processUsersQueryResult(queryDocumentSnapshots);

                        // Update the title to show found users
                        if (tvNearbyUsersTitle instanceof TextView) {
                            if (nearbyUsersList.isEmpty()) {
                                ((TextView) tvNearbyUsersTitle).setText("No users found within " + formatDistance(searchDistanceKm));
                            } else {
                                ((TextView) tvNearbyUsersTitle).setText("Found " + nearbyUsersList.size() +
                                        " users within " + formatDistance(searchDistanceKm));
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Error finding nearby users", e);
                        Toast.makeText(requireContext(), "Error finding nearby users", Toast.LENGTH_SHORT).show();
                        createMockUsersIfNeeded(true);

                        // Update title to show error
                        if (tvNearbyUsersTitle instanceof TextView) {
                            ((TextView) tvNearbyUsersTitle).setText("Error finding nearby users");
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

                    // Only show users within the specified distance (use the slider value)
                    if (distanceInKm <= searchDistanceKm) {
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
                Toast.makeText(requireContext(), "No users found within " + formatDistance(searchDistanceKm), Toast.LENGTH_SHORT).show();
            }

            // Center map on user's location again after adding markers
            if (mMap != null && lastKnownLocation != null) {
                LatLng currentUserLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentUserLatLng, DEFAULT_ZOOM));
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

                    // Calculate a random distance within the specified search range
                    // (but ensure it's at least 50m away to avoid markers overlapping)
                    double randomDistanceFactor = Math.random() * 0.95 + 0.05; // 5% to 100% of max distance
                    double targetDistanceKm = randomDistanceFactor * searchDistanceKm;

                    // Ensure minimum distance of 50m for visibility
                    targetDistanceKm = Math.max(targetDistanceKm, 0.05);

                    // Generate a random angle (0-360 degrees)
                    double angle = Math.random() * 2 * Math.PI;

                    // Convert the distance and angle to lat/lng offsets
                    // Approximate conversion: 1 degree latitude = 111km, 1 degree longitude = 111km * cos(latitude)
                    double currentLat = lastKnownLocation.getLatitude();
                    double latOffset = targetDistanceKm / 111.0;
                    double lngOffset = targetDistanceKm / (111.0 * Math.cos(Math.toRadians(currentLat)));

                    // Calculate the new position
                    double mockLat = currentLat + (latOffset * Math.cos(angle));
                    double mockLng = lastKnownLocation.getLongitude() + (lngOffset * Math.sin(angle));

                    // Calculate actual distance
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

                // Center map on user's location again after adding markers
                if (mMap != null && lastKnownLocation != null) {
                    LatLng currentUserLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentUserLatLng, DEFAULT_ZOOM));
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