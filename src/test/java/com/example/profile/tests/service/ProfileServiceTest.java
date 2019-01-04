package com.example.profile.tests.service;

import com.example.profile.service.ProfileService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.zun.util.test.AbstractTest;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.*;
import static com.mscharhag.oleaster.matcher.Matchers.*;

public class ProfileServiceTest extends AbstractTest {

  @Inject
  protected ProfileService service;

  protected String uuid;

  @Inject @ConfigProperty(name="rocksdb.path")
  protected String path;

  @PostConstruct
  protected void clean() {
    // delete the Rocks DB directory
    try {
      Files.walk(Paths.get(path))
          .map(Path::toFile)
          .sorted((o1, o2) -> o2.compareTo(o1))
          .forEach(File::delete);
    } catch (IOException e) {
      e.printStackTrace(System.err);
    }
  }

  @Override
  public void define() {
    ObjectMapper mapper = new ObjectMapper();
    describe("profile service", () -> {
      it("is injected", () -> {
        expect(service).toBeNotNull();
      });
      it("starts out empty", () -> {
        expect(service.count()).toEqual(0);
      });
      it("can accept objects for insertion, assigning a version 1 uuid", () -> {
        uuid = service.insert("tester", mapper.readTree("{ \"foo\": \"bar\" }"));
        expect(uuid).toBeNotNull();
        expect(uuid.length()).toBeGreaterThan(0);
        // version 1 uuids will have a digit '1' after the second hyphen
        // e.g. a4132f6c-047b-11e9-835a-00c0f03d5b7c
        expect(uuid.substring(13, 15)).toEqual("-1");
      });
      it("allows retrieval of stored objects with that uuid", () -> {
        JsonNode val = service.read("tester", uuid);
        expect(val.get("foo").asText()).toEqual("bar");
      });
      it("allows write to overwrite the object", () -> {
        expect(service.write("tester2", uuid, mapper.readTree("{ \"foo\": \"baz\" }"))).toBeTrue();
        JsonNode val = service.read("tester", uuid);
        expect(val.get("foo").asText()).toEqual("baz");
      });
      it("allows patching", () -> {
        JsonNode patch = JsonDiff.asJson(mapper.readTree("{ \"foo\": \"baz\" }"), mapper.readTree("{ \"foo\": \"boo\" }"));
        expect(service.update("tester3", uuid, patch)).toBeTrue();
        JsonNode val = service.read("tester", uuid);
        expect(val.get("foo").asText()).toEqual("boo");
      });
      it("returns values specified by a json path", () -> {
        JsonNode val = service.readPath("tester", uuid, "$.foo");
        expect(val.asText()).toEqual("boo");
      });
      it("restricts updating based test op in patch", () -> {
        JsonNode patch = mapper.readTree("[ { \"op\": \"replace\", \"path\": \"/foo\", \"value\": \"banana\" }, { \"op\": \"test\", \"path\": \"/foo\", \"value\": \"bar\" } ]");
        expect(service.update("tester4", uuid, patch)).toBeFalse();
        JsonNode val = service.readPath("tester", uuid, "$.foo");
        expect(val.asText()).toEqual("boo");
      });
      it("returns null when a uuid is not found when reading or writing", () -> {
        JsonNode val = service.read("tester", "unknown-key");
        expect(val).toBeNull();
        val = service.read("tester", "unknown-key");
        expect(val).toBeNull();
      });
      it("allows deletion via uuid", () -> {
        expect(service.delete("tester", uuid)).toBeTrue();
        expect(service.read("tester", uuid)).toBeNull();
        expect(service.delete("tester", "unknown-key")).toBeFalse();
      });
    });
  }
}
