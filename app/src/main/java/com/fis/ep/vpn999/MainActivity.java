package com.fis.ep.vpn999;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.fis.ep.vpn999.services.MyVpnService;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {
    public static final int VPN_REQUEST_CODE = 1;
    ImageView StartButton, StopButton, SettingsButton, MenuButton;
    NavigationView navigationView;
    DrawerLayout drawerLayout;
    ActionBarDrawerToggle toggle;
    String ServerName, ServerIcon, Serverip, username, password;
    private int ServerPort;
    TextView CountryNameTextView;
    static TextView ConnectionStatusTextView;
    ImageView CountryImageView;
    LottieAnimationView lottieAnimationView;
    private static final String TAG = "MainActivity";

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
        navigationView = findViewById(R.id.nav_view);
        drawerLayout = findViewById(R.id.drawer_layout);
        SettingsButton = findViewById(R.id.ic_setting);
        lottieAnimationView = findViewById(R.id.elotti);
        StopButton = findViewById(R.id.StopVpn);
        MenuButton = findViewById(R.id.ic_more);
        StartButton = findViewById(R.id.startVpn);
        StopButton.setVisibility(View.GONE);
        lottieAnimationView.setVisibility(View.GONE);
        ServerName = getIntent().getStringExtra("name");
        ServerIcon = getIntent().getStringExtra("flag");
        Serverip = getIntent().getStringExtra("ip");
        username = getIntent().getStringExtra("username");
        password = getIntent().getStringExtra("password");
        ServerPort = getIntent().getIntExtra("port", 0);
        if (ServerName != null && !ServerName.isEmpty()) {
            CountryNameTextView.setText(ServerName);
        } else {
            CountryNameTextView.setText("Country Name");
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
                lottieAnimationView.setVisibility(View.VISIBLE);
                StartButton.setVisibility(View.GONE);
                lottieAnimationView.playAnimation();
                establishedVpnConnection();
            }
        });
        StopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Stop button clicked");
                stopVpnConnection();
            }
        });
        MenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Menu button clicked");
                if (drawerLayout.isDrawerVisible(GravityCompat.START)) {
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
        LocalBroadcastManager.getInstance(this).registerReceiver(VpnStatusReceiver,
                new IntentFilter(MyVpnService.ACTION_VPN_CONNECTED));
        LocalBroadcastManager.getInstance(this).registerReceiver(VpnStatusReceiver,
                new IntentFilter(MyVpnService.ACTION_VPN_DISCONNECTED));
    }

    private void establishedVpnConnection() {
        Intent VpnIntent = VpnService.prepare((MainActivity.this));
        if (VpnIntent != null) {
            startActivityForResult(VpnIntent, VPN_REQUEST_CODE);
        } else {
            StartVpnServiceWithIp();
        }
    }

    private void StartVpnServiceWithIp() {
        Intent vpnIntent = new Intent(MainActivity.this, MyVpnService.class);
        vpnIntent.putExtra("vpnIp", Serverip);
        vpnIntent.putExtra("vpnPort", ServerPort);
        startService(vpnIntent);
        Log.d(TAG, "Starting VPN service");
    }

    private void stopVpnConnection() {
        Intent vpnIntent = new Intent(MainActivity.this, MyVpnService.class);
        stopService(vpnIntent);
        Log.d(TAG, "Stopping VPN service");
        updateConnectionStatus(false);
    }

    private BroadcastReceiver VpnStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MyVpnService.ACTION_VPN_CONNECTED.equals(action)) {
                Log.d(TAG, "VPN Connected broadcast received");
                updateConnectionStatus(true);
            } else if (MyVpnService.ACTION_VPN_DISCONNECTED.equals(action)) {
                Log.d(TAG, "VPN Disconnected broadcast received");
                String error = intent.getStringExtra("error");
                if (error != null) {
                    Log.e(TAG, "VPN connection failed: " + error);
                    Toast.makeText(context, "VPN connection failed: " + error, Toast.LENGTH_LONG).show();
                }
                updateConnectionStatus(false);
            }
        }
    };

    private void updateConnectionStatus(boolean isConnected) {
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
                Log.d(TAG, "VPN permission granted, starting service");
                StartVpnServiceWithIp();
            } else {
                Log.d(TAG, "VPN permission denied");
                Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show();
                updateConnectionStatus(false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(VpnStatusReceiver);
        Log.d(TAG, "MainActivity destroyed, receiver unregistered");
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerVisible(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}