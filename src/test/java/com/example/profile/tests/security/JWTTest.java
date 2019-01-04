package com.example.profile.tests.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.zun.util.test.AbstractTest;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.*;
import static com.mscharhag.oleaster.matcher.Matchers.*;

import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.List;

public class JWTTest extends AbstractTest {

  protected  Jws<Claims> jwt;

  @Override
  public void define() {
    SecretKey key = Keys.hmacShaKeyFor("BwtDdVmIisv6sCtG0xiZvdqeNQl8WBF7KhU7lGUHFkE2QLX1ZMaHVawulsn9OL8gxaLKZvDJk7Fb3pcwU6JRvWUwxD1xo6xW02iBymtHpPHmdGxaTSgUnpi3ipMsSFqv".getBytes());
    JwtParser parser = Jwts.parser().setSigningKey(key);
    describe("Example JWT token with 3 claims", () -> {
     String token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJleGFtcGxlLmNvbSIsImlhdCI6MTU0NTE5MzYxNywiZXhwIjoxNTc2NzI5NjE3LCJhdWQiOiJQcm9maWxlIFNlcnZpY2UiLCJzdWIiOiJDcmVkaXQgUmVwb3J0IFNlcnZpY2UiLCJQZXJtIjpbInByb2ZpbGU6cnciLCJwcm9maWxlLiouKjpydyIsInByb2ZpbGUuKjpoaXN0b3J5Il19.IwXq9r8uZUL3kq0lQL3BG3tvyRXybMd7amb_xcP9GTQ";
      it("can be decoded", () -> {
        jwt = parser.parseClaimsJws(token);
      });
      it("has the right audience", () -> {
        expect(jwt.getBody().getAudience()).toEqual("Profile Service");
      });
      it("has the right subject", () -> {
        expect(jwt.getBody().getSubject()).toEqual("Credit Report Service");
      });
      it("has 3 'Perm' claims", () -> {
        List<String> claims = (List<String>)jwt.getBody().get("Perm");
        expect(claims.size()).toEqual(3);
      });
      it("expires after it's creation", ()-> {
        expect(jwt.getBody().getExpiration().after(jwt.getBody().getIssuedAt())).toBeTrue();
      });
    });
    describe("Example JWT token with 1 claim", () -> {
      String token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJleGFtcGxlLmNvbSIsImlhdCI6MTU0NTE5MzYxNywiZXhwIjoxNTc2NzI5NjE3LCJhdWQiOiJQcm9maWxlIFNlcnZpY2UiLCJzdWIiOiJDcmVkaXQgUmVwb3J0IFNlcnZpY2UiLCJQZXJtIjoicHJvZmlsZTpydyJ9.sIxsyGvdDnNq_C8rg1AN8j33GO0Qit-JyPeROKZUBIg";
      it("can be decoded", () -> {
        jwt = parser.parseClaimsJws(token);
      });
      it("has 1 'Perm' claim", () -> {
        String claim = (String)jwt.getBody().get("Perm");
        expect(claim).toEqual("profile:rw");
      });
    });
  }
}
