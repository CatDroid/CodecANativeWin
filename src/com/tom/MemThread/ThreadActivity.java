package com.tom.MemThread;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.tom.codecanativewin.R;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;



public class ThreadActivity extends Activity {

    static private final String TAG = "ThreadActivity" ;

    private ActivityManager mAm = null;

    private AtomicInteger mThreadNum = new AtomicInteger(0) ;
    private AtomicInteger mMemThreadNum = new AtomicInteger(0) ;

    private TextView tvNumThread = null;
    private TextView tvNumMemThread = null;
    private TextView tvMemInfo = null;

    private ArrayList<Thread> mThreadList = new ArrayList<Thread>();

    private native void ThreadProc(int stack_size /*字节*/, boolean forever);
    static{
        System.loadLibrary("DirectCJNI");
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread);

        mAm = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        tvNumThread = (TextView)findViewById(R.id.numThread);
        tvNumMemThread = (TextView)findViewById(R.id.memThreadNum);
        tvMemInfo = (TextView)findViewById(R.id.meminfo);

        new Thread(new Runnable() {
            @Override
            public void run() {
                Debug.MemoryInfo info =  new Debug.MemoryInfo();
                while(true){
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    Debug.getMemoryInfo(info);
                    final String result = String.format(Locale.ENGLISH,
                            "dalvikPss %d kByte\n" +
                            "nativePss %d kByte\n" +
                            "otherPss  %d kByte\n" +
                            "dalvikPrivateDirty %d kByte\n" +
                            "nativePrivateDirty %d kByte\n" +
                            "otherPrivateDirty  %d kByte\n" ,
                            info.dalvikPss,
                            info.nativePss,
                            info.otherPss,
                            info.dalvikPrivateDirty,
                            info.nativePrivateDirty,
                            info.otherPrivateDirty
                    );
                    // dump meminfo
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvMemInfo.setText(result);
                        }
                    });

                }

            }
        }).start();

        Button btn = (Button)findViewById(R.id.startThread);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {

                                for(int i = 0 ; i < 2000 ; i++){

                                    Thread th = new Thread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    final int num = mThreadNum.addAndGet(1);

                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            tvNumThread.setText("狂开线程:"+num);
                                                        }
                                                    });

                                                    // 调用
//                                                    Debug.MemoryInfo info =  new Debug.MemoryInfo();
//                                                    Debug.getMemoryInfo(info);
//
//                                                    String result = String.format(Locale.ENGLISH,
//                                                            "dalvikPss %d kByte\n" +
//                                                            "nativePss %d kByte\n" +
//                                                            "otherPss  %d kByte\n" +
//                                                            "dalvikPrivateDirty %d kByte\n" +
//                                                            "nativePrivateDirty %d kByte\n" +
//                                                            "otherPrivateDirty  %d kByte\n" ,
//                                                            info.dalvikPss,
//                                                            info.nativePss,
//                                                            info.otherPss,
//                                                            info.dalvikPrivateDirty,
//                                                            info.nativePrivateDirty,
//                                                            info.otherPrivateDirty
//                                                    );
//                                                    info = null;
//                                                    Log.d(TAG , "ThreadNum = " + num + "\n" + result ) ;
//                                                    result = null; // 因为线程不退出 这里要及时释放

                                                    while(true){
                                                        try {
                                                            Thread.sleep(2000);
                                                        } catch (InterruptedException e) {
                                                            e.printStackTrace();
                                                        }
                                                    }
                                                }
                                            }
                                    );
                                    th.start();// new Thread
                                    mThreadList.add(th); // 避免线程GC掉 可能没有引用 系统会GC回收掉

                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                ).start();

                /**
                 * ThreadNum = 475  @ 华为荣耀V10 线程栈溢出
                 *
                 *
                 * java.lang.OutOfMemoryError: pthread_create (1040KB stack) failed: Out of memory
                 *
                 */
            }
        });


        btn = (Button)findViewById(R.id.startThreadWithMem);
        btn.setOnClickListener(new View.OnClickListener(){ // 每按一下 创建一个线程
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {

                        final int memThreadNum = mMemThreadNum.addAndGet(1);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tvNumMemThread.setText("线程数目:"+memThreadNum);
                            }
                        });

                        // 调用Native层方法 创建非堆内存 在Native层不返回
                        // Native层栈  signal 11 (SIGSEGV), code 2 (SEGV_ACCERR), fault addr 0xc8001000
                        ThreadProc(1024*1024,true);
                        // @荣耀V10 临界值 1028*1024
                        // @小米5 临界值 1029*1024

                    }
                }).start();
            }
        });
    }
}
