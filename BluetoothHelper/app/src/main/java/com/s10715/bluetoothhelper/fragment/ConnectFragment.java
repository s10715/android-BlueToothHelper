package com.s10715.bluetoothhelper.fragment;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import com.s10715.bluetoothhelper.MainActivity;
import com.s10715.bluetoothhelper.R;
import com.s10715.bluetoothhelper.utils.BluetoothHelper;
import com.s10715.bluetoothhelper.utils.DialogHelper;

import java.util.List;
import java.util.UUID;

public class ConnectFragment extends Fragment implements View.OnClickListener {


    private TextView supportTypeHint;

    private EditText nameEditText;
    private EditText macEditText;

    private RadioButton classicBtn;
    private RadioButton bleBtn;

    private LinearLayout classicUUIDLayout;
    private EditText classicUUIDEditText;
    private CheckBox asServer;
    private CheckBox asClient;

    private LinearLayout serviceUUIDLayout;
    private EditText serviceUUIDEditText;
    private LinearLayout characterUUIDLayout;
    private EditText characterUUIDEditText;

    private Button connectBtn;
    private Button findSupportUUID;//BLE蓝牙查找支持的serviceUUID和characterUUID
    private Button startTransferBtn;


    private BluetoothHelper bluetoothHelper;

    private Dialog loadingDialog;

    //防止由于内存不足或instant run时，出现错乱
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isClassic", classicBtn.isChecked());
    }

    //切换到后台停止连接
    @Override
    public void onPause() {
        super.onPause();

        if (bluetoothHelper != null && bluetoothHelper.getConnectionState() == BluetoothHelper.CONNECTING) {
            bluetoothHelper.disconnect();
            connectBtn.setText(R.string.connect_connect);
            findSupportUUID.setText(R.string.connect_finduuids);
            startTransferBtn.setEnabled(false);
            loadingDialog.dismiss();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connect, container, false);

        supportTypeHint = view.findViewById(R.id.connect_supporttypehint);

        nameEditText = view.findViewById(R.id.connect_name);
        macEditText = view.findViewById(R.id.connect_mac);

        classicBtn = view.findViewById(R.id.connect_radio_classic);
        bleBtn = view.findViewById(R.id.connect_radio_ble);

        classicUUIDLayout = view.findViewById(R.id.connect_clasicuuid_layout);
        classicUUIDEditText = view.findViewById(R.id.connect_clasicuuid);
        asServer = view.findViewById(R.id.connect_asserver);
        asClient = view.findViewById(R.id.connect_asclient);

        serviceUUIDLayout = view.findViewById(R.id.connect_serviceuuid_layout);
        serviceUUIDEditText = view.findViewById(R.id.connect_serviceuuid);
        characterUUIDLayout = view.findViewById(R.id.connect_characteruuid_layout);
        characterUUIDEditText = view.findViewById(R.id.connect_characteruuid);

        connectBtn = view.findViewById(R.id.connect_disconnect);
        findSupportUUID = view.findViewById(R.id.connect_findsupportuuid);
        startTransferBtn = view.findViewById(R.id.connect_starttransfer);


        bluetoothHelper = BluetoothHelper.getInstance(getActivity());

        classicBtn.setOnClickListener(this);
        bleBtn.setOnClickListener(this);

        connectBtn.setOnClickListener(this);
        findSupportUUID.setOnClickListener(this);
        startTransferBtn.setOnClickListener(this);

        startTransferBtn.setEnabled(false);

        //设置默认值
        classicUUIDEditText.setText(MainActivity.DEFAULT_CLASSIC_UUID);
        serviceUUIDEditText.setText(MainActivity.DEFAULT_SERVICE_UUID);
        characterUUIDEditText.setText(MainActivity.DEFAULT_CHARACTER_UUID);
        asServer.setChecked(true);

        showClassicLayout();

        //防止由于内存不足或instant run时，出现错乱
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean("isClassic"))
                showClassicLayout();
            else
                showBleLayout();
        }
        return view;
    }

    @Override
    public void onClick(View v) {
        if (v == classicBtn) {
            showClassicLayout();
        } else if (v == bleBtn) {
            showBleLayout();
        } else if (v == connectBtn || v == findSupportUUID) {
            final String name = nameEditText.getText().toString();
            final String mac = macEditText.getText().toString();
            final String classic = classicUUIDEditText.getText().toString();
            final String service = serviceUUIDEditText.getText().toString();
            final String character = characterUUIDEditText.getText().toString();


            switch (bluetoothHelper.getConnectionState()) {
                case BluetoothHelper.DISCONNECTED://如果还没有连接，则进行连接
                    //参数检查
                    String[] error = checkDataValidate(mac, classic, service, character);
                    if (classicBtn.isChecked()) {
                        if (!"".equals(error[0]) || !"".equals(error[1])) {
                            DialogHelper.makeDialog(getContext(), "错误", new String[]{error[0], error[1]}).show();
                            return;
                        }

                    } else if (bleBtn.isChecked()) {
                        if (!"".equals(error[0])) {
                            DialogHelper.makeDialog(getContext(), "错误", new String[]{error[0]}).show();
                            return;
                        }
                    }

                    connectBtn.setText(R.string.connect_connecting);
                    findSupportUUID.setText(R.string.connect_connecting);
                    startTransferBtn.setEnabled(false);

                    loadingDialog = DialogHelper.makeLoadingDialog(getContext(), "正在连接", new DialogHelper.OnBackListener() {
                        @Override
                        public void onBack(Dialog dialog) {
                            bluetoothHelper.disconnect();
                            connectBtn.setText(R.string.connect_connect);
                            findSupportUUID.setText(R.string.connect_finduuids);
                            startTransferBtn.setEnabled(false);
                            dialog.dismiss();
                        }
                    });
                    BluetoothHelper.OnConnectListener onConnectListener = new BluetoothHelper.OnConnectListener() {
                        @Override
                        public void onConnected() {
                            loadingDialog.dismiss();

                            connectBtn.setText(R.string.connect_disconnect);
                            findSupportUUID.setText(R.string.connect_disconnect);

                            if (getActivity() instanceof MainActivity) {
                                if (classicBtn.isChecked()) {
                                    ((MainActivity) getActivity()).startTransfer(name, mac, BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_CLASSIC, UUID.fromString(classic), null, null);

                                }
                            }
                        }

                        @Override
                        public void onFoundBleUUIDs(List<BluetoothGattService> serviceList) {
                            startTransferBtn.setEnabled(true);
                            //TODO
                            for (BluetoothGattService service : serviceList) {
                                Log.e("TAG", service.getUuid().toString());
                                List<BluetoothGattCharacteristic> characterList = service.getCharacteristics();
                                for (BluetoothGattCharacteristic character : characterList) {
                                    Log.e("TAG", "\t" + character.getUuid().toString());
                                }
                            }
                            try {
                                if (!serviceUUIDEditText.getText().toString().equals(MainActivity.DEFAULT_SERVICE_UUID))
                                    serviceUUIDEditText.setText(serviceList.get(0).getUuid().toString());
                                if (!characterUUIDEditText.getText().toString().equals(MainActivity.DEFAULT_CHARACTER_UUID))
                                    characterUUIDEditText.setText(serviceList.get(0).getCharacteristics().get(0).getUuid().toString());
                            } catch (NullPointerException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onError() {
                            loadingDialog.dismiss();

                            connectBtn.setText(R.string.connect_connect);
                            findSupportUUID.setText(R.string.connect_finduuids);
                            startTransferBtn.setEnabled(false);

                            DialogHelper.makeDialog(getContext(), "失败", "连接失败").show();
                        }

                        @Override
                        public void onDisconnect() {
                            connectBtn.setText(R.string.connect_connect);
                            findSupportUUID.setText(R.string.connect_finduuids);
                            startTransferBtn.setEnabled(false);
                            if (getActivity() instanceof MainActivity)
                                ((MainActivity) getActivity()).stopTransfer();
                        }
                    };

                    if (classicBtn.isChecked()) {
                        int type;
                        if (asServer.isChecked() && asClient.isChecked())
                            type = BluetoothHelper.CLASSIC_CONNECT_AS_SERVER_CLIENT;
                        else if (asServer.isChecked())
                            type = BluetoothHelper.CLASSIC_CONNECT_AS_SERVER;
                        else if (asClient.isChecked())
                            type = BluetoothHelper.CLASSIC_CONNECT_AS_CLIENT;
                        else {
                            DialogHelper.makeDialog(getContext(), "错误", "至少选择一种启动方式").show();
                            return;
                        }

                        //连接经典蓝牙
                        loadingDialog.show();
                        bluetoothHelper.connect(mac, UUID.fromString(classic), type, onConnectListener);
                    } else if (bleBtn.isChecked()) {
                        //连接BLE蓝牙
                        loadingDialog.show();
                        bluetoothHelper.connect(mac, onConnectListener);
                    }
                    break;
                case BluetoothHelper.CONNECTING:
                case BluetoothHelper.CONNECTED:
                    bluetoothHelper.disconnect();
                    connectBtn.setText(R.string.connect_connect);
                    findSupportUUID.setText(R.string.connect_finduuids);
                    break;
                default:
                    break;
            }
        } else if (v == startTransferBtn) {
            final String name = nameEditText.getText().toString();
            final String mac = macEditText.getText().toString();
            final String service = serviceUUIDEditText.getText().toString();
            final String character = characterUUIDEditText.getText().toString();

            String[] error = checkDataValidate(mac, null, service, character);

            if (!"".equals(error[0]) || !"".equals(error[2]) || !"".equals(error[3])) {
                DialogHelper.makeDialog(getContext(), "错误", new String[]{error[0], error[2], error[3]}).show();
                return;
            }

            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).startTransfer(name, mac, BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_BLE, null, UUID.fromString(service), UUID.fromString(character));
        }

    }

    private void showClassicLayout() {
        classicBtn.setChecked(true);
        classicUUIDLayout.setVisibility(View.VISIBLE);
        serviceUUIDLayout.setVisibility(View.GONE);
        characterUUIDLayout.setVisibility(View.GONE);
    }

    private void showBleLayout() {
        bleBtn.setChecked(true);
        classicUUIDLayout.setVisibility(View.GONE);
        serviceUUIDLayout.setVisibility(View.VISIBLE);
        characterUUIDLayout.setVisibility(View.VISIBLE);
    }

    private String[] checkDataValidate(String mac, String classicUUID, String serviceUUID, String characterUUID) {
        //连接参数格式检查
        String[] error = {"", "", "", ""};
        if ("".equals(mac)) {
            error[0] = "缺少mac";
        } else if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            error[0] = "请输入正确的MAC地址（注意：英文字母必须大写）";
        }

        if ("".equals(classicUUID)) {
            error[1] = "缺少蓝牙UUID";
        } else if (classicUUID.split("-").length != 5) {//检查UUID格式
            error[1] = "蓝牙UUID格式错误";
        }

        if ("".equals(serviceUUID)) {
            error[2] = "缺少serviceUUID";
        } else if (serviceUUID.split("-").length != 5) {//检查UUID格式
            error[2] = "serviceUUID格式错误";
        }

        if ("".equals(characterUUID)) {
            error[3] = "缺少characterUUID";
        } else if (characterUUID.split("-").length != 5) {//检查UUID格式
            error[3] = "characterUUID格式错误";
        }

        return error;
    }

    public void setData(BluetoothHelper.BluetoothInfo info) {
        if (info == null)
            return;

        nameEditText.setText(info.getName() == null ? "" : info.getName());
        macEditText.setText(info.getMac() == null ? "" : info.getMac());

        switch (info.getSupportType()) {
            case BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_CLASSIC:
                supportTypeHint.setText("该设备仅支持经典蓝牙");
                showClassicLayout();
                break;
            case BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_BLE:
                supportTypeHint.setText("该设备仅支持BLE蓝牙");
                showBleLayout();
                break;
            case BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_ALL:
                supportTypeHint.setText("该设备同时支持经典蓝牙和BLE蓝牙，建议使用BLE蓝牙方式连接");
                showBleLayout();
                break;
            default:
                supportTypeHint.setText("");
                break;
        }
    }
}
