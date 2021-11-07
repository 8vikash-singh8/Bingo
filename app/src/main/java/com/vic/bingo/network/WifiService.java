package com.vic.bingo.network;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.vic.bingo.Controller;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;

import static com.vic.bingo.Constants.*;
import static com.vic.bingo.Controller.getController;

@SuppressLint("MissingPermission")
public class WifiService extends Service {
    static String TAG = "BINGO::WifiService";
    Context context;
    Controller controller;
    WifiP2pManager wifiP2pManager;
    WifiManager wifiManager;
    LocationManager locationManager;
    Channel channel;
    WifiBroadcastReceiver receiver;
    ArrayList<WifiP2pDevice> deviceArrayList;
    ServerThread serverThread;
    ClientThread clientThread;
    boolean isServer = false;
    boolean groupFormed;
    String playerName = null;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        controller = getController();
        controller.setWifiHandler(new WifiHandler());
        serverThread = null;
        clientThread = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        close();
    }

    void close() {
        isServer = false;
        playerName = null;
        Message msg = controller.obtainMessage(CLOSE_THREAD);
        controller.dispatchMessage(msg);
        if (serverThread != null) {
            serverThread = null;
            wifiP2pManager.removeGroup(channel, null);
        }
        if (clientThread != null)
            clientThread = null;
        unregisterReceiver(receiver);
        channel = null;
        receiver = null;
    }

    boolean init() {
        wifiP2pManager = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
        wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(context, "WIFI is Disabled, please enable WIFI",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!locationManager.isLocationEnabled()) {
            Toast.makeText(context, "Location is Disabled, please enable Location",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        if (channel == null || receiver == null) {
            channel = wifiP2pManager.initialize(context, getMainLooper(), null);
            receiver = new WifiBroadcastReceiver(wifiP2pManager, channel);

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

            registerReceiver(receiver, intentFilter);
        }
        groupFormed = false;
        return true;
    }

    void createGroup(String groupName) {
        if (!init())
            return;
        isServer = true;
        playerName = groupName;
        if (channel != null) {

            try {
                Method m = wifiP2pManager.getClass().getMethod("setDeviceName", channel.getClass(), String.class,
                        WifiP2pManager.ActionListener.class);
                m.invoke(wifiP2pManager, channel, playerName, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                    }
                    @Override
                    public void onFailure(int reason) {
                        Log.e(TAG, "setDeviceName failure reason : " + reason);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Change Device name exception : " + e.getMessage());
                e.printStackTrace();
            }

            wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Group created!!");
                    Toast.makeText(context, "Group created!!",
                            Toast.LENGTH_SHORT).show();
                    serverThread = new ServerThread(playerName);
                    serverThread.start();

                    Message msg = controller.obtainMessage(WIFI_P2P_INITIALIZED);
                    controller.dispatchMessage(msg);
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Group creation failed!!");
                    Toast.makeText(context, "Group creation failed!! " +
                            "please disable and enable wifi and try again!", Toast.LENGTH_SHORT).show();
                    close();
                }
            });
        } else {
            Log.e(TAG, "channel is null, cannot create group!!");
            Toast.makeText(context, "WIFI of your device is not properly initialized!! " +
                    "cannot create group, please restart game!!", Toast.LENGTH_SHORT).show();
        }
    }

    void discoverGroup(String name) {
        if (!init())
            return;
        playerName = name;
        deviceArrayList = new ArrayList<>();
        if (channel != null) {
            wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Group discovery started!!");
                    Toast.makeText(context, "Group discovery started!!",
                            Toast.LENGTH_SHORT).show();

                    Message msg = controller.obtainMessage(WIFI_P2P_INITIALIZED);
                    controller.dispatchMessage(msg);
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Group discovery failed!!");
                    Toast.makeText(context, "Group discovery failed!!" +
                                    "please disable and enable wifi and try again!", Toast.LENGTH_SHORT).show();
                    close();
                }
            });
        } else {
            Log.e(TAG, "channel is null, cannot create group!!");
            Toast.makeText(context, "WIFI of your device is not properly initialized!! " +
                    "cannot discover group, please restart game!!", Toast.LENGTH_SHORT).show();
        }
    }

    void joinGroup(WifiP2pDevice device) {
        if (channel != null) {
            WifiP2pConfig config = new WifiP2pConfig();
            config.wps.setup = WpsInfo.PBC;
            config.deviceAddress = device.deviceAddress;
            wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Connection request successfully sent!!");
                    clientThread = new ClientThread(playerName);
                    clientThread.start();
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Connection request send failed!!");
                    Toast.makeText(context, "Connection request could not be sent!! please try again!",
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Log.e(TAG, "channel is null, cannot create group!!");
            Toast.makeText(context, "WIFI of your device is not properly initialized!! " +
                    "cannot join group, please restart game!!", Toast.LENGTH_SHORT).show();
        }
    }

    private class WifiHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case CREATE_GROUP:
                    createGroup((String) msg.obj);
                    break;
                case DISCOVER_GROUP:
                    discoverGroup((String) msg.obj);
                    break;
                case JOIN_GROUP:
                    for (WifiP2pDevice device : deviceArrayList) {
                        if (device.deviceName.equals((String) msg.obj)) {
                            joinGroup(device);
                            break;
                        }
                    }
                    break;
                case GROUP_CANCELLED:
                    close();
                    break;
                case GAME_STARTED:
                    if (isServer)
                        serverThread.interrupt();
                    break;
                default:
                    Log.e(TAG, "Message not expected!!! " + msg.what);
            }
        }
    }

    private class WifiBroadcastReceiver extends BroadcastReceiver {

        private final WifiP2pManager manager;
        private final Channel channel;

        public WifiBroadcastReceiver(WifiP2pManager manager, Channel channel) {
            super();
            this.manager = manager;
            this.channel = channel;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Log.i(TAG, "WIFI is enabled!!");
                } else {
                    Log.e(TAG, "WIFI is disabled!!");
                    Toast.makeText(context, "WIFI is disabled!!" +
                            " please enable WIFI!!", Toast.LENGTH_SHORT).show();
                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if (manager != null) {
                    manager.requestPeers(channel, peers -> {
                        if (deviceArrayList != null)
                            deviceArrayList.clear();
                        String [] peerNames = new String[peers.getDeviceList().size()];
                        int index = 0;
                        Collection<WifiP2pDevice> peerList = peers.getDeviceList();
                        for (WifiP2pDevice peer : peerList) {
                            if (deviceArrayList != null)
                                deviceArrayList.add(new WifiP2pDevice(peer));
                            peerNames[index++] = peer.deviceName;
                        }
                        Message msg = controller.obtainMessage(UPDATE_GROUP_LIST, peerNames);
                        controller.dispatchMessage(msg);
                    });
                }
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                WifiP2pInfo connectionInfo = intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                if (connectionInfo.groupFormed && !groupFormed) {
                    groupFormed = true;
                    if (isServer) {
                        Message msg = controller.obtainMessage(START_SERVER, connectionInfo.groupOwnerAddress);
                        controller.dispatchMessage(msg);
                    } else {
                        Message msg = controller.obtainMessage(CONNECT_SERVER, connectionInfo.groupOwnerAddress);
                        controller.dispatchMessage(msg);
                        msg = controller.obtainMessage(GROUP_JOINED);
                        controller.dispatchMessage(msg);
                    }
                }
            }
        }
    }
}