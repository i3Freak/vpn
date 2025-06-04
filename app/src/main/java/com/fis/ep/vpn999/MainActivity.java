package com.fis.ep.vpn999;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler; // Added for timer
import android.os.Looper;  // Added for timer
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.fis.ep.vpn999.services.MyVpnService;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    public static final int VPN_REQUEST_CODE = 1;
    private static final String TAG = "MainActivity";

    ImageView StartButton, StopButton, SettingsButton, MenuButton;
    DrawerLayout drawerLayout;

    String ServerName, ServerIcon, Serverip;
    private int ServerPort;

    TextView CountryNameTextView;
    TextView ConnectionStatusTextView;
    ImageView CountryImageView;
    LottieAnimationView lottieAnimationView;

    // Timer related variables
    private TextView timerTextView;
    private Handler timerHandler;
    private Runnable timerRunnable;
    private long startTime = 0L;
    private boolean isTimerRunning = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        CountryNameTextView = findViewById(R.id.cn_location);
        CountryImageView = findViewById(R.id.CountryImage);
        ConnectionStatusTextView = findViewById(R.id.cn_status);
        drawerLayout = findViewById(R.id.drawer_layout);
        SettingsButton = findViewById(R.id.ic_setting);
        lottieAnimationView = findViewById(R.id.elotti);
        StopButton = findViewById(R.id.StopVpn);
        MenuButton = findViewById(R.id.ic_more);
        StartButton = findViewById(R.id.startVpn);

        // Initialize timer TextView
        timerTextView = findViewById(R.id.cn_time);
        timerTextView.setText("00:00:00"); // Initial display

        // Initialize timer handler
        timerHandler = new Handler(Looper.getMainLooper());

        StopButton.setVisibility(View.GONE);
        lottieAnimationView.setVisibility(View.GONE);
        ConnectionStatusTextView.setText("Disconnected");

        ServerName = getIntent().getStringExtra("name");
        ServerIcon = getIntent().getStringExtra("flag");
        String potentialServerIp = getIntent().getStringExtra("ip");
        if (potentialServerIp != null && !potentialServerIp.isEmpty()) {
            Serverip = potentialServerIp;
        } else {
            Serverip = "wireguard.zapto.org";
        }
        ServerPort = getIntent().getIntExtra("port", 51820);

        if (ServerName != null && !ServerName.isEmpty()) {
            CountryNameTextView.setText(ServerName);
        } else {
            CountryNameTextView.setText("Select Server");
        }
        if (ServerIcon != null && !ServerIcon.isEmpty()) {
            Glide.with(this).load(ServerIcon).into(CountryImageView);
        } else {
            CountryImageView.setImageResource(R.drawable.ic_flag_canada);
        }

        StartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Start button clicked");
                establishedVpnConnection();
            }
        });

        StopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Stop button clicked");
                stopVpnServiceAction();
            }
        });

        MenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Menu button clicked");
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
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
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(MyVpnService.ACTION_VPN_CONNECTED);
        filter.addAction(MyVpnService.ACTION_VPN_DISCONNECTED);
        LocalBroadcastManager.getInstance(this).registerReceiver(vpnStatusReceiver, filter);

        // Initialize the timer runnable
        initializeTimerRunnable();
    }

    private void initializeTimerRunnable() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isTimerRunning) return;

                long millis = System.currentTimeMillis() - startTime;
                int seconds = (int) (millis / 1000);
                int minutes = seconds / 60;
                int hours = minutes / 60;
                seconds = seconds % 60;
                minutes = minutes % 60;

                timerTextView.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
                timerHandler.postDelayed(this, 1000); // Update every second
            }
        };
    }

    private void startTimer() {
        if (!isTimerRunning) {
            startTime = System.currentTimeMillis();
            isTimerRunning = true;
            timerHandler.removeCallbacks(timerRunnable); // Remove any existing callbacks
            timerHandler.post(timerRunnable);
            Log.d(TAG, "Timer started");
        }
    }

    private void stopTimer() {
        if (isTimerRunning) {
            isTimerRunning = false;
            timerHandler.removeCallbacks(timerRunnable);
            timerTextView.setText("00:00:00");
            Log.d(TAG, "Timer stopped");
        }
    }


    private void establishedVpnConnection() {
        Log.d(TAG, "Attempting to establish VPN connection.");
        Intent vpnPermissionIntent = VpnService.prepare(MainActivity.this);
        if (vpnPermissionIntent != null) {
            Log.d(TAG, "VPN permission required. Launching permission intent.");
            startActivityForResult(vpnPermissionIntent, VPN_REQUEST_CODE);
        } else {
            Log.d(TAG, "VPN permission already granted. Starting service.");
            startVpnServiceWithWgConfig();
        }
    }

    private void startVpnServiceWithWgConfig() {
        String clientPrivateKey = "8K32hn1g1BNTZhk8gEFrPjYqHbwJjia+Gya9hwGbZGU=";
        String clientVpnAddress = "10.0.0.2/24";
        String serverPublicKey = "NNtwN+LCs+mCU+GUwKpIJfx5BToCGL6424DyGwHNuH8=";
        String dnsServers = "1.1.1.1, 8.8.8.8";

        if (Serverip == null || Serverip.isEmpty()) {
            Toast.makeText(this, "Server address (hostname) not set!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Server address (hostname) missing for WireGuard config.");
            updateConnectionStatus(false);
            return;
        }
        if (ServerPort == 0) {
            Toast.makeText(this, "Server Port not set!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Server Port missing for WireGuard config.");
            updateConnectionStatus(false);
            return;
        }

        String wgQuickConfig = String.format(
                "[Interface]\n" +
                        "PrivateKey = %s\n" +
                        "Address = %s\n" +
                        "DNS = %s\n" +
                        "\n" +
                        "[Peer]\n" +
                        "PublicKey = %s\n" +
                        "AllowedIPs = 0.0.0.0/0, ::/0\n" +
                        "Endpoint = %s:%d\n" +
                        "PersistentKeepalive = 25\n",
                clientPrivateKey,
                clientVpnAddress,
                dnsServers,
                serverPublicKey,
                Serverip,
                ServerPort
        );

        Log.d(TAG, "WireGuard Config to be used:\n" + wgQuickConfig);

        if (clientPrivateKey.equals("YOUR_ANDROID_CLIENT_PRIVATE_KEY_HERE")) {
            Toast.makeText(this, "CRITICAL: Replace placeholder client private key!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Placeholder client private key found. VPN will not work.");
            updateConnectionStatus(false);
            return;
        }

        ConnectionStatusTextView.setText("Connecting...");
        lottieAnimationView.setVisibility(View.VISIBLE);
        StartButton.setVisibility(View.GONE);
        StopButton.setVisibility(View.GONE);
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

    private void stopVpnServiceAction() {
        Log.d(TAG, "Sending disconnect action to MyVpnService.");
        Intent vpnIntent = new Intent(MainActivity.this, MyVpnService.class);
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
                startTimer(); // Start the timer here
            } else if (MyVpnService.ACTION_VPN_DISCONNECTED.equals(action)) {
                Log.d(TAG, "VPN Disconnected broadcast received");
                String error = intent.getStringExtra("error");
                if (error != null) {
                    Log.e(TAG, "VPN connection failed/disconnected: " + error);
                    Toast.makeText(context, "VPN Error: " + error, Toast.LENGTH_LONG).show();
                }
                updateConnectionStatus(false);
                stopTimer(); // Stop the timer here
            }
        }
    };

    private void updateConnectionStatus(boolean isConnected) {
        Log.d(TAG, "Updating connection status UI. Connected: " + isConnected);
        if (isConnected) {
            ConnectionStatusTextView.setText("Connected");
            lottieAnimationView.pauseAnimation();
            lottieAnimationView.setVisibility(View.GONE);
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
                startVpnServiceWithWgConfig();
            } else {
                Log.d(TAG, "VPN permission denied by user.");
                Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show();
                updateConnectionStatus(false);
                stopTimer(); // Also stop timer if permission denied after trying to connect
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(vpnStatusReceiver);
        stopTimer();
        Log.d(TAG, "MainActivity destroyed, VpnStatusReceiver unregistered. Timer stopped.");
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