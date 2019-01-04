package com.example.profile.service;

import org.apache.deltaspike.core.api.config.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.WeakHashMap;

@ApplicationScoped
public class EncryptionService {

  @Inject
  @ConfigProperty(name="encryption.baseKey")
  protected String baseKey;

  @Inject
  @ConfigProperty(name="encryption.iterations")
  protected int iterations;

  @Inject
  @ConfigProperty(name="encryption.keySize")
  protected int keySize;

  protected WeakHashMap<ByteBuffer, SecretKey> saltToKeyCache;
  protected SecretKeyFactory factory;
  protected SecureRandom random;

  protected static final String protocol = "AES/GCM/NoPadding";

  @PostConstruct
  protected void init() throws NoSuchAlgorithmException, NoSuchPaddingException {
    saltToKeyCache = new WeakHashMap<>();
    factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    random = SecureRandom.getInstanceStrong();
    // create a dummy cipher to abort here if not supported
    Cipher.getInstance(protocol);
  }

  protected SecretKey getKey(byte[] salt) {
    ByteBuffer saltAsKey = ByteBuffer.wrap(salt);
    SecretKey k = saltToKeyCache.get(saltAsKey);
    if (k == null) {
      try {
        k = factory.generateSecret(new PBEKeySpec(baseKey.toCharArray(), salt, iterations, keySize));
        k = new SecretKeySpec(k.getEncoded(), "AES");
      } catch (InvalidKeySpecException e) {
        return null;
      }
      saltToKeyCache.put(saltAsKey, k);
    }
    return k;
  }

  // GCM auth tag length in bits
  protected static final int AUTH_TAG_BITLENGTH = 128;
  // salt and IV sizes in bytes
  public static final int SALT_SIZE = 8;
  public static final int IV_SIZE = 12;

  protected Cipher getCipher(int mode, byte[] salt, byte[] iv) {
    try {
      Cipher c = Cipher.getInstance(protocol);
      GCMParameterSpec parameterSpec = new GCMParameterSpec(AUTH_TAG_BITLENGTH, iv);
      c.init(mode, getKey(salt), parameterSpec);
      return c;
    }
    catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
      e.printStackTrace(System.err);
      return null;
    }
  }

  protected byte[] randomBytes(int size) {
    byte[] bytes = new byte[size];
    random.nextBytes(bytes);
    return bytes;
  }

  public byte[] generateSalt() {
    return randomBytes(SALT_SIZE);
  }

  public byte[] generateIV() {
    return randomBytes(IV_SIZE);
  }

  public CipherOutputStream getEncryptedOut(OutputStream os, byte[] salt, byte[] iv) {
    Cipher c = getCipher(Cipher.ENCRYPT_MODE, salt, iv);
    return new CipherOutputStream(os, c);
  }

  public CipherInputStream getDecryptedIn(InputStream is, byte[] salt, byte[] iv) {
    Cipher c = getCipher(Cipher.DECRYPT_MODE, salt, iv);
    return new CipherInputStream(is, c);
  }

}
