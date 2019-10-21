package xyz.hexene.localvpn;

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class NetTool {

    public static String TAG ="NetTool";

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
        try{
            Packet packet=new Packet(buffer);
            if(packet.isTCP()){
                sendTcpToNet(packet);
            }else if(packet.isUDP()){
                sendUdpToNet(packet);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void sendTcpToNet(Packet packet){
        if(packet.tcpHeader.isSYN()){

        }else if(packet.tcpHeader.isACK()){

        }else if(packet.tcpHeader.isSYN()){

        }
    }

    public static void sendUdpToNet(Packet packet){

    }

    public static void deviceToProxyServer(ByteBuffer buffer){
        proxyServerToNet(buffer);
    }

    public static void netToProxyServer(ByteBuffer buffer){

    }

    public static void closeResources(Closeable... resources) {
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (IOException e) {
                Log.e(TAG,e.getMessage(),e);
            }
        }
    }
}
