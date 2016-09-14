package com.tom.codecanativewin;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.media.MediaPlayer;
import android.media.TimedText;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class MediaPlayerActivity extends Activity {

	private final String TAG = "mp";
	private final String PLAY_THIS_FILE = "/mnt/sdcard/test1080p60fps.mp4";
	private SurfaceView mSurfaceView;
	private MediaPlayer mMediaPlayer;
	private TextView mTotalDuration;
	private int mTotalDurationms;
	private TextView mCurDuration;
	private int mCurDurationms;
	private Handler mMainHandler;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_media_player);

		// 保持屏幕长亮
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		mMainHandler = new MsgHandler();
		mTotalDuration = (TextView) findViewById(R.id.tvTotalDuration);
		mCurDuration = (TextView) findViewById(R.id.tvCurDuration);
		if (mTotalDuration == null || mCurDuration == null) {
			Log.e(TAG, "tvTotalDuration / tvCurDuration NOT Found");
		}

		mMediaPlayer = new MediaPlayer();
		mMediaPlayer.reset();
		mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				Toast.makeText(MediaPlayerActivity.this, "播放完成了", 2000).show();
			}
		});
		// 设置出现错误的时候的监听
		mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
			public boolean onError(MediaPlayer mediaplayer, int i, int j) {
				Toast.makeText(MediaPlayerActivity.this, "报错了！", 2000).show();
				return true;
			}
		});

		// 调用seekto方法完成之后，这里的完成代表的是当前播放进度已经调整到了要指定的时间，并且调整之后mediaPlayer开始播放了！
		mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
			@Override
			public void onSeekComplete(MediaPlayer media) {

			}
		});

		// 缓冲进度 这里的进度position指的应该就是百分比，因为我实验的时候，他最大的值就是100
		mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
			@Override
			public void onBufferingUpdate(MediaPlayer media, int position) {
				Log.d(TAG, "onBufferingUpdate " + position);
			}
		});

		mMediaPlayer.setOnTimedTextListener(new MediaPlayer.OnTimedTextListener() {
			@Override
			public void onTimedText(MediaPlayer mp, TimedText text) {
				Log.d(TAG, "onTimedText " + text.getText());

			}
		});

		mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {

			@Override
			public boolean onInfo(MediaPlayer mp, int what, int extra) {
				Log.d(TAG, "onInfo what = " + what + " extra = " + extra);
				switch (what) {
				case MediaPlayer.MEDIA_INFO_BUFFERING_START:
					Log.d(TAG, "onInfo BUFFERING_START extra = " + extra);
					break;
				case MediaPlayer.MEDIA_INFO_BUFFERING_END:
					Log.d(TAG, "onInfo BUFFERING_END extra = " + extra);
					break;
				case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
					Log.d(TAG, "onInfo VIDEO_RENDERING_START  extra = " + extra);
					break;
				case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
					Log.d(TAG,
							"onInfo VIDEO_TRACK_LAGGING " + " The video is too complex for the decoder:"
									+ "it can't decode frames fast enough. "
									+ "Possibly only the audio plays fine at this stage.");
					break;
				}
				return true;
			}

		});

		mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mp) {
				Log.d(TAG, "onPrepared");
				if (mp != null) {
					Log.d(TAG, "start");
					mTotalDurationms = mp.getDuration();
					mCurDurationms = mp.getCurrentPosition();

					Message m = mMainHandler.obtainMessage(MsgHandler.DURATION_CHANGE);
					m.arg1 = mTotalDurationms;
					m.arg2 = mCurDurationms;
					mMainHandler.sendMessage(m);

					new VideoTimeTrackThread().start();
					mp.start(); // 播放视频
				}
			}
		});// 监听缓冲是否完成

		mSurfaceView = (SurfaceView) findViewById(R.id.viewMediaSurface);
		mSurfaceView.getHolder().setKeepScreenOn(true);
		/*
		 * 设置SurfaceView自己不管理的缓冲区 过时:该值自动设置 如果低于Android 3.0. 需要设置这个
		 * 
		 * SURFACE_TYPE_NORMAL：用RAM缓存原生数据的普通Surface
		 * SURFACE_TYPE_HARDWARE：适用于DMA(Direct memory access )引擎和硬件加速的Surface
		 * SURFACE_TYPE_GPU：适用于GPU加速的Surface
		 * SURFACE_TYPE_PUSH_BUFFERS：表明该Surface不包含原生数据，Surface用到的数据由其他对象提供，
		 * 在Camera图像预览中就使用该类型的Surface，有Camera负责提供给预览Surface数据，
		 * 这样图像预览会比较流畅。如果设置这种类型则就不能调用lockCanvas来获取Canvas对象了
		 */
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			mSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
		mSurfaceView.getHolder().addCallback(new surfaceCallBack());

	}

	private class surfaceCallBack implements SurfaceHolder.Callback {

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			// 画布被创建的时候

			try {
				mMediaPlayer.setDataSource(PLAY_THIS_FILE);
			} catch (IllegalArgumentException e) {
				Log.e(TAG, "setDataSource:IllegalArgumentException " + e.getMessage());
				e.printStackTrace();
				return;
			} catch (SecurityException e) {
				Log.e(TAG, "setDataSource:SecurityException " + e.getMessage());
				e.printStackTrace();
				return;
			} catch (IllegalStateException e) {
				Log.e(TAG, "setDataSource:IllegalStateException " + e.getMessage());
				e.printStackTrace();
				return;
			} catch (IOException e) {
				Log.e(TAG, "setDataSource:IOException " + e.getMessage());
				e.printStackTrace();
				return;
			}
			mMediaPlayer.setDisplay(mSurfaceView.getHolder());
			mMediaPlayer.prepareAsync();// 进行缓冲处理(异步的方式去缓冲)
										// prepare()方法会阻塞主线程，有时候时间长的话会造成无响应（ANR错误）
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			// 画布改变的时候
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			// 画布被销毁的时候
		}
	}

	public class MsgHandler extends Handler {

		public static final int DURATION_CHANGE = 1;
		public String mTotal = null; 
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MsgHandler.DURATION_CHANGE:
				int total = msg.arg1;
				int cur = msg.arg2;
				if(mTotal == null){
					mTotal = getDateTimeFromMillisecond(total) ;
				}
				
				mTotalDuration.setText( "/" + mTotal  );
				mCurDuration.setText( getDateTimeFromMillisecond(cur)  );
				break;
			default:
				Log.e(TAG, "MsgHandler unknown message !");
				break;
			}
			super.handleMessage(msg);
		}

	}

	/**
	 * 将毫秒转化成固定格式的时间 时间格式: yyyy-MM-dd HH:mm:ss
	 *
	 * @param millisecond
	 * @return
	 */
	public static String getDateTimeFromMillisecond(int millisecond) {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
		Date date = new Date(millisecond);
		String dateStr = simpleDateFormat.format(date);
		return dateStr;
	}

	class VideoTimeTrackThread extends Thread {
		public void run() {
			while (!isInterrupted()) {
				mCurDurationms = mMediaPlayer.getCurrentPosition();
				mTotalDurationms = mMediaPlayer.getDuration();
				if (mCurDurationms == mTotalDurationms) {
					break;
				}

				Message m = mMainHandler.obtainMessage(MsgHandler.DURATION_CHANGE);
				m.arg1 = mTotalDurationms;
				m.arg2 = mCurDurationms;
				mMainHandler.sendMessage(m);

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

}
