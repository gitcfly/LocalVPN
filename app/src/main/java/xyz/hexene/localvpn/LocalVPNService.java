/*
 ** Copyright 2015, Mohamed Naufal
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

package xyz.hexene.localvpn;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalVPNService extends VpnService {
    private static final String TAG = LocalVPNService.class.getSimpleName();
    private static final String VPN_ADDRESS = "192.168.0.1"; // Only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything
    public static final String BROADCAST_VPN_STATE = "xyz.hexene.localvpn.VPN_STATE";

    private static boolean isRunning = false;
    private ParcelFileDescriptor vpnInterface = null;
    private PendingIntent pendingIntent;
    public static ExecutorService executorService;
    private Selector udpSelector;
    private Selector tcpSelector;
    FileChannel vpnInput;
    FileChannel vpnOutput;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        setupVPN();
    }

    private void setupVPN() {
        if (vpnInterface == null) {
            Builder builder = new Builder();
            builder.addAddress(VPN_ADDRESS, 24);
            builder.addRoute(VPN_ROUTE, 0);
            vpnInterface = builder.setSession(getString(R.string.app_name)).setConfigureIntent(pendingIntent).establish();
            vpnInput = new FileInputStream(vpnInterface.getFileDescriptor()).getChannel();
            vpnOutput = new FileOutputStream(vpnInterface.getFileDescriptor()).getChannel();
        }
        try {
            udpSelector = Selector.open();
            tcpSelector = Selector.open();
            executorService = Executors.newFixedThreadPool(5);
            DeviceToProxy deviceToProxy=new DeviceToProxy(vpnInput);
            executorService.submit(deviceToProxy);
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BROADCAST_VPN_STATE).putExtra("running", true));
            Log.i(TAG, "Started");
        } catch (IOException e) {
            // TODO: Here and elsewhere, we should explicitly notify the user of any errors
            // and suggest that they stop the service, since we can't do it ourselves
            Log.e(TAG, "Error starting service", e);
            cleanup();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        executorService.shutdownNow();
        cleanup();
        Log.i(TAG, "Stopped");
    }

    private void cleanup() {
        ByteBufferPool.clear();
        NetTool.closeResources(udpSelector, tcpSelector, vpnInterface);
    }
}
