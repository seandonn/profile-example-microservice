package com.example.profile.security;

import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;

public class ProfileSecurityContext implements SecurityContext {
  protected UriInfo uriInfo;
  protected Principal principal;
  protected HashMap<String,String> permissions;

  public ProfileSecurityContext(UriInfo uriInfo, String subject, List<String> claims) {
    this.uriInfo = uriInfo;
    permissions = new HashMap<>(claims.size());
    for (String claim: claims) {
      String[] parts = claim.split(":");
      if (parts.length != 2)
        continue;
      permissions.put(parts[0],parts[1]);
    }
    this.principal = new Principal() {
      @Override
      public String getName() {
        return subject;
      }
    };
  }

  @Override
  public Principal getUserPrincipal() {
    return principal;
  }

  @Override
  public boolean isUserInRole(String role) {
    // always return false if not secure
    if (!isSecure())
      return false;

    // split resource from rights
    String[] parts = role.split(":");
    if (parts.length != 2)
      return false;
    String resource = parts[0], ask = parts[1];
    if (ask.length() != 1)
      return false;

    // do we have a permission that is exactly the resource?
    String perms = permissions.get(resource);
    if (perms != null && perms.contains(ask))
      return true;

    // do we have wildcard permissions?
    // split levels from resource
    String[] levels = resource.split("\\.");
    if (levels.length == 1)  // there is no wildcard at the top level
      return false;
    if (levels.length == 2) {
      perms = permissions.get(levels[0] + ".*");
      return perms == null ? false : perms.contains(ask);
    }
    if (levels.length == 3) {
      // need three cases of wildcards
      perms = permissions.get(levels[0] + ".*." + levels[2]);
      if (perms != null && perms.contains(ask))
        return true;
      perms = permissions.get(levels[0] + "." + levels[1] + ".*");
      if (perms != null && perms.contains(ask))
        return true;
      perms = permissions.get(levels[0] + ".*.*");
      if (perms != null && perms.contains(ask))
        return true;
    }
    return false;
  }

  @Override
  public boolean isSecure() {
    // returning true for demo purposes running as a standalone HTTP server
    return true;
    // TODO update this method to the following once running as HTTPS server
    // return uriInfo.getAbsolutePath().toString().startsWith("https");
  }

  @Override
  public String getAuthenticationScheme() {
    return "JWT-Permission-Scheme";
  }
}
