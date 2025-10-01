package com.yourpackage.issuereporter;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 网络问题报告器
 * 监控和报告网络相关问题
 */
public class NetworkIssueReporter {
    private static final String TAG = "NetworkIssueReporter";
    
    private Context context;
    private IssueReporter issueReporter;
    private OkHttpClient httpClient;
    
    public NetworkIssueReporter(Context context, IssueReporter issueReporter) {
        this.context = context;
        this.issueReporter = issueReporter;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 监控网络请求
     */
    public void monitorRequest(String url, Request request) {
        long startTime = System.currentTimeMillis();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                long duration = System.currentTimeMillis() - startTime;
                reportNetworkFailure(url, e.getMessage(), duration);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                long duration = System.currentTimeMillis() - startTime;
                
                if (!response.isSuccessful()) {
                    reportNetworkError(url, response.code(), response.message(), duration);
                } else if (duration > 10000) { // 超过10秒
                    reportSlowNetwork(url, response.code(), duration);
                }
                
                response.close();
            }
        });
    }
    
    /**
     * 检查网络连接状态
     */
    public void checkNetworkStatus() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            
            if (!isConnected) {
                reportNetworkDisconnection();
            } else {
                String networkType = getNetworkType(activeNetwork);
                Log.d(TAG, "Network connected: " + networkType);
            }
        }
    }
    
    /**
     * 测试网络连通性
     */
    public void testNetworkConnectivity() {
        // 测试DNS解析
        testDNSResolution();
        
        // 测试HTTP连接
        testHTTPConnection();
        
        // 测试HTTPS连接
        testHTTPSConnection();
    }
    
    private void reportNetworkFailure(String url, String errorMessage, long duration) {
        try {
            JSONObject report = new JSONObject();
            report.put("timestamp", System.currentTimeMillis());
            report.put("issue_type", "Network Failure");
            report.put("url", url);
            report.put("error_message", errorMessage);
            report.put("duration_ms", duration);
            report.put("network_type", getCurrentNetworkType());
            
            issueReporter.reportNetworkIssue(url, -1, errorMessage);
            Log.w(TAG, "Network failure reported: " + url + " - " + errorMessage);
            
        } catch (Exception e) {
            Log.e(TAG, "Error reporting network failure", e);
        }
    }
    
    private void reportNetworkError(String url, int responseCode, String message, long duration) {
        try {
            issueReporter.reportNetworkIssue(url, responseCode, message);
            Log.w(TAG, "Network error reported: " + url + " - " + responseCode + " " + message);
            
        } catch (Exception e) {
            Log.e(TAG, "Error reporting network error", e);
        }
    }
    
    private void reportSlowNetwork(String url, int responseCode, long duration) {
        try {
            String message = "Slow network response: " + duration + "ms";
            issueReporter.reportNetworkIssue(url, responseCode, message);
            Log.w(TAG, "Slow network reported: " + url + " - " + duration + "ms");
            
        } catch (Exception e) {
            Log.e(TAG, "Error reporting slow network", e);
        }
    }
    
    private void reportNetworkDisconnection() {
        try {
            issueReporter.reportNetworkIssue("", -1, "Network disconnected");
            Log.w(TAG, "Network disconnection reported");
            
        } catch (Exception e) {
            Log.e(TAG, "Error reporting network disconnection", e);
        }
    }
    
    private void testDNSResolution() {
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                java.net.InetAddress.getByName("www.google.com");
                long duration = System.currentTimeMillis() - startTime;
                
                if (duration > 5000) { // DNS解析超过5秒
                    issueReporter.reportNetworkIssue("DNS Resolution", 200, "Slow DNS: " + duration + "ms");
                }
                
            } catch (Exception e) {
                issueReporter.reportNetworkIssue("DNS Resolution", -1, e.getMessage());
            }
        }).start();
    }
    
    private void testHTTPConnection() {
        Request request = new Request.Builder()
                .url("http://httpbin.org/get")
                .build();
                
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                issueReporter.reportNetworkIssue("HTTP Test", -1, e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    issueReporter.reportNetworkIssue("HTTP Test", response.code(), response.message());
                }
                response.close();
            }
        });
    }
    
    private void testHTTPSConnection() {
        Request request = new Request.Builder()
                .url("https://httpbin.org/get")
                .build();
                
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                issueReporter.reportNetworkIssue("HTTPS Test", -1, e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    issueReporter.reportNetworkIssue("HTTPS Test", response.code(), response.message());
                }
                response.close();
            }
        });
    }
    
    private String getCurrentNetworkType() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null) {
                return getNetworkType(activeNetwork);
            }
        }
        return "Unknown";
    }
    
    private String getNetworkType(NetworkInfo networkInfo) {
        switch (networkInfo.getType()) {
            case ConnectivityManager.TYPE_WIFI:
                return "WiFi";
            case ConnectivityManager.TYPE_MOBILE:
                return "Mobile (" + networkInfo.getSubtypeName() + ")";
            case ConnectivityManager.TYPE_ETHERNET:
                return "Ethernet";
            case ConnectivityManager.TYPE_BLUETOOTH:
                return "Bluetooth";
            default:
                return "Other";
        }
    }
}