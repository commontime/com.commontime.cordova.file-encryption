package com.commontime.cordova.plugins.fileencryption;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.webkit.MimeTypeMap;

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

    private static final String ENCRYPTED_SUFFIX = ".encrypted";
    private static final String ENCRYPT_ACTION = "encrypt";
    private static final String ENTITY_ID = "entity_id";
    private static final String DECRYPT_ACTION = "decrypt";
    private static final String VIEW_ENCRYPTED_IMAGE_ACTION = "viewEncryptedImage";
    private static final String USE_KEYSTORE = "usekeystore";

    private KeyChain keyChain;
    private static Crypto crypto;

    @Override
    protected void pluginInitialize() {
        SoLoader.init(cordova.getActivity(), false);

        boolean useKeyChain = webView.getPreferences().getBoolean(USE_KEYSTORE, false);
        if( useKeyChain && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyChain = new KeystoreBackedKeyChain(cordova.getActivity(), CryptoConfig.KEY_256);
        } else {
            keyChain = new SharedPrefsBackedKeyChain(cordova.getActivity(), CryptoConfig.KEY_256);
        }

        crypto = AndroidConceal.get().createDefaultCrypto(keyChain);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if( action.equals(ENCRYPT_ACTION)) {
            try {
                encrypt( args.getString(0), callbackContext );
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
            return true;
        }
        if( action.equals(DECRYPT_ACTION)) {
            try {
                decrypt( args.getString(0), callbackContext );
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
            return true;
        }
        if( action.equals(VIEW_ENCRYPTED_IMAGE_ACTION)) {
            try {
                viewEncryptedImage( args.getString(0), callbackContext );
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
            return true;
        }

        return false;
    }

    private void decrypt(final String uri, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!crypto.isAvailable()) {
                        callbackContext.error("Crypto library not available");
                        return;
                    }

                    final Uri parsed = Uri.parse(uri);
                    final File fileToDecrypt = webView.getResourceApi().mapUriToFile(parsed);
                    final String name = parsed.getLastPathSegment();
                    final String decryptedName = name.substring(0, name.length() - ENCRYPTED_SUFFIX.length() );

                    FileInputStream fileStream = new FileInputStream(fileToDecrypt);

                    InputStream fis = null;
                    fis = crypto.getCipherInputStream( fileStream, Entity.create(ENTITY_ID));

                    final File decryptedFile = new File(fileToDecrypt.getParent(), decryptedName);
                    OutputStream fos = new BufferedOutputStream(new FileOutputStream(decryptedFile));

                    IOUtils.copy(fis, fos);

                    fileStream.close();
                    fis.close();
                    fos.close();

                    fileToDecrypt.delete();

                    callbackContext.success(decryptedFile.toURI().toString());
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
                return;
            }
        });
    }

    private void encrypt(final String uri, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!crypto.isAvailable()) {
                        callbackContext.error("Crypto library not available");
                        return;
                    }

                    final File fileToEncrypt = webView.getResourceApi().mapUriToFile(Uri.parse(uri));
                    final File encryptedFile = new File( fileToEncrypt.getParentFile(), fileToEncrypt.getName() + ENCRYPTED_SUFFIX);

                    FileInputStream fis = new FileInputStream(fileToEncrypt);

                    OutputStream fileStream = new BufferedOutputStream(
                            new FileOutputStream(encryptedFile));

                    OutputStream fos = crypto.getCipherOutputStream(
                            fileStream,
                            Entity.create(ENTITY_ID));

                    IOUtils.copy(fis, fos);

                    fis.close();
                    fos.close();
                    fileStream.close();

                    fileToEncrypt.delete();

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

    private void viewEncryptedImage(final String uri, final CallbackContext callbackContext)
    {
        Intent i = new Intent(cordova.getActivity(), EncryptedImageViewerActivity.class);
        i.putExtra("path", uri);
        cordova.getActivity().startActivity(i);
    }

    @Override
    public Uri remapUri(Uri uri) {

        if( uri.toString().toLowerCase().endsWith(ENCRYPTED_SUFFIX) ) {
            return toPluginUri(uri);
        }

        return super.remapUri(uri);
    }

    @Override
    public CordovaResourceApi.OpenForReadResult handleOpenForRead(final Uri uri) throws IOException {
        Uri origUri = fromPluginUri(uri);

        String mimeType = "image/jpeg";
        final String url = origUri.toString().substring(0, origUri.toString().length() - ENCRYPTED_SUFFIX.length() );
        final String ext = MimeTypeMap.getFileExtensionFromUrl(url);
        if(ext != null) {
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        }

        final File fileToDecrypt = webView.getResourceApi().mapUriToFile(origUri);
        final FileInputStream fis = new FileInputStream(fileToDecrypt);

        InputStream inputStream = null;
        try {
            inputStream = crypto.getCipherInputStream( fis, Entity.create(ENTITY_ID));
            return new CordovaResourceApi.OpenForReadResult(origUri, inputStream, mimeType, 0, null );
        } catch (CryptoInitializationException e) {
            e.printStackTrace();
            throw new IOException(e);
        } catch (KeyChainException e) {
            e.printStackTrace();
            throw new IOException(e);
        }
    }

    public void decrypt(final String path, final DecryptCallback callback) {
        try {
            final Uri uri = Uri.parse(path);
            final File fileToDecrypt = new File(uri.getPath());
            final FileInputStream fis = new FileInputStream(fileToDecrypt);
            InputStream inputStream = crypto.getCipherInputStream( fis, Entity.create(ENTITY_ID));
            callback.onSuccess(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure();
        }
    }

    public interface DecryptCallback {
        public void onSuccess(InputStream is);
        public void onFailure();
    }
}