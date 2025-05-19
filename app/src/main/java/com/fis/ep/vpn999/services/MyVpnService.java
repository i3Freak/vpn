package com.fis.ep.vpn999.services;

import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.fis.ep.vpn999.R;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class MyVpnService extends VpnService {
    private static final String TAG = "MyVpnService";
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ParcelFileDescriptor VpnInterface;
    private String ServerIp;
    private int ServerPortNumber;
    private Handler handler = new Handler(Looper.getMainLooper());

    public static final String ACTION_VPN_CONNECTED = "com.fis.ep.vpn999.services.VPN_CONNECTED";
    public static final String ACTION_VPN_DISCONNECTED = "com.fis.ep.vpn999.services.VPN_DISCONNECTED";
    public static MyVpnService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "MyVpnService created");
    }

    public ParcelFileDescriptor getVpnInterface() {
        return VpnInterface;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received");
        if (intent != null) {
            ServerIp = intent.getStringExtra("vpnIp");
            ServerPortNumber = intent.getIntExtra("vpnPort", 0);
            Log.d(TAG, "Server IP: " + ServerIp + ", Port: " + ServerPortNumber);

            if (!isRunning.get()) {
                Thread VpnThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        MyVpnService.this.runVpnConnection();
                    }
                });
                VpnThread.start();
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MyVpnService.this, "Connection Already Established", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
        return START_STICKY;
    }

    private void runVpnConnection() {
        Log.d(TAG, "runVpnConnection started");
        try {
            if (establishVpnConnection()) {
                Log.d(TAG, "VPN connection established successfully");
                onVpnConnectionSuccess();
                readFromVpnInterface();
            } else {
                Log.e(TAG, "Failed to establish VPN connection");
                onVpnConnectionFailure("Failed to establish VPN connection");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during Vpn connection: " + e.getMessage());
            onVpnConnectionFailure("Error during VPN connection: " + e.getMessage());
        } finally {
            StopVpnConnection();
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

    private void StopVpnConnection() {
        stopVpnConnection();
    }

    private void stopVpnConnection() {
        Log.d(TAG, "Stopping VPN connection");
        isRunning.set(false);
        if (VpnInterface != null) {
            try {
                VpnInterface.close();
                VpnInterface = null;
                Log.d(TAG, "VPN Interface closed.");
            } catch (IOException e) {
                Log.e(TAG, "Error Closing Vpn Interface: " + e.getMessage());
            }
        }
        onVpnConnectionDisconnected();
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