package com.vic.bingo.network;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.vic.bingo.CommunicationData;
import com.vic.bingo.Controller;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static com.vic.bingo.Constants.*;

public class ClientThread extends Thread {
    static String TAG = "BINGO::ClientThread";
    String playerName;
    Controller controller;
    Socket client;
    ObjectOutputStream clientOutputStream;
    ObjectInputStream clientInputStream;
    boolean isConnected = false;

    public ClientThread(String name) {
        super("Bingo-client");
        playerName = name;
        controller = Controller.getController();
    }

    @Override
    public void run() {
        super.run();
        Looper.prepare();
        controller.setNetworkHandler(new NetworkHandler(Looper.myLooper()));
        Looper.loop();
    }

    public void close() {
        controller.setNetworkHandler(null);
        try {
            if (client != null)
                client.close();
            Looper.myLooper().quit();
        } catch (Exception e) {
            Log.e(TAG, "close() Exception : " + e.getMessage());
        }
    }

    private void connectToServer(InetAddress inetAddress) {
        client = new Socket();
        while (true) {
            try {
                Log.i(TAG, "trying to connect to : " + inetAddress);
                client.bind(new InetSocketAddress(COM_PORT));
                client.connect(new InetSocketAddress(inetAddress, COM_PORT), SO_TIMEOUT);
                client.setSoTimeout(SO_TIMEOUT);
                client.setKeepAlive(true);
                client.setTcpNoDelay(true);
                isConnected = client.isConnected();
                Log.i(TAG, "connected : " + isConnected);
                break;
            } catch (SocketTimeoutException e) {
                Log.e(TAG, "connectToServer() Exception : " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "connectToServer() Exception : " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }

        try {
            clientOutputStream = new ObjectOutputStream(client.getOutputStream());
            CommunicationData data = CommunicationData.getCommunicationData();
            data.setPlayerName(playerName);
            sendData(data);
            Log.i(TAG, "player name sent!!");

            clientInputStream = new ObjectInputStream(client.getInputStream());
            Log.i(TAG, "player name received : " + receiveData().getPlayerName());
        } catch (Exception e) {
            Log.e(TAG, "connectToServer() 1st transaction Exception : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendData(CommunicationData data) {
        if (isConnected) {
            try {
                clientOutputStream.reset();
                Log.i(TAG, "send data : " + data.toString());
                clientOutputStream.writeObject(data);
                clientOutputStream.flush();
            } catch (Exception e) {
                Log.e(TAG, "sendData() Exception : " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private CommunicationData receiveData() {
        while (true) {
            try {
                CommunicationData data = (CommunicationData) clientInputStream.readObject();
                Log.i(TAG, "receive data : " + data.toString());
                return data;
            } catch (SocketTimeoutException e) {
                Log.e(TAG, "receiveData() Timeout Exception : " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "receiveData() Exception : " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
    }

    private class NetworkHandler extends Handler {

        public NetworkHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case CONNECT_SERVER :
                    connectToServer((InetAddress) msg.obj);
                    break;
                case SEND_DATA:
                    sendData((CommunicationData) msg.obj);
                    break;
                case RECEIVE_DATA:
                    CommunicationData data = receiveData();
                    Message rMsg = controller.obtainMessage(DATA_RECEIVED, data);
                    controller.dispatchMessage(rMsg);
                    break;
                case CLOSE_THREAD:
                    close();
                    break;
                default:
                    Log.e(TAG, "Message not expected!!! " + msg.what);
            }
        }
    }
}
