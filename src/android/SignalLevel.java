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
import java.text.SimpleDateFormat;
import java.util.Date;
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
    private JSONObject lastInfo = new JSONObject();
    private boolean NO_PARSE_INTENT_VALS = false;
    String strength = "";
    String networkOperatorName = "";   //运营商名字

    public PhoneGeneralInfo phoneGeneralInfo;
    public CellGeneralInfo serverCellInfo;
    private List<CellGeneralInfo> HistoryServerCellList;

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
                LOG.e(LOG_TAG, p + "没权限11111");
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
        phoneGeneralInfo = new PhoneGeneralInfo();
        serverCellInfo = new CellGeneralInfo();
        this.sockMan = (ConnectivityManager) cordova.getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        mTelephonyManager = (TelephonyManager) cordova.getActivity().getSystemService(Context.TELEPHONY_SERVICE);

        mListener = new PhoneStatListener();
        mListenerLocation = new PhoneStatListener();
        mTelephonyManager.listen(mListener, PhoneStatListener.LISTEN_SIGNAL_STRENGTHS);

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

        JSONObject connectionInfo = new JSONObject();

        try {
            connectionInfo.put("type", type);
            connectionInfo.put("extraInfo", extraInfo);

            JSONObject phoneGeneralInfoJson = new JSONObject();
            phoneGeneralInfoJson.put("operaterName", phoneGeneralInfo.operaterName);
            phoneGeneralInfoJson.put("operaterId", phoneGeneralInfo.operaterId);
            phoneGeneralInfoJson.put("mnc", phoneGeneralInfo.mnc);
            phoneGeneralInfoJson.put("mcc", phoneGeneralInfo.mcc);
            phoneGeneralInfoJson.put("phoneDatastate", phoneGeneralInfo.phoneDatastate);
            phoneGeneralInfoJson.put("deviceId", phoneGeneralInfo.deviceId);
            phoneGeneralInfoJson.put("Imei", phoneGeneralInfo.Imei);
            phoneGeneralInfoJson.put("Imsi", phoneGeneralInfo.Imsi);
            phoneGeneralInfoJson.put("serialNumber", phoneGeneralInfo.serialNumber);
            phoneGeneralInfoJson.put("deviceSoftwareVersion", phoneGeneralInfo.deviceSoftwareVersion);
            phoneGeneralInfoJson.put("phoneModel", phoneGeneralInfo.phoneModel);
            phoneGeneralInfoJson.put("ratType", phoneGeneralInfo.ratType);
            phoneGeneralInfoJson.put("sdk", phoneGeneralInfo.sdk);

            connectionInfo.put("phoneGeneralInfoJson", phoneGeneralInfoJson);

            JSONObject serverCellInfoJson = new JSONObject();
            serverCellInfoJson.put("CId", serverCellInfo.CId);
            serverCellInfoJson.put("pci", serverCellInfo.pci);
            serverCellInfoJson.put("tac", serverCellInfo.tac);
            serverCellInfoJson.put("rsrp", serverCellInfo.rsrp);
            serverCellInfoJson.put("asulevel", serverCellInfo.asulevel);
            serverCellInfoJson.put("RatType", serverCellInfo.RatType);
            serverCellInfoJson.put("rssi", serverCellInfo.RatType);
            serverCellInfoJson.put("rsrp", serverCellInfo.rssi);
            serverCellInfoJson.put("sinr", serverCellInfo.sinr);
            serverCellInfoJson.put("cqi", serverCellInfo.cqi);


            connectionInfo.put("serverCellInfoJson", serverCellInfoJson);


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

            getPhoneGeneralInfo();
            getServerCellInfo();


            if (phoneGeneralInfo.ratType == TelephonyManager.NETWORK_TYPE_LTE) {
                try {
                    serverCellInfo.rssi = (Integer) signalStrength.getClass().getMethod("getLteSignalStrength").invoke(signalStrength);
                    serverCellInfo.rsrp = (Integer) signalStrength.getClass().getMethod("getLteRsrp").invoke(signalStrength);
                    serverCellInfo.rsrq = (Integer) signalStrength.getClass().getMethod("getLteRsrq").invoke(signalStrength);
                    serverCellInfo.sinr = (Integer) signalStrength.getClass().getMethod("getLteRssnr").invoke(signalStrength);
                    serverCellInfo.cqi = (Integer) signalStrength.getClass().getMethod("getLteCqi").invoke(signalStrength);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            } else if (phoneGeneralInfo.ratType == TelephonyManager.NETWORK_TYPE_GSM) {
                try {
                    serverCellInfo.rssi = signalStrength.getGsmSignalStrength();
                    serverCellInfo.rsrp = (Integer) signalStrength.getClass().getMethod("getGsmDbm").invoke(signalStrength);
                    serverCellInfo.asulevel = (Integer) signalStrength.getClass().getMethod("getAsuLevel").invoke(signalStrength);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            } else if (phoneGeneralInfo.ratType == TelephonyManager.NETWORK_TYPE_TD_SCDMA) {
                try {
                    serverCellInfo.rssi = (Integer) signalStrength.getClass().getMethod("getTdScdmaLevel").invoke(signalStrength);
                    serverCellInfo.rsrp = (Integer) signalStrength.getClass().getMethod("getTdScdmaDbm").invoke(signalStrength);
                    serverCellInfo.asulevel = (Integer) signalStrength.getClass().getMethod("getAsuLevel").invoke(signalStrength);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
            Date now = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("hh:mm:ss");
            serverCellInfo.time = formatter.format(now);

        }

        @Override
        public void onCellLocationChanged(CellLocation location) {
            super.onCellLocationChanged(location);
        }
    }


    @SuppressLint("MissingPermission")
    public void getPhoneGeneralInfo() {
        phoneGeneralInfo.operaterName = mTelephonyManager.getNetworkOperatorName();
        phoneGeneralInfo.operaterId = mTelephonyManager.getNetworkOperator();
        phoneGeneralInfo.mnc = Integer.parseInt(phoneGeneralInfo.operaterId.substring(0, 3));
        phoneGeneralInfo.mcc = Integer.parseInt(phoneGeneralInfo.operaterId.substring(3));
        phoneGeneralInfo.phoneDatastate = mTelephonyManager.getDataState();
        phoneGeneralInfo.deviceId = mTelephonyManager.getDeviceId();
        phoneGeneralInfo.Imei = mTelephonyManager.getSimSerialNumber();
        phoneGeneralInfo.Imsi = mTelephonyManager.getSubscriberId();
        phoneGeneralInfo.serialNumber = mTelephonyManager.getSimSerialNumber();
        phoneGeneralInfo.deviceSoftwareVersion = android.os.Build.VERSION.RELEASE;
        phoneGeneralInfo.phoneModel = android.os.Build.MODEL;
        phoneGeneralInfo.ratType = mTelephonyManager.getNetworkType();
        phoneGeneralInfo.sdk = android.os.Build.VERSION.SDK_INT;
    }

    @SuppressLint("MissingPermission")
    public void getServerCellInfo() {
        try {
            List<CellInfo> allCellinfo;
            allCellinfo = mTelephonyManager.getAllCellInfo();
            if (allCellinfo != null) {
                CellInfo cellInfo = allCellinfo.get(0);
                serverCellInfo.getInfoType = 1;
                if (cellInfo instanceof CellInfoGsm) {
                    CellInfoGsm cellInfoGsm = (CellInfoGsm) cellInfo;
                    serverCellInfo.CId = cellInfoGsm.getCellIdentity().getCid();
                    serverCellInfo.rsrp = cellInfoGsm.getCellSignalStrength().getDbm();
                    serverCellInfo.asulevel = cellInfoGsm.getCellSignalStrength().getAsuLevel();
                    serverCellInfo.lac = cellInfoGsm.getCellIdentity().getLac();
                    serverCellInfo.RatType = TelephonyManager.NETWORK_TYPE_GSM;
                } else if (cellInfo instanceof CellInfoWcdma) {
                    CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) cellInfo;
                    serverCellInfo.CId = cellInfoWcdma.getCellIdentity().getCid();
                    serverCellInfo.psc = cellInfoWcdma.getCellIdentity().getPsc();
                    serverCellInfo.lac = cellInfoWcdma.getCellIdentity().getLac();
                    serverCellInfo.rsrp = cellInfoWcdma.getCellSignalStrength().getDbm();
                    serverCellInfo.asulevel = cellInfoWcdma.getCellSignalStrength().getAsuLevel();
                    serverCellInfo.RatType = TelephonyManager.NETWORK_TYPE_UMTS;
                } else if (cellInfo instanceof CellInfoLte) {
                    CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
                    serverCellInfo.CId = cellInfoLte.getCellIdentity().getCi();
                    serverCellInfo.pci = cellInfoLte.getCellIdentity().getPci();
                    serverCellInfo.tac = cellInfoLte.getCellIdentity().getTac();
                    serverCellInfo.rsrp = cellInfoLte.getCellSignalStrength().getDbm();
                    serverCellInfo.asulevel = cellInfoLte.getCellSignalStrength().getAsuLevel();
                    serverCellInfo.RatType = TelephonyManager.NETWORK_TYPE_LTE;
                }
            } else
            //for older devices
            {
                getServerCellInfoOnOlderDevices();
            }
        } catch (Exception e) {
//            getServerCellInfoOnOlderDevices();
        }

    }

    void getServerCellInfoOnOlderDevices() {
        @SuppressLint("MissingPermission") GsmCellLocation location = (GsmCellLocation) mTelephonyManager.getCellLocation();
        serverCellInfo.getInfoType = 0;
        serverCellInfo.CId = location.getCid();
        serverCellInfo.tac = location.getLac();
        serverCellInfo.psc = location.getPsc();
        serverCellInfo.type = phoneGeneralInfo.ratType;
    }

    void updateHistoryCellList(CellGeneralInfo serverinfo) {
        CellGeneralInfo newcellInfo = (CellGeneralInfo) serverinfo;
        HistoryServerCellList.add(newcellInfo);
    }

    class PhoneGeneralInfo {
        public String serialNumber;
        public String operaterName;
        public String operaterId;
        public String deviceId;
        public String deviceSoftwareVersion;
        public String Imsi;
        public String Imei;
        public int mnc;
        public int mcc;
        public int ratType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        public int phoneDatastate;
        public String phoneModel;
        public int sdk;
    }

    class CellGeneralInfo {
        public int type;
        public int CId;
        public int lac;
        public int tac;
        public int psc;
        public int pci;
        public int RatType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        public int rsrp;
        public int rsrq;
        public int sinr;
        public int rssi;
        public int cqi;
        public int asulevel;
        public int getInfoType;
        public String time;

    }


    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        if (mTelephonyManager != null) {
            mTelephonyManager.listen(mListener, PhoneStatListener.LISTEN_NONE);
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        if (mTelephonyManager != null) {
            mTelephonyManager.listen(mListener, PhoneStatListener.LISTEN_SIGNAL_STRENGTHS);
        }
    }


}
