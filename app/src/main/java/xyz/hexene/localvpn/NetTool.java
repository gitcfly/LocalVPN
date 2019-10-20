package xyz.hexene.localvpn;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class NetTool {


    public static FileChannel vpnOut;

    public static void initTool(FileChannel mvpnOut) {
        synchronized (vpnOut){
            if(vpnOut==null){
                vpnOut=mvpnOut;
            }
        }
    }

    public static void proxyServerToDevice(ByteBuffer buffer){
        try{
            buffer.flip();
            vpnOut.write(buffer);
            buffer.clear();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void proxyServerToNet(ByteBuffer buffer){

    }

    public static void deviceToProxyServer(ByteBuffer buffer){

    }

    public static void netToProxyServer(ByteBuffer buffer){

    }
}
