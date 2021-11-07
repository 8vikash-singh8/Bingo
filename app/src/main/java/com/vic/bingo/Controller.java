package com.vic.bingo;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import static com.vic.bingo.Constants.*;

import androidx.annotation.NonNull;

public class Controller extends Handler {
    static String TAG = "BINGO::Controller";
    private Handler mainHandler = null;
    private Handler gameHandler = null;
    private Handler wifiHandler = null;
    private Handler networkHandler = null;

    private Controller(){
        //Do Nothing
    }

    private static class Singleton {
        private static final Controller Instance = new Controller();
    }

    public static Controller getController(){
        return Singleton.Instance;
    }

    public void setMainHandler(Handler handler){
        mainHandler = handler;
    }
    public void setGameHandler(Handler handler){
        gameHandler = handler;
    }
    public void setWifiHandler(Handler handler){
        wifiHandler = handler;
    }
    public void setNetworkHandler(Handler handler){
        networkHandler = handler;
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        super.handleMessage(msg);

        switch (msg.what){
            case CREATE_GROUP:
            case DISCOVER_GROUP:
            case JOIN_GROUP:
            case GROUP_CANCELLED:
            case GAME_STARTED:
                if(wifiHandler != null)
                    wifiHandler.sendMessage(msg);
                break;
            case UPDATE_PLAYER_LIST:
            case UPDATE_GROUP_LIST:
            case WIFI_P2P_INITIALIZED:
            case GROUP_JOINED:
                if(mainHandler != null)
                    mainHandler.sendMessage(msg);
                break;
            case START_SERVER:
            case CONNECT_SERVER:
            case SEND_DATA:
            case RECEIVE_DATA:
            case CLOSE_THREAD:
                if (networkHandler != null)
                    networkHandler.sendMessage(msg);
                break;
            case DATA_RECEIVED:
                if (gameHandler != null)
                    gameHandler.sendMessage(msg);
                break;
            default:
                Log.e(TAG,"Message not expected!!! " + msg.what);
        }
    }
}
