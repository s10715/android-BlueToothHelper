package com.s10715.bluetoothhelper.utils;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class BluetoothHelper {
    //经典蓝牙连接使用的UUID，客户端必须使用相同的UUID才能连接成功
    public final static UUID DEFAULT_CLASSIC_UUID = UUID.fromString("af9465ff-2551-4c90-a2ad-f32e2452f7a5");

    //经典蓝牙的启动方式
    public final static int NOT_CLASSIC = 1;
    public final static int CLASSIC_CONNECT_AS_SERVER = 2;
    public final static int CLASSIC_CONNECT_AS_CLIENT = 3;
    public final static int CLASSIC_CONNECT_AS_SERVER_CLIENT = 4;

    //对外提供的连接状态常量，因为disconnect不需要知道当前连接的是什么类型的蓝牙，也就不需要分开描述
    public final static int DISCONNECTED = 1;
    public final static int CONNECTING = 2;
    public final static int CONNECTED = 3;
    public final static int DISCONNECTING = 4;

    //经典蓝牙连接状态
    private final static int SERVER_DISCONNECTED = 1;//服务器端已断开连接，或重来没有连接过任何设备
    private final static int SERVER_CONNECTING = 2;//服务器端正在连接中
    private final static int SERVER_CONNECTED = 3;//服务器端已连接
    private final static int SERVER_DISCONNECTING = 4;//服务器端正在断开连接
    private final static int CLIENT_DISCONNECTED = 5;//客户端已断开连接，或重来没有连接过任何设备
    private final static int CLIENT_CONNECTING = 6;//客户端正在连接中
    private final static int CLIENT_CONNECTED = 7;//客户端已连接
    private final static int CLIENT_DISCONNECTING = 8;//客户端正在断开连接

    //BLE蓝牙连接状态常量
    private final static int BLE_DISCONNECTED = 9;//已断开连接，或重来没有连接过任何设备
    private final static int BLE_CONNECTING = 10;//正在连接中
    private final static int BLE_CONNECTED = 11;//已断开连接
    private final static int BLE_DISCONNECTING = 12;//正在断开连接

    //经典蓝牙连接缓存
    private volatile int classicServerConnectionState = SERVER_DISCONNECTED;
    private volatile int classicClientConnectionState = CLIENT_DISCONNECTED;
    private BluetoothServerSocket classicServer_ServerSocket;//服务器端用于接收新连进来的连接的serverSocket，一般同一时间只有一个客户端连接，所以当得到classicServer_Socket以后，会close掉这个serverSocket
    private BluetoothSocket classicServer_Socket;//服务器端socket
    private BluetoothSocket classicClient_Socket;//客户端socket

    //BLE蓝牙连接缓存
    private volatile int bleConnectionState = BLE_DISCONNECTED;
    BluetoothGatt bleBluetoothGatt;


    //单例模式，获取实例前必须设置Context
    private static BluetoothHelper instance;
    private WeakReference<Activity> activity;
    private BluetoothAdapter bluetoothAdapter;

    private Handler handler;
    private BroadcastReceiver scanClassicReceiver;//用于接收经典蓝牙扫描的广播接收者
    private ScanCallback scanBleCallback;//用于接收BLE蓝牙的回调

    private volatile OnScanListener scanListener;//用户传过来的listener
    private volatile OnConnectListener connectListener;//用户传过来的listener
    private volatile OnReadListener readListener;//用户传过来的listener
    private volatile OnWriteListener writeListener;//用户传过来的listener

    //扫描到的设备集合，key是mac，在配对时需找到已扫描到的设备进行配对
    private HashMap<String, BluetoothInfo> scannedBluetoothInfo;

    private BluetoothHelper(Activity activity) {
        this.activity = new WeakReference<>(activity);

        //在主线程创建Handler，用于倒计时
        if (Looper.myLooper() != activity.getMainLooper())
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handler = new Handler();
                }
            });
        else
            handler = new Handler();

        scannedBluetoothInfo = new HashMap<>();
    }

    public static BluetoothHelper getInstance(Activity activity) {
        if (activity == null) {
            return null;
        }
        if (instance == null) {
            synchronized (BluetoothHelper.class) {
                if (instance == null) {
                    instance = new BluetoothHelper(activity);
                }
            }
        }
        return instance;
    }

    private BluetoothAdapter getBluetoothAdapter() {
        if (bluetoothAdapter == null)
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return bluetoothAdapter;
    }

    public boolean isSupport() {
        return getBluetoothAdapter() != null;
    }

    public boolean isOpened() {
        BluetoothAdapter adapter = getBluetoothAdapter();
        return adapter != null && adapter.getState() == BluetoothAdapter.STATE_ON;
    }

    /**
     * 开启蓝牙需要BLUETOOTH_ADMIN权限
     */
    public boolean open() {
        Activity activity = this.activity.get();
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null || activity == null)
            return false;

        if (isOpened())
            return true;

        if (!adapter.enable()) {
            //开启失败，让用户手动开启
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivity(enableBtIntent);
            return false;
        } else {
            return true;
        }
    }

    public boolean close() {
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null)
            return true;

        setDiscoverable(false, 1);
        stopScan();
        disconnect();
        return adapter.disable();
    }

    public boolean isDiscoverable() {
        BluetoothAdapter adapter = getBluetoothAdapter();
        return adapter != null && adapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
    }

    /**
     * @param discoverable 为false时，duration参数无效
     * @param duration     超过300则会自动变成300，为0表示永久可见，如果想取消可见性，应该设置为1，1秒后不可见（实际上无论duration为何值，都永久可见）
     */
    public void setDiscoverable(boolean discoverable, int duration) {
        Activity activity = this.activity.get();
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (activity == null || adapter == null)
            return;
        if (discoverable) {
            if (isDiscoverable())
                return;
            //弹框让用户确定是否设置可见
            /*Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
            activity.startActivity(discoverableIntent);*/

            //也可以使用隐藏API设置（不需要弹框）
            try {
                Method setDiscoverableTimeout = BluetoothAdapter.class.getMethod("setDiscoverableTimeout", int.class);
                setDiscoverableTimeout.setAccessible(true);
                Method setScanMode = BluetoothAdapter.class.getMethod("setScanMode", int.class, int.class);
                setScanMode.setAccessible(true);

                setDiscoverableTimeout.invoke(adapter, duration);
                setScanMode.invoke(adapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, duration);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            //设置不可见要用隐藏的API（不需要弹框）
            try {
                Method setDiscoverableTimeout = BluetoothAdapter.class.getMethod("setDiscoverableTimeout", int.class);
                setDiscoverableTimeout.setAccessible(true);
                Method setScanMode = BluetoothAdapter.class.getMethod("setScanMode", int.class);
                setScanMode.setAccessible(true);

                setDiscoverableTimeout.invoke(adapter, 1);
                setScanMode.invoke(adapter, BluetoothAdapter.SCAN_MODE_NONE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public void scan(OnScanListener listener) {
        scan(listener, 10, 15);
    }

    /**
     * 经典蓝牙和BLE蓝牙混合扫描
     * Android6.0以上需要开启GPS才能扫描，否则扫描不到设备
     * 由于这里使用了动态注册的BroadcastReceiver，需在onDestroy前使用stopScan动态反注册，防止内存泄露
     *
     * @param classicDuration 单位为秒
     * @param bleDuration     单位为秒
     */
    public void scan(OnScanListener listener, int classicDuration, final int bleDuration) {
        Activity activity = this.activity.get();
        BluetoothAdapter adapter = getBluetoothAdapter();

        if (activity == null || adapter == null)
            return;

        //清空扫描缓存
        scannedBluetoothInfo.clear();

        //如果重复扫描，要停止之前的扫描，清除之前的BroadcastReceiver，防止内存泄露
        if (adapter.isDiscovering() || scanBleCallback != null) {
            releaseScanResource(true, true, true);
        }

        //保存listener，因为stopScan时需调用对应回调
        scanListener = listener;

        //使用广播接收者接收经典蓝牙扫描结果
        scanClassicReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (scanListener == null)
                    return;

                switch (intent.getAction()) {
                    case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                        scanListener.onStart();
                        break;
                    case BluetoothDevice.ACTION_FOUND:
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                        String name;
                        if (device.getName() == null || "".equals(device.getName()))
                            name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                        else
                            name = device.getName();

                        ParcelUuid[] classicUUIDs = device.getUuids();
                        ArrayList<UUID> classicUUIDList = new ArrayList<>();
                        if (classicUUIDs != null)
                            for (ParcelUuid uuid : classicUUIDs)
                                classicUUIDList.add(uuid.getUuid());

                        BluetoothInfo info = new BluetoothInfo();
                        info.setSupportType(BluetoothInfo.SUPPORT_TYPE_CLASSIC);
                        info.setName(name);
                        info.setMac(device.getAddress());
                        info.setRssi(intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short) 0));
                        info.setClassicUUIDList(classicUUIDList);

                        //去重
                        if (scannedBluetoothInfo.get(info.getMac()) == null) {
                            scanListener.onFound(info);
                        }
                        //往HashMap中存入最新结果
                        scannedBluetoothInfo.put(device.getAddress(), info);
                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        //因为后面还有BLE蓝牙扫描，所以这里还没有stop
                        //scanListener.onStop();
                        //释放资源（如果停止扫描可能导致无限递归），仅需要解注册BroadcastReceiver
                        releaseScanResource(false, true, false);
                        break;
                    default:
                        break;
                }
            }
        };

        //BLE蓝牙扫描回调
        scanBleCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                if (scanListener != null) {// && callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                    BluetoothDevice device = result.getDevice();

                    String name;
                    if (device.getName() == null || "".equals(device.getName()) && result.getScanRecord() != null)
                        name = result.getScanRecord().getDeviceName();
                    else
                        name = device.getName();

                    BluetoothInfo info = scannedBluetoothInfo.get(device.getAddress());
                    //如果已经扫描过该设备，则更新数据
                    if (info != null) {
                        info = scannedBluetoothInfo.get(device.getAddress());
                        if (info.getSupportType() == BluetoothInfo.SUPPORT_TYPE_CLASSIC) {
                            info.setSupportType(BluetoothInfo.SUPPORT_TYPE_ALL);
                        }
                    } else {
                        info = new BluetoothInfo();
                        info.setSupportType(BluetoothInfo.SUPPORT_TYPE_BLE);
                    }

                    info.setName(name);
                    info.setMac(device.getAddress());
                    info.setRssi(result.getRssi());


                    //如果没有扫描过该设备，调用用户回调
                    if (scannedBluetoothInfo.get(info.getMac()) == null) {
                        scanListener.onFound(info);
                    }
                    scannedBluetoothInfo.put(info.getMac(), info);
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                if (scanListener != null)
                    scanListener.onError();
            }
        };

        //开始扫描经典蓝牙
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        activity.registerReceiver(scanClassicReceiver, filter);
        adapter.startDiscovery();

        //在classicDuration后停止经典蓝牙扫描，并开启BLE蓝牙扫描
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //停止扫描并释放资源
                releaseScanResource(true, true, false);


                BluetoothAdapter adapter = getBluetoothAdapter();
                if (adapter != null) {
                    adapter.getBluetoothLeScanner().startScan(scanBleCallback);

                    //在bleDurationn后停止BLE蓝牙扫描，并释放资源
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (scanListener != null) {
                                scanListener.onStop();
                            }
                            releaseScanResource(true, true, true);
                        }
                    }, bleDuration * 1000);
                }

            }
        }, classicDuration * 1000);

    }

    /**
     * 尽量在connect之前停止扫描，否则会降低连接效率
     * 调用该方法时，调用的回调是onCancel而非onStop
     * 可以重复调用，但onCancel只会被调用一次
     */
    public void stopScan() {
        if (scanListener != null) {
            scanListener.onCancel();
        }
        //停止扫描并释放资源
        releaseScanResource(true, true, true);
    }

    private void releaseScanResource(boolean cancelClassicDiscovery, boolean unregisterClassicReceiver, boolean cancelBleDiscovery) {
        Activity activity = this.activity.get();
        BluetoothAdapter adapter = getBluetoothAdapter();
        //取消经典蓝牙扫描
        if (cancelClassicDiscovery) {
            if (adapter != null && adapter.isDiscovering())
                adapter.cancelDiscovery();
        }
        //取消BLE蓝牙扫描
        if (cancelBleDiscovery) {
            if (scanBleCallback != null) {
                if (adapter != null && adapter.getBluetoothLeScanner() != null)
                    adapter.getBluetoothLeScanner().stopScan(scanBleCallback);
                scanBleCallback = null;
            }
        }
        //解注册BroadcastReceiver
        if (unregisterClassicReceiver) {
            if (scanClassicReceiver != null) {
                if (activity != null)
                    activity.unregisterReceiver(scanClassicReceiver);
                scanClassicReceiver = null;
            }
        }
        //如果经典蓝牙和BLE蓝牙都取消了，就没有需要用到用户回调的地方了，清空用户回调
        if (cancelClassicDiscovery && unregisterClassicReceiver && cancelBleDiscovery && scanListener != null) {
            handler.removeCallbacksAndMessages(null);
            scanListener = null;
        }
    }


    /**
     * 经典蓝牙连接，无需担心设备是否已配对，如果两台设备之前尚未配对，则在连接过程中，Android 框架会自动向用户显示配对请求通知或对话框
     *
     * @param connectType 可选值为BluetoothHelper#CLASSIC_CONNECT_AS_SERVER、BluetoothHelper#CLASSIC_CONNECT_AS_CLIENT、BluetoothHelper#CLASSIC_CONNECT_AS_SERVER_CLIENT
     */
    public void connect(String mac, final UUID classicUUID, int connectType, OnConnectListener listener) {
        final BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null)
            return;

        //连接需要消耗大量资源，应该在连接前停止扫描，否则会显著地降低连接速率，且很大程度上会连接失败
        stopScan();

        //如果之前正在连接，先停止
        if (classicServerConnectionState != SERVER_DISCONNECTED || classicClientConnectionState != CLIENT_DISCONNECTED || bleConnectionState != BLE_DISCONNECTED) {
            disconnect();
        }

        final BluetoothDevice device = adapter.getRemoteDevice(mac);

        this.connectListener = listener;

        //连接经典蓝牙，阻塞方法，必须开线程
        //服务器端
        Thread serverThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    classicServerConnectionState = SERVER_CONNECTING;
                    classicServer_ServerSocket = adapter.listenUsingRfcommWithServiceRecord(this.getClass().getSimpleName(), classicUUID);
                    while (classicServer_Socket == null) {
                        classicServer_Socket = classicServer_ServerSocket.accept();
                    }
                    classicServer_ServerSocket = null;
                    classicServerConnectionState = SERVER_CONNECTED;
                    //在主线执行回调
                    if (connectListener != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                connectListener.onConnected();
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    //如果12秒钟内未能成功连接，将会抛出一个异常
                    //timeout
                    classicServerConnectionState = SERVER_DISCONNECTED;
                    //在主线执行回调
                    if (connectListener != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                connectListener.onError();
                            }
                        });
                    }
                } finally {
                    try {
                        if (classicServer_ServerSocket != null)
                            classicServer_ServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        //客户端
        Thread clientThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    stopScan();

                    classicClientConnectionState = CLIENT_CONNECTING;
                    classicClient_Socket = device.createRfcommSocketToServiceRecord(classicUUID);
                    classicClient_Socket.connect();
                    classicClientConnectionState = CLIENT_CONNECTED;
                    //在主线执行回调
                    if (connectListener != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                connectListener.onConnected();
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    classicClientConnectionState = CLIENT_DISCONNECTED;
                    //在主线执行回调
                    if (connectListener != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                connectListener.onError();
                            }
                        });
                    }
                }
            }
        });
        switch (connectType) {
            case CLASSIC_CONNECT_AS_CLIENT:
                clientThread.start();
                break;
            case CLASSIC_CONNECT_AS_SERVER:
                serverThread.start();
                break;
            case CLASSIC_CONNECT_AS_SERVER_CLIENT:
                clientThread.start();
                serverThread.start();
                break;
            default:
                break;
        }

    }


    //BLE蓝牙连接
    public void connect(String mac, OnConnectListener listener) {
        final BluetoothAdapter adapter = getBluetoothAdapter();
        Activity activity = this.activity.get();
        if (adapter == null || activity == null)
            return;

        //连接需要消耗大量资源，应该在连接前停止扫描，否则会显著地降低连接速率，且很大程度上会连接失败
        stopScan();

        //如果之前正在连接，先停止
        if (classicServerConnectionState != SERVER_DISCONNECTED || classicClientConnectionState != CLIENT_DISCONNECTED || bleConnectionState != BLE_DISCONNECTED) {
            disconnect();
        }

        final BluetoothDevice device = adapter.getRemoteDevice(mac);

        this.connectListener = listener;

        bleConnectionState = BLE_CONNECTING;
        bleBluetoothGatt = device.connectGatt(activity, true, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                switch (status) {
                    case BluetoothGatt.GATT_SUCCESS:
                        //此时newStatus才有效
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            bleConnectionState = BLE_CONNECTED;
                            //在主线程执行回调
                            if (connectListener != null) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        connectListener.onConnected();
                                    }
                                });
                            }
                            //连接成功不代表可以通信，只有当onServicesDiscovered被回调时才可以开始通信
                            gatt.discoverServices();
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            bleConnectionState = BLE_DISCONNECTED;
                            //在主线程执行回调
                            if (connectListener != null) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        connectListener.onDisconnect();
                                    }
                                });
                            }
                        }
                        break;
                    case 133:
                        //超过连接数限制
                        bleConnectionState = BLE_DISCONNECTED;
                        //在主线程执行回调
                        if (connectListener != null) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    connectListener.onError();
                                }
                            });
                        }
                    default:
                        gatt.close();
                        break;
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                final List<BluetoothGattService> serviceList = gatt.getServices();
                if (connectListener != null && serviceList != null && serviceList.size() > 0) {
                    //在主线程执行回调
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            connectListener.onFoundBleUUIDs(serviceList);
                        }
                    });
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (readListener != null) {
                        //在主线程执行回调
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                readListener.onReceived(characteristic.getValue());
                            }
                        });
                    }
                } else {
                    //在主线程执行回调
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            readListener.onError();
                        }
                    });
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (writeListener != null) {
                        //在主线程执行回调
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                writeListener.onSuccess();
                            }
                        });
                    }
                } else {
                    if (writeListener != null) {
                        //在主线程执行回调
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                writeListener.onError();
                            }
                        });
                    }
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
            }


        });


    }

    /**
     * 只要有一个正在连接，都认为还没有完成连接，状态都是正在连接中
     * 如果有一个已连接，一个未连接，都认为已连接
     */
    public int getConnectionState() {
        if (classicServerConnectionState == SERVER_CONNECTING || classicClientConnectionState == CLIENT_CONNECTING || bleConnectionState == BLE_DISCONNECTING)
            return CONNECTING;
        else if (classicServerConnectionState == SERVER_CONNECTED || classicClientConnectionState == CLIENT_CONNECTED || bleConnectionState == BLE_CONNECTED)
            return CONNECTED;
        else if (classicServerConnectionState == SERVER_DISCONNECTING || classicClientConnectionState == CLIENT_DISCONNECTING || bleConnectionState == BLE_DISCONNECTING)
            return DISCONNECTING;
        else
            return DISCONNECTED;
    }

    /**
     * 断开连接，包括经典蓝牙和BLE蓝牙
     * 可以重复调用，但对应的回调只会被调用一次
     */
    public void disconnect() {
        //关闭经典蓝牙的服务器端
        if (classicServerConnectionState != SERVER_DISCONNECTED) {
            classicServerConnectionState = SERVER_DISCONNECTING;
            if (classicServer_ServerSocket != null) {
                try {
                    classicServer_ServerSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                classicServer_ServerSocket = null;
            }

            if (classicServer_Socket != null) {
                try {
                    classicServer_Socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                classicServer_Socket = null;
            }
            classicServerConnectionState = SERVER_DISCONNECTED;
        }
        //关闭经典蓝牙的客户端
        if (classicClientConnectionState != CLIENT_DISCONNECTED) {
            classicClientConnectionState = CLIENT_DISCONNECTING;
            if (classicClient_Socket != null) {
                try {
                    classicClient_Socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                classicClient_Socket = null;
            }
            classicClientConnectionState = CLIENT_DISCONNECTED;
        }
        //断开BLE蓝牙
        if (bleConnectionState != BLE_DISCONNECTED) {
            bleConnectionState = BLE_DISCONNECTING;
            if (bleBluetoothGatt != null) {
                bleBluetoothGatt.disconnect();
                bleBluetoothGatt.close();
                bleBluetoothGatt = null;
            }
            bleConnectionState = BLE_DISCONNECTED;
        }

        if (connectListener != null) {
            //connectListener.onDisconnect();
            connectListener = null;
        }
        if (readListener != null) {
            readListener = null;
        }
        if (writeListener != null) {
            writeListener = null;
        }
    }

    /**
     * 经典蓝牙读取
     * 如果没有使用本类建立连接，将读不到任何东西
     * 只需设置一次，后续如果有新数据都会调用OnReadListener.onReceived(byte[])
     */
    public void read(OnReadListener listener) {
        if (classicServer_Socket != null) {
            this.readListener = listener;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStream inputStream = classicServer_Socket.getInputStream();
                        byte[] buffer = new byte[1024];
                        int bytes = 0;
                        while ((bytes = inputStream.read(buffer)) != -1) {
                            //经测试，read方法一直不会停止（除非关闭连接时抛出异常），没有读到数据时不应该调用用户回调
                            if (bytes == 0) {
                                continue;
                            }
                            //在主线程执行回调
                            if (readListener != null) {
                                final byte[] finalBuffer = buffer;
                                final int finalBytes = bytes;
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        readListener.onReceived(Arrays.copyOf(finalBuffer, finalBytes));
                                    }
                                });
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        //在主线程执行回调
                        if (readListener != null) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    //外部关闭连接会抛出异常，但没有读写出错
                                    if (classicServer_Socket == null || !classicServer_Socket.isConnected())
                                        readListener.onClose();
                                    else
                                        readListener.onError();
                                }
                            });
                        }
                    }

                }
            }).start();

        } else if (classicClient_Socket != null) {
            this.readListener = listener;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStream inputStream = classicClient_Socket.getInputStream();
                        byte[] buffer = new byte[1024];
                        int bytes = 0;
                        while ((bytes = inputStream.read(buffer)) != -1) {
                            //经测试，read方法一直不会停止（除非关闭连接时抛出异常），没有读到数据时不应该调用用户回调
                            if (bytes == 0) {
                                continue;
                            }
                            //在主线程执行回调
                            if (readListener != null) {
                                final byte[] finalBuffer = buffer;
                                final int finalBytes = bytes;
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        readListener.onReceived(Arrays.copyOf(finalBuffer, finalBytes));
                                    }
                                });
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        //在主线程执行回调
                        if (readListener != null) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    readListener.onError();
                                }
                            });
                        }
                    }

                }
            }).start();
        }
    }

    /**
     * 经典蓝牙写入
     */
    public void write(final byte[] data, OnWriteListener listener) {
        if (data == null)
            return;

        this.writeListener = listener;

        if (classicServer_Socket != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        OutputStream outputStream = classicServer_Socket.getOutputStream();
                        outputStream.write(data);
                        //在主线程执行回调
                        if (writeListener != null) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    writeListener.onSuccess();
                                }
                            });
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        //在主线程执行回调
                        if (writeListener != null) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    writeListener.onError();
                                }
                            });
                        }
                    }

                }
            }).start();

        } else if (classicClient_Socket != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        OutputStream outputStream = classicClient_Socket.getOutputStream();
                        outputStream.write(data);
                        //在主线程执行回调
                        if (writeListener != null) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    writeListener.onSuccess();
                                }
                            });
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        //在主线程执行回调
                        if (writeListener != null) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    writeListener.onError();
                                }
                            });
                        }
                    }

                }
            }).start();
        }
    }


    /**
     * ble蓝牙读取
     */
    public void read(UUID serviceUUID, UUID characterUUID, final OnReadListener listener) {
        if (bleBluetoothGatt == null)
            return;

        this.readListener = listener;

        BluetoothGattService service = bleBluetoothGatt.getService(serviceUUID);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characterUUID);
        bleBluetoothGatt.readCharacteristic(characteristic);
    }

    //ble蓝牙写入
    public void write(UUID serviceUUID, UUID characterUUID, byte[] data, final OnWriteListener listener) {
        if (bleBluetoothGatt == null)
            return;
        if (data == null)
            return;

        this.writeListener = listener;

        BluetoothGattService service = bleBluetoothGatt.getService(serviceUUID);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characterUUID);
        characteristic.setValue(data);
        bleBluetoothGatt.writeCharacteristic(characteristic);
    }


    public interface OnScanListener {
        void onStart();

        void onFound(BluetoothInfo info);

        void onStop();

        void onCancel();

        void onError();
    }

    public interface OnConnectListener {
        void onConnected();

        void onFoundBleUUIDs(List<BluetoothGattService> serviceList);

        void onError();

        void onDisconnect();
    }

    public interface OnReadListener {
        void onReceived(byte[] data);

        void onError();

        void onClose();
    }

    public interface OnWriteListener {
        void onSuccess();

        void onError();
    }


    public static class BluetoothInfo {
        public static final int SUPPORT_TYPE_CLASSIC = 1;
        public static final int SUPPORT_TYPE_BLE = 2;
        public static final int SUPPORT_TYPE_ALL = 3;//同时支持经典蓝牙和BLE蓝牙

        private int supportType;
        private String name;
        private String mac;
        private int rssi;//信号强度
        private ArrayList<UUID> classicUUIDList;

        public int getSupportType() {
            return supportType;
        }

        public void setSupportType(int supportType) {
            this.supportType = supportType;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            if (name == null || "".equals(name))
                name = "未知";
            this.name = name;
        }

        public String getMac() {
            return mac;
        }

        public void setMac(String mac) {
            this.mac = mac;
        }

        public int getRssi() {
            return rssi;
        }

        public void setRssi(int rssi) {
            //把dbm转成百分比(rssi 原取值范围为-127 ~ 128，负得越多信号越好)
            rssi = (int) (-rssi + 127 / 256.0);
            this.rssi = rssi;
        }

        public ArrayList<UUID> getClassicUUIDList() {
            return classicUUIDList;
        }

        public void setClassicUUIDList(ArrayList<UUID> classicUUIDList) {
            this.classicUUIDList = classicUUIDList;
        }

    }

}
