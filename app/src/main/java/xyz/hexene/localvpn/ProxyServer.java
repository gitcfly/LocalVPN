package xyz.hexene.localvpn;

import android.net.VpnService;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Random;
import xyz.hexene.localvpn.Packet.TCPHeader;
import xyz.hexene.localvpn.TCB.TCBStatus;

public class ProxyServer implements Runnable {
    private Selector udpSelector;
    private Selector tcpSelector;
    public FileChannel clientChannel;
    private Random random = new Random();
    private VpnService vpnService;

    public ProxyServer(VpnService vpnService,FileChannel clientChannel,Selector tcpSelector, Selector udpSelector){
        this.vpnService=vpnService;
        this.clientChannel=clientChannel;
        this.tcpSelector=tcpSelector;
        this.udpSelector=udpSelector;
    }


    public void onRecivedClientData(ByteBuffer byteBuffer){
        try{
            Packet packet=new Packet(byteBuffer);
            if(packet.isTCP()){
                TCPHeader tcpHeader=packet.tcpHeader;
                InetAddress destinationAddress = packet.ip4Header.destinationAddress;
                int destinationPort = tcpHeader.destinationPort;
                int sourcePort = tcpHeader.sourcePort;
                String ipAndPort = destinationAddress.getHostAddress() + ":" + destinationPort + ":" + sourcePort;
                packet.swapSourceAndDestination();
                TCB tcb=TCB.getTCB(ipAndPort);
                ByteBuffer responseBuffer = ByteBufferPool.acquire();
                if(tcb==null){
                    SocketChannel remoteChannel = SocketChannel.open();
                    remoteChannel.configureBlocking(false);
                    vpnService.protect(remoteChannel.socket());
                    remoteChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));
                    tcb=new TCB(ipAndPort,random.nextInt(Short.MAX_VALUE),tcpHeader.sequenceNumber, tcpHeader.sequenceNumber, tcpHeader.acknowledgementNumber, remoteChannel, packet);
                    tcb.status=TCBStatus.LISTEN;
                }
                tcb.theirAcknowledgementNum=tcpHeader.acknowledgementNumber;
                tcb.theirSequenceNum=tcpHeader.sequenceNumber;
                tcb.referencePacket=packet;
                if(tcpHeader.isSYN()){
                    packet.updateTCPBuffer(responseBuffer,(byte)(TCPHeader.SYN|TCPHeader.ACK),tcb.mySequenceNum,packet.tcpHeader.sequenceNumber+1,0);
                    sendDataToClient(responseBuffer);
                    tcb.status=TCBStatus.SYN_RECEIVED;
                }else if(tcpHeader.isACK()){
                    if(tcb.status==TCBStatus.SYN_RECEIVED){
                        tcb.status=TCBStatus.ESTABLISHED;
                    }else if(tcb.status==TCBStatus.ESTABLISHED){//连接成功.可以传输数据
                        System.out.println("client确认收到数据包");
                        //todo 更新tcb的seq和ack的值,后续发送时更新
                        tcb.mySequenceNum=tcpHeader.acknowledgementNumber;
                    }else if(tcb.status==TCBStatus.CLOSE_WAIT){
                        packet.updateTCPBuffer(responseBuffer,(byte)(TCPHeader.ACK),0,packet.tcpHeader.sequenceNumber+1,0);
                        sendDataToClient(responseBuffer);
                        tcb.status=TCBStatus.LAST_ACK;
                    }
                }else if(tcpHeader.isPSH()){
                    //todo :需要做什么呢
                }else if(tcpHeader.isURG()){
                    //todo :需要怎么做呢
                }else if(tcpHeader.isFIN()){
                    packet.updateTCPBuffer(responseBuffer,(byte)(TCPHeader.ACK),0,packet.tcpHeader.sequenceNumber+1,0);
                    sendDataToClient(responseBuffer);
                    tcb.status=TCBStatus.CLOSE_WAIT;
                }else if(tcpHeader.isRST()){
                    packet.updateTCPBuffer(responseBuffer,(byte)(TCPHeader.ACK),0,packet.tcpHeader.sequenceNumber+1,0);
                    sendDataToClient(responseBuffer);
                    tcb.status=TCBStatus.CLOSE_WAIT;
                }
            }else if(packet.isUDP()){

            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void sendDataToClient(ByteBuffer byteBuffer){
        try{
            clientChannel.write(byteBuffer);
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @Override
    public void run() {
        while (!Thread.interrupted()){
            try {

            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
