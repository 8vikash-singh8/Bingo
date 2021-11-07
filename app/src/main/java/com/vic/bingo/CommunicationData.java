package com.vic.bingo;

import java.io.Serializable;

public class CommunicationData implements Serializable {
    private int msgType;
    private int numberCrossed;
    private String playerName;
    private boolean isWinner;
    private static CommunicationData data;

    public static CommunicationData getCommunicationData() {
        if (data == null) {
            data = new CommunicationData();
        }
        data.msgType = 0;
        data.numberCrossed = 0;
        data.playerName = null;
        data.isWinner = false;
        return data;
    }

    public void setNumberCrossed(int num) {
        msgType = 1;
        numberCrossed = num;
    }

    public void setPlayerName(String name) {
        msgType = 2;
        playerName = name;
    }

    public void setWinner(boolean isWin) {
        msgType = 3;
        isWinner = isWin;
    }

    public int getMsgType() {
        return msgType;
    }

    public String getPlayerName() {
        return playerName;
    }

    public boolean getWinner() {
        return isWinner;
    }

    public int getNumberCrossed() {
        return numberCrossed;
    }

    @Override
    public String toString() {
        return "CommunicationData{" +
                "msgType=" + msgType +
                ", numberCrossed=" + numberCrossed +
                ", playerName='" + playerName + '\'' +
                ", isWinner=" + isWinner +
                '}';
    }
}
