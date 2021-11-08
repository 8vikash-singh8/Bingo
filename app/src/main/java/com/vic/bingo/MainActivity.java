package com.vic.bingo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.vic.bingo.network.WifiService;

import static com.vic.bingo.Controller.getController;
import static com.vic.bingo.Constants.*;


public class MainActivity extends AppCompatActivity {

    static String TAG = "BINGO::MainActivity";
    Context context;
    Controller controller;
    MainHandler mainHandler;
    EditText playerName;
    ActivityResultLauncher<String> requestPermissionLauncher;
    ArrayAdapter<String> dialogList;
    AlertDialog alertDialog;
    ProgressDialog progressDialog;
    String selectedGroup;
    boolean isServer = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;
        controller = getController();
        mainHandler = new MainHandler();
        controller.setMainHandler(mainHandler);

        Intent intent = new Intent(this, WifiService.class);
        context.startService(intent);

        playerName = findViewById(R.id.edit_text_name);
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                isGranted -> { if (!isGranted) {
                    Toast.makeText(this, "Without this permission, game will not work!! " +
                            "Sorry, cannot proceed!!, Please enable permissions from settings",
                            Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        controller.setMainHandler(null);
        if (alertDialog != null)
            alertDialog.dismiss();
        if (progressDialog != null)
            progressDialog.dismiss();
    }

    boolean getPermission(String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission);
        }
        return (ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED);
    }

    boolean hasAllPermissions() {
        if (!getPermission(Manifest.permission.INTERNET)) return false;
        if (!getPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return false;
        if (!getPermission(Manifest.permission.CHANGE_NETWORK_STATE)) return false;
        if (!getPermission(Manifest.permission.ACCESS_NETWORK_STATE)) return false;
        if (!getPermission(Manifest.permission.ACCESS_WIFI_STATE)) return false;
        if (!getPermission(Manifest.permission.CHANGE_WIFI_STATE)) return false;
        return true;
    }

    boolean isPlayerNameEmpty() {
        String name = playerName.getText().toString().trim();
        if (name.isEmpty())
            return true;
        return false;
    }

    public void createGroup(View v) {
        if (isPlayerNameEmpty()) {
            Toast.makeText(this, "player name is empty! Please enter a name!",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!hasAllPermissions()) {
            return;
        }

        isServer = true;

        Message msg = controller.obtainMessage(CREATE_GROUP, playerName.getText().toString());
        controller.dispatchMessage(msg);

        Toast.makeText(context, "Request to create group has been sent!" +
                "please wait, a dialog box will open shortly!", Toast.LENGTH_SHORT).show();
    }

    public void joinGroup(View v) {
        if (isPlayerNameEmpty()) {
            Toast.makeText(this, "player name is empty! Please enter a name!",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!hasAllPermissions()) {
            return;
        }

        Message msg = controller.obtainMessage(DISCOVER_GROUP, playerName.getText().toString());
        controller.dispatchMessage(msg);

        Toast.makeText(context, "Request to discover groups has been sent!" +
                "please wait, a dialog box will open shortly!", Toast.LENGTH_SHORT).show();
    }

    void showCreateJoinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        View view = getLayoutInflater().inflate(R.layout.group_dialog, null);
        builder.setView(view);
        dialogList = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1);

        TextView dialogMessage = view.findViewById(R.id.dialog_message);
        TextView dialogListTitle = view.findViewById(R.id.dialog_list_title);
        ListView dialogListView = view.findViewById(R.id.dialog_list);
        dialogListView.setAdapter(dialogList);

        if (isServer) {
            dialogMessage.setText("Waiting for players to connect, Press 'Start Game' once all players are connected!");
            dialogListTitle.setText("Connected Players List");
            builder.setPositiveButton("Start Game", (dialog, which) -> {
                if (dialogList.getCount() > 0) {
                    alertDialog.dismiss();
                    Message msg = controller.obtainMessage(GAME_STARTED);
                    controller.dispatchMessage(msg);
                    startGame();
                }
            });
        } else {
            dialogMessage.setText("Searching nearby groups, please select available group and press 'Connect'");
            dialogListTitle.setText("Available Groups");

            dialogListView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            dialogListView.setOnItemClickListener((parent, view1, position, id) ->
                    selectedGroup = dialogList.getItem(position));

            builder.setPositiveButton("Connect", (dialog, which) -> {
                if (selectedGroup != null) {
                    alertDialog.dismiss();

                    progressDialog = new ProgressDialog(context);
                    progressDialog.setMessage("Waiting for '" + selectedGroup + "' to start game!!");
                    progressDialog.setIndeterminate(true);
                    progressDialog.setCancelable(false);
                    progressDialog.create();
                    progressDialog.show();

                    Message msg = controller.obtainMessage(JOIN_GROUP, selectedGroup);
                    controller.dispatchMessage(msg);
                } else {
                    Toast.makeText(context, "please select a group!!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            if (alertDialog.isShowing())
                alertDialog.dismiss();
            isServer = false;
            Message msg = controller.obtainMessage(GROUP_CANCELLED);
            controller.dispatchMessage(msg);
        });

        alertDialog = builder.create();
        alertDialog.show();
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
    }

    void updatePlayerList(String playerName) {
        if (isServer) {
            dialogList.add(playerName);
            dialogList.notifyDataSetChanged();
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
        }
    }

    void updateGroupList(String[] groupNames){
        if (!isServer) {
            dialogList.clear();
            dialogList.addAll(groupNames);
            dialogList.notifyDataSetChanged();
            if (groupNames.length != 0)
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
            else
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        }
    }

    void startGame() {
        if (progressDialog != null)
            progressDialog.dismiss();
        Intent intent = new Intent(context, GameActivity.class);
        intent.putExtra("playerName", playerName.getText().toString().trim());
        startActivity(intent);
        finish();
    }

    private class MainHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case UPDATE_PLAYER_LIST:
                    updatePlayerList((String) msg.obj);
                    break;
                case UPDATE_GROUP_LIST:
                    updateGroupList((String []) msg.obj);
                    break;
                case WIFI_P2P_INITIALIZED:
                    showCreateJoinDialog();
                    break;
                case GROUP_JOINED:
                    startGame();
                    break;
                default:
                    Log.e(TAG,"Message not expected!!! " + msg.what);
            }
        }
    }
}