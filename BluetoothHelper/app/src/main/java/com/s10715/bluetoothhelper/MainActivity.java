package com.s10715.bluetoothhelper;

import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.RadioButton;

import com.s10715.bluetoothhelper.fragment.ConnectFragment;
import com.s10715.bluetoothhelper.fragment.SearchFragment;
import com.s10715.bluetoothhelper.fragment.TransferFragment;
import com.s10715.bluetoothhelper.utils.BluetoothHelper;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private SearchFragment searchFragment;
    private ConnectFragment connectFragment;
    private TransferFragment transferFragment;

    public static final String DEFAULT_CLASSIC_UUID = "00001101-0000-1000-8000-00805F9B34FB";
    public static final String DEFAULT_SERVICE_UUID = "FFA5417A-2C26-43EA-8A6B-4BD5C51ADBCF";
    public static final String DEFAULT_CHARACTER_UUID = "32B82DB5-79CA-451B-8C80-A9B4C2AD5E49";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FragmentManager fragmentManager = getSupportFragmentManager();

        //防止由于内存不足或instant run时，fragment产生重叠问题
        if (savedInstanceState == null) {
            searchFragment = new SearchFragment();
            connectFragment = new ConnectFragment();
            transferFragment = new TransferFragment();

            fragmentManager.beginTransaction()
                    .add(R.id.frameLayout, searchFragment, SearchFragment.class.getName())
                    .add(R.id.frameLayout, connectFragment, ConnectFragment.class.getName())
                    .add(R.id.frameLayout, transferFragment, TransferFragment.class.getName())
                    .commit();
            startSearch();
        } else {
            searchFragment = (SearchFragment) fragmentManager.findFragmentByTag(SearchFragment.class.getName());
            connectFragment = (ConnectFragment) fragmentManager.findFragmentByTag(ConnectFragment.class.getName());
            transferFragment = (TransferFragment) fragmentManager.findFragmentByTag(TransferFragment.class.getName());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (BluetoothHelper.getInstance(this) != null)
            BluetoothHelper.getInstance(this).close();
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.main_search_btn:
                startSearch();
                break;
            case R.id.main_connect_btn:
                startConnect();
                break;
            case R.id.main_transfer_btn:
                startTransfer();
                break;
            default:
                break;
        }
    }

    private void startSearch() {
        //切换到SearchFragment
        ((RadioButton) findViewById(R.id.main_search_btn)).setChecked(true);
        getSupportFragmentManager()
                .beginTransaction()
                .show(searchFragment)
                .hide(connectFragment)
                .hide(transferFragment)
                .commit();
    }

    private void startConnect() {
        //切换到ConnectFragment
        ((RadioButton) findViewById(R.id.main_connect_btn)).setChecked(true);
        getSupportFragmentManager()
                .beginTransaction()
                .hide(searchFragment)
                .show(connectFragment)
                .hide(transferFragment)
                .commit();
    }

    private void startTransfer() {
        //切换到TransferFragment
        ((RadioButton) findViewById(R.id.main_transfer_btn)).setChecked(true);
        getSupportFragmentManager()
                .beginTransaction()
                .hide(searchFragment)
                .hide(connectFragment)
                .show(transferFragment)
                .commit();
    }

    public void startConnect(BluetoothHelper.BluetoothInfo info) {
        startConnect();
        connectFragment.setData(info);
    }

    public void startTransfer(String name, String mac, int connectType, UUID classicUUID, UUID serviceUUID, UUID characterUUID) {
        startTransfer();
        transferFragment.setData(name, mac, connectType, serviceUUID, characterUUID);
    }

    public void stopTransfer() {
        transferFragment.stopTransfer();
    }
}
