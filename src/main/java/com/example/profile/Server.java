package com.example.profile;

import com.example.profile.api.ProfileResource;
import com.example.profile.security.JWTSecurityInterceptor;
import org.apache.deltaspike.cdise.api.CdiContainer;
import org.apache.deltaspike.cdise.api.CdiContainerLoader;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.jboss.resteasy.plugins.server.netty.cdi.CdiNettyJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.slf4j.Logger;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.zun.util.CDIFetch;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.naming.NamingException;
import javax.ws.rs.core.Application;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

@ApplicationScoped
public class Server {
  @Inject
  protected Logger log;

  @Inject
  @ConfigProperty(name="server.apiPort")
  protected int apiPort;

  @Inject
  @ConfigProperty(name="server.baseUrl")
  protected String baseUrl;

  public void start() {
    ResteasyDeployment deployment = new ResteasyDeployment();
    deployment.setApplication(new Application() {
      @Override
      public Set<Class<?>> getClasses() {
        Set<Class<?>> resources = new LinkedHashSet<>();
        // add JaxRS resources here
        resources.add(ProfileResource.class);
        resources.add(JWTSecurityInterceptor.class);
        return resources;
      }
    });
    deployment.setInjectorFactoryClass("org.jboss.resteasy.cdi.CdiInjectorFactory");

    CdiNettyJaxrsServer netty = new CdiNettyJaxrsServer();
    netty.setDeployment(deployment);
    netty.setPort(apiPort);
    netty.setRootResourcePath(baseUrl);
    netty.setSecurityDomain(null);
    log.info("Starting Resteasy server at {}", apiPort);
    netty.start();

  }

  public static void main(String[] args) throws IllegalArgumentException, IOException, NamingException {
    CdiContainer cdiContainer = CdiContainerLoader.getCdiContainer();
    cdiContainer.boot();
    cdiContainer.getContextControl().startContext(ApplicationScoped.class);
    cdiContainer.getContextControl().startContexts();

    Runtime.getRuntime().addShutdownHook(new Thread(cdiContainer::shutdown));

    CDIFetch.getBean(Server.class).start();
  }

  static {
    // Remove existing handlers attached to j.u.l root logger
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
    // the initialization phase of your application
    SLF4JBridgeHandler.install();
  }
}
