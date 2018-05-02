/**
 * com.lampa.startapp
 * https://github.com/lampaa/com.lampa.startapp
 * <p>
 * Phonegap plugin for check or launch other application in android device (iOS support).
 * bug tracker: https://github.com/lampaa/com.lampa.startapp/issues
 */
package com.chinamobile.gdwy;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class SignalLevel extends CordovaPlugin {

    public static final String TAG = "SignalLevel";

    public SignalLevel() {
    }

    String[] permissions = {Manifest.permission.INTERNET, Manifest.permission.CHANGE_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};

    public static int NOT_REACHABLE = 0;
    public static int REACHABLE_VIA_CARRIER_DATA_NETWORK = 1;
    public static int REACHABLE_VIA_WIFI_NETWORK = 2;

    public static final String WIFI = "wifi";
    public static final String WIMAX = "wimax";
    // mobile
    public static final String MOBILE = "mobile";

    // Android L calls this Cellular, because I have no idea!
    public static final String CELLULAR = "cellular";
    // 2G network types
    public static final String TWO_G = "2g";
    public static final String GSM = "gsm";
    public static final String GPRS = "gprs";
    public static final String EDGE = "edge";
    // 3G network types
    public static final String THREE_G = "3g";
    public static final String CDMA = "cdma";
    public static final String UMTS = "umts";
    public static final String HSPA = "hspa";
    public static final String HSUPA = "hsupa";
    public static final String HSDPA = "hsdpa";
    public static final String ONEXRTT = "1xrtt";
    public static final String EHRPD = "ehrpd";
    // 4G network types
    public static final String FOUR_G = "4g";
    public static final String LTE = "lte";
    public static final String UMB = "umb";
    public static final String HSPA_PLUS = "hspa+";
    // return type
    public static final String TYPE_UNKNOWN = "unknown";
    public static final String TYPE_ETHERNET = "ethernet";
    public static final String TYPE_ETHERNET_SHORT = "eth";
    public static final String TYPE_WIFI = "wifi";
    public static final String TYPE_2G = "2g";
    public static final String TYPE_3G = "3g";
    public static final String TYPE_4G = "4g";
    public static final String TYPE_NONE = "none";

    private static final String LOG_TAG = "NetworkManager";
    public PhoneStatListener mListener; //监听强度
    public PhoneStatListener mListenerLocation;  //监听站点变化

    private CallbackContext connectionCallbackContext;
    TelephonyManager mTelephonyManager;
    ConnectivityManager sockMan;
    BroadcastReceiver receiver; //注册广播
    private JSONObject lastInfo = null;
    private boolean NO_PARSE_INTENT_VALS = false;
    String strength = "";
    GsmCell gsmCell = new GsmCell(); //当前基站对象
    String networkOperatorName = "";   //运营商名字

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action          The action to execute.
     * @param args            JSONArray of arguments for the plugin.
     * @param callbackContext The callback context used when calling back into JavaScript.
     * @return Always return true.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        connectionCallbackContext = callbackContext;
        if (action.equals("getSignal")) {
            try {
                if (hasPermisssion()) {
                    connectionCallbackContext.success(lastInfo);
                    return true;
                } else {
                    PermissionHelper.requestPermissions(this, 0, permissions);
                }
            } catch (Exception e) {
                connectionCallbackContext.error("获取信息失败");
            }
            return true;
        }

        return true;
    }


    public boolean hasPermisssion() {
        for (String p : permissions) {
            if (!PermissionHelper.hasPermission(this, p)) {
                LOG.e(LOG_TAG, p + "没权限");
                return false;
            }
        }
        return true;
    }

    public void requestPermissions(int requestCode) {
        PermissionHelper.requestPermissions(this, requestCode, permissions);
    }


    //network plugin
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.sockMan = (ConnectivityManager) cordova.getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        mTelephonyManager = (TelephonyManager) cordova.getActivity().getSystemService(Context.TELEPHONY_SERVICE);

        mListener = new PhoneStatListener();
        mListenerLocation = new PhoneStatListener();
        mTelephonyManager.listen(mListener, PhoneStatListener.LISTEN_SIGNAL_STRENGTHS);
        mTelephonyManager.listen(mListenerLocation, PhoneStateListener.LISTEN_CELL_LOCATION);

        this.connectionCallbackContext = null;
        if (hasPermisssion()) {

        } else {
            PermissionHelper.requestPermissions(this, 0, permissions);
        }

        // We need to listen to connectivity events to update navigator.connection
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);

        if (this.receiver == null) {
            this.receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // (The null check is for the ARM Emulator, please use Intel Emulator for better results)
                    if (SignalLevel.this.webView != null) {
                        updateConnectionInfo(sockMan.getActiveNetworkInfo());
                    }
                }
            };
            webView.getContext().registerReceiver(this.receiver, intentFilter);
        }

        if (hasPermisssion()) {

        }

    }


    /**
     * Stop network receiver.
     */
    public void onDestroy() {
        if (this.receiver != null) {
            try {

                webView.getContext().unregisterReceiver(this.receiver);
                mTelephonyManager.listen(mListener, PhoneStatListener.LISTEN_NONE);
            } catch (Exception e) {
                LOG.e(LOG_TAG, "Error unregistering network receiver: " + e.getMessage(), e);
            } finally {
                receiver = null;
            }
        }
    }

    protected void onResume() {
        LOG.e(LOG_TAG, "唤醒");
        mTelephonyManager.listen(mListener, PhoneStatListener.LISTEN_SIGNAL_STRENGTHS);
    }

    protected void onPause() {
        //用户不在当前页面时，停止监听
        LOG.e(LOG_TAG, "死了");
        mTelephonyManager.listen(mListener, PhoneStatListener.LISTEN_NONE);
    }


    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    /**
     * Updates the JavaScript side whenever the connection changes
     *
     * @param info the current active network info
     * @return
     */
    private void updateConnectionInfo(NetworkInfo info) {
        // send update to javascript "navigator.network.connection"
        // Jellybean sends its own info
        JSONObject thisInfo = this.getConnectionInfo(info);
        if (!thisInfo.equals(lastInfo)) {
            String connectionType = "";
            try {
                connectionType = thisInfo.get("type").toString();
            } catch (JSONException e) {
                LOG.d(LOG_TAG, e.getLocalizedMessage());
            }
            lastInfo = thisInfo;
            sendUpdate(connectionType);

        }
    }

    /**
     * Get the latest network connection information
     *
     * @param info the current active network info
     * @return a JSONObject that represents the network info
     */
    private JSONObject getConnectionInfo(NetworkInfo info) {
        String type = TYPE_NONE;
        String extraInfo = "";
        if (info != null) {
            // If we are not connected to any network set type to none
            if (!info.isConnected()) {
                type = TYPE_NONE;
            } else {
                type = getType(info);
            }
            extraInfo = info.getExtraInfo();
        }

        LOG.e(LOG_TAG, "Connection Type: " + type);
        LOG.e(LOG_TAG, "Connection Extra Info: " + extraInfo);
        LOG.e(LOG_TAG, "Connection strength Info: " + strength);

        JSONObject connectionInfo = new JSONObject();

        try {
            connectionInfo.put("type", type);
            connectionInfo.put("extraInfo", extraInfo);
            connectionInfo.put("strength", strength);
            connectionInfo.put("gsmCell", gsmCell);
            connectionInfo.put("name", mTelephonyManager.getNetworkOperatorName());
            connectionInfo.put("networkType", mTelephonyManager.getNetworkType());

        } catch (JSONException e) {
            LOG.d(LOG_TAG, e.getLocalizedMessage());
        }

        return connectionInfo;
    }

    /**
     * Create a new plugin result and send it back to JavaScript
     */
    private void sendUpdate(String type) {
        if (connectionCallbackContext != null) {
            if (lastInfo == null)
                return;
            PluginResult result = new PluginResult(PluginResult.Status.OK, lastInfo);
            result.setKeepCallback(true);
            connectionCallbackContext.sendPluginResult(result);
        }
        webView.postMessage("networkconnection", lastInfo);
    }

    /**
     * Determine the type of connection
     *
     * @param info the network info so we can determine connection type.
     * @return the type of mobile network we are on
     */
    private String getType(NetworkInfo info) {
        if (info != null) {
            String type = info.getTypeName().toLowerCase(Locale.US);

            LOG.d(LOG_TAG, "toLower : " + type.toLowerCase());
            LOG.d(LOG_TAG, "wifi : " + WIFI);
            if (type.equals(WIFI)) {
                return TYPE_WIFI;
            } else if (type.toLowerCase().equals(TYPE_ETHERNET) || type.toLowerCase().startsWith(TYPE_ETHERNET_SHORT)) {
                return TYPE_ETHERNET;
            } else if (type.equals(MOBILE) || type.equals(CELLULAR)) {
                type = info.getSubtypeName().toLowerCase(Locale.US);
                if (type.equals(GSM) ||
                        type.equals(GPRS) ||
                        type.equals(EDGE) ||
                        type.equals(TWO_G)) {
                    return TYPE_2G;
                } else if (type.startsWith(CDMA) ||
                        type.equals(UMTS) ||
                        type.equals(ONEXRTT) ||
                        type.equals(EHRPD) ||
                        type.equals(HSUPA) ||
                        type.equals(HSDPA) ||
                        type.equals(HSPA) ||
                        type.equals(THREE_G)) {
                    return TYPE_3G;
                } else if (type.equals(LTE) ||
                        type.equals(UMB) ||
                        type.equals(HSPA_PLUS) ||
                        type.equals(FOUR_G)) {
                    return TYPE_4G;
                }
            }
        } else {
            return TYPE_NONE;
        }
        return TYPE_UNKNOWN;
    }

    public void setDBM(String DBM) {
        this.strength = DBM;
    }


    //listen
    private class PhoneStatListener extends PhoneStateListener {
        //获取信号强度
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);
//获取网络信号强度
//获取0-4的5种信号级别，越大信号越好,但是api23开始才能用
// int level = signalStrength.getLevel();


            String signalInfo = signalStrength.toString();
            String[] params = signalInfo.split(" ");


            if (mTelephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_LTE) {
                //4G网络 最佳范围   >-90dBm 越大越好

                Method method1 = null;

                try {
                    method1 = signalStrength.getClass().getMethod("getDbm");
                    strength = String.valueOf(method1.invoke(signalStrength));
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
                LOG.e(LOG_TAG, strength + "lte");

                int asu = signalStrength.getGsmSignalStrength();
                int dbm = -113 + 2 * asu; //信号强度
                LOG.e(LOG_TAG, dbm + "lte2");

            } else if (mTelephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_HSDPA ||
                    mTelephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_HSPA ||
                    mTelephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_HSUPA ||
                    mTelephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_UMTS) {
                //3G网络最佳范围  >-90dBm  越大越好  ps:中国移动3G获取不到  返回的无效dbm值是正数（85dbm）
                //在这个范围的已经确定是3G，但不同运营商的3G有不同的获取方法，故在此需做判断 判断运营商与网络类型的工具类在最下方

                String temp = mTelephonyManager.getNetworkOperator();
                if (temp.equals("46000") || temp.equals("46002")) {
                    int asu = signalStrength.getGsmSignalStrength();
                    int dbm = -113 + 2 * asu;
                    setDBM(dbm + "");
                    LOG.e(LOG_TAG, strength + "other");
                } else if (temp.equals("46001")) {
                    int cdmaDbm = signalStrength.getCdmaDbm();
                    setDBM(cdmaDbm + "");
                    LOG.e(LOG_TAG, strength + "46001");
                } else if (temp.equals("46003")) {
                    int evdoDbm = signalStrength.getEvdoDbm();
                    setDBM(evdoDbm + "");
                    LOG.e(LOG_TAG, strength + "46003");
                }

            } else {
                //2G网络最佳范围>-90dBm 越大越好
                int asu = signalStrength.getGsmSignalStrength();
                int dbm = -113 + 2 * asu;
                setDBM(dbm + "");
                LOG.e(LOG_TAG, strength + "other");
            }

//            @SuppressLint("MissingPermission") List<NeighboringCellInfo> infos = mTelephonyManager.getNeighboringCellInfo();
//            for (NeighboringCellInfo info : infos) {
//                //获取邻居小区号
//                int cid = info.getCid();
//                //获取邻居小区LAC，LAC: 位置区域码。为了确定移动台的位置，每个GSM/PLMN的覆盖区都被划分成许多位置区，LAC则用于标识不同的位置区。
//                info.getLac();
//                info.getNetworkType();
//                info.getPsc();
//                //获取邻居小区信号强度
//                info.getRssi();
//            }


            getOther();
        }

        @Override
        public void onCellLocationChanged(CellLocation location) {
            super.onCellLocationChanged(location);
            if (location instanceof GsmCellLocation) {// gsm网络
                getOther();
            }

        }
    }

    private void getOther() {
        mTelephonyManager.getNetworkOperatorName();


        try {
            @SuppressLint("MissingPermission") List<NeighboringCellInfo> infos = mTelephonyManager.getNeighboringCellInfo();
            for (NeighboringCellInfo info : infos) {
                //获取邻居小区号
                int cid = info.getCid();
                //获取邻居小区LAC，LAC: 位置区域码。为了确定移动台的位置，每个GSM/PLMN的覆盖区都被划分成许多位置区，LAC则用于标识不同的位置区。
                info.getLac();
                info.getNetworkType();
                info.getPsc();
                //获取邻居小区信号强度
                info.getRssi();
                int ss = -131 + 2 * info.getRssi();
                LOG.e(LOG_TAG, cid + " cid " + info.getLac() + " " + info.getNetworkType() + "   " + info.getRssi());

            }
        } catch (Exception e) {

        }


        @SuppressLint("MissingPermission") GsmCellLocation location = (GsmCellLocation) mTelephonyManager.getCellLocation();
        if (location != null) {
            gsmCell.lac = ((GsmCellLocation) location).getLac() + "";
            gsmCell.cid = ((GsmCellLocation) location).getCid() + "";
            String mccMnc = mTelephonyManager.getNetworkOperator();
            if (mccMnc != null && mccMnc.length() >= 5) {
                gsmCell.mcc = mccMnc.substring(0, 3);
                gsmCell.mnc = mccMnc.substring(3, 5);
            }
            LOG.e(LOG_TAG, gsmCell.toString());
        }
        updateConnectionInfo(sockMan.getActiveNetworkInfo());
    }


    /**
     * 基站信息结构体
     */
    public class GsmCell {
        public String mcc;
        public String mnc;
        public String lac;
        public String cid;

        @Override
        public String toString() {
            return "mcc: " + mcc + " mnc " + mnc + " lac " + lac + "  cid  " + cid;
        }
    }

    /**
     * 经纬度信息结构体
     */
    public class SItude {
        public String latitude;
        public String longitude;
    }
}
