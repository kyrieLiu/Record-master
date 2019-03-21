package com.inno.backdot.activity;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.inno.backdot.R;
import com.inno.backdot.utils.mMediaController;
import com.inno.backdot.view.MyVideoView;

import java.io.File;

/**
 * Created by liuyin on 2019/3/6 11:38
 *
 * @Describe 播放视频
 */
public class PlayVideoActivity extends AppCompatActivity implements MediaPlayer.OnPreparedListener, com.inno.backdot.utils.mMediaController.MediaPlayerControl {
    public static final String TAG = "PlayVideo";
    private MyVideoView videoView;
    private mMediaController controller;
    private String mVideoPath;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.now_playvideo);
        Bundle bundle = getIntent().getExtras();
        mVideoPath = bundle.getString("videoPath");
        int type = bundle.getInt("type");
        File sourceVideoFile = new File(mVideoPath);
        videoView = (MyVideoView) findViewById(R.id.videoView);
        int screenW = getWindowManager().getDefaultDisplay().getWidth();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) videoView.getLayoutParams();
        params.width = screenW;
        if (type == 1) {//手机摄像头录像
            params.height = (int) (screenW * 1.5);
        } else {//USB录像
            params.height = screenW * 3 / 4;
        }

        params.gravity = Gravity.TOP;
        videoView.setLayoutParams(params);

        videoView.setOnPreparedListener(this);
        controller = new mMediaController(this);
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            videoView.setVideoURI(Uri.fromFile(sourceVideoFile));
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        controller.setMediaPlayer(this);
        controller.setAnchorView((ViewGroup) findViewById(R.id.fl_videoView_parent));
        controller.show();

    }

    @Override
    public void start() {
        videoView.start();
    }

    @Override
    public void pause() {
        if (videoView.isPlaying()) {
            videoView.pause();
        }
    }

    @Override
    public int getDuration() {
        return videoView.getDuration();
    }

    @Override
    public int getCurrentPosition() {
        return videoView.getCurrentPosition();
    }

    @Override
    public void seekTo(int pos) {
        videoView.seekTo(pos);
    }

    @Override
    public boolean isPlaying() {
        return videoView.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return videoView.canPause();
    }

    @Override
    public boolean canSeekBackward() {
        return videoView.canSeekBackward();
    }

    @Override
    public boolean canSeekForward() {
        return videoView.canSeekForward();
    }

    @Override
    public boolean isFullScreen() {
        return false;
    }

    @Override
    public void toggleFullScreen() {

    }
}
