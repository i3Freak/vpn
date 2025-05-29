package com.fis.ep.vpn999.services;
// In MyVpnService.java

import android.app.PendingIntent; // For notification
import android.content.Intent;
import android.net.VpnService;
import android.os.Build; // For notification channel
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper; //
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast; //

import androidx.core.app.NotificationCompat; // For foreground service notification
import androidx.localbroadcastmanager.content.LocalBroadcastManager; //

import com.fis.ep.vpn999.MainActivity; // For notification intent
import com.fis.ep.vpn999.R; //

// WireGuard specific imports
import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend; // The Go userspace implementation
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;


import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean; //
// ...


public class MyVpnService extends VpnService {
    private static final String TAG = "MyVpnService";

    public static final String ACTION_VPN_CONNECTED = "com.fis.ep.vpn999.services.VPN_CONNECTED";
    public static final String ACTION_VPN_DISCONNECTED = "com.fis.ep.vpn999.services.VPN_DISCONNECTED"; // For broadcasting status

    public static final String ACTION_COMMAND_DISCONNECT = "com.fis.ep.vpn999.services.COMMAND_DISCONNECT";

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ParcelFileDescriptor vpnInterfaceFd = null;
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_CHANNEL_ID = "VpnServiceChannel";


    // WireGuard specific objects
    private Tunnel wireGuardTunnel; // Represents the active tunnel
    private Backend wireGuardBackend;
    private Config wireGuardConfig; // Parsed WireGuard configuration

    private String wgQuickConfigContent; // Raw string content of .conf file
    private Thread vpnWorkerThread; // Thread to run blocking WireGuard operations
    private ParcelFileDescriptor VpnInterface;
    private String ServerIp;
    private int ServerPortNumber;
    public static MyVpnService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MyVpnService created"); //
        try {
            wireGuardBackend = new GoBackend(this); // 'this' is Context
        } catch (Exception e) { // GoBackend constructor can throw an exception if native lib fails to load
            Log.e(TAG, "Failed to initialize GoBackend", e);
            // Handle this critical failure - perhaps notify UI and stop service
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

    // Start Foreground Service with Notification
    private void startVpnForegroundService(String statusMessage) {
        Intent notificationIntent = new Intent(this, MainActivity.class); // Points to your main UI
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(getString(R.string.app_name)) //
                        .setContentText(statusMessage)
                        .setSmallIcon(R.mipmap.ic_launcher) // Replace with your VPN icon
                        .setContentIntent(pendingIntent);

        startForeground(NOTIFICATION_ID, notificationBuilder.build());
    }

    public ParcelFileDescriptor getVpnInterface() {
        return VpnInterface;
    }

    // In MyVpnService.java

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received, action: " + (intent != null ? intent.getAction() : "null intent"));
        if (intent != null) {
            String action = intent.getAction();

            // Check for the disconnect command
            if (ACTION_COMMAND_DISCONNECT.equals(action)) { // Use the newly defined constant
                Log.d(TAG, "Received ACTION_COMMAND_DISCONNECT. Stopping VPN connection.");
                stopVpnConnection(); // This is your service's internal method to stop the VPN
                // stopSelf(); // Consider if the service should stop itself completely after disconnect
                return START_NOT_STICKY; // If stopped, don't restart unless told to
            }

            // Existing logic for starting the VPN with wgQuickConfigContent
            // This should only proceed if it's not a disconnect action
            if (wgQuickConfigContent == null && action == null) { // Or some other logic to distinguish start command
                wgQuickConfigContent = intent.getStringExtra("wgQuickConfigContent");
            } else if (intent.hasExtra("wgQuickConfigContent")) { // If new config is explicitly passed
                wgQuickConfigContent = intent.getStringExtra("wgQuickConfigContent");
            }


            if (wgQuickConfigContent != null && !wgQuickConfigContent.isEmpty() && !ACTION_COMMAND_DISCONNECT.equals(action) ) {
                // Prevent re-processing config if it's already running or if it's a disconnect command
                if (wireGuardBackend == null) {
                    Log.e(TAG, "WireGuard backend not initialized, cannot start VPN.");
                    onVpnConnectionFailure("VPN backend not initialized.");
                    stopSelf();
                    return START_NOT_STICKY;
                }

                if (isRunning.get() || (vpnWorkerThread != null && vpnWorkerThread.isAlive())) {
                    Log.d(TAG, "VPN already running or thread active, stopping first before restarting.");
                    // If you want a new config to take effect, you must properly stop the old one.
                    // The stopVpnConnection() here might be too aggressive if just receiving a new config
                    // while already connected. This part needs careful thought on behavior.
                    // For now, assuming if config is passed, we try to start/restart.
                    stopVpnConnection(); // Ensure previous connection is fully torn down
                    handler.postDelayed(() -> startNewVpnWorkerThread(), 500);
                } else {
                    startNewVpnWorkerThread();
                }
                return START_STICKY; // Or START_REDELIVER_INTENT if you want the last intent redelivered
            } else if (!ACTION_COMMAND_DISCONNECT.equals(action)) {
                Log.e(TAG, "WireGuard configuration content is missing or action is not to start!");
                // onVpnConnectionFailure("Missing WireGuard configuration.");
                // stopSelf(); // Don't stop self if it was just an empty start command without action
                // return START_NOT_STICKY;
            }
        }
        // If intent is null or no specific action handled that returns,
        // START_STICKY means the service will be restarted if killed, but the intent won't be redelivered.
        // This might be okay if you re-establish connection state in onCreate or based on some persisted state.
        return START_STICKY;
    }


    // Inside MyVpnService.java or as a separate file in the same package

    class MySimpleTunnel implements Tunnel {
        private final String name;
        private Config config; // The active configuration for this tunnel instance
        private Tunnel.State currentState; // To store the last known state from the backend

        public MySimpleTunnel(String name, Config initialConfig) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Tunnel name cannot be null or empty");
            }
            this.name = name;
            this.config = initialConfig; // Store the initial config
            this.currentState = Tunnel.State.DOWN; // Default initial state
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void onStateChange(State newState) {

        }

        // Call this to update the config on this Tunnel object if it changes
        public void setConfig(Config newConfig) {
            this.config = newConfig;
        }

        // Call this to get the config that should be used with this tunnel
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
            // 1. Parse the WireGuard configuration string
            // wireGuardConfig is a class member, populated here
            wireGuardConfig = Config.parse(new ByteArrayInputStream(wgQuickConfigContent.getBytes(StandardCharsets.UTF_8)));

            // 2. Create a tunnel name
            String tunnelName = "wg0"; // Or generate from config

            // 3. Instantiate your Tunnel implementation
            // 'wireGuardTunnel' is your class member of type Tunnel.
            // Assign a new instance of your concrete tunnel class.
            wireGuardTunnel = new MySimpleTunnel(tunnelName, wireGuardConfig);

            // 4. Bring the tunnel UP using the GoBackend
            Tunnel.State newActualState = wireGuardBackend.setState(
                    wireGuardTunnel,    // Your non-null Tunnel instance
                    Tunnel.State.UP,    // Desired state
                    wireGuardConfig     // The configuration to apply
            );

            ((MySimpleTunnel) wireGuardTunnel).setCurrentState(newActualState); // Update our object's state

            if (newActualState == Tunnel.State.UP) {
                Log.d(TAG, "WireGuard tunnel state set to UP. Tunnel name: " + wireGuardTunnel.getName());
                isRunning.set(true);
                onVpnConnectionSuccess();
                startVpnForegroundService("Connected");

                while (isRunning.get()) {
                    try {
                        Thread.sleep(1000);
                        // You can add periodic checks for tunnel statistics or other events here if needed
                        // Statistics stats = wireGuardBackend.getStatistics(wireGuardTunnel);
                        // Log.d(TAG, "RX: " + stats.rxBytes() + ", TX: " + stats.txBytes());
                    } catch (InterruptedException e) {
                        Log.d(TAG, "VPN worker thread interrupted, likely for shutdown.");
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) { // Catch exceptions within the loop
                        Log.e(TAG, "Error in VPN run loop: " + e.getMessage(), e);
                        onVpnConnectionFailure("Runtime VPN Error: " + e.getMessage());
                        // isRunning.set(false) will be handled by onVpnConnectionFailure -> stopVpnConnection
                        break;
                    }
                }
            } else {
                throw new Exception("Failed to bring up WireGuard tunnel. Backend returned state: " + newActualState +
                        " for tunnel " + wireGuardTunnel.getName());
            }

        } catch (Exception e) { // Catches exceptions from parsing or initial setState
            Log.e(TAG, "Error setting up WireGuard connection: " + e.getMessage(), e);
            // onVpnConnectionFailure calls stopVpnConnection, which sets isRunning to false.
            onVpnConnectionFailure("WireGuard Setup Error: " + e.getMessage());
        } finally {
            Log.d(TAG, "VPN worker thread finishing. isRunning: " + isRunning.get());
            // If the loop exited but isRunning was not explicitly set to false
            if (isRunning.get()) { // Indicates an abnormal loop exit not caught by specific error handling above
                onVpnConnectionDisconnected(); // Ensure disconnected state is broadcast
            }
            isRunning.set(false); // Final authoritative state

            // Ensure tunnel is brought DOWN if it was successfully created and might be up
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

    private boolean establishVpnConnection() throws IOException {
        if (VpnInterface == null) {
            Log.d(TAG, "Establishing new VPN connection");
            Builder builder = new Builder();
            if (ServerIp != null && !ServerIp.isEmpty()) {
                builder.addAddress(ServerIp, 32);
            } else {
                Log.e(TAG, "Server IP is null or empty, cannot add address.");
                return false;
            }
            builder.addRoute("0.0.0.0", 0);

            VpnInterface = builder.setSession(getString(R.string.app_name))
                    .setConfigureIntent(null)
                    .establish();

            return VpnInterface != null;
        } else {
            Log.d(TAG, "VPN connection already established, skipping establishment.");
            return true;
        }
    }

    private void readFromVpnInterface() throws IOException {
        isRunning.set(true);
        ByteBuffer buffer = ByteBuffer.allocate(32767);
        FileInputStream inputStream = null;

        try {
            inputStream = new FileInputStream(VpnInterface.getFileDescriptor());
            Log.d(TAG, "Reading from VPN interface");
            while (isRunning.get()) {
                buffer.clear();
                int length = inputStream.read(buffer.array());

                if (length > 0) {
                } else if (length == -1) {
                    Log.d(TAG, "End of stream on VPN interface, stopping connection.");
                    break;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading data from VPN interface: " + e.getMessage());
            onVpnConnectionFailure("Error reading data from VPN interface");
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing VPN input stream: " + e.getMessage());
                }
            }
        }
    }

    private void writeToNetwork(ByteBuffer buffer, int length) {
        Log.w(TAG, "writeToNetwork called - this method needs proper VPN implementation.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MyVpnService destroyed");
        stopVpnConnection();
    }


    private void stopVpnConnection() {
        Log.d(TAG, "Attempting to stop VPN connection for tunnel: " + (wireGuardTunnel != null ? wireGuardTunnel.getName() : "null"));

        // Critical: Set isRunning to false first to stop the loop in runVpnConnection
        isRunning.set(false);

        // Interrupt the worker thread if it's alive and different from the current thread
        if (vpnWorkerThread != null && vpnWorkerThread.isAlive() && !Thread.currentThread().equals(vpnWorkerThread)) {
            Log.d(TAG, "Interrupting VPN worker thread.");
            vpnWorkerThread.interrupt();
            try {
                Log.d(TAG, "Waiting for VPN worker thread to join.");
                vpnWorkerThread.join(1000); // Wait a bit for the thread to finish
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for VPN worker thread to join.");
                Thread.currentThread().interrupt();
            }
        }
        vpnWorkerThread = null; // Clear the reference

        if (wireGuardTunnel != null && wireGuardBackend != null) {
            try {
                // Check the locally stored state if available
                Tunnel.State currentTunnelState = (wireGuardTunnel instanceof MySimpleTunnel) ?
                        ((MySimpleTunnel) wireGuardTunnel).getCurrentState() :
                        Tunnel.State.UP; // Assume UP if unknown, to try to shut down

                if (currentTunnelState == Tunnel.State.UP) { // Only try to set DOWN if it might be UP
                    Log.d(TAG, "Setting tunnel " + wireGuardTunnel.getName() + " to DOWN.");
                    // Config can be null when setting state to DOWN.
                    // Using the stored config is also fine.
                    Config configForShutdown = ((MySimpleTunnel) wireGuardTunnel).getCurrentConfig();
                    wireGuardBackend.setState(wireGuardTunnel, Tunnel.State.DOWN, configForShutdown);
                    if (wireGuardTunnel instanceof MySimpleTunnel) {
                        ((MySimpleTunnel) wireGuardTunnel).setCurrentState(Tunnel.State.DOWN);
                    }
                    Log.d(TAG, "Tunnel " + wireGuardTunnel.getName() + " state set to DOWN by stopVpnConnection.");
                } else {
                    Log.d(TAG, "Tunnel " + wireGuardTunnel.getName() + " not in UP state, skipping set DOWN.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting tunnel " + wireGuardTunnel.getName() + " to DOWN: " + e.getMessage());
            }
            // Consider whether wireGuardTunnel itself should be nulled here or managed elsewhere
            // wireGuardTunnel = null;
        }

        // This ParcelFileDescriptor and related methods are for manual VpnService.
        // They are not used by GoBackend, which manages its own TUN interface.
        // You should remove 'VpnInterface', 'establishVpnConnection', 'readFromVpnInterface'.
        if (VpnInterface != null) {
            try {
                VpnInterface.close();
                Log.d(TAG, "Closed manually managed VpnInterface ParcelFileDescriptor.");
            } catch (IOException e) {
                Log.e(TAG, "Error closing manually managed VpnInterface: " + e.getMessage());
            }
            VpnInterface = null;
        }

        onVpnConnectionDisconnected(); // Notify UI/components
        Log.d(TAG, "stopVpnConnection completed.");
        // stopForeground(true) is called by runVpnConnection's finally block when the thread ends.
        // If stopVpnConnection is called from onDestroy and runVpnConnection isn't running,
        // then stopForeground might need to be ensured here too if not already handled.
        // However, the primary foreground management should be tied to runVpnConnection's lifecycle.
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
        stopVpnConnection();
    }

    private void onVpnConnectionDisconnected() {
        Log.d(TAG, "VPN Connection Disconnected - sending broadcast");
        Intent intent = new Intent(ACTION_VPN_DISCONNECTED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}