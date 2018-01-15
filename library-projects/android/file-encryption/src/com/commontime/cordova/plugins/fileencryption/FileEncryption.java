package com.commontime.cordova.plugins.fileencryption;

import android.net.Uri;

import com.facebook.android.crypto.keychain.AndroidConceal;
import com.facebook.android.crypto.keychain.SharedPrefsBackedKeyChain;
import com.facebook.crypto.Crypto;
import com.facebook.crypto.CryptoConfig;
import com.facebook.crypto.Entity;
import com.facebook.crypto.exception.CryptoInitializationException;
import com.facebook.crypto.exception.KeyChainException;
import com.facebook.crypto.keychain.KeyChain;
import com.facebook.soloader.SoLoader;

import org.apache.commons.io.IOUtils;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileEncryption extends CordovaPlugin {

    private static final String TAG = "FileEncryption";
    private SharedPrefsBackedKeyChain keyChain;
    private Crypto crypto;

    @Override
    protected void pluginInitialize() {
        SoLoader.init(cordova.getActivity(), false);
        keyChain = new SharedPrefsBackedKeyChain(cordova.getActivity(), CryptoConfig.KEY_256);
        crypto = AndroidConceal.get().createDefaultCrypto(keyChain);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if( action.equals("encrypt")) {
            try {
                encrypt( args.getString(0), callbackContext );
                return true;
            } catch (Exception e) {

            }
        }

        return false;
    }

    private void encrypt(final String uri, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!crypto.isAvailable()) {
                        callbackContext.error("Crypto library not available");
                    }

                    final File fileToEncrypt = webView.getResourceApi().mapUriToFile(Uri.parse(uri));
                    final File encryptedFile = new File( fileToEncrypt.getParentFile(), fileToEncrypt.getName() + ".encrypted");

                    FileInputStream fis = new FileInputStream(fileToEncrypt);

                    OutputStream fileStream = new BufferedOutputStream(
                            new FileOutputStream(encryptedFile));

                    OutputStream outputStream = crypto.getCipherOutputStream(
                            fileStream,
                            Entity.create("entity_id"));

                    IOUtils.copy(fis, outputStream);

                    fis.close();
                    outputStream.close();

                    callbackContext.success(encryptedFile.toURI().toString());
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                    callbackContext.error(e.getMessage());
                } catch (CryptoInitializationException e) {
                    e.printStackTrace();
                    callbackContext.error(e.getMessage());
                } catch (KeyChainException e) {
                    e.printStackTrace();
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    @Override
    public Uri remapUri(Uri uri) {

        if( uri.toString().toLowerCase().contains(".encrypted") ) {
            return toPluginUri(uri);
        }

        return super.remapUri(uri);
    }

    @Override
    public CordovaResourceApi.OpenForReadResult handleOpenForRead(Uri uri) throws IOException {
        Uri origUri = fromPluginUri(uri);

        final File fileToDecrypt = webView.getResourceApi().mapUriToFile(origUri);
        FileInputStream fis = new FileInputStream(fileToDecrypt);

        InputStream inputStream = null;
        try {
            inputStream = crypto.getCipherInputStream( fis, Entity.create("entity_id"));
            return new CordovaResourceApi.OpenForReadResult(origUri, inputStream, "image/jpeg", 0, null );
        } catch (CryptoInitializationException e) {
            e.printStackTrace();
            throw new IOException(e);
        } catch (KeyChainException e) {
            e.printStackTrace();
            throw new IOException(e);
        }
    }
}