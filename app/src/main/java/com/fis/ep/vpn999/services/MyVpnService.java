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
    public MyVpnService() {
    }

    private static final String TAG="MyVpnService";
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ParcelFileDescriptor VpnInterface;
    private String ServerIp;
    private int ServerPortNumber;
    private Handler handler = new Handler(Looper.getMainLooper());

    public static final String ACTION_VPN_CONNECTED = "com.fis.ep.vpn999.services";
    public static MyVpnService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance=this;
    }

    public ParcelFileDescriptor getVpnInterface() {
        return VpnInterface;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            //get the server Ip address and port number from the intent
            ServerIp = intent.getStringExtra("vpnIp");
            ServerPortNumber = intent.getStringExtra("vpnPort", 0);
            Thread VpnThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    MyVpnService.this.runVpnConnection();
                }
            });
            VpnThread.start();
        }
        return START_STICKY;
    }

    private void runVpnConnection() {
        try {
            // Estalish the vpn connection
            if (establishedVpnConnection()) {
                readFromVpnInterface();
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Error during Vpn connection: "+ e.getMessage());
        } finally {
            StopVpnConnection();
        }
    }

    private void StopVpnConnection() {

    }

    private boolean establishedVpnConnection() throws IOException {
        if (VpnInterface != null) {
            Builder builder = new Builder();
            builder.addAddress(ServerIp, 32); //32 is the prefix leangth for single ip address
            builder.addRoute("0.0.0.0", 0);

            VpnInterface = builder.setSession(getString(R.string.app_name)).setConfigureIntent(null).establish();

            return VpnInterface != null;
        }
        else {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onVpnConnectionSuccess();
                    Toast.makeText(MyVpnService.this, "Connection Already Established", Toast.LENGTH_SHORT).show();
                }
            });
        }
        return  true;
    }

    //now read from the vpn interface and write to the network
    private  void readFromVpnInterface() throws IOException{
        isRunning.set(true);
        ByteBuffer buffer = ByteBuffer.allocate(32767);
        while (isRunning.get()) {
            try {
                FileInputStream inputStream = new FileInputStream(VpnInterface.getFileDescriptor());
                int length = inputStream.read(buffer.array());
                if (length>0) {
                    String receivedData = new String(buffer.array(), 0, length);
                    //send the received data to the main activity using local braod cast receiver
                    Intent intent = new Intent("received_data_from vpn");
                    intent.putExtra("data", receivedData);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

                    //write the processed data to the network
                    writeToNetwork(buffer, length);
                }
            } catch (Exception e) {
                Log.e(TAG, "error reading data from vpn interface" + e.getMessage());
            }
        }
    }

    private void writeToNetwork(ByteBuffer buffer, int length) {
        String processdata = new String(buffer.array(), 0, length);
        try {
            Socket socket = new Socket(ServerIp, ServerPortNumber);
            OutputStream outputStream = socket.getOutputStream();

            //convert the process data into bytes and write to the server
            byte[] databytes = processdata.getBytes(StandardCharsets.UTF_8);
            outputStream.write(databytes);

            outputStream.close();
            socket.close();
        } catch (Exception e) {
            Log.e(TAG, "error seding data to the server" + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopVpnConnection();

    }

    private void stopVpnConnection() {
        isRunning.set(false);
        if(VpnInterface != null) {
            try {
                VpnInterface.close();
            } catch (Exception e) {
                Log.e(TAG, "Error Closing Vpn Interface" + e.getMessage());
            }
        }
    }

    private void onVpnConnectionSuccess() {
        // send a local broadcast to the main activity to notify user msg
        Intent intent = new Intent(ACTION_VPN_CONNECTED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)    ;j
    }
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}