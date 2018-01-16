# com.commontime.cordova.file-encryption

# Encrypting:

```
plugins.fileEncryption.encrypt( successCallback, failCallback, urlToEncryptedFile );
```

For example:

```
plugins.fileEncryption.encrypt( function(x) {
    console.log(x);
}, function(e) {
    console.error(e);
}, "file:///storage/emulated/0/Android/data/io.cordova.hellocordova/cache/1516093358240.jpg" );
```

Result:

```
"file:///storage/emulated/0/Android/data/io.cordova.hellocordova/cache/1516093358240.jpg.encrypted"
```

# Decrypting

Decrypting is done automatically when a file is requested that ends with ".encrypted"

# Android preferences:

Apply the preference "usekeychain" to make the plugin generate and store the key in the KeyStore.  Otherwise the key is stored unencrypted in Shared Prefs.

