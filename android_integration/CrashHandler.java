package com.yourpackage.issuereporter;

import android.app.Application;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 全局崩溃处理器
 * 自动捕获和处理应用崩溃
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";
    private static final long ANR_TIMEOUT = 5000; // 5秒ANR检测
    
    private Application application;
    private IssueReporter issueReporter;
    private Thread.UncaughtExceptionHandler defaultHandler;
    private Handler mainHandler;
    private Runnable anrRunnable;
    
    public CrashHandler(Application application, String userId) {
        this.application = application;
        this.issueReporter = new IssueReporter(application, userId);
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // 设置ANR检测
        setupANRDetection();
    }
    
    public void install() {
        Thread.setDefaultUncaughtExceptionHandler(this);
        Log.i(TAG, "CrashHandler installed");
    }
    
    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            // 收集崩溃信息
            String additionalInfo = collectAdditionalInfo();
            
            // 报告崩溃
            issueReporter.reportCrash(throwable, additionalInfo);
            
            // 记录到系统日志
            Log.e(TAG, "Uncaught exception in thread " + thread.getName(), throwable);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in crash handler", e);
        } finally {
            // 调用默认处理器
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        }
    }
    
    private void setupANRDetection() {
        anrRunnable = new Runnable() {
            @Override
            public void run() {
                // 检查主线程是否卡住
                if (Looper.getMainLooper().getThread().getState() == Thread.State.BLOCKED) {
                    reportANR();
                }
                
                // 检查内存使用情况
                checkMemoryUsage();
                
                // 继续检测
                mainHandler.postDelayed(this, ANR_TIMEOUT);
            }
        };
        
        mainHandler.post(anrRunnable);
    }
    
    private void reportANR() {
        try {
            String currentActivity = getCurrentActivity();
            issueReporter.reportANR(currentActivity, ANR_TIMEOUT);
            Log.w(TAG, "ANR detected in activity: " + currentActivity);
        } catch (Exception e) {
            Log.e(TAG, "Error reporting ANR", e);
        }
    }
    
    private void checkMemoryUsage() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            
            // 如果内存使用超过80%，报告内存问题
            if ((usedMemory * 100) / maxMemory > 80) {
                issueReporter.reportMemoryIssue("High Memory Usage", usedMemory, maxMemory);
                Log.w(TAG, "High memory usage detected: " + (usedMemory * 100) / maxMemory + "%");
            }
            
            // 如果可用内存很少，报告内存不足
            long freeMemory = runtime.freeMemory();
            if (freeMemory < 50 * 1024 * 1024) { // 少于50MB
                issueReporter.reportMemoryIssue("Low Free Memory", usedMemory, maxMemory);
                Log.w(TAG, "Low free memory detected: " + freeMemory / 1024 / 1024 + "MB");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking memory usage", e);
        }
    }
    
    private String getCurrentActivity() {
        // 这里可以通过ActivityLifecycleCallbacks获取当前Activity
        // 或者使用其他方式获取当前Activity名称
        return "Unknown";
    }
    
    private String collectAdditionalInfo() {
        StringBuilder info = new StringBuilder();
        
        try {
            // 系统信息
            info.append("System Info:\n");
            info.append("Android Version: ").append(Build.VERSION.RELEASE).append("\n");
            info.append("API Level: ").append(Build.VERSION.SDK_INT).append("\n");
            info.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
            info.append("Architecture: ").append(Build.CPU_ABI).append("\n");
            
            // 内存信息
            Runtime runtime = Runtime.getRuntime();
            info.append("\nMemory Info:\n");
            info.append("Max Memory: ").append(runtime.maxMemory() / 1024 / 1024).append("MB\n");
            info.append("Total Memory: ").append(runtime.totalMemory() / 1024 / 1024).append("MB\n");
            info.append("Free Memory: ").append(runtime.freeMemory() / 1024 / 1024).append("MB\n");
            info.append("Used Memory: ").append((runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024).append("MB\n");
            
            // 线程信息
            info.append("\nThread Info:\n");
            info.append("Active Threads: ").append(Thread.activeCount()).append("\n");
            info.append("Main Thread State: ").append(Looper.getMainLooper().getThread().getState()).append("\n");
            
            // 调试信息
            if (BuildConfig.DEBUG) {
                info.append("\nDebug Info:\n");
                info.append("Debugger Connected: ").append(Debug.isDebuggerConnected()).append("\n");
            }
            
        } catch (Exception e) {
            info.append("Error collecting additional info: ").append(e.getMessage());
        }
        
        return info.toString();
    }
    
    /**
     * 停止ANR检测
     */
    public void stopANRDetection() {
        if (anrRunnable != null) {
            mainHandler.removeCallbacks(anrRunnable);
        }
    }
}