var exec = require('cordova/exec');
var cordova = require('cordova');
var channel = require('cordova/channel');
var utils = require('cordova/utils');

/**
 * MyToast :是plugin.xml中配置的feature的nema
 * showToast: 是js中调用的方法名
 */


/**
 * MyToast :是plugin.xml中配置的feature的nema
 * showToast: 是js中调用的方法名
 */
//exports.getSignal = function(success, error) {
//    exec(success, error, "SignalLevel", "getSignal", []);
//};

function SignalLevel() {
    this.data = '';
}


SignalLevel.prototype.getInfo = function (successCallback, errorCallback) {
    exec(successCallback, errorCallback, "SignalLevel", "getSignal", []);
};

SignalLevel.prototype.getData = function () {
    return this.data;
}

channel.createSticky('onCordovaConnectionReady');
channel.waitForInitialization('onCordovaConnectionReady');


var me = new SignalLevel();
var timerId = null;
var timeout = 2000;


channel.onCordovaReady.subscribe(function () {
    me.getInfo(function (info) {
            me.data = info;
            if (info === 'none') {
                // set a timer if still offline at the end of timer send the offline event
                timerId = setTimeout(function () {
                    cordova.fireDocumentEvent('offline');
                    timerId = null;
                }, timeout);
            } else {
                // If there is a current offline event pending clear it
                if (timerId !== null) {
                    clearTimeout(timerId);
                    timerId = null;
                }
                cordova.fireDocumentEvent('online');
            }

            // should only fire this once
            if (channel.onCordovaConnectionReady.state !== 2) {
                channel.onCordovaConnectionReady.fire();
            }
        },
        function (e) {
            // If we can't get the network info we should still tell Cordova
            // to fire the deviceready event.
            if (channel.onCordovaConnectionReady.state !== 2) {
                channel.onCordovaConnectionReady.fire();
            }
            console.log('getLevel ' + e);
        });
});

module.exports = me;