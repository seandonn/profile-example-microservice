package com.example.profile.tests.service;

import com.example.profile.service.EncryptionService;
import com.sun.mail.iap.ByteArray;
import org.zun.util.test.AbstractTest;

import javax.inject.Inject;

import java.io.*;
import java.nio.charset.Charset;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.*;
import static com.mscharhag.oleaster.matcher.Matchers.*;

public class EncryptionServiceTest extends AbstractTest {

  @Inject
  protected EncryptionService service;

  protected byte[] salt, iv, data;

  @Override
  public void define() {
    describe("encryption service", () -> {
      it("can generate salt and IV", () -> {
        salt = service.generateSalt();
        expect(salt).toBeNotNull();
        expect(salt.length).toBeGreaterThan(0);
        iv = service.generateIV();
        expect(iv).toBeNotNull();
        expect(iv.length).toBeGreaterThan(0);
      });
      it("can provide an output stream that encrypts", () -> {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
          OutputStream os = service.getEncryptedOut(bos, salt,iv);
          expect(os).toBeNotNull();
          os.write("hello, world!".getBytes(Charset.defaultCharset()));
          os.close();
          data = bos.toByteArray();
          expect(data).toBeNotNull();
          expect(data.length).toBeGreaterThan(0);
        }
      });
      it("can provide an input stream that decrypts previously encrypted data", () -> {
        try (ByteArrayInputStream bin = new ByteArrayInputStream(data);
             InputStream in = service.getDecryptedIn(bin, salt, iv)) {
          expect(in).toBeNotNull();
          BufferedReader reader = new BufferedReader(new InputStreamReader(in));
          String line = reader.readLine();
          expect(line).toEqual("hello, world!");
        }
      });
    });
  }
}
