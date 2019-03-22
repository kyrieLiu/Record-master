package com.inno.record.engine;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.usb.UsbDevice;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.inno.record.R;
import com.inno.record.activity.PlayVideoActivity;
import com.inno.record.utils.VideoUtils;
import com.jiangdg.usbcamera.UVCCameraHelper;
import com.jiangdg.usbcamera.utils.FileUtils;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.encoder.RecordParams;
import com.serenegiant.usb.widget.CameraViewInterface;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Created by didik on 2016/5/12.
 */
public class Dot implements GestureDetector.OnDoubleTapListener, GestureDetector.OnGestureListener, View.OnClickListener, CameraDialog.CameraDialogParent, CameraViewInterface.Callback {
    private static final String TAG = "CustomRecordActivity";
    private static Dot mDot;
    private Context mContext;

    private WindowManager.LayoutParams params = new WindowManager.LayoutParams();
    private static ImageView imageView;
    private static View suspensionView;
    private GestureDetector mGesture;

    private WindowManager windowManager;
    private Handler mHandler;
    private static AccessibilityService acs;
    private static DevicePolicyManager dpm;
    private static Vibrator vibrator;
    private static boolean isVibratored = false;

    //UI
    private ImageView mRecordControl;
    private ImageView mPauseRecord;
    private Chronometer mRecordTime;

    //DATA

    //录像机状态标识
    private int mRecorderState = 2;

    public static final int STATE_INIT = 0;
    public static final int STATE_RECORDING = 1;
    public static final int STATE_PAUSE = 2;


    //    private boolean isRecording;// 标记，判断当前是否正在录制
//    private boolean isPause; //暂停标识
    private long mPauseTime = 0;           //录制暂停时间间隔

    // 存储文件
    private File mVecordFile;
    private Camera mCamera;
    private MediaRecorder mediaRecorder;
    private String currentVideoFilePath;
    private String saveVideoPath = "";

    private LinearLayout mRlRecordParent;

    View mTextureView;

    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;

    private boolean isRequest;
    private boolean isPreview;
    private Button mBtPlay, mBtAgainRecord, mBtAutoRecord;

    private Subscription mSubscription;

    private boolean isTerminateAutoPlay = true;//是否已经终止连续录制


    private Dot(Context mContext) {
        this.mContext = mContext;
        this.windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        this.mHandler = new Handler();
        this.dpm = (DevicePolicyManager) mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mGesture = new GestureDetector(mContext, this);
        mGesture.setOnDoubleTapListener(this);
        initWindow();
    }

    public static Dot getInstance(Context context) {
        if (mDot == null) {
            return new Dot(context);
        } else {
            return mDot;
        }
    }

    public void setInstanceNull() {
        mDot = null;
    }

    public void removeView() {
        UVCCameraHelper.getInstance().release();
        if (windowManager != null && suspensionView != null) {
            windowManager.removeView(suspensionView);
            suspensionView = null;
        }
    }


    private void initWindow() {
        // 注意，悬浮窗只有一个，而当打开应用的时候才会产生悬浮窗，所以要判断悬浮窗是否已经存在，
        if (suspensionView != null) {
            windowManager.removeView(suspensionView);
        }
        // 使用Application context
        // 创建UI控件，避免Activity销毁导致上下文出现问题,因为现在的悬浮窗是系统级别的，不依赖与Activity存在
        suspensionView = LayoutInflater.from(mContext).inflate(R.layout.view_suspension, null);
        imageView = (ImageView) suspensionView.findViewById(R.id.iv_suspension_dot);

//        params.type=WindowManager.LayoutParams.TYPE_SYSTEM_ALERT |WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;


        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        //TYPE_SYSTEM_ALERT  系统提示,它总是出现在应用程序窗口之上
        //TYPE_SYSTEM_OVERLAY   系统顶层窗口。显示在其他一切内容之上。此窗口不能获得输入焦点，否则影响锁屏
        // FLAG_NOT_FOCUSABLE 悬浮窗口较小时，后面的应用图标由不可长按变为可长按,不设置这个flag的话，home页的划屏会有问题
        // FLAG_NOT_TOUCH_MODAL不阻塞事件传递到后面的窗口
        params.gravity = Gravity.LEFT | Gravity.TOP;  //显示在屏幕左中部

        //显示位置与指定位置的相对位置差
        params.x = 50;
        params.y = 0;
        //悬浮窗的宽高
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;


        //设置透明
        params.format = PixelFormat.TRANSPARENT;
        initView();
        //添加到window中
        windowManager.addView(suspensionView, params);

        initImageView();

    }


    private void initView() {

        mRecordControl = (ImageView) suspensionView.findViewById(R.id.record_control);
        mRecordTime = (Chronometer) suspensionView.findViewById(R.id.record_time);
        mPauseRecord = (ImageView) suspensionView.findViewById(R.id.record_pause);
        mRlRecordParent = (LinearLayout) suspensionView.findViewById(R.id.rl_dot_bottom);
        mBtPlay = (Button) suspensionView.findViewById(R.id.record_play);
        mBtAgainRecord = (Button) suspensionView.findViewById(R.id.bt_again_record);
        mBtAutoRecord = (Button) suspensionView.findViewById(R.id.bt_auto_usb_record);

        mBtPlay.setOnClickListener(this);
        mBtAgainRecord.setOnClickListener(this);
        mRecordControl.setOnClickListener(this);
        mPauseRecord.setOnClickListener(this);
        imageView.setOnClickListener(this);
        mBtAutoRecord.setOnClickListener(this);

        mTextureView = suspensionView.findViewById(R.id.camera_view);
        mUVCCameraView = (CameraViewInterface) mTextureView;


        if (mCameraHelper == null) {
            initUSBCamera();
        }


    }

    private void initUSBCamera() {
        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_YUYV);
        mCameraHelper.initUSBMonitor(mContext, mUVCCameraView, listener);
        mCameraHelper.setOnPreviewFrameListener(new AbstractUVCCameraHandler.OnPreViewResultListener() {
            @Override
            public void onPreviewResult(byte[] nv21Yuv) {

            }
        });
        if (mCameraHelper != null) {
            mCameraHelper.registerUSB();
        }
    }


    private UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {

        @Override
        public void onAttachDev(UsbDevice device) {
            if (mCameraHelper == null || mCameraHelper.getUsbDeviceCount() == 0) {
                showShortMsg("check no usb camera");
                return;
            }
            // request open permission
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    mCameraHelper.requestPermission(0);
                }
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            // close camera
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
                //showShortMsg(device.getDeviceName() + " is out");

            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (!isConnected) {
                showShortMsg("fail to connect,please check resolution params");
                isPreview = false;
            } else {
                isPreview = true;
                showShortMsg("connecting");
                // initialize seekbar
                // need to wait UVCCamera initialize over
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Looper.prepare();
//                        if(mCameraHelper != null && mCameraHelper.isCameraOpened()) {
//                            mSeekBrightness.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_BRIGHTNESS));
//                            mSeekContrast.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_CONTRAST));
//                        }
                        Looper.loop();
                    }
                }).start();
            }
        }

        @Override
        public void onDisConnectDev(UsbDevice device) {
            showShortMsg("disconnecting");
            //mTextureView.setBackgroundColor(Color.parseColor("#80ffffff"));
            mRlRecordParent.setVisibility(View.GONE);
        }
    };


    private void showShortMsg(String msg) {
        Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.record_control:
                startOrStopRecord();

                break;

            case R.id.record_pause:
                startOrPause();
                break;
            case R.id.iv_suspension_dot:
                if (mRlRecordParent.getVisibility() == View.VISIBLE) {
//                    if (mCameraHelper != null) {
//                        mCameraHelper.unregisterUSB();
//                    }
//                    FileUtils.releaseFile();
//                    // step.4 release uvc camera resources
//                    if (mCameraHelper != null) {
//                        mCameraHelper.release();
//                    }
                    mRlRecordParent.setVisibility(View.GONE);
                } else {
                    //initUSBCamera();
                    mRlRecordParent.setVisibility(View.VISIBLE);
                }
                break;
            case R.id.record_play:
                mRlRecordParent.setVisibility(View.GONE);
                Intent intent = new Intent(mContext, PlayVideoActivity.class);
                Bundle bundle = new Bundle();
                bundle.putString("videoPath", saveVideoPath);
                intent.putExtras(bundle);
                mContext.startActivity(intent);

                break;
            case R.id.bt_again_record:
                saveVideoPath = "";
                mPauseTime = 0;
                mRecordTime.setBase(SystemClock.elapsedRealtime());
                mRecordTime.stop();
                mPauseRecord.setImageResource(R.drawable.control_play);
                break;
            case R.id.bt_auto_usb_record:
                if (isTerminateAutoPlay) {
                    isTerminateAutoPlay = false;
                    mBtAutoRecord.setText("停止连续录制");
                    autoRecord();
                } else {
                    isTerminateAutoPlay = true;
                    mBtAutoRecord.setText("连续录制");
                    if (mSubscription != null && !mSubscription.isUnsubscribed()) {
                        mSubscription.unsubscribe();
                    }
                    stopAutoRecord();
                }
                break;
        }
    }

    /**
     * 开始录制视频
     */
    public void startOrStopRecord() {

        if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
            showShortMsg("sorry,camera open failed");
            return;
        }
        if (mRecorderState == STATE_INIT) {
            String videoPath = getSDPath(mContext) + System.currentTimeMillis();
            FileUtils.createfile(getSDPath(mContext) + "test666.h264");

            // if you want to record,please create RecordParams like this
            RecordParams params = new RecordParams();
            params.setRecordPath(videoPath);
            params.setRecordDuration(0);                        // 设置为0，不分割保存
            params.setVoiceClose(true);    // is close voice
            mCameraHelper.startPusher(params, new AbstractUVCCameraHandler.OnEncodeResultListener() {
                @Override
                public void onEncodeResult(byte[] data, int offset, int length, long timestamp, int type) {
                    // type = 1,h264 video stream
                    if (type == 1) {
                        FileUtils.putFileStream(data, offset, length);
                    }
                }

                @Override
                public void onRecordResult(String videoPath) {
                    saveVideoPath = videoPath;
                }
            });
            // if you only want to push stream,please call like this
            // mCameraHelper.startPusher(listener);

        } else if (mRecorderState == STATE_RECORDING) {
            FileUtils.releaseFile();
            mCameraHelper.stopPusher();

        }

        refreshControlUI();


    }

    private void startOrPause() {
        if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
            showShortMsg("sorry,camera open failed");
            return;
        }
        if (mRecorderState == STATE_PAUSE) {
            //String videoPath = UVCCameraHelper.ROOT_PATH + System.currentTimeMillis();
            String videoPath = getSDPath(mContext) + System.currentTimeMillis();
            FileUtils.createfile(getSDPath(mContext) + "test666.h264");

            // if you want to record,please create RecordParams like this
            RecordParams params = new RecordParams();
            params.setRecordPath(videoPath);
            params.setRecordDuration(0);                        // 设置为0，不分割保存
            params.setVoiceClose(true);    // is close voice
            mCameraHelper.startPusher(params, new AbstractUVCCameraHandler.OnEncodeResultListener() {
                @Override
                public void onEncodeResult(byte[] data, int offset, int length, long timestamp, int type) {
                    // type = 1,h264 video stream
                    if (type == 1) {
                        FileUtils.putFileStream(data, offset, length);
                    }
                }

                @Override
                public void onRecordResult(String videoPath) {
                    currentVideoFilePath = videoPath;


                    if (TextUtils.isEmpty(saveVideoPath)) {
                        saveVideoPath = currentVideoFilePath;
                    } else if (!currentVideoFilePath.equals(saveVideoPath)) {
                        mergeRecordVideoFile();
                    }
                    // showShortMsg("保存完毕  saveVideoPath="+saveVideoPath+"  currentVideoFilePath="+currentVideoFilePath);
                }
            });
            // if you only want to push stream,please call like this
            // mCameraHelper.startPusher(listener);

        } else if (mRecorderState == STATE_RECORDING) {

            FileUtils.releaseFile();
            mCameraHelper.stopPusher();

        }

        refreshPauseUI();
    }

    private void autoRecord() {
        startAutoRecord();
        mSubscription = Observable.interval(0, 21, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())//操作UI主要在UI线程
                .subscribe(new Subscriber<Long>() {
                    @Override
                    public void onCompleted() {
                        Log.d("tag", "执行onCompleted==");
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onNext(Long aLong) {
                        stopAutoRecord();
                    }
                });
    }

    private void startAutoRecord() {
        String videoPath = getSDPath(mContext) + System.currentTimeMillis();
        FileUtils.createfile(getSDPath(mContext) + "test666.h264");

        // if you want to record,please create RecordParams like this
        RecordParams params = new RecordParams();
        params.setRecordPath(videoPath);
        params.setRecordDuration(0);                        // 设置为0，不分割保存
        params.setVoiceClose(true);    // is close voice
        mCameraHelper.startPusher(params, new AbstractUVCCameraHandler.OnEncodeResultListener() {
            @Override
            public void onEncodeResult(byte[] data, int offset, int length, long timestamp, int type) {
                // type = 1,h264 video stream
                if (type == 1) {
                    FileUtils.putFileStream(data, offset, length);
                }
            }

            @Override
            public void onRecordResult(String videoPath) {
//                currentVideoFilePath = videoPath;
//
//
//                if (TextUtils.isEmpty(saveVideoPath)) {
//                    saveVideoPath = currentVideoFilePath;
//                } else if (!currentVideoFilePath.equals(saveVideoPath)){
//                    mergeRecordVideoFile();
//                }
                // showShortMsg("保存完毕  saveVideoPath="+saveVideoPath+"  currentVideoFilePath="+currentVideoFilePath);
            }
        });
        mPauseRecord.setImageResource(R.drawable.control_pause);

        mRecordTime.setBase(SystemClock.elapsedRealtime());

        mRecordTime.start();
        mRecorderState = STATE_RECORDING;
        mBtAgainRecord.setVisibility(View.GONE);
        mBtPlay.setVisibility(View.GONE);
    }

    private void stopAutoRecord() {
        FileUtils.releaseFile();
        mCameraHelper.stopPusher();

        mPauseRecord.setImageResource(R.drawable.control_play);

        mPauseTime = SystemClock.elapsedRealtime();
        mRecordTime.stop();
        mRecorderState = STATE_PAUSE;

        mBtAgainRecord.setVisibility(View.VISIBLE);
        mBtPlay.setVisibility(View.VISIBLE);
        if (!isTerminateAutoPlay) {
            startAutoRecord();
        }

    }


    /**
     * 合并录像视频方法
     */
    private void mergeRecordVideoFile() {

        //Toast.makeText(mContext,"saveVideoPath=="+saveVideoPath+"   currentVideoFilePath=="+currentVideoFilePath,Toast.LENGTH_LONG).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] str = new String[]{saveVideoPath, currentVideoFilePath};
                    //将2个视频文件合并到 append.mp4文件下
                    VideoUtils.appendVideo(mContext, getSDPath(mContext) + "append.mp4", str);

                    File reName = new File(saveVideoPath);
                    File f = new File(getSDPath(mContext) + "append.mp4");
                    //再将合成的append.mp4视频文件 移动到 saveVideoPath 路径下
                    f.renameTo(reName);
                    if (reName.exists()) {
                        f.delete();
                        File file = new File(currentVideoFilePath);
                        file.delete();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    /**
     * 点击中间按钮，执行的UI更新操作
     */
    private void refreshControlUI() {
        if (mRecorderState == STATE_INIT) {
            //录像时间计时
            mRecordTime.setBase(SystemClock.elapsedRealtime());
            mRecordTime.start();

            mRecordControl.setImageResource(R.drawable.recordvideo_stop);
            //1s后才能按停止录制按钮
            mRecordControl.setEnabled(false);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mRecordControl.setEnabled(true);
                }
            }, 1000);
            mPauseRecord.setVisibility(View.VISIBLE);
            mPauseRecord.setEnabled(true);
            mRecorderState = STATE_RECORDING;

        } else if (mRecorderState == STATE_RECORDING) {
            mPauseTime = 0;
            mRecordTime.stop();

            mRecordControl.setImageResource(R.drawable.recordvideo_start);
            mPauseRecord.setVisibility(View.GONE);
            mPauseRecord.setEnabled(false);
            mRecorderState = STATE_INIT;
        }

    }

    /**
     * 点击暂停继续按钮，执行的UI更新操作
     */
    private void refreshPauseUI() {
        if (mRecorderState == STATE_RECORDING) {
            mPauseRecord.setImageResource(R.drawable.control_play);

            mPauseTime = SystemClock.elapsedRealtime();
            mRecordTime.stop();
            mRecorderState = STATE_PAUSE;

            mBtAgainRecord.setVisibility(View.VISIBLE);
            mBtPlay.setVisibility(View.VISIBLE);

        } else if (mRecorderState == STATE_PAUSE) {
            mPauseRecord.setImageResource(R.drawable.control_pause);

            if (mPauseTime == 0) {
                mRecordTime.setBase(SystemClock.elapsedRealtime());
            } else {
                mRecordTime.setBase(SystemClock.elapsedRealtime() - (mPauseTime - mRecordTime.getBase()));
            }
            mRecordTime.start();
            mRecorderState = STATE_RECORDING;
            mBtAgainRecord.setVisibility(View.GONE);
            mBtPlay.setVisibility(View.GONE);
        }
    }


    private MediaRecorder.OnErrorListener OnErrorListener = new MediaRecorder.OnErrorListener() {
        @Override
        public void onError(MediaRecorder mediaRecorder, int what, int extra) {
            try {
                if (mediaRecorder != null) {
                    mediaRecorder.reset();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };


    /**
     * 创建视频文件保存路径
     */
    public static String getSDPath(Context context) {
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Toast.makeText(context, "请查看您的SD卡是否存在！", Toast.LENGTH_SHORT).show();
            return null;
        }

        File sdDir = Environment.getExternalStorageDirectory();
        File eis = new File(sdDir.toString() + "/RecordVideo/");
        if (!eis.exists()) {
            eis.mkdir();
        }
        return sdDir.toString() + "/RecordVideo/";
    }

    private String getVideoName() {
        return "VID_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp4";
    }


    /**
     * 获取屏幕分辨率
     *
     * @return
     */
    public int[] getScreenSize() {
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(outMetrics);
        return new int[]{outMetrics.widthPixels, outMetrics.heightPixels};
    }


    private void initImageView() {
        suspensionView.setOnTouchListener(new View.OnTouchListener() {
            private float lastX; //上一次位置的X.Y坐标
            private float lastY;
            private float nowX;  //当前移动位置的X.Y坐标
            private float nowY;
            private float tranX; //悬浮窗移动位置的相对值
            private float tranY;

            private float preY = 0;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                boolean ret = true;
                ret = mGesture.onTouchEvent(event);
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 获取按下时的X，Y坐标
                        lastX = event.getRawX();
                        lastY = event.getRawY();

                        preY = lastY;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        // 获取移动时的X，Y坐标
                        nowX = event.getRawX();
                        nowY = event.getRawY();
                        if (preY == 0) {
                            preY = nowY;
                        }
                        // 计算XY坐标偏移量
                        tranX = nowX - lastX;
                        tranY = nowY - lastY;
                        // 移动悬浮窗
                        params.x += tranX;//左对齐是+,右对齐是-
                        params.y += tranY;
                        //更新悬浮窗位置
                        windowManager.updateViewLayout(suspensionView, params);
                        //记录当前坐标作为下一次计算的上一次移动的位置坐标
                        lastX = nowX;
                        lastY = nowY;
                        break;
                    case MotionEvent.ACTION_UP:
//                        float dy = nowY - preY;
//                        Log.e("@@@", "dy:" + dy);
//                        if (isVibratored) {
//                            if (dy > 10) {
//                                //down
//                                actions(AppApplication.actions[3]);
//                            } else if (dy < -10) {
//                                //up
//                                actions(AppApplication.actions[4]);
//                            } else {
//                                //longClick
//                                actions(AppApplication.actions[2]);
//                            }
//                            isVibratored = false;
//                        }
//                        //根据移动的位置来判断
//                        dy = 0;
//                        tranY = 0;
                        break;
                }
                return ret;
            }
        });
    }


    @SuppressLint("NewApi")
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
//        Toast.makeText(mContext, "onSingleTapConfirmed", Toast.LENGTH_SHORT).show();
        //返回上一级
//        actionBack();
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
//        Toast.makeText(mContext, "onDoubleTap", Toast.LENGTH_SHORT).show();
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    //-------------------------------------------------------------
    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {
//        Toast.makeText(mContext, "onLongPress", Toast.LENGTH_SHORT).show();
        //历史应用(相当于长按菜单键)
//        actionRecent();
        vibrator.vibrate(200);
        isVibratored = true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return true;
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return mCameraHelper.getUSBMonitor();
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
            showShortMsg("取消操作");
        }
    }

    @Override
    public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
        if (!isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.startPreview(mUVCCameraView);
            isPreview = true;
        }
    }

    @Override
    public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {

    }

    @Override
    public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
        if (isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.stopPreview();
            isPreview = false;
        }
    }
}
