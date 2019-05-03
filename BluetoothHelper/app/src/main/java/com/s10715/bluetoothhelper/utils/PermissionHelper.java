package com.s10715.bluetoothhelper.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;

public class PermissionHelper {

    private WeakReference<Activity> activity;
    private ArrayList<String> permissionList;
    private ArrayList<Dialog> tipsDialogsList;//用户第一次使用或点击此次拒绝后的提示
    private ArrayList<Dialog> warnDialogsList;//用户点击拒绝且勾选不再提示后的提示

    public PermissionHelper(Activity activity) {
        this.activity = new WeakReference<>(activity);

        permissionList = new ArrayList<>();
        tipsDialogsList = new ArrayList<>();
        warnDialogsList = new ArrayList<>();
    }

    public PermissionHelper addPermission(String permission) {
        permissionList.add(permission);
        return this;
    }

    public PermissionHelper addPermission(String[] permissions) {
        permissionList.addAll(Arrays.asList(permissions));
        return this;
    }


    public PermissionHelper addTipsDialog(Dialog dialog) {
        tipsDialogsList.add(dialog);
        return this;
    }

    public PermissionHelper addTipsDialog(Dialog[] dialogs) {
        tipsDialogsList.addAll(Arrays.asList(dialogs));
        return this;
    }

    public PermissionHelper addWarnDialog(Dialog dialog) {
        warnDialogsList.add(dialog);
        return this;
    }

    public PermissionHelper addWarnDialog(Dialog[] dialogs) {
        warnDialogsList.addAll(Arrays.asList(dialogs));
        return this;
    }


    //当全部授权后返回true
    public boolean checkPermission() {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }

        int count = 0;

        for (int i = 0; i < permissionList.size(); i++) {
            if (ContextCompat.checkSelfPermission(activity, permissionList.get(i)) != PackageManager.PERMISSION_GRANTED) {
                //没有获取权限，尝试请求权限
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permissionList.get(i))) {
                    //用户拒绝
                    /** tipsDialog中一般包含一个点击事件为PermissionHelper.getPermissionInActivity的按钮*/
                    if (tipsDialogsList.get(i) != null)
                        tipsDialogsList.get(i).show();
                } else {
                    //用户第一次使用
                    //用户拒绝且勾选不再提示
                    /**由于无法得知是第一次使用还是拒绝且勾选不再提示，使用时需在点击需要权限的地方先使用
                      PermissionHelper.getPermissionInActivity，然后在onRequestPermissionsResult
                      中判断，如果用户在之前的弹框中点击了本次拒绝（即 grantResults[0] !=
                      PackageManager.PERMISSION_GRANTED），再调用checkPermission（即本方法）尝试弹框
                      提醒用户去系统设置中设置*/
                    if (warnDialogsList.get(i) != null)
                        warnDialogsList.get(i).show();
                }
            } else {
                //已获得该权限
                count++;
            }
        }
        return count == permissionList.size();
    }

    public static void getPermissionInFragment(Fragment fragment, String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(fragment.getContext(), permission) != PackageManager.PERMISSION_GRANTED) {
            fragment.requestPermissions(new String[]{permission}, requestCode);
        }
    }

    public static void getPermissionInActivity(Activity activity, String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{permission}, requestCode);
        }
    }


}
