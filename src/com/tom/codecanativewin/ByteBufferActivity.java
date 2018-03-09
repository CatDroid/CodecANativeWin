package com.tom.codecanativewin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;

import com.tom.codecanativewin.jni.ABuffer;
import com.tom.codecanativewin.jni.DecodeH264;
import com.tom.util.NioUtilsInvoke;

import android.app.Activity;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

public class ByteBufferActivity extends Activity {

	final public static String TAG = "ByteBuffer";
	private DecodeH264 mH264de = null;

	private SurfaceView mSv = null;
	private SurfaceHolder mSh = null;
	private boolean surfaceCreated = false;
	private Handler mUIHandler = new Handler();

	/* 	
	
	在Android Dalvik JVM 上 ByteBuffer.allocateDirect出来的DirectByteBuffer
	a. 可以 array()  
	b. isDirect是ture 
	c. 分配在Dalvik Heap 
	d. 会导致OOM 
	e. 没有引用的话,GC会回收 
	
	在Android JNI  NewDirectByteBuffer出来的ByteBuffer
	a. 不能 array
	b. 行为跟OpenJDK JVM一样
	
	在PC OpenJDK JVM运行  	ByteBuffer.allocateDirect
	a. 就不能array 异常 UnsupportedOperationException 
	
	 
	Android DirecteByteBuffer的继承关系 
		java.lang.Object
		   ↳	java.nio.Buffer
		 	   ↳	java.nio.ByteBuffer
		 	 	   ↳	java.nio.MappedByteBuffer	A direct byte buffer whose content is a memory-mapped region of a file.
		 	 	   								Mapped byte buffers are created via the FileChannel.map method
		 	 	   								A mapped byte buffer and the file mapping that it represents remain valid until the buffer itself is garbage-collected.
		 	 	   		 ↳	java.nio.DirectByteBuffer
	   		
   		
	ByteBuffer的allocate和 allocateDirect说明:
	
		#allocate(int) 
			Allocate  a new byte array and create a buffer based on it; 				
			创建byte[]数组  并用ByteArrayBuffer封装 (所以实际就是byte[]数组)
	
		#allocateDirect(int) 
			Allocate a memory block and create a direct buffer based on it; 
			创建memory block 并用DirectByteBuffer封装
							
	DirectByteBuffer的构造:
		1.ByteBuffer.allocateDirect 构造函数是:
			a.先创建 MemoryBlock
				MemoryBlock.allocate(capacity + 7);
				
				public static MemoryBlock allocate(int byteCount) {
	    			VMRuntime runtime = VMRuntime.getRuntime();
	    			byte[] array = (byte[]) runtime.newNonMovableArray(byte.class, byteCount);
	    			
	    			// 注意这里分配得到的是  byte[] java Heap的！！！ 但是是用VMRuntime的 newNonMovableArray获取到的
	    			// 如果是ByteBuffer.allocate() 的话 , 是直接new byte[capacity] 然后封装到java/nio/ByteArrayBuffer.java返回 
	    			// 获得对应的地址 !!
	    			
	    			long address = runtime.addressOf(array); 
	    			return new NonMovableHeapBlock(array, address, byteCount);
	    			
	    			// << 使用的是 NonMovableHeapBlock  
				}
			
			b.使用DirectByteBuffer封装
				--> new DirectByteBuffer(memoryBlock, capacity, (int)(alignedAddress - address), false, null);
					--> protected DirectByteBuffer(MemoryBlock block, int capacity, int offset, boolean isReadOnly, MapMode mapMode)
					
		2. JNI NewDirectByteBuffer 构造函数是:
					
			// Used by the JNI NewDirectByteBuffer function.
			DirectByteBuffer(long address, int capacity) {
				this(MemoryBlock.wrapFromJni(address, capacity), capacity, 0, false, null);
			}
						
						
			public static MemoryBlock wrapFromJni(long address, long byteCount) {
				return new UnmanagedBlock(address, byteCount);
			}
				 
			* Represents a block of memory we don't own. 			代表一块内存 不是有GC拥有的
			* (We don't take ownership of memory corresponding
			* to direct buffers created by the JNI NewDirectByteBuffer function.)
	
		    private static class UnmanagedBlock extends MemoryBlock {
		        private UnmanagedBlock(long address, long byteCount) {
		            super(address, byteCount);
		        }
		    } 														// UnmanagedBlock 没有override MemoryBlock的array方法(返回NULL)
	
		 	private MemoryBlock(long address, long size) {			// 只是保存了内存地址
				this.address = address;
				this.size = size;
				accessible = true;
				freed = false;
			}



	ByteBuffer的array:
			
		// java/nio/ByteBuffer.java
		@Override 
		public final byte[] array() {
			return protectedArray();
		}

		// java/nio/DirectByteBuffer.java
		@Override 
		byte[] protectedArray() {
			checkIsAccessible();
			if (isReadOnly) {
				throw new ReadOnlyBufferException();
			}
			byte[] array = this.block.array(); <= 实际调用的是MemoryBlock的array
			if (array == null) {
				throw new UnsupportedOperationException();
			}
			return array;
		}
			
		// java/nio/MemoryBlock.java
		class MemoryBlock{
			... 
			public byte[] array() {
				return null;		//	基类MemoryBlock就是返回NULL 
			}						//	JNI创建DirectByteBuffer的UnmanagedBlock没有override
			... 					//  ByteBuffer.allcateDirect创建DirectByteBuffer的NonMovableHeapBlock override了返回array
		}

		JNI 的 MemoryBlock 是  UnmanagedBlock 
		没有override  array() 
		
		ByteBuffer.allcateDirect 的MemoryBlock 是 NonMovableHeapBlock 
			@Override public byte[] array() {
			 return array ; 有返回 array 
		}


	Bytebuffer.allocateDirect的实现:
		 
			
		=> libcore/libart/src/main/java/dalvik/system/VMRuntime.java
				
		Returns an array allocated in an area of the Java heap where it will never be moved.
		This is used to implement native allocations on the Java heap, such as DirectByteBuffers
		and Bitmaps. 执行原生分配在Java Heap上 比如DirectByteBuffers和Bitmap

		=> public native Object newNonMovableArray(Class<?> componentType, int length);
		
		=> art/runtime/native/dalvik_system_VMRuntime.cc
		=>  static jobject VMRuntime_newNonMovableArray(JNIEnv* env, jobject, jclass javaElementClass,jint length) 

	
	MemoryBlock继承关系 (java/nio/MemoryBlock.java )
   		MemoryBlock
   		-> MemoryMappedBlock    
   		-> NonMovableHeapBlock 
   		-> UnmanagedBlock 
   		
   		
	*/
	private class MallocDirect implements Runnable
	{
	
		public ArrayList<ByteBuffer> direct_array  = new ArrayList<ByteBuffer>();
		
		@Override
		public void run() {
			for(int j = 0 ; j < 257 ; j++){
				Log.v(TAG, "> >" + Long.toString(Debug.getNativeHeapAllocatedSize()));
				ByteBuffer bbu = ByteBuffer.allocateDirect(1000000); 	// 在Davlik JVM上 分配在Davlik Heap, 所以也会导致 OOM Davlik Heap超过256 
				//ByteBuffer bbu = ByteBuffer.allocate(1000000); 		// 都会导致 java.lang.OutOfMemoryError
				Log.d(TAG, "bbu direct = " + bbu.isDirect() + " j = " + j );
				byte[] bba = bbu.array(); 	
				
				bba[0] = 1 ;
				direct_array.add(bbu); 
				
				Log.v(TAG, "< <" +  Long.toString(Debug.getNativeHeapAllocatedSize()));
				
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			Log.d(TAG, "free bbu ");
			while(direct_array.size() > 0){
				direct_array.remove(0);
			}
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); 
		setContentView(R.layout.activity_display_h264);
	
		
		/*
		 *  测试 ByteBuffer.array()之后,使用InputStream来读 ,是否改变 ByteBuffer的position??   不会改变 !
		 */
		final int TRY_TO_READ = 100 ;
		ByteBuffer arrayBuf = ByteBuffer.allocateDirect(TRY_TO_READ) ;
		
		byte[] array = arrayBuf.array();
		int offset = arrayBuf.arrayOffset();
		
		InputStream alphaInputStream = null; 
		try {
			alphaInputStream = this.getApplicationContext().getResources().openRawResource(R.raw.alphamerged);
			int readed = alphaInputStream.read(array, offset, TRY_TO_READ);
			if(readed != TRY_TO_READ ){
				Log.e(TAG, "InputStream read less than " + TRY_TO_READ );
			}
			Log.d(TAG , "after InputStream.read "  + readed + " ByteBuffer Pos is " + arrayBuf.position() );
			
			arrayBuf.position( TRY_TO_READ );
			Log.d(TAG, "after Bytebuffer.position " + arrayBuf.position() );
			arrayBuf.flip(); // for read 
			Log.d(TAG, "after Bytebuffer.flip pos: " + arrayBuf.position() + " limit: " + arrayBuf.limit()  + " mark: " + arrayBuf.limit() );
			Log.d(TAG, "result: arrayBuf = " + arrayBuf );
		}catch( android.content.res.Resources.NotFoundException e){
			Log.e(TAG, "IOException " + e.getMessage() );
			e.printStackTrace();			
		}catch (IOException e) {
			Log.e(TAG, "IOException " + e.getMessage() );
			e.printStackTrace();
		}
		if( alphaInputStream != null ){
			try {
				alphaInputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
		
		Log.d(TAG, "-----------" ); 
		ByteBuffer bb = ByteBuffer.allocateDirect(200) ; 
		DecodeH264.analyseByteBuffer( bb );
		Log.d(TAG, "before pos  " + bb.get(2) ); 
		bb.position(3); // 不会影响到 底层 GetDirectBufferAddress 返回的地址  所以应该使用 ByteBuffer.position来告诉底层偏移位置
		DecodeH264.analyseByteBuffer( bb );
		Log.d(TAG, "after  pos  " + bb.get(2) ); 
		
		ByteBuffer rdbuffer =  ByteBuffer.allocate(10);
		rdbuffer.put(3, (byte)11);
		rdbuffer = rdbuffer.asReadOnlyBuffer();
		Log.d(TAG, "rdbuffer isReadOnly = " + rdbuffer.isReadOnly() );
		// rdbuffer.array();  只读的ByteBuffer都不能array() 和  arrayOffset()
		// rdbuffer.arrayOffset(); 
		/*
		 * 	Caused by: java.nio.ReadOnlyBufferException
				at java.nio.ByteArrayBuffer.protectedArray(ByteArrayBuffer.java:88)
				at java.nio.ByteBuffer.array(ByteBuffer.java:133)
		 */
		
		// NioUtils是hide的  
		// NioUtils.unsafeArray(rdbuffer);  由于ByteBuffer可能是readonly的所以不能array() 系统AudioTrack在使用NioUtils.unsafeArray而不是array()来得到数组
		byte[] rdonlyByteArray = NioUtilsInvoke.unsafeArray(rdbuffer);
		int array_offset_isNOT_position = NioUtilsInvoke.unsafeArrayOffset(rdbuffer); 
		
		Log.d(TAG, "rdonlyByteArray[3] = " + rdonlyByteArray[3] 
					+ " array_offset_isNOT_position " + array_offset_isNOT_position ); // 11 
		
		
		
		mSv = (SurfaceView) findViewById(R.id.viewSurface);
		mSh = mSv.getHolder();
		mSh.setKeepScreenOn(true);
		mSh.addCallback(new SurfaceCallback());
		
		
		//new Thread(new MallocDirect()).start();
		
		((Button) findViewById(R.id.bStart)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mH264de == null){
					mH264de = new DecodeH264();
					mH264de.setInfoListener(new DecodeH264.onInfoListener() {
						@Override
						public void onInfo(int type, int arg1, int arg2) {
						//	Log.d(TAG, "1 postEventFromNative update time = " 
						//				+ arg1 + " sec ; " + " size = " + arg2 );
						}
					});
					mH264de.setOnDataListener(new ByteBufferListener());
				}
				
				if (surfaceCreated == false){
					Toast.makeText(ByteBufferActivity.this, "Surface Not Available now", Toast.LENGTH_LONG)
							.show();
				}else{
					String path1080p60fps = "/mnt/sdcard/" + "1080p60fps.h264" ;
					byte[] sps1080p60fps =  {
							(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,
							(byte)0x67,(byte)0x64,(byte)0x00,(byte)0x2a,
							(byte)0xac,(byte)0xd1,(byte)0x00,(byte)0x78,
							(byte)0x02,(byte)0x27,(byte)0xe5,(byte)0xc0,
							(byte)0x44,(byte)0x00,(byte)0x00,(byte)0x0f,
							(byte)0xa4,(byte)0x00,(byte)0x07,(byte)0x53,
							(byte)0x00,(byte)0x3c,(byte)0x60,(byte)0xc4,
							(byte)0x48,
					};
					byte[] pps1080p60fps = {
							(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,
							(byte)0x68,(byte)0xeb,(byte)0xef,(byte)0x2c,
					};
					mH264de.start(mSh .getSurface() , path1080p60fps , 
									sps1080p60fps  , pps1080p60fps );
				} 
			}
		});
		
		((Button) findViewById(R.id.bStop)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mH264de != null){
					mH264de.release();
					mH264de = null;
				}else{
					Toast.makeText(ByteBufferActivity.this, "DecodeH264 Not Created now", Toast.LENGTH_LONG)
					.show();
				}
			}
		});
	}

	@Override
	protected void onDestroy() {
		if (mH264de != null){
			mH264de.release();
		}
		super.onDestroy();
	}

	private class SurfaceCallback implements SurfaceHolder.Callback {
		@Override
		public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
			Log.d(TAG, "surfaceChanged " + " arg1 = " + arg1 + " arg2 = " + arg2 + " arg3 = " + arg3);
		}
		@Override
		public void surfaceCreated(SurfaceHolder arg0) {
			Log.d(TAG, "surfaceCreated created");
			surfaceCreated = true;
		}
		@Override
		public void surfaceDestroyed(SurfaceHolder arg0) {
			Log.d(TAG, "surfaceCreated destroyed");
		}
	}

	// adb logcat -s jni_decodeh264 java_decodeh264  ByteBuffer ABuffer
	
	private class ByteBufferListener implements DecodeH264.onDataListener
	{
		@Override
		public void onData(int data_type, ABuffer adata) {
			
			ByteBuffer data = adata.mData ;
			if( data_type == DecodeH264.MEDIA_H264_SAMPLE ){
				String log = String.format("[%d %d][%x %x %x %x][pos:%d lef:%d cap:%d lim:%d dir:%b rd:%b] ",
						adata.mTimestamp ,adata.mActualSize ,
						0xFF&data.get(0), 
						0xFF&data.get(1),
						0xFF&data.get(2),
						0xFF&data.get(3),
						data.position(),
						data.remaining(),
						data.capacity(),
						data.limit(),
						data.isDirect(),
						data.isReadOnly() );	
				/*
				 * 如果调用了asReadOnlyBuffer:
				 * ByteBuffer: [1479463015 1314][0 0 0 1][pos:0 lef:1314 cap:1314 lim:1314 dir:true rd:true]
				 * ByteBuffer: [1479463015 2161][0 0 0 1][pos:0 lef:2161 cap:2161 lim:2161 dir:true rd:true]
				 * ByteBuffer: [1479463015 25303][0 0 0 1][pos:0 lef:25303 cap:25303 lim:25303 dir:true rd:true]
				 *
				 * 如果不调用 asReadOnlyBuffer
				 * ByteBuffer: [1479453722 9431][0 0 0 1][pos:0 lef:9431 cap:9431 lim:9431 dir:true rd:false]
				 * ByteBuffer: [1479453722 3399][0 0 0 1][pos:0 lef:3399 cap:3399 lim:3399 dir:true rd:false]
				 * ByteBuffer: [1479453722 9677][0 0 0 1][pos:0 lef:9677 cap:9677 lim:9677 dir:true rd:false]
				 * ByteBuffer: [1479453722 10880][0 0 0 1][pos:0 lef:10880 cap:10880 lim:10880 dir:true rd:false]
				 *
				 */
				final ABuffer delayBuffer = adata ;
				mUIHandler.postDelayed(new Runnable(){
					@Override
					public void run() {
						Log.d(TAG , "delay release");
						
						
						ByteBuffer data =  delayBuffer.mData ;
						/*
						 * 1.对于 DirectByteBuffer
						 * 	不可以
						 * 		byte[] array = data.array();
						 * 	会出现异常
						 * 		Pending exception java.lang.UnsupportedOperationException:
						 * 
						 * 	但是可以 (write或者read only都可以)
						 *		IntBuffer buffer = data.asIntBuffer();
						 *
						 *	对于可write的direct buffer:
						 *		buffer.put(12); // postion会向前移动 , 使用put和get来读取数据
						 *		也可以进行 buffer.put(byte[])
						 *		或者进行 buffer.put(ByteBuffer)  两个JNI创建的DirectByteBuffer相互拷贝 注意postion都会同时改变
						 *	对于only read的direct buffer
						 *		不能进行put操作 否则 ReadOnlyBufferException
						 *	
						 *	
						 * 2.ReadOnly的DirectByteBuffer,不能进行put
						 * 	否则 
						 * 		Pending exception java.nio.ReadOnlyBufferException
						 * 	try{
						 *		data.put((byte)12);
						 * 	}catch(java.nio.ReadOnlyBufferException ronly){
						 *		Log.e(TAG,"ronly " + ronly.getMessage() );
						 * 	}
						 * 
						 * 3.如果put的数据大于容量 将会出现exception:
						 * 		java.nio.BufferOverflowException
						 * 
						 * 
						 */
				
						/*
						 * Demo:
						 * 底层同一个BufferManager限制了数目
						 * 所以如果onData返回的ABuffer不释放的话 这样这里也不能再获取数据
						 * 底层抛送数据可能以及用光了ABuffer
						 */
//						ABuffer dstBuffer = mH264de.obtainBuffer(data.remaining());
//						if(dstBuffer != null){
//							Log.d(TAG, "1 dstBuffer.mData pos = " + dstBuffer.mData.position() + " cap = " + dstBuffer.mData.capacity() + " limit = " +  dstBuffer.mData.limit());
//							dstBuffer.mData.put(data); // 从一个JNI的NewDirectByteBuffer到另外一个
//							Log.d(TAG, "2 dstBuffer.mData pos = " + dstBuffer.mData.position() + " cap = " + dstBuffer.mData.capacity() + " limit = " +  dstBuffer.mData.limit());
//							dstBuffer.release();
//							Log.d(TAG, "srcBuffer after put " + data.position() ); 	// 注意ByteBuffer.put之后 会修改掉 源ByteBuffer和目标ByteBuffer的postion 
//							data.position(0);										// 如果把ByteBuffer postion为0 后面就会  IntBuffer pos = 0 cap = 0 limit = 0	 				
//						}
							

						/*
							ByteBuffer: 1 dstBuffer.mData pos = 0 cap = 4441 limit = 4441
							ByteBuffer: 2 dstBuffer.mData pos = 4441 cap = 4441 limit = 4441
							ByteBuffer: srcBuffer after put 4441
						 * 
						 * */
						data.order(ByteOrder.nativeOrder());// 00 00 00 01  => big:00000001 / little:01000000
						IntBuffer buffer = data.asIntBuffer(); // asIntBuffer只会对应 ByteBuffer剩下的内容 
						Log.d(TAG, "IntBuffer pos = " + buffer.position() + " cap = " + buffer.capacity() + " limit = " + buffer.limit() );
						Log.d(TAG, ">>> 0: " + buffer.get(0) + " pos:" + buffer.position() );
						buffer.put(10);
						Log.d(TAG, "<<< 0: " + buffer.get(0) + " pos:" + buffer.position() ); //  <<< 0: 10 pos:1
						
						Log.d(TAG, "bytebuffer pos " + data.position() ); // bytebuffer pos 0   对IntBuffer的修改不会影响原来ByteBuffer的position
						data.position(0);
						byte[] setdata = {0 , 1 , 2 , 3};
						data.put(setdata);	

						Log.d(TAG, "<<< 0: " + data.get(2) + " pos:" + data.position() );
						
						delayBuffer.release();
						
					}
				},1000); // delay 1 second , and then release buffer
				
				Log.d(TAG,log);
	
			}
			
		}
	}
}
