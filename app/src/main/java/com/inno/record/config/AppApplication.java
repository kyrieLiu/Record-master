package com.inno.record.config;

import android.accessibilityservice.AccessibilityService;
import android.app.Application;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.view.WindowManager;

import com.inno.record.utils.CrashHandler;

/**
 * Created by liuyin on 2019/3/6 11:39
 * @Describe 应用初始化
 */
public class AppApplication extends Application{

    public static WindowManager mWindowManager;

    public static DevicePolicyManager mDevicePolicyManager;

    public static int[] actions=new int[]{1,2,3,4,5};

    private static AppApplication instance ;
    public static AppApplication getInstance(){
        return instance;
    }

    public static AccessibilityService abs=null;

    private CrashHandler mCrashHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        instance=this;
        this.mWindowManager= (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        this.mDevicePolicyManager= (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        mCrashHandler = CrashHandler.getInstance();
        mCrashHandler.init(getApplicationContext(), getClass());

    }

    public void setASB(AccessibilityService abs){
        this.abs=abs;
    }
}
