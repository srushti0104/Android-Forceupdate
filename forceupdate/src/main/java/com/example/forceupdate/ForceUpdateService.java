package com.example.forceupdate;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;


import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.appsonair.AppUpdateActivity;
import com.appsonair.AppsOnAirServices;
import com.appsonair.BuildConfig;
import com.appsonair.MaintenanceActivity;
import com.appsonair.UpdateCallBack;

public class ForceUpdateService {

    static String appId;
    static boolean showNativeDialog;
    public static  void getAppId(Context context, boolean showNativeUI){
        showNativeDialog = showNativeUI;
        AppsOnAirServices.setAppId(context,showNativeUI);
    }


    public static void getResponse(@NonNull Response response, Context context, UpdateCallBack callBack, boolean isFromCDN) {
        try {
            if (response.code() == 200) {
                String myResponse = response.body().string();
                JSONObject jsonObject = new JSONObject(myResponse);
                JSONObject updateData = jsonObject.getJSONObject("updateData");
                boolean isAndroidUpdate = updateData.getBoolean("isAndroidUpdate");
                boolean isMaintenance = jsonObject.getBoolean("isMaintenance");
                if (isAndroidUpdate) {
                    boolean isAndroidForcedUpdate = updateData.getBoolean("isAndroidForcedUpdate");
                    String androidBuildNumber = updateData.getString("androidBuildNumber");
                    PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                    int versionCode = info.versionCode;
                    int buildNum = 0;

                    if (!(androidBuildNumber.equals(null))) {
                        buildNum = Integer.parseInt(androidBuildNumber);
                    }
                    boolean isUpdate = versionCode < buildNum;
                    if (showNativeDialog && isUpdate && (isAndroidForcedUpdate || isAndroidUpdate)) {
                        Intent intent = new Intent(context, AppUpdateActivity.class);
                        intent.putExtra("res", myResponse);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    }
                } else if (isMaintenance && showNativeDialog) {
                    Intent intent = new Intent(context, MaintenanceActivity.class);
                    intent.putExtra("res", myResponse);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
                callBack.onSuccess(myResponse);
            } else if (isFromCDN) {
                callServiceApi(context, callBack);
            }
        } catch (Exception e) {
            callBack.onFailure(e.getMessage());
            Log.d("CDN", "getResponse: " + e.getMessage());
        }
    }

    public static void callCDNServiceApi(Context context, UpdateCallBack callBack) {
        String baseUrl = BuildConfig.Base_URL;

        String pathSegment = ForceUpdateService.appId + ".json";

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        long unixTime = System.currentTimeMillis() / 1000L;
        // Add the path segment
        urlBuilder.addPathSegment(pathSegment);
        // Add query parameters
        urlBuilder.addQueryParameter("now", String.valueOf(unixTime));
        // Build the URL with query parameters
        String url = urlBuilder.build().toString();
        Log.d("CDN", "URL: AppsOnAirCDNApi" + url);

        OkHttpClient client = new OkHttpClient().newBuilder().build();
        Request request = new Request.Builder().url(url).method("GET", null).build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.d("CDN", "onFailure: AppsOnAirCDNApi" + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                Log.d("CDN", "CDN Service response: " + response);
                getResponse(response, context, callBack, true);
            }
        });
    }

    public static void callServiceApi(Context context, UpdateCallBack callBack) {
        String url = BuildConfig.Base_URL + ForceUpdateService.appId;
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        Request request = new Request.Builder().url(url).method("GET", null).build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.d("BASE URL", "onFailure: AppsOnAirServiceApi" + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                getResponse(response, context, callBack, false);
            }
        });
    }

    public static void checkForAppUpdate(Context context, UpdateCallBack callBack, boolean showNativeUI) {
        getAppId(context,showNativeUI);
        ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                callCDNServiceApi(context, callBack);
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
            }
        };

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } else {
            NetworkRequest request = new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
            connectivityManager.registerNetworkCallback(request, networkCallback);
        }
    }

}
