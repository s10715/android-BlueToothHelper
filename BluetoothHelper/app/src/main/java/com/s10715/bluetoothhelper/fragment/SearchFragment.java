package com.s10715.bluetoothhelper.fragment;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.s10715.bluetoothhelper.MainActivity;
import com.s10715.bluetoothhelper.R;
import com.s10715.bluetoothhelper.utils.BluetoothHelper;
import com.s10715.bluetoothhelper.utils.DialogHelper;
import com.s10715.bluetoothhelper.utils.PermissionHelper;
import com.s10715.bluetoothhelper.view.BluetoothListView;

import java.util.ArrayList;

public class SearchFragment extends Fragment implements View.OnClickListener, BluetoothListView.OnItemClickListener {

    private BluetoothHelper bluetoothHelper;
    private PermissionHelper permissionHelper;

    private Button searchBtn;
    private BluetoothListView listView;
    private ArrayList<BluetoothHelper.BluetoothInfo> infoList;

    private boolean isSearching = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        infoList = new ArrayList<>(8);

        View view = inflater.inflate(R.layout.fragment_search, container, false);
        searchBtn = view.findViewById(R.id.search_search_btn);
        listView = view.findViewById(R.id.search_listview);

        searchBtn.setOnClickListener(this);
        listView.setOnItemClickListener(this);

        listView.setData(null);


        bluetoothHelper = BluetoothHelper.getInstance(getActivity());


        permissionHelper = new PermissionHelper(getActivity())
                .addPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .addTipsDialog(DialogHelper.makeDialog(getContext(), "定位权限不可用", "需要使用定位权限才能获取蓝牙信息", false,
                        "立即打开", new DialogHelper.OnClickListener() {
                            @Override
                            public void onClick() {
                                PermissionHelper.getPermissionInFragment(SearchFragment.this, Manifest.permission.ACCESS_COARSE_LOCATION, 1000);
                            }
                        }, "取消", null))
                .addWarnDialog(DialogHelper.makeGoToSettingDialog(getContext(), "定位权限不可用", "请在应用设置-权限-中，允许定位权限", false));


        return view;
    }

    //切换到后台停止扫描
    @Override
    public void onPause() {
        super.onPause();

        if (isSearching) {
            if (bluetoothHelper != null) {
                bluetoothHelper.stopScan();
                bluetoothHelper.setDiscoverable(false, 1);
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.search_search_btn:
                //正在搜索，点击停止
                if (isSearching) {
                    if (bluetoothHelper != null) {
                        bluetoothHelper.stopScan();
                        bluetoothHelper.setDiscoverable(false, 1);
                    }
                } else {
                    //权限检查，大于23需要定位权限
                    if (Build.VERSION.SDK_INT >= 23)
                        PermissionHelper.getPermissionInFragment(this, Manifest.permission.ACCESS_FINE_LOCATION, 1000);
                    tryOpenAndScanBluetooth();
                }
                break;
            default:
                break;
        }
    }

    private void tryOpenAndScanBluetooth() {
        //检查是否支持蓝牙
        if (!bluetoothHelper.isSupport())
            DialogHelper.makeDialog(getContext(), "错误", "当前设备不支持蓝牙", false,
                    "确定", new DialogHelper.OnClickListener() {
                        @Override
                        public void onClick() {
                            getActivity().finish();
                        }
                    }, "取消", null).show();

        //检测蓝牙是否打开，没有打开则先打开蓝牙
        if (!bluetoothHelper.isOpened()) {
            if (!bluetoothHelper.open()) {
                //如果打开失败
                DialogHelper.makeDialog(getContext(), "蓝牙没有打开", "请允许程序打开蓝牙或在系统设置中打开蓝牙").show();
                return;
            }
            //等待蓝牙开启
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


        //API大于23需要开启GPS才能扫描
        if (Build.VERSION.SDK_INT > 23) {
            LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                DialogHelper.makeDialog(getContext(), "GPS没有打开", "Android 6.0以上需要开启GPS才能扫描", true,
                        "去打开", new DialogHelper.OnClickListener() {
                            @Override
                            public void onClick() {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(intent);
                            }
                        }, "取消", null).show();
            }
            //如果还没有打开则返回
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                return;
            }
        }

        //设置蓝牙可见性
        bluetoothHelper.setDiscoverable(true, 300);

        //蓝牙已打开，开始扫描
        searchBtn.setText(R.string.searching);
        isSearching = true;
        infoList.clear();
        listView.setData(infoList);

        bluetoothHelper.scan(new BluetoothHelper.OnScanListener() {
            @Override
            public void onStart() {

            }

            @Override
            public void onFound(BluetoothHelper.BluetoothInfo info) {
                infoList.add(info);
                listView.setData(infoList);
            }

            @Override
            public void onStop() {
                isSearching = false;
                searchBtn.setText(R.string.search_searchBegin);
                //防止蓝牙列表数据有变（经典蓝牙和BLE蓝牙都支持的设备在扫描BLE蓝牙后supportType会改为ALL，但不会调用onFound）
                listView.setData(infoList);
            }

            @Override
            public void onCancel() {
                isSearching = false;
                searchBtn.setText(R.string.search_searchBegin);
                //防止蓝牙列表数据有变（经典蓝牙和BLE蓝牙都支持的设备在扫描BLE蓝牙后supportType会改为ALL，但不会调用onFound）
                listView.setData(infoList);
            }

            @Override
            public void onError() {
                isSearching = false;
                searchBtn.setText(R.string.search_searchBegin);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1000:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    tryOpenAndScanBluetooth();
                } else if (permissionHelper.checkPermission()) {//拒绝的话尝试弹框提示用户原因
                    tryOpenAndScanBluetooth();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onItemClick(final BluetoothHelper.BluetoothInfo info) {
        //停止扫描
        bluetoothHelper.stopScan();

        //跳转到ConnectFragment
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).startConnect(info);
        }
    }
}