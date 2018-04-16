#### ThreadActivity
* 栈的OOM (目前在华为手机上常见 应该ROM限制每个APP只能创建500左右的线程)
    * 异常打印 java.lang.OutOfMemoryError: pthread_create (1040KB stack) failed: Out of memory
        * 注意异常打印, 后面有 stack标记
        * 在Java层不断new Thread.start
            * ThreadNum = 475  @ 华为荣耀V10  
    * 崩溃时java堆内存和设备物理内存都充足
    * stack上的内存不需要GC，因为内存会在函数退出时回收
    
* 创建线程时出现栈溢出的原因：
    * thread的栈内存是相互独立的
    * 对于java，分配线程栈资源是在调用start()后开始，会调用native方法创建线程并获取相关资源，然后调用线程的run()方法 
    * 操作系统进程数限制通常比较大，但栈内存限制比较小
    
* 栈大小：
    * Dalvik上把java和native stack分开，默认java stack是32KB，native stack是1MB
    * ART一般情况线程栈的大小和Dalvik是一样的
    * https://developer.android.com/guide/practices/verifying-apps-art.html
    * 还有一个为了避免StackOverFlow的额外的8K 再加上Native的1MB 总量是1056KB
    * pthread_attr_getstacksize 返回的: 小米5 1016KByte 荣耀V10 1008KByte 但实际可以多几个KByte
    * ulimit -a 看到的 stack(KiB)  8192 不准确(不清楚原因)

* Native的栈溢出
    * SIGSEGV
    * 不能超过1M
    * 递归或者数组过大
    * 观察Debug.Meminfo获取的otherPrivateDirty和otherPss会增大
    * otherPss区别与dalvikPss nativePss，包含C/C++的非堆内存(包含栈内存)
    * otherPss是dumpsys meminfo <appID> 返回 TOTAL - GL_mtrack - Native_Heap - Dalvik_Heap
    * dumpsys meminfo <appID> 中 Unknown一行 PrivateDirty反映函数中栈内存 比如int8_t temp[1024*1024]
