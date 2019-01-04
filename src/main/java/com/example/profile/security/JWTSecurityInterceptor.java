package com.example.profile.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Priority;
import javax.crypto.SecretKey;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class JWTSecurityInterceptor implements ContainerRequestFilter {
  @Context
  protected UriInfo uriInfo;

  @Inject
  protected Logger log;

  @Inject @ConfigProperty(name="jwt.secretkey")
  protected String keyvalue;

  @Inject @ConfigProperty(name="jwt.audience")
  protected String audience;

  @Inject @ConfigProperty(name="jwt.issuer")
  protected String issuer;

  protected JwtParser parser;

  @PostConstruct
  protected void initialize() {
    parser = Jwts.parser().setSigningKey(Keys.hmacShaKeyFor(keyvalue.getBytes()));
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

    if(authorizationHeader == null || !authorizationHeader.startsWith("Bearer")) {
      requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
      return;
    }

    String token = authorizationHeader.substring("Bearer".length()).trim();
    try {
      Jws<Claims> jwt = parser.parseClaimsJws(token);

      // verify JWT is what we expect
      assert jwt.getBody().getAudience().equals(audience);
      assert jwt.getBody().getIssuer().equals(issuer);
      assert jwt.getBody().getExpiration().after(new Date());
      assert jwt.getBody().getSubject() != null;
      Object permobj = jwt.getBody().get("Perm");
      assert permobj != null;
      List<String> perms;
      if (permobj instanceof String) {
        perms = new ArrayList<>(1);
        perms.add((String)permobj);
      } else if (permobj instanceof List) {
        perms = (List<String>)permobj;
      } else {
        throw new Error("expected Perm claim "+permobj.getClass()+" to be either String or List");
      }

      // set the security context to be our custom context
      requestContext.setSecurityContext(new ProfileSecurityContext(uriInfo, jwt.getBody().getSubject(), perms));
    } catch (Exception | AssertionError e) {
      log.error("unauthorized",e);
      requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
      return;
    }
  }
}
