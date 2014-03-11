package org.whispersystems.textsecure.crypto;

import android.util.Log;

import org.whispersystems.textsecure.util.Util;

import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class AttachmentCipherOutputStream extends OutputStream {

  private final OutputStream out;
  private final Cipher       cipher;
  private final Mac          mac;

  private long ciphertextLength = 0;

  public AttachmentCipherOutputStream(OutputStream out, byte[] combinedKeyMaterial)
      throws IOException
  {
    try {
      this.out    = out;
      this.cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      this.mac    = Mac.getInstance("HmacSHA256");

      byte[][] keyParts = Util.split(combinedKeyMaterial, 32, 32);
      this.cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyParts[0], "AES"));
      this.mac.init(new SecretKeySpec(keyParts[1], "HmacSHA256"));

      out.write(cipher.getIV());
      mac.update(cipher.getIV());

      ciphertextLength += cipher.getIV().length;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (java.security.InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public void write(byte[] data) throws IOException {
    write(data, 0, data.length);
  }

  @Override
  public void write(byte[] data, int offset, int length) throws IOException {
    byte[] cipherSegment = cipher.update(data, offset, length);

    if (cipherSegment != null) {
      mac.update(cipherSegment);
      out.write(cipherSegment);
      ciphertextLength += cipherSegment.length;
    }
  }

  @Override
  public void write(int oneByte) throws IOException {
    throw new AssertionError();
  }

  @Override
  public void flush() throws IOException {
    try {
      byte[] finalBlock = cipher.doFinal();

      mac.update(finalBlock);
      byte[] hmac = mac.doFinal();

      out.write(finalBlock);
      out.write(hmac);

      ciphertextLength += finalBlock.length;
      ciphertextLength += hmac.length;

      Log.w("AttachmentCipherOutputStream", "Wrote ciphertext length: " + ciphertextLength);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  public static long getCiphertextLength(long plaintextLength) {
    return 16 + (((plaintextLength / 16) +1) * 16) + 32;
  }
}
