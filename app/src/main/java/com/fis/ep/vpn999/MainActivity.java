package com.fis.ep.vpn999;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {
    ImageButton Start, Stop, Settings, viewNav1;
    NavigationView navigationView;
    DrawerLayout drawerLayout;
    ActionBarDrawerToggle toggle;
    String ServerName, ServerIcon, Serverip, username, password;
    private int ServerPort;
    TextView CountryName;
    static TextView Connection_status;
    //CircleImageView CountryImage;
    LottieAnimationView lottieAnimationView;
    //ServerInfo serverInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_thin);

        // Initialize views
        CountryName = findViewById(R.id.CountryName);
        CountryImage = findViewById(R.id.CountryImage);
        Connection_status = findViewById(R.id.txtStatus);
        navigationView = findViewById(R.id.nov_view);
        drawerLayout = findViewById(R.id.drower_Layout);

        // Get intent data
        ServerName = getIntent().getStringExtra("name");
        ServerIcon = getIntent().getStringExtra("flag");
        Serverip = getIntent().getStringExtra("ip");
        username = getIntent().getStringExtra("username");
        password = getIntent().getStringExtra("password");
        ServerPort = getIntent().getIntExtra("port", 0);

        // Set server info
        if (ServerName != null) {
            CountryName.setText(ServerName);
        } else {
            CountryName.setText("CountryName");
        }

        Glide.with(this).load(ServerIcon).into(CountryImage);

        // Initialize buttons and animations
        Settings = findViewById(R.id.Settings);
        lottieAnimationView = findViewById(R.id.elotti);
        Stop = findViewById(R.id.StopVpn);
        viewNavi = findViewById(R.id.viewNavi);
        Start = findViewById(R.id.startVpn);

        // Button click listeners
        Start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                lottieAnimationView.setVisibility(View.VISIBLE);
                Start.setVisibility(View.GONE);
                lottieAnimationView.playAnimation();
            }
        });

        viewNav1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (drawerLayout.isDrawerVisible(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            }
        });

        Settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Settings click implementation
            }
        });
    }

    private void establishedVpnConnection() {
        // VPN connection implementation
    }

    private BroadcastReceiver vpnConnectedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MyVpnService vpnService = MyVpnService.instance;
            if (vpnService != null && vpnService.getVpnInterface() != null) {
                Connection_status.setText("Connected");
            } else {
                Connection_status.setText("Disconnect");
            }
        }
    };
}