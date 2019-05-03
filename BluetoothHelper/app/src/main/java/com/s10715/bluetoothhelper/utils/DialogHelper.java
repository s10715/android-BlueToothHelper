package com.s10715.bluetoothhelper.utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.s10715.bluetoothhelper.R;

public class DialogHelper {

    //单行
    public static Dialog makeDialog(Context context, String tittle, String message) {
        return new AlertDialog.Builder(context)
                .setTitle(tittle)
                .setMessage(message)
                .setNegativeButton("确定", null)
                .create();
    }

    //多行
    public static Dialog makeDialog(Context context, String tittle, String[] messages) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        for (String message : messages) {
            TextView textView = new TextView(context);
            textView.setText(message);
            textView.setPadding(50, 20, 0, 0);

            linearLayout.addView(textView);
        }

        return new AlertDialog.Builder(context)
                .setTitle(tittle)
                .setView(linearLayout)
                .setNegativeButton("确定", null)
                .create();
    }


    //两个按钮、带监听
    public static Dialog makeDialog(Context context, String tittle, String message, boolean cancelable, String positive, final OnClickListener positiveListener, String negative, final OnClickListener negativeListener) {
        return new AlertDialog.Builder(context)
                .setTitle(tittle)
                .setMessage(message)
                .setNegativeButton(negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (negativeListener != null)
                            negativeListener.onClick();
                    }
                })
                .setPositiveButton(positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (positiveListener != null)
                            positiveListener.onClick();
                    }
                })
                .setCancelable(cancelable)
                .create();
    }

    //打开应用设置
    public static Dialog makeGoToSettingDialog(final Context context, String tittle, String message, boolean cancelable) {
        return new AlertDialog.Builder(context)
                .setTitle(tittle)
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
                        intent.setData(uri);
                        context.startActivity(intent);
                    }
                })
                .setCancelable(cancelable)
                .create();
    }


    public static Dialog makeLoadingDialog(Context context) {

        return new AlertDialog.Builder(context)
                .setView(R.layout.dialog_loading)
                .setCancelable(false)
                .create();
    }

    public static Dialog makeLoadingDialog(Context context, String hint) {
        View loading = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null, false);
        TextView text = loading.findViewById(R.id.dialog_loading_text);
        text.setText(hint);

        return new AlertDialog.Builder(context)
                .setView(loading)
                .setCancelable(false)
                .create();
    }

    //点其他区域不会消失，但按返回键会消失的Dialog，onBack代表用户按下了返回键
    public static Dialog makeLoadingDialog(Context context, String hint, final OnBackListener listener) {
        View loading = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null, false);
        TextView text = loading.findViewById(R.id.dialog_loading_text);
        text.setText(hint);

        return new AlertDialog.Builder(context)
                .setView(loading)
                .setCancelable(false)
                .setOnKeyListener(new DialogInterface.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
                            if (listener != null)
                                listener.onBack((Dialog) dialog);
                        }
                        return false;
                    }
                })
                .create();
    }


    public interface OnClickListener {
        void onClick();
    }

    public interface OnBackListener {
        void onBack(Dialog dialog);
    }
}
