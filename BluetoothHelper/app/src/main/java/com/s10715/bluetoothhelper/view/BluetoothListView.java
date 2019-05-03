package com.s10715.bluetoothhelper.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.s10715.bluetoothhelper.R;
import com.s10715.bluetoothhelper.utils.BluetoothHelper;

import java.util.ArrayList;

public class BluetoothListView extends ListView {

    private BluetoothListViewAdapter adapter;
    private OnItemClickListener listener;

    public BluetoothListView(Context context) {
        this(context, null);
    }

    public BluetoothListView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BluetoothListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        View headerView = LayoutInflater.from(context).inflate(R.layout.bluetooth_listview_item, null, false);
        ((TextView) headerView.findViewById(R.id.bluetooth_listview_item_name)).setText("蓝牙名称");
        ((TextView) headerView.findViewById(R.id.bluetooth_listview_item_mac)).setText("MAC");
        ((TextView) headerView.findViewById(R.id.bluetooth_listview_item_rssi)).setText("强度");
        addHeaderView(headerView);

    }

    public void setData(ArrayList<BluetoothHelper.BluetoothInfo> infoList) {
        if (adapter == null) {
            adapter = new BluetoothListViewAdapter();
            setAdapter(adapter);
        }

        adapter.setDevices(infoList);
        adapter.notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public interface OnItemClickListener {
        void onItemClick(BluetoothHelper.BluetoothInfo info);
    }

    private class BluetoothListViewAdapter extends BaseAdapter {
        ArrayList<BluetoothHelper.BluetoothInfo> infoList;

        private void setDevices(ArrayList<BluetoothHelper.BluetoothInfo> infoList) {
            this.infoList = infoList;
        }

        @Override
        public int getCount() {
            if (infoList != null)
                return infoList.size();
            else
                return 0;
        }

        @Override
        public Object getItem(int position) {
            if (infoList != null)
                return infoList.get(position);
            else
                return null;
        }

        @Override
        public long getItemId(int position) {
            if (infoList != null)
                return position;
            else
                return 0;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.bluetooth_listview_item, null);
                ViewHolder holder = new ViewHolder();
                holder.name = convertView.findViewById(R.id.bluetooth_listview_item_name);
                holder.mac = convertView.findViewById(R.id.bluetooth_listview_item_mac);
                holder.rssi = convertView.findViewById(R.id.bluetooth_listview_item_rssi);
                convertView.setTag(holder);
            }
            if (infoList != null) {
                ViewHolder holder = (ViewHolder) convertView.getTag();
                //根据不同的蓝牙类型（经典、BLE）设置不同颜色
                if (infoList.get(position).getSupportType() == BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_CLASSIC)
                    holder.rssi.setTextColor(0xFF2196F3);
                else if (infoList.get(position).getSupportType() == BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_BLE)
                    holder.rssi.setTextColor(0xFF3F51B5);
                else if (infoList.get(position).getSupportType() == BluetoothHelper.BluetoothInfo.SUPPORT_TYPE_ALL)
                    holder.rssi.setTextColor(0xFF000000);

                holder.name.setText(infoList.get(position).getName());
                holder.mac.setText(infoList.get(position).getMac());
                holder.rssi.setText(String.valueOf(infoList.get(position).getRssi()));
            }

            if (listener != null) {
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onItemClick(infoList.get(position));
                    }
                });
            } else {
                convertView.setOnClickListener(null);
            }
            return convertView;
        }

        private class ViewHolder {
            TextView name;
            TextView mac;
            TextView rssi;
        }
    }
}
