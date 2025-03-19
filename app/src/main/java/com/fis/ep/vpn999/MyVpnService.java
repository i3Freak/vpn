package com.fis.ep.vpn999;

import static android.app.Service.START_STICKY;

import android.app.Service;

public class MyVpnService : VpnService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        val builder = Builder()
        builder.addAddress("10.0.0.2", 24) // Địa chỉ IP VPN
        builder.addRoute("0.0.0.0", 0) // Định tuyến toàn bộ lưu lượng qua VPN
        val interface = builder.establish()
    }
}