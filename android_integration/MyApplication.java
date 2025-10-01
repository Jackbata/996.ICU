package com.yourpackage.issuereporter;

import android.app.Application;
import android.util.Log;

/**
 * 应用主类
 * 初始化问题诊断系统
 */
public class MyApplication extends Application {
    private static final String TAG = "MyApplication";
    private static final String USER_ID = "user_12345"; // 实际使用时从用户登录信息获取
    
    private CrashHandler crashHandler;
    private IssueReporter issueReporter;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 初始化问题诊断系统
        initializeIssueDiagnosis();
        
        Log.i(TAG, "Application initialized with issue diagnosis system");
    }
    
    private void initializeIssueDiagnosis() {
        try {
            // 创建问题报告器
            issueReporter = new IssueReporter(this, USER_ID);
            
            // 创建并安装崩溃处理器
            crashHandler = new CrashHandler(this, USER_ID);
            crashHandler.install();
            
            // 清理旧的日志文件
            issueReporter.cleanupOldLogs();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize issue diagnosis system", e);
        }
    }
    
    /**
     * 获取问题报告器实例
     */
    public IssueReporter getIssueReporter() {
        return issueReporter;
    }
    
    /**
     * 获取崩溃处理器实例
     */
    public CrashHandler getCrashHandler() {
        return crashHandler;
    }
    
    @Override
    public void onTerminate() {
        super.onTerminate();
        
        // 停止ANR检测
        if (crashHandler != null) {
            crashHandler.stopANRDetection();
        }
    }
}