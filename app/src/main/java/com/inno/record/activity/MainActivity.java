package com.inno.record.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.inno.record.R;
import com.inno.record.config.AppApplication;
import com.inno.record.reciver.DotReceiver;
import com.inno.record.service.RecordWindowService;
import com.inno.record.service.WindowService;
import com.inno.record.utils.PermissionHelper;
import com.inno.record.utils.Utils;
import com.inno.record.utils.gputil.BlueToothPrintUtil;
import com.inno.record.utils.gputil.DeviceConnFactoryManager;
import com.inno.record.view.SelectDialog;

/**
 * Created by liuyin on 2019/3/6 11:36
 *
 * @Describe 按钮操作页
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Switch mSw_Lock;
    private Switch mSw_FloatView;
    private Switch mSw_Record;
    private ComponentName who;

    //文字说明
    private TextView mLock_info;
    private TextView mFloatView_info;
    private TextView mTvRecord;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        init();
    }

    private void init() {
        initView();
        initAdmin();
        who = new ComponentName(MainActivity.this, DotReceiver.class);
    }


    private void initAdmin() {
        boolean isActive = AppApplication.mDevicePolicyManager.isAdminActive(who);
        if (isActive) {
            mLock_info.setText("( 已开启 )");
            mSw_Lock.setChecked(true);
        } else {
            mLock_info.setText("( 已关闭 )");
            mSw_Lock.setChecked(false);
        }

    }

    private void initView() {
        mSw_Lock = (Switch) findViewById(R.id.sw_lock);
        mSw_FloatView = ((Switch) findViewById(R.id.sw_dot));
        mSw_Record = (Switch) findViewById(R.id.sw_record_service);
        mTvRecord = (TextView) findViewById(R.id.tv_record_service);

        /**文字描述**/
        mLock_info = ((TextView) findViewById(R.id.tv_lock_text));
        mFloatView_info = ((TextView) findViewById(R.id.tv_dot_text));

        LinearLayout mLlPrinter = (LinearLayout) findViewById(R.id.ll_printer);
        mLlPrinter.setOnClickListener(this);


        //获取或取消 锁屏权限
        mSw_Lock.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                boolean adminActive = AppApplication.mDevicePolicyManager.isAdminActive(who);
                if (isChecked) {
                    if (!adminActive) {
                        Intent intent = new Intent();
                        // 指定动作名称
                        intent.setAction(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                        // 指定给哪个组件授权
                        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, who);
                        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "说明文档");
                        MainActivity.this.startActivityForResult(intent, 0);
                    }
                    mLock_info.setText("( 已开启 )");
                } else {
                    if (adminActive) {
                        AppApplication.mDevicePolicyManager.removeActiveAdmin(who);
                        Toast.makeText(MainActivity.this, "已经移除,你可以重新激活,或者执行卸载操作.", Toast.LENGTH_LONG).show();
                    }
                    mLock_info.setText("( 已关闭 )");
                }
            }
        });

        mSw_FloatView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                boolean serviceActive = Utils.isServiceActive(MainActivity.this, "com.inno.record.service.WindowService");
                Log.d("tag", "serviceActivity==" + serviceActive);
                if (isChecked) {
                    if (!serviceActive) {
                        requestPermission(1);
                    }
                } else {
                    if (serviceActive) {
                        stopService(new Intent(MainActivity.this, WindowService.class));
                    }
                    mFloatView_info.setText("( 已关闭 )");
                }
            }
        });
        mSw_Record.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                boolean serviceActive = Utils.isServiceActive(MainActivity.this, "com.inno.record.service.RecordWindowService");
                if (isChecked) {
                    if (!serviceActive) {
                        requestPermission(2);
                    }
                } else {
                    if (serviceActive) {
                        stopService(new Intent(MainActivity.this, RecordWindowService.class));

                    }
                    mTvRecord.setText("( 已关闭 )");
                }
            }
        });
    }


    private PermissionHelper mHelper;

    /**
     * 请求录像权限
     *
     * @param type 1为USB 2为摄像头
     */
    private void requestPermission(final int type) {
        mHelper = new PermissionHelper(this);
        mHelper.requestPermissions(getResources().getString(R.string.image_permission_first_hint),
                new PermissionHelper.PermissionListener() {
                    @Override
                    public void doAfterGrand(String... permission) {
                        setOpen(type);
                    }

                    @Override
                    public void doAfterDenied(String... permission) {
                        mHelper.againWarnRequestPermission(getResources().getString(R.string.image_permission_hint), MainActivity.this);
                    }
                }, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO);
    }

    private void setOpen(int type) {
        if (type == 1) {
            startService(new Intent(MainActivity.this, WindowService.class));
            mFloatView_info.setText("( 已开启 )");
        } else {
            startService(new Intent(MainActivity.this, RecordWindowService.class));
            mTvRecord.setText("已开启");
        }


    }


    @SuppressLint("NewApi")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 12) {
            if (Settings.canDrawOverlays(this)) {
                Intent intent = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
                startActivity(intent);
            }
            return;
        }
        boolean isActive = AppApplication.mDevicePolicyManager.isAdminActive(who);
        if (isActive) {
            Log.e("@@@", "result+已激活");
        } else {
            Log.e("@@@", "result+未激活");
        }

    }

    @Override
    protected void onResume() {
        initAdmin();
        boolean serviceActive = Utils.isServiceActive(MainActivity.this, "com.inno.record.service.WindowService");
        if (serviceActive) {
            mFloatView_info.setText("( 已开启 )");
            mSw_FloatView.setChecked(true);
        } else {
            mFloatView_info.setText("( 已关闭 )");
            mSw_FloatView.setChecked(false);
        }
        boolean recordActive = Utils.isServiceActive(MainActivity.this, "com.inno.record.service.RecordWindowService");
        if (recordActive) {
            mTvRecord.setText("( 已开启 )");
            mSw_Record.setChecked(true);
        } else {
            mTvRecord.setText("( 已关闭 )");
            mSw_Record.setChecked(false);
        }
        super.onResume();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ll_printer:

                if (deviceManager == null) {
                    connectBlueTooth();
                } else {
                    BlueToothPrintUtil.getInstance().printRefoundItemInformation(deviceManager);
                }
                break;
            default:
                break;
        }
    }

    private DeviceConnFactoryManager deviceManager;

    private void connectBlueTooth() {
        SelectDialog dialog = new SelectDialog(this);
        dialog.setSelectListener(new SelectDialog.BlueToothSelectInterface() {
            @Override
            public void callBack(BluetoothDevice device) {
                new DeviceConnFactoryManager.Build()
                        .setId(0)
                        //设置连接方式
                        .setConnMethod(DeviceConnFactoryManager.CONN_METHOD.BLUETOOTH)
                        //设置连接的蓝牙mac地址
                        .setMacAddress(device.getAddress())
                        .build();
                //打开端口
                deviceManager = DeviceConnFactoryManager.getDeviceConnFactoryManagers()[0];

                if (deviceManager != null) {
                    deviceManager.openPort();
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            BlueToothPrintUtil.getInstance().printRefoundItemInformation(deviceManager);
                        }
                    }, 1000);

                }

            }
        });
        dialog.show();
    }

    /**
     * 连接打印接
     *
     * @param device
     */
    private void connectPrintBlueTooth(BluetoothDevice device) {
        new DeviceConnFactoryManager.Build()
                .setId(0)
                //设置连接方式
                .setConnMethod(DeviceConnFactoryManager.CONN_METHOD.BLUETOOTH)
                //设置连接的蓝牙mac地址
                .setMacAddress(device.getAddress())
                .build();
        //打开端口
        deviceManager = DeviceConnFactoryManager.getDeviceConnFactoryManagers()[0];
    }

    //授权回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mHelper != null)
            mHelper.handleRequestPermissionsResult(requestCode, permissions, grantResults);
    }

}
