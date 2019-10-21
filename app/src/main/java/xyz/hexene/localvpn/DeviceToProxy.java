package xyz.hexene.localvpn;

import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;

public class DeviceToProxy implements Runnable {
    private static final String TAG = DeviceToProxy.class.getSimpleName();
    FileChannel vpnInput;


    public DeviceToProxy(FileChannel vpnInput) {
        this.vpnInput=vpnInput;
    }

    @Override
    public void run() {
        Log.i(TAG, "Started");
        while (!Thread.interrupted()) {
            try {
                ByteBuffer deviceToProxyServer = ByteBufferPool.acquire();
                int readBytes = vpnInput.read(deviceToProxyServer);
                if (readBytes > 0) {
                    NetTool.deviceToProxyServer(deviceToProxyServer);
                } else {
                    deviceToProxyServer.clear();
                }
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Log.e(TAG, "服務停止");
            } catch (Exception e) {
                Log.w(TAG, e.toString(), e);
            }
        }
    }

}
