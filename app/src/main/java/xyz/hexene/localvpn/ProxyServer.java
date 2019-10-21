package xyz.hexene.localvpn;

import android.net.VpnService;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

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
                packet.backingBuffer=null;
                TCB tcb=TCB.getTCB(ipAndPort);
                ByteBuffer responseBuffer = ByteBufferPool.acquire();
                if(tcb==null){
                    SocketChannel remoteChannel = SocketChannel.open();
                    remoteChannel.configureBlocking(false);
                    vpnService.protect(remoteChannel.socket());
                    remoteChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));
                    tcb=new TCB(ipAndPort,random.nextInt(Short.MAX_VALUE),tcpHeader.sequenceNumber, tcpHeader.sequenceNumber, tcpHeader.acknowledgementNumber, remoteChannel, packet);
                    tcb.status=TCBStatus.LISTEN;
                    TCB.putTCB(ipAndPort,tcb);
                }
                synchronized (tcb){
                    tcb.theirAcknowledgementNum=tcpHeader.acknowledgementNumber;
                    tcb.theirSequenceNum=tcpHeader.sequenceNumber;
                    tcb.referencePacket=packet;
                    if(tcpHeader.isSYN()){
                        packet.updateTCPBuffer(responseBuffer,(byte)(TCPHeader.SYN|TCPHeader.ACK),tcb.mySequenceNum,packet.tcpHeader.sequenceNumber+1,0);
                        sendDataToClient(packet.backingBuffer);
                        tcb.status=TCBStatus.SYN_RECEIVED;
                    }else if(tcpHeader.isACK()){
                        if(tcb.status==TCBStatus.SYN_RECEIVED){
                            if(tcb.channel.finishConnect()){
                                tcb.status=TCBStatus.ESTABLISHED;
                                tcb.selectionKey=tcb.channel.register(tcpSelector, SelectionKey.OP_READ,tcb);
                            }else {
                                packet.updateTCPBuffer(responseBuffer,(byte)TCPHeader.RST,0,0,0);
                                sendDataToClient(packet.backingBuffer);
                                TCB.closeTCB(tcb);
                            }
                        }else if(tcb.status==TCBStatus.ESTABLISHED){//连接成功.可以传输数据
                            //todo 更新tcb的seq和ack的值,后续发送时更新
                            tcb.mySequenceNum=tcpHeader.acknowledgementNumber;
                        }else if(tcb.status==TCBStatus.LAST_ACK){//关闭连接
                            TCB.closeTCB(tcb);
                        }
                    }else if(tcpHeader.isPSH()){
                        //todo :需要做什么呢
                    }else if(tcpHeader.isURG()){
                        //todo :需要怎么做呢
                    }else if(tcpHeader.isFIN()){
                        packet.updateTCPBuffer(responseBuffer,(byte)(TCPHeader.ACK),0,packet.tcpHeader.sequenceNumber+1,0);
                        sendDataToClient(packet.backingBuffer);
                        tcb.status=TCBStatus.CLOSE_WAIT;
                    }else if(tcpHeader.isRST()){
                        packet.updateTCPBuffer(responseBuffer,(byte)(TCPHeader.ACK),0,packet.tcpHeader.sequenceNumber+1,0);
                        sendDataToClient(packet.backingBuffer);
                        tcb.status=TCBStatus.CLOSE_WAIT;
                    }
                }
            }else if(packet.isUDP()){

            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void sendDataToClient(ByteBuffer byteBuffer){
        try{
            byteBuffer.flip();
            clientChannel.write(byteBuffer);
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @Override
    public void run() {
        while (!Thread.interrupted()){
            try {
                if(tcpSelector.select()<=0){
                    continue;
                }
                Set<SelectionKey> keys = tcpSelector.selectedKeys();
                Iterator<SelectionKey> keyIterator = keys.iterator();
                while (keyIterator.hasNext() && !Thread.interrupted()) {
                    keyIterator.remove();
                    SelectionKey key = keyIterator.next();
                    TCB tcb= (TCB) key.attachment();
                    synchronized (tcb){
                        if (!key.isValid()) {
                            continue;
                        }
                        if (!key.isReadable()){ //服务端发送数据过来了
                            continue;
                        }
                        ByteBuffer responseBuffer = ByteBufferPool.acquire();
                        switch (tcb.status){
                            case ESTABLISHED: //连接建立，可互传数据
                                try{
                                    Packet packet=tcb.referencePacket;
                                    int headerLength=packet.ip4Header.headerChecksum+packet.tcpHeader.headerLength;
                                    responseBuffer.position(headerLength);
                                    int length=tcb.channel.read(responseBuffer);
                                    if(length>0){
                                        packet.updateTCPBuffer(responseBuffer,(byte) 0,tcb.mySequenceNum,0,responseBuffer.limit()-headerLength);
                                        sendDataToClient(packet.backingBuffer);
                                    }else {
                                        packet.updateTCPBuffer(responseBuffer,(byte) TCPHeader.FIN,tcb.mySequenceNum,0,0);
                                        sendDataToClient(packet.backingBuffer);
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                    tcb.referencePacket.updateTCPBuffer(responseBuffer,(byte)TCPHeader.RST,0,0,0);
                                    sendDataToClient(tcb.referencePacket.backingBuffer);
                                    TCB.closeTCB(tcb);
                                }
                                break;
                            case CLOSE_WAIT: //服务端在断开之前发送剩余数据
                                try{
                                    Packet packet=tcb.referencePacket;
                                    int headerLength=packet.ip4Header.headerChecksum+packet.tcpHeader.headerLength;
                                    responseBuffer.position(headerLength);
                                    int length=tcb.channel.read(responseBuffer);
                                    if(length>0){
                                        packet.updateTCPBuffer(responseBuffer,(byte) 0,tcb.mySequenceNum,0,responseBuffer.limit()-headerLength);
                                        sendDataToClient(packet.backingBuffer);
                                    }else {
                                        packet.updateTCPBuffer(responseBuffer,(byte) TCPHeader.FIN,tcb.mySequenceNum,0,0);
                                        sendDataToClient(packet.backingBuffer);
                                        tcb.status=TCBStatus.LAST_ACK;
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                    tcb.referencePacket.updateTCPBuffer(responseBuffer,(byte)TCPHeader.RST,0,0,0);
                                    sendDataToClient(tcb.referencePacket.backingBuffer);
                                    TCB.closeTCB(tcb);
                                }
                                break;
                            case LAST_ACK: //服务端完成数据发送，确认关闭
                                break;
                        }
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
