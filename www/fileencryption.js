
var exec    = require('cordova/exec'),
    channel = require('cordova/channel'),
    _isIos = false,
    _isAndroid = false;

module.exports = {

    encrypt: function (successCallback, errorCallback, path) {
        cordova.exec(successCallback, errorCallback, 'FileEncryption', 'encrypt', [path]);        
    }

}

// Called before 'deviceready' listener will be called
channel.onCordovaReady.subscribe(function() {
    channel.onCordovaInfoReady.subscribe(function() {
        _isAndroid = device.platform.match(/^android/i) !== null;
        _isIos = device.platform.match(/^iOS/i) !== null;
    });
});
