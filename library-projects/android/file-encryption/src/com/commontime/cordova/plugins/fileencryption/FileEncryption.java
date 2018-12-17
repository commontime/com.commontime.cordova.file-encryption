package com.commontime.cordova.plugins.fileencryption;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
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
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileEncryption extends CordovaPlugin {

    private static final String ENCRYPTED_SUFFIX = ".encrypted";
    private static final String ENCRYPT_ACTION = "encrypt";
    private static final String ENTITY_ID = "entity_id";
    private static final String DECRYPT_ACTION = "decrypt";
    private static final String GET_FILE_SIZE_ACTION = "getFileSize";
    private static final String VIEW_ENCRYPTED_IMAGE_ACTION = "viewEncryptedImage";
    private static final String USE_KEYSTORE = "usekeystore";

    private static final String ENCRYPT_FILE_MESSAGE_ID = "ENCRYPT_FILE";
    private static final String DECRYPT_FILE_MESSAGE_ID = "DECRYPT_FILE";
    private static final String ENCRYPT_DECRYPT_FILE_ORIGINAL_URI_KEY = "originalUri";
    private static final String ENCRYPT_DECRYPT_FILE_URI_KEY = "uri";
    private static final String ENCRYPT_DECRYPT_FILE_CALLBACK_KEY = "cb";
    private static final String ENCRYPT_DECRYPT_REQUEST_ID_KEY = "encryptDecryptRequestId";
    private static final String DECRYPT_TARGET_KEY = "target";

    private KeyChain keyChain;
    private static Crypto crypto;

    @Override
    protected void pluginInitialize() {
        SoLoader.init(cordova.getActivity(), false);

        boolean useKeyChain = webView.getPreferences().getBoolean(USE_KEYSTORE, false);
        if (useKeyChain && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyChain = new KeystoreBackedKeyChain(cordova.getActivity(), CryptoConfig.KEY_256);
        } else {
            keyChain = new SharedPrefsBackedKeyChain(cordova.getActivity(), CryptoConfig.KEY_256);
        }

        crypto = AndroidConceal.get().createDefaultCrypto(keyChain);
    }

    @Override
    public Object onMessage(String id, Object data) {
        if (id.equals(ENCRYPT_FILE_MESSAGE_ID)) {
            try {
                JSONObject dataJson = (JSONObject)data;
                encrypt(dataJson.getString(ENCRYPT_DECRYPT_FILE_URI_KEY), new CustomCallbackContext(dataJson.getString(ENCRYPT_DECRYPT_REQUEST_ID_KEY), dataJson.getString(ENCRYPT_DECRYPT_FILE_CALLBACK_KEY)));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (id.equals(DECRYPT_FILE_MESSAGE_ID)) {
            try {
                JSONObject dataJson = (JSONObject)data;
                decrypt(dataJson.getString(ENCRYPT_DECRYPT_FILE_URI_KEY), dataJson.getString(DECRYPT_TARGET_KEY), new CustomCallbackContext(dataJson.getString(ENCRYPT_DECRYPT_REQUEST_ID_KEY), dataJson.getString(ENCRYPT_DECRYPT_FILE_CALLBACK_KEY)));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return super.onMessage(id, data);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals(ENCRYPT_ACTION)) {
            try {
                encrypt(args.getString(0), callbackContext);
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
            return true;
        }
        if (action.equals(DECRYPT_ACTION)) {
            try {
                decrypt(args.getString(0), null, callbackContext);
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
            return true;
        }
        if (action.equals(VIEW_ENCRYPTED_IMAGE_ACTION)) {
            try {
                viewEncryptedImage(args.getString(0), callbackContext);
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
            return true;
        }
        if (action.equals(GET_FILE_SIZE_ACTION)) {
            try {
                getFileSize(args.getString(0), callbackContext);
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
            return true;
        }


        return false;
    }

    private void decrypt(final String uri, final String target, final CallbackContext callbackContext) {
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

                    final File decryptedFile = new File(target != null ? target : fileToDecrypt.getParent(), decryptedName);
                    OutputStream fos = new BufferedOutputStream(new FileOutputStream(decryptedFile));

                    IOUtils.copy(fis, fos);

                    fileStream.close();
                    fis.close();
                    fos.close();

                    if (target == null) {
                        fileToDecrypt.delete();
                    }

                    if (callbackContext instanceof CustomCallbackContext) {
                        JSONObject data = new JSONObject();
                        data.put(ENCRYPT_DECRYPT_FILE_ORIGINAL_URI_KEY, uri);
                        data.put(ENCRYPT_DECRYPT_FILE_URI_KEY, decryptedFile.toURI().toString());
                        data.put(ENCRYPT_DECRYPT_REQUEST_ID_KEY, ((CustomCallbackContext) callbackContext).getRequestId());
                        webView.getPluginManager().postMessage(((CustomCallbackContext) callbackContext).getCallbackKey(), data);
                    } else {
                        callbackContext.success(decryptedFile.toURI().toString());
                    }
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                    if (callbackContext instanceof CustomCallbackContext) {
                        webView.getPluginManager().postMessage(((CustomCallbackContext) callbackContext).getCallbackKey(), null);
                    } else {
                        callbackContext.error(e.getMessage());
                    }
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

                    if (callbackContext instanceof CustomCallbackContext) {
                        JSONObject data = new JSONObject();
                        data.put(ENCRYPT_DECRYPT_FILE_URI_KEY, encryptedFile.toURI().toString());
                        webView.getPluginManager().postMessage(callbackContext.getCallbackId(), data);
                    } else {
                        callbackContext.success(encryptedFile.toURI().toString());
                    }
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                    if (callbackContext instanceof CustomCallbackContext) {
                        webView.getPluginManager().postMessage(callbackContext.getCallbackId(), null);
                    } else {
                        callbackContext.error(e.getMessage());
                    }
                }
            }
        });
    }

    private void getFileSize(final String uri, final CallbackContext callbackContext) {
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

                    FileInputStream fileStream = new FileInputStream(fileToDecrypt);

                    InputStream fis = null;
                    fis = crypto.getCipherInputStream( fileStream, Entity.create(ENTITY_ID));

                    long size = 0;
                    int chunk = 0;
                    byte[] buffer = new byte[1024];
                    while((chunk = fis.read(buffer)) != -1){
                        size += chunk;
                    }

                    fis.close();

                    callbackContext.success(String.valueOf(size));
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

    public void decrypt(String path, String target, DecryptCallback callback) {
        new DecryptAsyncOperation(path, target, callback).execute();
    }

    public interface DecryptCallback {
        void onSuccess(String path);
        void onFailure();
    }

    private class CustomCallbackContext extends CallbackContext {
        private String requestId;
        private String callbackKey;

        public CustomCallbackContext(String requestId, String callbackKey) {
            super(null, null);
            this.requestId = requestId;
            this.callbackKey = callbackKey;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getCallbackKey() {
            return callbackKey;
        }
    }

    private class DecryptAsyncOperation extends AsyncTask<String, Void, String> {

        private DecryptCallback callback;
        private String path;
        private String target;

        public DecryptAsyncOperation(String path, String target, DecryptCallback callback) {
            this.callback = callback;
            this.path = path;
            this.target = target;
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                final Uri uri = Uri.parse(path);
                final File fileToDecrypt = new File(uri.getPath());
                final String name = uri.getLastPathSegment();
                final String decryptedName = name.substring(0, name.length() - ENCRYPTED_SUFFIX.length());

                FileInputStream fileStream = new FileInputStream(fileToDecrypt);

                InputStream fis = null;
                fis = crypto.getCipherInputStream( fileStream, Entity.create(ENTITY_ID));

                final File decryptedFile = new File(target, decryptedName);
                OutputStream fos = new BufferedOutputStream(new FileOutputStream(decryptedFile));

                IOUtils.copy(fis, fos);

                fileStream.close();
                fis.close();
                fos.close();

                return decryptedFile.getPath();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String path) {
            if (path == null) {
                callback.onFailure();
            } else {
                callback.onSuccess(path);
            }
        }

        @Override
        protected void onPreExecute() {}

        @Override
        protected void onProgressUpdate(Void... values) {}
    }
}