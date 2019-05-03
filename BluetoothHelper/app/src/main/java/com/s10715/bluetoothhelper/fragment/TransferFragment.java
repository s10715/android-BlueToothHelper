package com.s10715.bluetoothhelper.fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.s10715.bluetoothhelper.R;
import com.s10715.bluetoothhelper.utils.BluetoothHelper;
import com.s10715.bluetoothhelper.utils.DialogHelper;
import com.s10715.bluetoothhelper.utils.PermissionHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

public class TransferFragment extends Fragment implements View.OnClickListener {

    private String mac;
    private int connectType;
    private UUID serviceUUID;
    private UUID characterUUID;

    private TextView nameTextView;
    private TextView macTextView;

    private EditText readEditText;
    private Button readReadBtn;
    private Button readSaveBtn;
    private Button readCleanBtn;

    private EditText writeEditText;
    private Button writeWriteBtn;
    private Button writeClearBtn;

    private PermissionHelper permissionHelper;//存储权限检查

    private BluetoothHelper bluetoothHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transfer, container, false);

        nameTextView = view.findViewById(R.id.transfer_name);
        macTextView = view.findViewById(R.id.transfer_mac);

        readEditText = view.findViewById(R.id.transfer_read_content);
        readReadBtn = view.findViewById(R.id.transfer_read_readbtn);
        readSaveBtn = view.findViewById(R.id.transfer_read_savebtn);
        readCleanBtn = view.findViewById(R.id.transfer_read_clearbtn);

        writeEditText = view.findViewById(R.id.transfer_write_content);
        writeWriteBtn = view.findViewById(R.id.transfer_write_writebtn);
        writeClearBtn = view.findViewById(R.id.transfer_write_clearbtn);


        bluetoothHelper = BluetoothHelper.getInstance(getActivity());
        permissionHelper = new PermissionHelper(getActivity())
                .addPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .addTipsDialog(DialogHelper.makeDialog(getContext(), "存储权限不可用", "需要使用存储权限才能保存文件", false,
                        "立即打开", new DialogHelper.OnClickListener() {
                            @Override
                            public void onClick() {
                                PermissionHelper.getPermissionInFragment(TransferFragment.this, Manifest.permission.WRITE_EXTERNAL_STORAGE, 1001);
                            }
                        }, "取消", null))
                .addWarnDialog(DialogHelper.makeGoToSettingDialog(getContext(), "存储权限不可用", "请在应用设置-权限-中，允许存储权限", false));


        readReadBtn.setOnClickListener(this);
        readSaveBtn.setOnClickListener(this);
        readCleanBtn.setOnClickListener(this);
        writeWriteBtn.setOnClickListener(this);
        writeClearBtn.setOnClickListener(this);


        //设置不可编辑
        nameTextView.setKeyListener(null);
        macTextView.setKeyListener(null);
        readEditText.setKeyListener(null);

        return view;
    }

    @Override
    public void onClick(View v) {

        String writeContent = writeEditText.getText().toString();

        if (v == readReadBtn) {
            switch (connectType) {
                case BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_CLASSIC:
                    readOnClassicMode();
                    break;
                case BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_BLE:
                case BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_ALL:
                    readOnBleMode();
                    break;
                default:
                    break;
            }


        } else if (v == writeWriteBtn) {
            switch (connectType) {
                case BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_CLASSIC:
                    writeOnClassicMode(writeContent);
                    break;
                case BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_BLE:
                case BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_ALL:
                    writeOnBleMode(writeContent);
                    break;
                default:
                    break;
            }

        } else if (v == readSaveBtn) {
            //权限检查，大于23需要动态获取
            if (Build.VERSION.SDK_INT >= 23)
                PermissionHelper.getPermissionInFragment(this, Manifest.permission.WRITE_EXTERNAL_STORAGE, 1001);
            saveReadContent();
        } else if (v == readCleanBtn) {
            readEditText.setText("");
        } else if (v == writeClearBtn) {
            writeEditText.setText("");
        }
    }


    private void readOnClassicMode() {
        bluetoothHelper.read(new BluetoothHelper.OnReadListener() {
            @Override
            public void onReceived(byte[] data) {
                String text = readEditText.getText().toString() + "\n" + decodeData(data);
                readEditText.setText(text);
            }

            @Override
            public void onError() {
                DialogHelper.makeDialog(getContext(), "错误", "读取失败").show();
            }

            @Override
            public void onClose() {

            }
        });
    }

    private void writeOnClassicMode(String writeContent) {

        bluetoothHelper.write(encodeData(writeContent), new BluetoothHelper.OnWriteListener() {
            @Override
            public void onSuccess() {
                DialogHelper.makeDialog(getContext(), "成功", "写入成功").show();
            }

            @Override
            public void onError() {
                DialogHelper.makeDialog(getContext(), "错误", "写入失败").show();
            }
        });
    }

    private void readOnBleMode() {

        bluetoothHelper.read(serviceUUID, characterUUID, new BluetoothHelper.OnReadListener() {
            @Override
            public void onReceived(byte[] data) {
                String text = readEditText.getText().toString() + "\n" + decodeData((data));
                readEditText.setText(text);
            }

            @Override
            public void onError() {
                DialogHelper.makeDialog(getContext(), "错误", "读取失败").show();
            }

            @Override
            public void onClose() {

            }
        });
    }

    private void writeOnBleMode(String writeContent) {
        bluetoothHelper.write(serviceUUID, characterUUID, encodeData(writeContent), new BluetoothHelper.OnWriteListener() {
            @Override
            public void onSuccess() {
                DialogHelper.makeDialog(getContext(), "成功", "写入成功").show();
            }

            @Override
            public void onError() {
                DialogHelper.makeDialog(getContext(), "错误", "写入失败").show();
            }
        });
    }

    //保存读取框中的内容到文件
    private void saveReadContent() {
        //保存文件
        String content = readEditText.getText().toString();
        if (!"".equals(content)) {
            try {
                File file = new File(getContext().getExternalFilesDir(null) + File.separator + "BluetoothHelper" + File.separator + "characteristic.txt");
                //如果文件夹不存在则创建文件夹
                if (!file.getParentFile().exists()) {
                    if (!file.getParentFile().mkdir()) {
                        DialogHelper.makeDialog(getContext(), "出错", "本地文件创建失败，请检查是否授予存储权限").show();
                        return;
                    }
                }

                FileOutputStream fos = new FileOutputStream(file, true);
                fos.write("\n".getBytes());
                fos.write(content.getBytes());
                fos.flush();
                fos.close();
            } catch (IOException e) {
                DialogHelper.makeDialog(getContext(), "出错", "写入文件时出错").show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1001:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    saveReadContent();
                } else if (permissionHelper.checkPermission()) {//拒绝的话尝试弹框提示用户原因
                    saveReadContent();
                }
                break;
            default:
                break;
        }
    }

    public void setData(String name, String mac, int connectType, UUID serviceUUID, UUID characterUUID) {
        this.mac = mac;
        this.connectType = connectType;
        this.serviceUUID = serviceUUID;
        this.characterUUID = characterUUID;

        this.nameTextView.setText(name);
        this.macTextView.setText(mac);
    }

    public void stopTransfer() {
        this.nameTextView.setText(R.string.transfer_disconnect);
        this.macTextView.setText(R.string.transfer_disconnect);
    }


    //读取/写入框中的数据和要传输的数据之间的转换
    private String decodeData(byte[] data) {
        return new String(data);
    }

    private byte[] encodeData(String data) {
        return data.getBytes();
    }
}
