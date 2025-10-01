package com.yourpackage.issuereporter;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 安卓问题报告器
 * 自动收集崩溃日志和设备信息，便于线上问题诊断
 */
public class IssueReporter {
    private static final String TAG = "IssueReporter";
    private static final String LOG_DIR = "issue_logs";
    
    private Context context;
    private String userId;
    private String appVersion;
    private String deviceInfo;
    
    public IssueReporter(Context context, String userId) {
        this.context = context.getApplicationContext();
        this.userId = userId;
        this.appVersion = getAppVersion();
        this.deviceInfo = getDeviceInfo();
    }
    
    /**
     * 报告崩溃异常
     */
    public void reportCrash(Throwable throwable, String additionalInfo) {
        try {
            JSONObject crashReport = new JSONObject();
            crashReport.put("timestamp", getCurrentTimestamp());
            crashReport.put("user_id", userId);
            crashReport.put("app_version", appVersion);
            crashReport.put("device_info", deviceInfo);
            crashReport.put("exception_type", throwable.getClass().getSimpleName());
            crashReport.put("exception_message", throwable.getMessage());
            crashReport.put("stack_trace", getStackTrace(throwable));
            crashReport.put("additional_info", additionalInfo);
            
            // 保存到本地文件
            saveCrashReport(crashReport);
            
            // 可选：上传到服务器
            uploadCrashReport(crashReport);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to report crash", e);
        }
    }
    
    /**
     * 报告ANR (Application Not Responding)
     */
    public void reportANR(String activityName, long duration) {
        try {
            JSONObject anrReport = new JSONObject();
            anrReport.put("timestamp", getCurrentTimestamp());
            anrReport.put("user_id", userId);
            anrReport.put("app_version", appVersion);
            anrReport.put("device_info", deviceInfo);
            anrReport.put("issue_type", "ANR");
            anrReport.put("activity_name", activityName);
            anrReport.put("duration_ms", duration);
            anrReport.put("main_thread_info", getMainThreadInfo());
            
            saveCrashReport(anrReport);
            uploadCrashReport(anrReport);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to report ANR", e);
        }
    }
    
    /**
     * 报告内存问题
     */
    public void reportMemoryIssue(String issueType, long usedMemory, long maxMemory) {
        try {
            JSONObject memoryReport = new JSONObject();
            memoryReport.put("timestamp", getCurrentTimestamp());
            memoryReport.put("user_id", userId);
            memoryReport.put("app_version", appVersion);
            memoryReport.put("device_info", deviceInfo);
            memoryReport.put("issue_type", issueType);
            memoryReport.put("used_memory_mb", usedMemory / 1024 / 1024);
            memoryReport.put("max_memory_mb", maxMemory / 1024 / 1024);
            memoryReport.put("memory_usage_percent", (usedMemory * 100) / maxMemory);
            
            saveCrashReport(memoryReport);
            uploadCrashReport(memoryReport);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to report memory issue", e);
        }
    }
    
    /**
     * 报告网络问题
     */
    public void reportNetworkIssue(String url, int responseCode, String errorMessage) {
        try {
            JSONObject networkReport = new JSONObject();
            networkReport.put("timestamp", getCurrentTimestamp());
            networkReport.put("user_id", userId);
            networkReport.put("app_version", appVersion);
            networkReport.put("device_info", deviceInfo);
            networkReport.put("issue_type", "Network");
            networkReport.put("url", url);
            networkReport.put("response_code", responseCode);
            networkReport.put("error_message", errorMessage);
            networkReport.put("network_type", getNetworkType());
            
            saveCrashReport(networkReport);
            uploadCrashReport(networkReport);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to report network issue", e);
        }
    }
    
    private String getAppVersion() {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName + " (" + packageInfo.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return "Unknown";
        }
    }
    
    private String getDeviceInfo() {
        return String.format(Locale.getDefault(),
                "Android %s, API %d, %s %s",
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                Build.MANUFACTURER,
                Build.MODEL);
    }
    
    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
    
    private String getStackTrace(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");
        
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("    at ").append(element.toString()).append("\n");
        }
        
        // 添加cause信息
        Throwable cause = throwable.getCause();
        if (cause != null) {
            sb.append("Caused by: ").append(cause.toString()).append("\n");
            for (StackTraceElement element : cause.getStackTrace()) {
                sb.append("    at ").append(element.toString()).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    private String getMainThreadInfo() {
        Thread mainThread = Thread.currentThread();
        return String.format("Thread: %s, State: %s, Priority: %d",
                mainThread.getName(),
                mainThread.getState(),
                mainThread.getPriority());
    }
    
    private String getNetworkType() {
        // 这里可以添加网络类型检测逻辑
        return "Unknown";
    }
    
    private void saveCrashReport(JSONObject report) throws IOException {
        File logDir = new File(context.getFilesDir(), LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        
        String fileName = String.format("crash_%s_%s.json",
                userId,
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));
        
        File logFile = new File(logDir, fileName);
        try (FileWriter writer = new FileWriter(logFile)) {
            writer.write(report.toString(2));
        }
        
        Log.i(TAG, "Crash report saved: " + logFile.getAbsolutePath());
    }
    
    private void uploadCrashReport(JSONObject report) {
        // 这里实现上传到服务器的逻辑
        // 可以使用Retrofit、OkHttp等网络库
        Log.i(TAG, "Uploading crash report: " + report.optString("issue_type"));
        
        // 示例：异步上传
        new Thread(() -> {
            try {
                // 实现上传逻辑
                // uploadToServer(report);
                Log.i(TAG, "Crash report uploaded successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to upload crash report", e);
            }
        }).start();
    }
    
    /**
     * 获取所有本地日志文件
     */
    public File[] getLocalLogFiles() {
        File logDir = new File(context.getFilesDir(), LOG_DIR);
        if (logDir.exists()) {
            return logDir.listFiles((dir, name) -> name.endsWith(".json"));
        }
        return new File[0];
    }
    
    /**
     * 清理旧的日志文件（保留最近7天）
     */
    public void cleanupOldLogs() {
        File logDir = new File(context.getFilesDir(), LOG_DIR);
        if (logDir.exists()) {
            File[] files = logDir.listFiles();
            if (files != null) {
                long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
                for (File file : files) {
                    if (file.lastModified() < sevenDaysAgo) {
                        file.delete();
                    }
                }
            }
        }
    }
}