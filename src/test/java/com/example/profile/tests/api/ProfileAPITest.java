package com.example.profile.tests.api;

import com.example.profile.api.ProfileResource;
import com.example.profile.security.JWTSecurityInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.junit.experimental.categories.Category;
import org.zun.util.test.MockJaxRSTest;
import org.zun.util.test.UnitTest;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;

@Category(UnitTest.class)
public class ProfileAPITest extends MockJaxRSTest {

  // JWT Permissions

  protected static final String PERM_FOO_BAR_RW = "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJleGFtcGxlLmNvbSIsImlhdCI6MTU0NjU2MjUwNywiZXhwIjoxNTc4MDk4NTA3LCJhdWQiOiJQcm9maWxlIFNlcnZpY2UiLCJzdWIiOiJ0ZXN0ZXIiLCJQZXJtIjpbInByb2ZpbGU6cnciLCJwcm9maWxlLiouZm9vOnJ3IiwicHJvZmlsZS4qOnJ3IiwicHJvZmlsZS4qLmJhcjpydyJdfQ.RTSajBr9Gyiz6KXDN1w9vh1p6h0cPZX5_TACdNMdYmU";
  protected static final String PERM_FOO_ONLY_R = "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJleGFtcGxlLmNvbSIsImlhdCI6MTU0NjU2MjUwNywiZXhwIjoxNTc4MDk4NTA3LCJhdWQiOiJQcm9maWxlIFNlcnZpY2UiLCJzdWIiOiJ0ZXN0ZXIiLCJQZXJtIjpbInByb2ZpbGU6cnciLCJwcm9maWxlLiouZm9vOnIiLCJwcm9maWxlLio6cnciXX0.VGUFKe4eGy_NYVyNhmGBz8oI83lx8J0OrT2mm_hG-_k";
  protected static final String PERM_NO_PROFILE = "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJleGFtcGxlLmNvbSIsImlhdCI6MTU0NjU2MjUwNywiZXhwIjoxNTc4MDk4NTA3LCJhdWQiOiJQcm9maWxlIFNlcnZpY2UiLCJzdWIiOiJ0ZXN0ZXIifQ.7pMiuOpUdTmhKyBFtw7oXWFav9QcwNIhSr02dKIEomw";


  @Inject
  protected JWTSecurityInterceptor jwtSecurityInterceptor;

  @Inject
  protected ProfileResource profileResource;

  protected String uuid;

  protected final ObjectMapper mapper = new ObjectMapper();
  protected byte[] foobaz;
  protected byte[] foobatPatch;
  protected byte[] foobamPatch;

  @PostConstruct
  public void init() throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      mapper.writeValue(bos, mapper.readTree("{ \"foo\": \"baz\", \"bar\": \"fop\" }"));
      foobaz = bos.toByteArray();
    }
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      JsonNode patch = JsonDiff.asJson(mapper.readTree("{ \"foo\": \"baz\", \"bar\": \"fop\" }"),
          mapper.readTree("{ \"foo\": \"bat\", \"bar\": \"fop\" }"));
      mapper.writeValue(bos, patch);
      foobatPatch = bos.toByteArray();
    }
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      JsonNode patch = JsonDiff.asJson(mapper.readTree("{ \"foo\": \"bat\", \"bar\": \"fop\" }"),
          mapper.readTree("{ \"foo\": \"bam\", \"bar\": \"fop\" }"));
      mapper.writeValue(bos, patch);
      foobamPatch = bos.toByteArray();
    }
  }

  @Override
  public void define() {
    dispatcher.getRegistry().addSingletonResource(profileResource);
    dispatcher.getProviderFactory().getContainerRequestFilterRegistry().registerSingleton(jwtSecurityInterceptor);

    describe("The Profile API", () -> {
      it("should allow inserting new profile JSON via POST", () -> {
        request = MockHttpRequest.post("/profile");
        request.contentType(MediaType.APPLICATION_JSON);
        request.content(foobaz);
        request.header("Authorization", PERM_FOO_BAR_RW);
        dispatch(request);
        expect(response.getStatus()).toEqual(Response.Status.OK.getStatusCode());
        uuid = response.getContentAsString();
        expect(uuid).toBeNotNull();
        expect(uuid.length()).toBeGreaterThan(0);
      });
      it("should not allow inserting new profile JSON via POST without the right permission", () -> {
        request = MockHttpRequest.post("/profile");
        request.contentType(MediaType.APPLICATION_JSON);
        request.content(foobaz);
        request.header("Authorization", PERM_NO_PROFILE);
        dispatch(request);
        expect(response.getStatus()).toEqual(Response.Status.UNAUTHORIZED.getStatusCode());
      });
      it("should allow reading of the same profile JSON via GET", () -> {
        request = MockHttpRequest.get("/profile/"+uuid);
        request.header("Authorization", PERM_FOO_BAR_RW);
        dispatch(request);
        expect(response.getStatus()).toEqual(Response.Status.OK.getStatusCode());
        JsonNode body = mapper.readTree(response.getContentAsString());
        expect(body.get("foo").asText()).toEqual("baz");
        expect(body.get("bar").asText()).toEqual("fop");
      });
      it("should filter out the bar field of the same profile JSON via GET if perm on bar does not exist", () -> {
        request = MockHttpRequest.get("/profile/"+uuid);
        request.header("Authorization", PERM_FOO_ONLY_R);
        dispatch(request);
        expect(response.getStatus()).toEqual(Response.Status.OK.getStatusCode());
        JsonNode body = mapper.readTree(response.getContentAsString());
        expect(body.get("foo").asText()).toEqual("baz");
        expect(body.get("bar")).toBeNull();
      });
      it("should update profile through JSON patch via PATCH", () -> {
        request = MockHttpRequest.create("PATCH","/profile/"+uuid);
        request.contentType(MediaType.APPLICATION_JSON);
        request.header("Authorization", PERM_FOO_BAR_RW);
        request.content(foobatPatch);
        dispatch(request);
        expect(response.getStatus()).toEqual(Response.Status.OK.getStatusCode());
        request = MockHttpRequest.get("/profile/"+uuid);
        request.header("Authorization", PERM_FOO_BAR_RW);
        dispatch(request);
        JsonNode body = mapper.readTree(response.getContentAsString());
        expect(body.get("foo").asText()).toEqual("bat");
        expect(body.get("bar").asText()).toEqual("fop");
      });
      it("should fail to update profile through JSON patch via PATCH if perm does not exist", () -> {
        request = MockHttpRequest.create("PATCH","/profile/"+uuid);
        request.contentType(MediaType.APPLICATION_JSON);
        request.header("Authorization", PERM_FOO_ONLY_R);
        request.content(foobamPatch);
        dispatch(request);
        expect(response.getStatus()).toEqual(Response.Status.UNAUTHORIZED.getStatusCode());
        request = MockHttpRequest.get("/profile/"+uuid);
        request.header("Authorization", PERM_FOO_BAR_RW);
        dispatch(request);
        JsonNode body = mapper.readTree(response.getContentAsString());
        expect(body.get("foo").asText()).toEqual("bat");
        expect(body.get("bar").asText()).toEqual("fop");
      });
    });
  }
}
