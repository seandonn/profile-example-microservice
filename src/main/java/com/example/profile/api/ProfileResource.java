package com.example.profile.api;

import com.example.profile.service.ProfileService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.zjsonpatch.InvalidJsonPatchException;
import com.flipkart.zjsonpatch.JsonPatch;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.Iterator;

@Singleton
@Path("/profile")
public class ProfileResource {
  @Inject
  protected Logger log;

  @Inject
  protected ProfileService service;

  @POST
  @Path("/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response create(@Context SecurityContext sec, JsonNode body) {
    if (!sec.isUserInRole("profile:w"))
      return Response.status(Response.Status.UNAUTHORIZED).build();
    // verify permissions for each top-level attribute in body
    for (Iterator<String> it = body.fieldNames(); it.hasNext(); ) {
      String attr = it.next();
      if (!sec.isUserInRole("profile.*."+attr+":w"))
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    String uuid;
    try {
      uuid = service.insert(sec.getUserPrincipal().getName(), body);
    } catch (IOException e) {
      log.error("post",e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
    return Response.ok(uuid).build();
  }

  @GET
  @Path("/{uuid}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response read(@PathParam("uuid") String uuid, @Context SecurityContext sec) {
    if (!sec.isUserInRole("profile."+uuid+":r"))
      return Response.status(Response.Status.UNAUTHORIZED).build();
    JsonNode body = service.read(sec.getUserPrincipal().getName(), uuid);
    if (body == null)
      return Response.status(Response.Status.NOT_FOUND).build();
    if (body instanceof ObjectNode) {
      ObjectNode obj = (ObjectNode)body;
      // check permissions and filter out objects there are no perms for
      for (Iterator<String> it = body.fieldNames(); it.hasNext(); ) {
        String attr = it.next();
        if (!sec.isUserInRole("profile."+uuid+"."+attr+":r")) {
          obj.remove(attr);
        }
      }
      return Response.ok(obj).build();
    }
    return Response.ok(body).build();
  }

  @PATCH
  @Path("/{uuid}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response update(@PathParam("uuid") String uuid, @Context SecurityContext sec, JsonNode patch) {
    try {
      JsonPatch.validate(patch);
    }
    catch (InvalidJsonPatchException e) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    if (!sec.isUserInRole("profile."+uuid+":w"))
      return Response.status(Response.Status.UNAUTHORIZED).build();
    // check the paths of the patch
    for (Iterator<JsonNode> it = patch.iterator(); it.hasNext(); ) {
      JsonNode n = it.next();
      String[] pathParts = n.get("path").asText().split("/");
      String perm = "profile."+uuid;
      if (pathParts.length == 1) {
        // this path is the root so no perm adjustment needed
      } else if (pathParts.length > 1) {
        perm = perm + "."+pathParts[1];
      }
      if (n.get("op").asText().equals("test")) {
        // for the test op validate read perm for the path
        if (!sec.isUserInRole(perm+":r"))
          return Response.status(Response.Status.UNAUTHORIZED).build();
      } else {
        if (!sec.isUserInRole(perm+":w"))
          return Response.status(Response.Status.UNAUTHORIZED).build();
      }
      // check the from attribute if applicable, which will require read permission
      if (n.get("from") == null)
        continue;
      String[] fromParts = n.get("from").asText().split("/");
      perm = "profile."+uuid;
      if (fromParts.length == 1) {
        // this path is the root so no perm adjustment needed
      } else if (fromParts.length > 1) {
        perm = perm + "."+fromParts[1];
      }
      if (!sec.isUserInRole(perm+":r"))
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      if (service.update(sec.getUserPrincipal().getName(), uuid, patch))
        return Response.ok().build();
      else
        return Response.status(Response.Status.NOT_MODIFIED).build();
    } catch (IOException e) {
      log.error("patch",e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

}
