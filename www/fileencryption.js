
module.exports = {
    encrypt: function (successCallback, errorCallback, path) {
        cordova.exec(successCallback, errorCallback, 'FileEncryption', 'encrypt', [path]);        
    },
    decrypt: function (successCallback, errorCallback, path) {
        cordova.exec(successCallback, errorCallback, 'FileEncryption', 'decrypt', [path]);
    },
    viewEncryptedImage: function (successCallback, errorCallback, path) {
        cordova.exec(successCallback, errorCallback, 'FileEncryption', 'decrypt', [path]);
    }
}

