package com.vic.bingo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.vic.bingo.network.WifiService;

import static com.vic.bingo.Constants.*;
import static com.vic.bingo.Controller.getController;

public class GameActivity extends AppCompatActivity {

    static String TAG = "BINGO::GameActivity";
    Context context;
    Controller controller;
    GameHandler gameHandler;
    ProgressDialog readyDialog;
    AlertDialog winnerDialog;
    boolean ready = false;
    boolean myTurn = false;
    String playerName;
    int exitCount = 0, numCount = 0, diagonal = 0, antiDiagonal = 0, bingoCount = 0;
    int[] numbers, positions, checked, rowCount, colCount;

    int[] BingoIds = {
            R.id.Bingo_1,
            R.id.Bingo_2,
            R.id.Bingo_3,
            R.id.Bingo_4,
            R.id.Bingo_5,
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        context = this;
        gameHandler = new GameHandler();
        controller = getController();
        controller.setGameHandler(gameHandler);

        playerName = getIntent().getStringExtra("playerName");

        numbers = new int[TOTAL_GRID_CELLS + 1];
        positions = new int[TOTAL_GRID_CELLS + 1];
        checked = new int[TOTAL_GRID_CELLS + 1];
        rowCount = new int[6];
        colCount = new int[6];

        for (int i = 0 ; i < 5; i++) {
            rowCount[i] = colCount[i] = 0;
        }

        for (int i = 0; i <= TOTAL_GRID_CELLS; i++) {
            numbers[i] = positions[i] = checked[i] = 0;
        }

        GridView gridView = findViewById(R.id.grid);
        gridView.setAdapter(new GridAdapter());

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            if (numbers[position] == 0) {
                numCount += 1;
                positions[numCount] = position;
                numbers[position] = numCount;
                TextView tv = view.findViewById(R.id.cell_text);
                tv.setText(String.valueOf(numCount));

                if (numCount == TOTAL_GRID_CELLS) {
                    Button button = findViewById(R.id.ready);
                    button.setEnabled(true);
                }
            } else if (ready && myTurn && checked[numbers[position]] == 0) {
                checked[numbers[position]] = 1;
                TextView tv = view.findViewById(R.id.cell_text);
                tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tv.setBackgroundColor(Color.parseColor("#99aa11"));
                gameAlgo(numbers[position], position, myTurn);
                myTurn = false;
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        controller.setGameHandler(null);
    }

    public void setReady(View view) {
        ready = true;
        Button button = findViewById(R.id.ready);
        button.setEnabled(false);

        readyDialog = new ProgressDialog(context);
        readyDialog.setMessage("Waiting for other players to get ready!!");
        readyDialog.setIndeterminate(true);
        readyDialog.setCancelable(false);
        readyDialog.create();
        readyDialog.show();

        CommunicationData readyData = CommunicationData.getCommunicationData();
        readyData.setNumberCrossed(READY);

        Message msg = controller.obtainMessage(SEND_DATA, readyData);
        controller.dispatchMessage(msg);

        Message msg1 = controller.obtainMessage(RECEIVE_DATA);
        controller.dispatchMessage(msg1);
    }

    public void playAgain(View view) {
        Message msg = controller.obtainMessage(PLAY_AGAIN);
        controller.dispatchMessage(msg);
        Intent intent = new Intent(this, GameActivity.class);
        startActivity(intent);
        finish();
    }

    public void goBack(View view) {
        gameHandler.postDelayed(() -> exitCount = 0, 3000);
        exitCount += 1;
        Toast.makeText(this, "Press again to leave game screen!!!", Toast.LENGTH_SHORT).show();
        if (exitCount >= 2) {
            Intent intent = new Intent(this, WifiService.class);
            stopService(intent);
            Intent intent1 = new Intent(this, MainActivity.class);
            startActivity(intent1);
            finish();
        }
    }

    private void processData(CommunicationData data) {
        if (data.getMsgType() == MSG_TYPE_NUMBER) {
            if (data.getNumberCrossed() == READY) {
                if(readyDialog != null && readyDialog.isShowing())
                    readyDialog.dismiss();
                Message msg1 = controller.obtainMessage(RECEIVE_DATA);
                controller.dispatchMessage(msg1);
            } else {
                int position = positions[data.getNumberCrossed()];
                checked[numbers[position]] = 1;
                GridView gridView = findViewById(R.id.grid);
                View view = gridView.getChildAt(position);
                TextView tv = view.findViewById(R.id.cell_text);
                tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tv.setBackgroundColor(Color.parseColor("#99aa11"));
                gameAlgo(numbers[position], position, myTurn);
            }
        } else if (data.getMsgType() == MSG_TYPE_NAME) {
            TextView tv = findViewById(R.id.turn);
            tv.setText(data.getPlayerName());
            if (data.getPlayerName().equals(playerName)) {
                myTurn = true;
            } else {
                Message msg1 = controller.obtainMessage(RECEIVE_DATA);
                controller.dispatchMessage(msg1);
            }
        } else if (data.getMsgType() == MSG_TYPE_WINNER) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setPositiveButton("OK", (dialog, which) -> {
                if (winnerDialog.isShowing()) {
                    winnerDialog.dismiss();
                }
            });
            builder.setCancelable(false);
            builder.setTitle("!!! BINGO !!!");
            builder.setMessage(data.getPlayerName() + " wins!");
            winnerDialog = builder.create();
            winnerDialog.show();

            Button button = findViewById(R.id.play_again);
            button.setEnabled(true);
        }
    }

    private void gameAlgo(int num, int position, boolean myTurn) {
        boolean isWin = false;
        int q = (int) position / 5;
        int r = (int) position % 5;
        Log.i(TAG, "position : " + position + " quotient : " + q + " reminder : " + r);
        rowCount[q] += 1;
        colCount[r] += 1;
        if (q == r) diagonal += 1;
        if (q + r == 4) antiDiagonal += 1;

        if (rowCount[q] == 5) {
            updateBingoHead();
            rowCount[q] = 0;
        }
        if (colCount[r] == 5) {
            updateBingoHead();
            colCount[r] = 0;
        }
        if (antiDiagonal == 5) {
            updateBingoHead();
            antiDiagonal = 0;
        }
        if (diagonal == 5) {
            updateBingoHead();
            diagonal = 0;
        }

        if (bingoCount == 5) {
            isWin = true;
        }

        CommunicationData data = CommunicationData.getCommunicationData();
        if (myTurn) {
            data.setNumberCrossed(num);
        }
        data.setWinner(isWin);
        Message msg = controller.obtainMessage(SEND_DATA, data);
        controller.dispatchMessage(msg);

        Message msg1 = controller.obtainMessage(RECEIVE_DATA);
        controller.dispatchMessage(msg1);
    }

    private void updateBingoHead() {
        if (bingoCount < 5) {
            Button button = findViewById(BingoIds[bingoCount]);
            button.setPaintFlags(button.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            button.setBackgroundColor(Color.parseColor("#44dd88"));
            bingoCount += 1;
        }
    }

    @Override
    public void onBackPressed() {
        gameHandler.postDelayed(() -> exitCount = 0, 3000);
        exitCount += 1;
        Toast.makeText(this, "Press again to exit!!", Toast.LENGTH_SHORT).show();
        if (exitCount >= 2) {
            Intent intent = new Intent(this, WifiService.class);
            stopService(intent);
            System.exit(0);
        }
    }

    private class GameHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case DATA_RECEIVED :
                    processData((CommunicationData) msg.obj);
                    break;
                default:
                    Log.e(TAG,"Message not expected!!! " + msg.what);
            }
        }
    }

    private class GridAdapter extends BaseAdapter {
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.grid_cell_text, parent, false);
            }
            convertView.setMinimumHeight((parent.getHeight()/5)-10);
            TextView tv = convertView.findViewById(R.id.cell_text);
            tv.setText(" ");
            return convertView;
        }

        @Override
        public int getCount() {
            return TOTAL_GRID_CELLS;
        }

        @Override
        public Object getItem(int position) {
            return numbers[position];
        }

        @Override
        public long getItemId(int position) {
            return (positions[numbers[position]] / 5) + 1;
        }
    }
}