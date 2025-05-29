package com.fis.ep.vpn999;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build; // Import Build for API version check
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity; // Use AppCompatActivity consistently
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.fis.ep.vpn999.services.MyVpnService;
import com.google.android.material.navigation.NavigationView; // Keep if used, though not in findViewById

public class MainActivity extends AppCompatActivity {
    public static final int VPN_REQUEST_CODE = 1; // Ensure this is a unique request code
    private static final String TAG = "MainActivity";

    ImageView StartButton, StopButton, SettingsButton, MenuButton;
    // NavigationView navigationView; // Was commented out in your findViewById
    DrawerLayout drawerLayout;


    String ServerName, ServerIcon, Serverip, username, password; // Note: username/password not directly used by WireGuard .conf
    private int ServerPort;

    TextView CountryNameTextView;
    TextView ConnectionStatusTextView; // Made non-static, UI update through method
    ImageView CountryImageView;
    LottieAnimationView lottieAnimationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fullscreen setup
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        CountryNameTextView = findViewById(R.id.cn_location);
        CountryImageView = findViewById(R.id.CountryImage);
        ConnectionStatusTextView = findViewById(R.id.cn_status);
        drawerLayout = findViewById(R.id.drawer_layout);
        SettingsButton = findViewById(R.id.ic_setting);
        lottieAnimationView = findViewById(R.id.elotti);
        StopButton = findViewById(R.id.StopVpn);
        MenuButton = findViewById(R.id.ic_more);
        StartButton = findViewById(R.id.startVpn);

        // Initial UI State
        StopButton.setVisibility(View.GONE);
        lottieAnimationView.setVisibility(View.GONE);
        ConnectionStatusTextView.setText("Disconnected"); // Initial status

        // Get data from Intent (presumably from a server selection activity)
        ServerName = getIntent().getStringExtra("name");
        ServerIcon = getIntent().getStringExtra("flag");
        String potentialServerIp = getIntent().getStringExtra("ip");
        if (potentialServerIp != null && !potentialServerIp.isEmpty()) {
            Serverip = potentialServerIp;
        } else {
            Serverip = "wireguard.zapto.org"; // Assign default if "ip" extra is not found or is empty
        }
        username = getIntent().getStringExtra("username"); // How is this used?
        password = getIntent().getStringExtra("password"); // How is this used?
        ServerPort = getIntent().getIntExtra("port", 51820); // This is the WireGuard server's port

        // Update UI with server info
        if (ServerName != null && !ServerName.isEmpty()) {
            CountryNameTextView.setText(ServerName);
        } else {
            CountryNameTextView.setText("Select Server"); // Default text
        }
        if (ServerIcon != null && !ServerIcon.isEmpty()) {
            Glide.with(this).load(ServerIcon).into(CountryImageView);
        } else {
            // Consider a default local flag image
            CountryImageView.setImageResource(R.drawable.ic_flag_canada); // Example default
        }

        StartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Start button clicked");
                // It's better to show "Connecting..." status after permission is granted
                // and just before starting the service.
                // For now, we'll initiate the permission check.
                establishedVpnConnection();
            }
        });

        StopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Stop button clicked");
                stopVpnServiceAction(); // Corrected stop method
            }
        });

        MenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Menu button clicked");
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) { // Use isDrawerOpen
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            }
        });

        SettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Settings button clicked");
                Toast.makeText(MainActivity.this, "Settings clicked", Toast.LENGTH_SHORT).show();
                // Implement settings functionality
            }
        });

        // Register BroadcastReceiver for VPN status updates
        IntentFilter filter = new IntentFilter();
        filter.addAction(MyVpnService.ACTION_VPN_CONNECTED);
        filter.addAction(MyVpnService.ACTION_VPN_DISCONNECTED);
        LocalBroadcastManager.getInstance(this).registerReceiver(vpnStatusReceiver, filter);
    }

    private void establishedVpnConnection() {
        Log.d(TAG, "Attempting to establish VPN connection.");
        // ConnectionStatusTextView.setText("Connecting..."); // Update status
        // lottieAnimationView.setVisibility(View.VISIBLE);
        // StartButton.setVisibility(View.GONE);
        // lottieAnimationView.playAnimation(); // Start animation

        Intent vpnPermissionIntent = VpnService.prepare(MainActivity.this);
        if (vpnPermissionIntent != null) {
            // Permission is required
            Log.d(TAG, "VPN permission required. Launching permission intent.");
            startActivityForResult(vpnPermissionIntent, VPN_REQUEST_CODE);
        } else {
            // Permission already granted
            Log.d(TAG, "VPN permission already granted. Starting service.");
            startVpnServiceWithWgConfig();
        }
    }

    /**
     * Constructs the WireGuard configuration string and starts MyVpnService.
     */
    private void startVpnServiceWithWgConfig() {
        // --- YOU MUST PROVIDE THE CLIENT'S PRIVATE KEY ---
        // This key is unique to THIS Android client and is NOT from the server image.
        // Generate it (see instructions above) and add its corresponding public key to your server config.
        String clientPrivateKey = "8K32hn1g1BNTZhk8gEFrPjYqHbwJjia+Gya9hwGbZGU="; // <<<<<< IMPORTANT: REPLACE THIS

        // IP address for this Android client INSIDE the VPN tunnel.
        // It should be in the same network as your server's VPN IP (10.0.0.1/24)
        String clientVpnAddress = "10.0.0.2/24"; // Example: Second IP in the 10.0.0.x network

        // Server's Public Key from your WireGuard server configuration image
        String serverPublicKey = "NNtwN+LCs+mCU+GUwKpIJfx5BToCGL6424DyGwHNuH8=";

        // DNS servers to be used by the VPN client. These are common public DNS.
        // You can change these if your VPN server provides specific DNS (e.g., "10.0.0.1")
        String dnsServers = "1.1.1.1, 8.8.8.8";

        // Serverip should be your No-IP hostname (e.g., "yourname.ddns.net")
        // ServerPort should be 51820
        // These are obtained from getIntent().getStringExtra("ip") and getIntExtra("port", 51820)

        if (Serverip == null || Serverip.isEmpty()) {
            Toast.makeText(this, "Server address (hostname) not set!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Server address (hostname) missing for WireGuard config.");
            updateConnectionStatus(false);
            return;
        }
        if (ServerPort == 0) { // Should default to 51820 if not passed, but good to check
            Toast.makeText(this, "Server Port not set!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Server Port missing for WireGuard config.");
            updateConnectionStatus(false);
            return;
        }

        // Construct the WireGuard configuration string
        String wgQuickConfig = String.format(
                "[Interface]\n" +
                        "PrivateKey = %s\n" +
                        "Address = %s\n" +
                        "DNS = %s\n" +
                        "\n" +
                        "[Peer]\n" +
                        "PublicKey = %s\n" +
                        "AllowedIPs = 0.0.0.0/0, ::/0\n" + // Route all IPv4 and IPv6 traffic through VPN
                        "Endpoint = %s:%d\n" +
                        "PersistentKeepalive = 25\n", // Optional, good for mobile networks
                clientPrivateKey,
                clientVpnAddress,
                dnsServers,
                serverPublicKey,
                Serverip,    // This should be your No-IP hostname (e.g., yourname.ddns.net)
                ServerPort   // This should be 51820
        );

        Log.d(TAG, "WireGuard Config to be used:\n" + wgQuickConfig);

        // Basic check for placeholder - IMPROVE THIS FOR PRODUCTION
        if (clientPrivateKey.equals("YOUR_ANDROID_CLIENT_PRIVATE_KEY_HERE")) {
            Toast.makeText(this, "CRITICAL: Replace placeholder client private key!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Placeholder client private key found. VPN will not work.");
            updateConnectionStatus(false);
            return;
        }

        // Update UI to "Connecting" state
        ConnectionStatusTextView.setText("Connecting...");
        lottieAnimationView.setVisibility(View.VISIBLE);
        StartButton.setVisibility(View.GONE);
        StopButton.setVisibility(View.GONE); // Hide stop button while connecting initially
        lottieAnimationView.playAnimation();

        Intent vpnIntent = new Intent(MainActivity.this, MyVpnService.class);
        vpnIntent.putExtra("wgQuickConfigContent", wgQuickConfig);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(vpnIntent);
        } else {
            startService(vpnIntent);
        }
        Log.d(TAG, "Starting MyVpnService with WireGuard config.");
    }

    /**
     * Sends an ACTION_DISCONNECT intent to MyVpnService.
     */
    // In MainActivity.java

    private void stopVpnServiceAction() {
        Log.d(TAG, "Sending disconnect action to MyVpnService.");
        Intent vpnIntent = new Intent(MainActivity.this, MyVpnService.class);
        // Use the correctly defined public static final String from MyVpnService
        vpnIntent.setAction(MyVpnService.ACTION_COMMAND_DISCONNECT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(vpnIntent);
        } else {
            startService(vpnIntent);
        }
        ConnectionStatusTextView.setText("Disconnecting...");
    }


    private BroadcastReceiver vpnStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Received broadcast: " + action);
            if (MyVpnService.ACTION_VPN_CONNECTED.equals(action)) {
                Log.d(TAG, "VPN Connected broadcast received");
                updateConnectionStatus(true);
            } else if (MyVpnService.ACTION_VPN_DISCONNECTED.equals(action)) {
                Log.d(TAG, "VPN Disconnected broadcast received");
                String error = intent.getStringExtra("error");
                if (error != null) {
                    Log.e(TAG, "VPN connection failed/disconnected: " + error);
                    Toast.makeText(context, "VPN Error: " + error, Toast.LENGTH_LONG).show();
                }
                updateConnectionStatus(false);
            }
        }
    };

    private void updateConnectionStatus(boolean isConnected) {
        Log.d(TAG, "Updating connection status UI. Connected: " + isConnected);
        if (isConnected) {
            ConnectionStatusTextView.setText("Connected");
            lottieAnimationView.pauseAnimation(); // Or set a success animation
            lottieAnimationView.setVisibility(View.GONE); // Hide after connecting
            StartButton.setVisibility(View.GONE);
            StopButton.setVisibility(View.VISIBLE);
        } else {
            ConnectionStatusTextView.setText("Disconnected");
            lottieAnimationView.cancelAnimation();
            lottieAnimationView.setVisibility(View.GONE);
            StartButton.setVisibility(View.VISIBLE);
            StopButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "VPN permission granted, proceeding to start service with config.");
                startVpnServiceWithWgConfig(); // Call the method that sends the WG config
            } else {
                Log.d(TAG, "VPN permission denied by user.");
                Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show();
                updateConnectionStatus(false); // Update UI to disconnected state
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(vpnStatusReceiver);
        Log.d(TAG, "MainActivity destroyed, VpnStatusReceiver unregistered.");
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}