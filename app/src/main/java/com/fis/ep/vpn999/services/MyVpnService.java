package com.fis.ep.vpn999.services;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.fis.ep.vpn999.MainActivity;
import com.fis.ep.vpn999.R;

import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyVpnService extends VpnService {
    private static final String TAG = "MyVpnService";

    public static final String ACTION_VPN_CONNECTED = "com.fis.ep.vpn999.services.VPN_CONNECTED";
    public static final String ACTION_VPN_DISCONNECTED = "com.fis.ep.vpn999.services.VPN_DISCONNECTED";
    public static final String ACTION_COMMAND_DISCONNECT = "com.fis.ep.vpn999.services.COMMAND_DISCONNECT";

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_CHANNEL_ID = "VpnServiceChannel";

    private Tunnel wireGuardTunnel;
    private Backend wireGuardBackend;
    private Config wireGuardConfig;

    private String wgQuickConfigContent;
    private Thread vpnWorkerThread;
    public static MyVpnService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MyVpnService created");
        instance = this;
        try {
            wireGuardBackend = new GoBackend(this);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize GoBackend", e);
            onVpnConnectionFailure("Failed to initialize VPN backend.");
            stopSelf();
            return;
        }
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel serviceChannel = new android.app.NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "VPN Service Channel",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
            );
            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void startVpnForegroundService(String statusMessage) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(statusMessage)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentIntent(pendingIntent);

        startForeground(NOTIFICATION_ID, notificationBuilder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received, action: " + (intent != null ? intent.getAction() : "null intent"));
        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_COMMAND_DISCONNECT.equals(action)) {
                Log.d(TAG, "Received ACTION_COMMAND_DISCONNECT. Stopping VPN connection.");
                stopVpnConnection();
                return START_NOT_STICKY;
            }

            if (wgQuickConfigContent == null && action == null) {
                wgQuickConfigContent = intent.getStringExtra("wgQuickConfigContent");
            } else if (intent.hasExtra("wgQuickConfigContent")) {
                wgQuickConfigContent = intent.getStringExtra("wgQuickConfigContent");
            }

            if (wgQuickConfigContent != null && !wgQuickConfigContent.isEmpty() && !ACTION_COMMAND_DISCONNECT.equals(action) ) {
                if (wireGuardBackend == null) {
                    Log.e(TAG, "WireGuard backend not initialized, cannot start VPN.");
                    onVpnConnectionFailure("VPN backend not initialized.");
                    stopSelf();
                    return START_NOT_STICKY;
                }

                if (isRunning.get() || (vpnWorkerThread != null && vpnWorkerThread.isAlive())) {
                    Log.d(TAG, "VPN already running or thread active, stopping first before restarting.");
                    stopVpnConnection();
                    handler.postDelayed(this::startNewVpnWorkerThread, 500);
                } else {
                    startNewVpnWorkerThread();
                }
                return START_STICKY;
            } else if (!ACTION_COMMAND_DISCONNECT.equals(action)) {
                Log.e(TAG, "WireGuard configuration content is missing or action is not to start!");
            }
        }
        return START_STICKY;
    }

    class MySimpleTunnel implements Tunnel {
        private final String name;
        private Config config;
        private Tunnel.State currentState;

        public MySimpleTunnel(String name, Config initialConfig) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Tunnel name cannot be null or empty");
            }
            this.name = name;
            this.config = initialConfig;
            this.currentState = Tunnel.State.DOWN;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void onStateChange(State newState) {
            Log.d(TAG, "Tunnel " + name + " onStateChange: " + newState);
            this.currentState = newState;
        }

        public void setConfig(Config newConfig) {
            this.config = newConfig;
        }

        public Config getCurrentConfig() {
            return this.config;
        }

        public Tunnel.State getCurrentState() {
            return currentState;
        }

        public void setCurrentState(Tunnel.State state) {
            this.currentState = state;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MySimpleTunnel that = (MySimpleTunnel) o;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    private void startNewVpnWorkerThread() {
        if (vpnWorkerThread != null && vpnWorkerThread.isAlive()) {
            Log.w(TAG, "VPN worker thread still alive, not starting a new one immediately.");
            return;
        }
        Log.d(TAG, "Starting new VPN worker thread.");
        vpnWorkerThread = new Thread(this::runVpnConnection);
        vpnWorkerThread.start();
    }

    private void runVpnConnection() {
        Log.d(TAG, "runVpnConnection started with WireGuard");
        startVpnForegroundService("Connecting...");
        try {
            wireGuardConfig = Config.parse(new ByteArrayInputStream(wgQuickConfigContent.getBytes(StandardCharsets.UTF_8)));
            String tunnelName = "wg0";
            wireGuardTunnel = new MySimpleTunnel(tunnelName, wireGuardConfig);

            Tunnel.State newActualState = wireGuardBackend.setState(
                    wireGuardTunnel,
                    Tunnel.State.UP,
                    wireGuardConfig
            );

            ((MySimpleTunnel) wireGuardTunnel).setCurrentState(newActualState);

            if (newActualState == Tunnel.State.UP) {
                Log.d(TAG, "WireGuard tunnel state set to UP. Tunnel name: " + wireGuardTunnel.getName());
                isRunning.set(true);
                onVpnConnectionSuccess();
                startVpnForegroundService("Connected");

                while (isRunning.get()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "VPN worker thread interrupted, likely for shutdown.");
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Error in VPN run loop: " + e.getMessage(), e);
                        onVpnConnectionFailure("Runtime VPN Error: " + e.getMessage());
                        break;
                    }
                }
            } else {
                throw new Exception("Failed to bring up WireGuard tunnel. Backend returned state: " + newActualState +
                        " for tunnel " + wireGuardTunnel.getName());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error setting up WireGuard connection: " + e.getMessage(), e);
            onVpnConnectionFailure("WireGuard Setup Error: " + e.getMessage());
        } finally {
            Log.d(TAG, "VPN worker thread finishing. isRunning: " + isRunning.get());
            if (isRunning.get()) {
                onVpnConnectionDisconnected();
            }
            isRunning.set(false);

            if (wireGuardTunnel != null && wireGuardBackend != null &&
                    ((MySimpleTunnel) wireGuardTunnel).getCurrentState() == Tunnel.State.UP) {
                try {
                    Log.d(TAG, "Ensuring tunnel " + wireGuardTunnel.getName() + " is set to DOWN in finally block.");
                    Config configForShutdown = ((MySimpleTunnel) wireGuardTunnel).getCurrentConfig();
                    wireGuardBackend.setState(wireGuardTunnel, Tunnel.State.DOWN, configForShutdown);
                    ((MySimpleTunnel) wireGuardTunnel).setCurrentState(Tunnel.State.DOWN);
                } catch (Exception ex) {
                    Log.e(TAG, "Error setting tunnel state to DOWN in finally: " + ex.getMessage());
                }
            }
            stopForeground(true);
            Log.d(TAG, "Foreground service stopped, notification removed.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MyVpnService destroyed");
        stopVpnConnection();
        instance = null;
    }

    private void stopVpnConnection() {
        Log.d(TAG, "Attempting to stop VPN connection for tunnel: " + (wireGuardTunnel != null ? wireGuardTunnel.getName() : "null"));
        isRunning.set(false);

        if (vpnWorkerThread != null && vpnWorkerThread.isAlive() && !Thread.currentThread().equals(vpnWorkerThread)) {
            Log.d(TAG, "Interrupting VPN worker thread.");
            vpnWorkerThread.interrupt();
            try {
                Log.d(TAG, "Waiting for VPN worker thread to join.");
                vpnWorkerThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for VPN worker thread to join.");
                Thread.currentThread().interrupt();
            }
        }
        vpnWorkerThread = null;

        if (wireGuardTunnel != null && wireGuardBackend != null) {
            try {
                Tunnel.State currentTunnelState = ((MySimpleTunnel) wireGuardTunnel).getCurrentState();
                if (currentTunnelState == Tunnel.State.UP) {
                    Log.d(TAG, "Setting tunnel " + wireGuardTunnel.getName() + " to DOWN.");
                    Config configForShutdown = ((MySimpleTunnel) wireGuardTunnel).getCurrentConfig();
                    wireGuardBackend.setState(wireGuardTunnel, Tunnel.State.DOWN, configForShutdown);
                    ((MySimpleTunnel) wireGuardTunnel).setCurrentState(Tunnel.State.DOWN);
                    Log.d(TAG, "Tunnel " + wireGuardTunnel.getName() + " state set to DOWN by stopVpnConnection.");
                } else {
                    Log.d(TAG, "Tunnel " + wireGuardTunnel.getName() + " not in UP state (" + currentTunnelState + "), skipping set DOWN.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting tunnel " + wireGuardTunnel.getName() + " to DOWN: " + e.getMessage());
            }
        }

        onVpnConnectionDisconnected();
        Log.d(TAG, "stopVpnConnection completed.");
    }

    private void onVpnConnectionSuccess() {
        Log.d(TAG, "VPN Connection Success - sending broadcast");
        Intent intent = new Intent(ACTION_VPN_CONNECTED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void onVpnConnectionFailure(String errorMessage) {
        Log.e(TAG, "VPN Connection Failure - sending broadcast: " + errorMessage);
        Intent intent = new Intent(ACTION_VPN_DISCONNECTED);
        intent.putExtra("error", errorMessage);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        if (isRunning.get()) {
            stopVpnConnection();
        }
    }

    private void onVpnConnectionDisconnected() {
        Log.d(TAG, "VPN Connection Disconnected - sending broadcast");
        Intent intent = new Intent(ACTION_VPN_DISCONNECTED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (VpnService.SERVICE_INTERFACE.equals(intent.getAction())) {
            return super.onBind(intent);
        }
        return super.onBind(intent);
    }
}