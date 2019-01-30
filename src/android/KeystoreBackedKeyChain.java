package com.commontime.cordova.plugins.fileencryption;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.widget.Toast;

import com.facebook.android.crypto.keychain.SecureRandomFix;
import com.facebook.crypto.CryptoConfig;
import com.facebook.crypto.MacConfig;
import com.facebook.crypto.exception.KeyChainException;
import com.facebook.crypto.keychain.KeyChain;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;

/**
 * An implementation of a keychain that is backed by the Android Keystore.
**/
public class KeystoreBackedKeyChain implements KeyChain {

    // Visible for testing.
  /* package */ static final String SHARED_PREF_NAME = "crypto";
    /* package */ static final String CIPHER_KEY_PREF = "cipher_key";
    /* package */ static final String MAC_KEY_PREF = "mac_key";

    private static final String TAG = "KeystoreBackedKeyChain";

    private static final String AndroidKeyStore = "AndroidKeyStore";
    private static final String KEY_ALIAS = "FileEncryptionKey";
    private static final String AES_MODE = "AES/GCM/NoPadding";

    private final CryptoConfig mCryptoConfig;

    private final SharedPreferences mSharedPreferences;
    private final SecureRandom mSecureRandom;
    private KeyStore keyStore = null;

    protected byte[] mCipherKey;
    protected boolean mSetCipherKey;

    protected byte[] mMacKey;
    protected boolean mSetMacKey;

    private final static byte[] FIXED_IV = new byte[12];

    @TargetApi(Build.VERSION_CODES.M)
    public KeystoreBackedKeyChain(Context context, CryptoConfig config) {
        String prefName = prefNameForConfig(config);
        mSecureRandom = SecureRandomFix.createLocalSecureRandom();
        mSharedPreferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        mCryptoConfig = config;

        try {
            keyStore = KeyStore.getInstance(AndroidKeyStore);
            keyStore.load(null);

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore);
                keyGenerator.init(
                        new KeyGenParameterSpec.Builder(KEY_ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setRandomizedEncryptionRequired(false)
                                .setUserAuthenticationRequired(false)
                                .build());
                keyGenerator.generateKey();
            }
            return;
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }

        Toast.makeText(context, "Serious error!  Unable to use KeyStore", Toast.LENGTH_LONG).show();
    }

    /**
     * We should store different configuration keys separately, specially to support the
     * case of migration: one KeyChain has the 128-bit to read old stored data, another KeyChain
     * has the 256-bit value to rewrite all data.
     * <p>
     * So the preference name will depend on the config.
     * For backward compatibility the name for 128-bits is kept as SHARED_PREF_NAME.
     */
    private static String prefNameForConfig(CryptoConfig config) {
        return config == CryptoConfig.KEY_128
                ? SHARED_PREF_NAME
                : SHARED_PREF_NAME + "." + String.valueOf(config);
    }

    @Override
    public synchronized byte[] getCipherKey() throws KeyChainException {
        if (!mSetCipherKey) {
            mCipherKey = maybeGenerateKey(CIPHER_KEY_PREF, mCryptoConfig.keyLength);
        }
        mSetCipherKey = true;
        return mCipherKey;
    }

    @Override
    public byte[] getMacKey() throws KeyChainException {
        if (!mSetMacKey) {
            mMacKey = maybeGenerateKey(MAC_KEY_PREF, MacConfig.DEFAULT.keyLength);
        }
        mSetMacKey = true;
        return mMacKey;
    }

    @Override
    public byte[] getNewIV() throws KeyChainException {
        byte[] iv = new byte[mCryptoConfig.ivLength];
        mSecureRandom.nextBytes(iv);
        return iv;
    }

    @Override
    public synchronized void destroyKeys() {
        mSetCipherKey = false;
        mSetMacKey = false;
        if (mCipherKey != null) {
            Arrays.fill(mCipherKey, (byte) 0);
        }
        if (mMacKey != null) {
            Arrays.fill(mMacKey, (byte) 0);
        }
        mCipherKey = null;
        mMacKey = null;
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.remove(CIPHER_KEY_PREF);
        editor.remove(MAC_KEY_PREF);
        editor.commit();
    }

    /**
     * Generates a key associated with a preference.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private byte[] maybeGenerateKey(String pref, int length) throws KeyChainException {
        String base64Key = mSharedPreferences.getString(pref, null);
        if (base64Key == null) {
            // Generate key if it doesn't exist.
            return generateAndSaveKey(pref, length);
        } else {
            try {
                byte[] encrypted = Base64.decode(base64Key, Base64.DEFAULT);
                Cipher c = Cipher.getInstance(AES_MODE);
                c.init(Cipher.DECRYPT_MODE, keyStore.getKey(KEY_ALIAS, null), new GCMParameterSpec(128, FIXED_IV));
                byte[] decodedBytes = c.doFinal(encrypted);
                return decodedBytes;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            } catch (KeyStoreException e) {
                e.printStackTrace();
            } catch (UnrecoverableKeyException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            }
        }

        return new byte[0];
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private byte[] generateAndSaveKey(String pref, int length) throws KeyChainException {
        byte[] key = new byte[length];
        try {
            mSecureRandom.nextBytes(key);
            // encrypt using keystore key
            Cipher c = Cipher.getInstance(AES_MODE);
            c.init(Cipher.ENCRYPT_MODE, keyStore.getKey(KEY_ALIAS, null), new GCMParameterSpec(128, FIXED_IV));
            byte[] encodedBytes = c.doFinal(key);
            String encryptedBase64Encoded = Base64.encodeToString(encodedBytes, Base64.DEFAULT);

            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putString(
                    pref,
                    encryptedBase64Encoded);
            editor.commit();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
        return key;
    }
}