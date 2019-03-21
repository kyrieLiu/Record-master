package com.inno.record.engine;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.inno.record.R;
import com.inno.record.activity.PlayVideoActivity;
import com.inno.record.config.AppApplication;
import com.inno.record.encoder.RecordMediaAudioEncoder;
import com.inno.record.encoder.RecordMediaEncoder;
import com.inno.record.encoder.RecordMediaMuxerWrapper;
import com.inno.record.encoder.RecordRecordMediaVideoEncoder;
import com.inno.record.utils.VideoUtils;
import com.inno.record.view.CameraGLView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by didik on 2016/5/12.
 */
public class RecordDot implements GestureDetector.OnDoubleTapListener, GestureDetector.OnGestureListener, View.OnClickListener {
    private static final String TAG = "CustomRecordActivity";
    private static RecordDot mDot;
    private Context mContext;

    private WindowManager.LayoutParams params = new WindowManager.LayoutParams();
    private static ImageView imageView;
    private static View suspensionView;
    private GestureDetector mGesture;

    private WindowManager windowManager;
    private static AccessibilityService acs;
    private static DevicePolicyManager dpm;
    private static Vibrator vibrator;
    private static boolean isVibratored = false;

    //UI
    private ImageView mRecordControl;
    private ImageView mPauseRecord;
    private Chronometer mRecordTime;



    //    private boolean isRecording;// 标记，判断当前是否正在录制
//    private boolean isPause; //暂停标识
    private long mPauseTime = 0;           //录制暂停时间间隔


    private RelativeLayout mRlRecordParent;


    /**
     * for camera preview display
     */
    private CameraGLView mCameraView;
    /**
     * for scale mode display
     */
    /**
     * button for start/stop recording
     */
    /**
     * muxer for audio/video recording
     */
    private RecordMediaMuxerWrapper mMuxer;

    private String saveFilePath;//保存文件路径
    private String currentFilePath;//临时文件存储路径

    private Button mBtPlay, mBtAgainRecord;

    private RecordDot(Context mContext) {
        this.mContext = mContext;
        this.windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        this.dpm = (DevicePolicyManager) mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mGesture = new GestureDetector(mContext, this);
        mGesture.setOnDoubleTapListener(this);
        initWindow();
    }

    public static RecordDot getInstance(Context context) {
        if (mDot == null) {
            return new RecordDot(context);
        } else {
            return mDot;
        }
    }

    public void setInstanceNull() {
        mDot = null;
    }

    public void removeView() {
        onPause();
        if (windowManager != null && suspensionView != null) {
            windowManager.removeView(suspensionView);
            suspensionView = null;
        }
    }


    private void actionBack() {
        if (acs != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                acs.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mContext, "你的手机版本低于 4.03 ,请升级后使用!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else {
            Toast.makeText(mContext, "null", Toast.LENGTH_SHORT).show();
        }
    }

    private void actionLock() {
        if (dpm != null) {
            dpm.lockNow();
        }
    }

    /**
     * 打开通知中心
     **/
    private void actionPull2Notification() {
        if (acs != null) {
            acs.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
        }
    }

    /**
     * 回到桌面
     **/
    private void action2Home() {
        if (acs != null) {
            acs.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
        }
    }

    private void actionRecent() {
        if (acs != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                acs.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mContext, "你的手机版本低于 4.03 ,请升级后使用!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private void initWindow() {
        // 注意，悬浮窗只有一个，而当打开应用的时候才会产生悬浮窗，所以要判断悬浮窗是否已经存在，
        if (suspensionView != null) {
            windowManager.removeView(suspensionView);
        }
        // 使用Application context
        // 创建UI控件，避免Activity销毁导致上下文出现问题,因为现在的悬浮窗是系统级别的，不依赖与Activity存在
        suspensionView = LayoutInflater.from(mContext).inflate(R.layout.record_view_suspension, null);
        imageView = (ImageView) suspensionView.findViewById(R.id.iv_suspension_dot);


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


        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;


        //设置透明
        //params.format = PixelFormat.TRANSPARENT;
        initView();
        //添加到window中
        windowManager.addView(suspensionView, params);

        initImageView();

    }


    private void initView() {
        mRecordControl = (ImageView) suspensionView.findViewById(R.id.record_control);
        mRecordTime = (Chronometer) suspensionView.findViewById(R.id.record_time);
        mPauseRecord = (ImageView) suspensionView.findViewById(R.id.record_pause);
        mRlRecordParent = (RelativeLayout) suspensionView.findViewById(R.id.rl_dot_bottom);

        mCameraView = (CameraGLView) suspensionView.findViewById(R.id.cameraView);
        mCameraView.setVideoSize(1280, 720);

        mBtPlay = (Button) suspensionView.findViewById(R.id.record_play);
        mBtAgainRecord = (Button) suspensionView.findViewById(R.id.bt_again_record);
        mBtPlay.setOnClickListener(this);
        mBtAgainRecord.setOnClickListener(this);


        mRecordControl.setOnClickListener(this);
        mPauseRecord.setOnClickListener(this);
        imageView.setOnClickListener(this);

        mCameraView.onResume();

    }


    /**
     * 合并录像视频方法
     */
    private void mergeRecordVideoFile(final String saveVideoPath, final String currentVideoFilePath) {

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
                        new File(currentVideoFilePath).delete();
                    }
                    handler.sendEmptyMessage(1);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("myTag", e.getLocalizedMessage());
                }
            }
        }).start();
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 1:
                    Toast.makeText(mContext, "保存完成", Toast.LENGTH_SHORT).show();
                    mBtPlay.setVisibility(View.VISIBLE);
                    mBtAgainRecord.setVisibility(View.VISIBLE);
                    break;
            }
        }
    };

    public void onPause() {
        stopRecording();
        mCameraView.onPause();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.record_control:
                break;

            case R.id.record_pause:
                if (mMuxer == null) {
                    startRecording();
                } else {
                    stopRecording();
                }

                break;
            case R.id.iv_suspension_dot:
                if (mRlRecordParent.getVisibility() == View.VISIBLE) {
                    mRlRecordParent.setVisibility(View.GONE);
                    onPause();
                } else {
                    mRlRecordParent.setVisibility(View.VISIBLE);
                    mCameraView.onResume();
                }
                break;

            case R.id.record_play:
                mRlRecordParent.setVisibility(View.GONE);
                Intent intent = new Intent(mContext, PlayVideoActivity.class);
                Bundle bundle = new Bundle();
                bundle.putString("videoPath", saveFilePath);
                bundle.putInt("type",1);
                intent.putExtras(bundle);
                mContext.startActivity(intent);

                break;
            case R.id.bt_again_record:
                saveFilePath = "";
                mPauseTime = 0;
                mRecordTime.setBase(SystemClock.elapsedRealtime());
                mRecordTime.stop();
                mPauseRecord.setImageResource(R.drawable.control_play);
                break;
        }

    }

    /**
     * start resorcing
     * This is a sample project and call this on UI thread to avoid being complicated
     * but basically this should be called on private thread because prepareing
     * of encoder is heavy work
     */
    private void startRecording() {
        mBtPlay.setVisibility(View.GONE);
        mBtAgainRecord.setVisibility(View.GONE);
        try {
            mMuxer = new RecordMediaMuxerWrapper(".mp4");    // if you record audio only, ".m4a" is also OK.
            // for video capturing
            new RecordRecordMediaVideoEncoder(mMuxer, mMediaEncoderListener, mCameraView.getVideoWidth(), mCameraView.getVideoHeight());
            // for audio capturing
            new RecordMediaAudioEncoder(mMuxer, mAudioListener);
            if (TextUtils.isEmpty(saveFilePath)) {
                saveFilePath = mMuxer.getOutputPath();
            }
            currentFilePath = mMuxer.getOutputPath();
            mMuxer.prepare();
            mMuxer.startRecording();

            mPauseRecord.setImageResource(R.drawable.control_pause);

            if (mPauseTime == 0) {
                mRecordTime.setBase(SystemClock.elapsedRealtime());
            } else {
                mRecordTime.setBase(SystemClock.elapsedRealtime() - (mPauseTime - mRecordTime.getBase()));
            }
            mRecordTime.start();

        } catch (final IOException e) {
            Log.e(TAG, "startCapture:", e);
            mBtPlay.setVisibility(View.VISIBLE);
            mBtAgainRecord.setVisibility(View.VISIBLE);
        }
    }

    /**
     * request stop recording
     */
    private void stopRecording() {
        mPauseRecord.setImageResource(R.drawable.control_play);
        mPauseTime = SystemClock.elapsedRealtime();
        mRecordTime.stop();

        if (mMuxer != null) {
            mMuxer.stopRecording();
            mMuxer = null;

        }
    }


    /**
     * callback methods from encoder
     */
    private final RecordMediaEncoder.MediaEncoderListener mMediaEncoderListener = new RecordMediaEncoder.MediaEncoderListener() {
        @Override
        public void onPrepared(final RecordMediaEncoder encoder) {
            if (encoder instanceof RecordRecordMediaVideoEncoder)
                mCameraView.setVideoEncoder((RecordRecordMediaVideoEncoder) encoder);
        }

        @Override
        public void onStopped(final RecordMediaEncoder encoder) {
            if (encoder instanceof RecordRecordMediaVideoEncoder) {
                mCameraView.setVideoEncoder(null);
            }

            if (!currentFilePath.equals(saveFilePath)) {
                mergeRecordVideoFile(saveFilePath, currentFilePath);
            }else{
                handler.sendEmptyMessage(1);
            }
        }
    };

    /**
     * callback methods from encoder
     */
    private final RecordMediaEncoder.MediaEncoderListener mAudioListener = new RecordMediaEncoder.MediaEncoderListener() {
        @Override
        public void onPrepared(final RecordMediaEncoder encoder) {
            if (encoder instanceof RecordRecordMediaVideoEncoder)
                mCameraView.setVideoEncoder((RecordRecordMediaVideoEncoder) encoder);
        }

        @Override
        public void onStopped(final RecordMediaEncoder encoder) {
            if (encoder instanceof RecordRecordMediaVideoEncoder)
                mCameraView.setVideoEncoder(null);
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
                        float dy = nowY - preY;
                        Log.e("@@@", "dy:" + dy);
                        if (isVibratored) {
                            if (dy > 10) {
                                //down
                                actions(AppApplication.actions[3]);
                            } else if (dy < -10) {
                                //up
                                actions(AppApplication.actions[4]);
                            } else {
                                //longClick
                                actions(AppApplication.actions[2]);
                            }
                            isVibratored = false;
                        }
                        //根据移动的位置来判断
                        dy = 0;
                        tranY = 0;
                        break;
                }
                return ret;
            }
        });
    }

    private void actions(int what) {
        Log.e("@@@", "what:" + what);
        switch (what) {
            case 1:
                actionBack();
                break;
            case 2:
                actionLock();
                break;
            case 3:
                action2Home();
                break;
            case 4:
                actionPull2Notification();
                break;
            case 5:
                actionRecent();
                break;
            default:
                break;
        }
    }

    @SuppressLint("NewApi")
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
//        Toast.makeText(mContext, "onSingleTapConfirmed", Toast.LENGTH_SHORT).show();
        //返回上一级
//        actionBack();
        actions(AppApplication.actions[0]);
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
//        Toast.makeText(mContext, "onDoubleTap", Toast.LENGTH_SHORT).show();
        actions(AppApplication.actions[1]);
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

}
