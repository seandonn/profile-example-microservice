package com.example.profile.tests.security;

import com.example.profile.security.ProfileSecurityContext;
import org.zun.util.test.AbstractTest;

import java.util.Arrays;
import java.util.UUID;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.*;
import static com.mscharhag.oleaster.matcher.Matchers.*;

public class PermissionsTest extends AbstractTest {

  protected ProfileSecurityContext contextFor(String... a) {
    return new ProfileSecurityContext(null, "", Arrays.asList(a));
  }

  @Override
  public void define() {
    describe("wildcard permissions", () -> {
      ProfileSecurityContext sec = contextFor("profile:rw", "profile.*:rwh", "profile.*.*:rw");
      it("permits reading and writing profiles", () -> {
        assert sec.isUserInRole("profile:r");
        assert sec.isUserInRole("profile:w");
      });
      it("permits reading, writing, and history querying to arbitrary ids", () -> {
        UUID uuid = UUID.randomUUID();
        assert sec.isUserInRole("profile."+uuid.toString()+":r");
        assert sec.isUserInRole("profile."+uuid.toString()+":w");
        assert sec.isUserInRole("profile."+uuid.toString()+":h");
      });
      it("permits reading and writing to arbitrary attribute in arbitrary ids", () -> {
        UUID uuid = UUID.randomUUID();
        assert sec.isUserInRole("profile."+uuid.toString()+".credit:r");
        assert sec.isUserInRole("profile."+uuid.toString()+".fooblah:w");
      });
      it("should fail bad/malformed requests", () -> {
        assert !sec.isUserInRole("fooblah");
        assert !sec.isUserInRole("fooblah:rw");
        assert !sec.isUserInRole("foo:blah:hooey");
        assert !sec.isUserInRole("foo.blah.hoo.ey:r");
      });
    });
    describe("permissions specific to profile id", () -> {
      ProfileSecurityContext sec = contextFor("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b:rw",
          "profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.*:w");
      it("permits reading and writing to that profile",() -> {
        assert sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b:r");
        assert sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b:w");
      });
      it("does not permit history to that profile",() -> {
        assert !sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b:h");
      });
      it("permits writing but not reading to attributes in that profile", () -> {
        assert !sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.credit:r");
        assert sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.comms:w");
      });
      it("does not permit access to any other profile", () -> {
        assert !sec.isUserInRole("profile.9daec264-c959-4dc1-9b48-7d65480e0d8b:r");
        assert !sec.isUserInRole("profile.abcd:w");
      });
    });
    describe("permissions specific to profile id and attribute", () -> {
      ProfileSecurityContext sec = contextFor("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.credit:r",
          "profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.contact:w");
      it("permits reading but not writing to credit attribute in that profile", () -> {
        assert sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.credit:r");
        assert !sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.credit:w");
      });
      it("permits writing but not reading to contact attribute in that profile", () -> {
        assert !sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.contact:r");
        assert sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.contact:w");
      });
      it("does not permit access to any other attribute", () -> {
        assert !sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.loan:r");
        assert !sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.login:w");
      });
      it("does not permit access to any other profile", () -> {
        assert !sec.isUserInRole("profile.9daec264-c959-4dc1-9b48-7d65480e0d8b.credit:r");
        assert !sec.isUserInRole("profile.abcd.contact:w");
      });
    });
    describe("wildcard permissions specific to an attribute", () -> {
      ProfileSecurityContext sec = contextFor("profile.*.credit:r", "profile.*.contact:w");
      it("permits reading but not writing to credit attribute in any profile", () -> {
        assert sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.credit:r");
        assert sec.isUserInRole("profile.9daec264-c959-4dc1-9b48-7d65480e0d8b.credit:r");
        assert !sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.credit:w");
        assert !sec.isUserInRole("profile.9daec264-c959-4dc1-9b48-7d65480e0d8b.credit:w");
      });
      it("permits writing but not reading to contact attribute in any profile", () -> {
        assert !sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.contact:r");
        assert sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.contact:w");
        assert !sec.isUserInRole("profile.9daec264-c959-4dc1-9b48-7d65480e0d8b.contact:r");
        assert sec.isUserInRole("profile.9daec264-c959-4dc1-9b48-7d65480e0d8b.contact:w");
      });
      it("does not permit access to any other attribute", () -> {
        assert !sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.loan:r");
        assert !sec.isUserInRole("profile.5daec264-c959-4dc1-9b48-7d65480e0d8b.login:w");
      });
    });
  }
}
