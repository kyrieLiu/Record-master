package com.inno.backdot.utils;

import android.app.ActivityManager;
import android.content.Context;

import java.util.ArrayList;

/**
 * Created by didik on 2016/5/11.
 */
public class Utils {


    /**
     * 判断一个服务是否在运行
     * @return
     */
    public static boolean isServiceActive(Context context,String serviceName){
        ActivityManager mAM= (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ArrayList<ActivityManager.RunningServiceInfo> runningServices = (ArrayList
                <ActivityManager.RunningServiceInfo>) mAM.getRunningServices(Integer.MAX_VALUE);
        for (int i=0;i<runningServices.size();i++){
//            Log.e("@@@","+++"+runningServices.get(i).service.getClassName().toString());
            if (runningServices.get(i).service.getClassName().toString().equalsIgnoreCase(serviceName)){
                return  true;
            }
        }
        return  false;
    }


}
