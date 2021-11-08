package com.vic.bingo.network;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOptions;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import com.vic.bingo.CommunicationData;
import com.vic.bingo.Controller;

import static com.vic.bingo.Constants.*;
import static com.vic.bingo.Controller.getController;

public class ServerThread extends Thread {
    static String TAG = "BINGO::ServerThread";
    ArrayList<String> playerList;
    Controller controller;
    ServerSocket serverSocket;
    ArrayList<Socket> clients;
    ArrayList<ObjectOutputStream> clientOutputStream;
    ArrayList<ObjectInputStream> clientInputStream;
    String playerName;
    boolean winRound = false;
    boolean sendTurn = false;
    int number = 0;
    int readyCount = 0;
    int turnCount = 0;
    StringBuilder winnersName;

    public ServerThread(String name) {
        super("Bingo-server");
        playerList = new ArrayList<>();
        clients = new ArrayList<>();
        clientInputStream = new ArrayList<>();
        clientOutputStream = new ArrayList<>();
        controller = getController();
        playerName = name;
        winnersName = new StringBuilder().append("Player ");
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
            serverSocket.close();
            for (Socket client : clients) {
                client.close();
            }
            Looper.myLooper().quit();
        } catch (Exception e) {
            Log.e(TAG, "close() Exception : " + e.getMessage());
        }
    }

    void acceptConnections(InetAddress inetAddress) {
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(inetAddress, COM_PORT));
        } catch (Exception e) {
            Log.e(TAG, "acceptConnections() bind Exception : " + e.getMessage());
        }

        while (true) {
            try {
                if (this.isInterrupted())
                    break;
                serverSocket.setSoTimeout(SO_TIMEOUT);
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(SO_TIMEOUT);
                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);
                clients.add(socket);
                Log.i(TAG, "Connected to a player!!");

                clientOutputStream.add(new ObjectOutputStream(socket.getOutputStream()));
                CommunicationData sData = CommunicationData.getCommunicationData();
                sData.setPlayerName(playerName);
                clientOutputStream.get(clientOutputStream.size()-1).writeObject(sData);
                clientOutputStream.get(clientOutputStream.size()-1).flush();
                Log.i(TAG, "player name sent!!");

                clientInputStream.add(new ObjectInputStream(socket.getInputStream()));
                //get player name
                CommunicationData rData = receiveData(clientInputStream.get(clientInputStream.size() - 1));
                if (rData.getMsgType() == MSG_TYPE_NAME)
                    playerList.add(rData.getPlayerName());
                Log.i(TAG, "player name received : " + rData.getPlayerName());

                //update controller with new player
                Message msg = controller.obtainMessage(UPDATE_PLAYER_LIST, rData.getPlayerName());
                controller.dispatchMessage(msg);
            } catch (SocketTimeoutException e) {
                Log.e(TAG, "acceptConnections() Exception : " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "acceptConnections() Exception : " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }
        playerList.add(playerName);
    }

    private void sendData(CommunicationData data, boolean skip, int turn) {
        int count = -1;
        for (ObjectOutputStream outputStream : clientOutputStream) {
            count += 1;
            if (skip && (count == turn))
                continue;
            try {
                outputStream.reset();
                Log.i(TAG, "send data : " + data.toString());
                outputStream.writeObject(data);
            } catch (Exception e) {
                Log.e(TAG, "sendData() Exception : " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private CommunicationData receiveData(ObjectInputStream inputStream) {
        while (true) {
            try {
                CommunicationData data = (CommunicationData) inputStream.readObject();
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

    //send received data to all player except turn player and receive winning condition
    private void sendDataToAllAndCheckWin(CommunicationData data) {
        sendData(data, true, turnCount);
        int count = -1;
        for (ObjectInputStream inputStream : clientInputStream) {
            count += 1;
            if(turnCount == count)
                continue;
            data = receiveData(inputStream);
            if (data.getWinner()) {
                winnersName.append(playerList.get(turnCount)).append(", ");
                winRound = true;
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
                case START_SERVER :
                    acceptConnections((InetAddress) msg.obj);
                    break;
                case SEND_DATA:
                    number = ((CommunicationData)msg.obj).getNumberCrossed();
                    if (number == READY) {
                        readyCount++;
                        sendTurn = true;
                    } else {
                        if (((CommunicationData)msg.obj).getWinner()) {
                            winnersName.append(playerName).append(", ");
                            winRound = true;
                        }
                    }
                    break;
                case RECEIVE_DATA:
                    CommunicationData data = CommunicationData.getCommunicationData();
                    if (readyCount < playerList.size()) {
                        for (ObjectInputStream inputStream : clientInputStream) {
                            if (receiveData(inputStream).getNumberCrossed() == READY) {
                                readyCount++;
                            }
                        }
                        data.setNumberCrossed(READY);
                        sendData(data, false, 0);
                    } else if (sendTurn) {
                        data = CommunicationData.getCommunicationData();
                        data.setPlayerName(playerList.get(turnCount));
                        sendData(data, false, 0);
                        sendTurn = false;
                    } else if (!winRound) {
                        //get data from turn player other than server
                        if(!playerList.get(turnCount).equals(playerName)) {
                            data = receiveData(clientInputStream.get(turnCount));
                            number = data.getNumberCrossed();
                            if (data.getWinner()) {
                                winnersName.append(playerList.get(turnCount)).append(", ");
                                winRound = true;
                            }
                        }
                        data.setNumberCrossed(number);
                        sendDataToAllAndCheckWin(data);
                        if(!playerList.get(turnCount).equals(playerName))
                            data.setNumberCrossed(number);
                        else
                            data.setNumberCrossed(READY);

                        if(!winRound)
                            sendTurn = true;
                        turnCount = (turnCount + 1) % playerList.size();
                    } else {
                        if (playerList.get(turnCount).equals(playerName)) {
                            data.setNumberCrossed(number);
                            sendDataToAllAndCheckWin(data);
                        }
                        data.setPlayerName(winnersName.toString());
                        data.setWinner(true);
                        sendData(data, false, 0);
                    }
                    Message msg1 = controller.obtainMessage(DATA_RECEIVED, data);
                    controller.dispatchMessage(msg1);
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
