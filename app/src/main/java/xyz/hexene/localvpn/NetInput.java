package xyz.hexene.localvpn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NetInput extends Thread {

    private FileChannel vpnOutput;
    private Queue<ByteBuffer> networkToDeviceQueue;

    NetInput(FileChannel vpnOut, Queue<ByteBuffer> networkToDeviceQueue){
        this.vpnOutput=vpnOut;
        this.networkToDeviceQueue=networkToDeviceQueue;
    }

    @Override
    public void run() {
        while (!Thread.interrupted()){
            boolean dataReceived=false;
            try{
                ByteBuffer bufferFromNetwork = networkToDeviceQueue.poll();
                if (bufferFromNetwork != null) {
                    bufferFromNetwork.flip();
                    bufferFromNetwork.position(0);
                    while (bufferFromNetwork.hasRemaining()){
                        try{
                            int len=vpnOutput.write(bufferFromNetwork);
                            if(len==0){
                                Thread.sleep(10);
                            }
                        }catch (IllegalArgumentException e){
                            e.printStackTrace();
                            break;
                        }catch (IOException e){
                            e.printStackTrace();
                            break;
                        }catch (Exception e){
                            e.printStackTrace();
                            Thread.sleep(10);
                        }
                    }
                    dataReceived = true;
                    ByteBufferPool.release(bufferFromNetwork);
                } else {
                    dataReceived = false;
                }
                if(!dataReceived){
                    Thread.sleep(10);
                }
            }catch (InterruptedException e){
                break;
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
