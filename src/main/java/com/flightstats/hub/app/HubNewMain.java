package com.flightstats.hub.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.glassfish.jersey.server.ResourceConfig;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Main entry point for the hub.  This is the main runnable class.
 */
public class HubNewMain {

    private static final Logger logger = LoggerFactory.getLogger(HubNewMain.class);
    private static final DateTime startTime = new DateTime();
    private static Injector injector;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new UnsupportedOperationException("HubMain requires a property filename, or 'useDefault'");
        }
        HubProperties.loadProperties(args[0]);
        start();
    }

    static void start() throws Exception {
        startZookeeperIfSingle();

        HubJettyServer server = startServer();

        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                logger.info("Jetty Server shutting down...");
                latch.countDown();
            }
        });
        latch.await();
        HubServices.stopAll();
        server.halt();
        logger.info("Server shutdown complete.  Exiting application.");
    }

    public static HubJettyServer startServer() throws IOException {
        URI baseUri = UriBuilder.fromUri("http://localhost/")
                .port(HubProperties.getProperty("http.bind_port", 8080)).build();
        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig.packages(AwsBindings.packages());
        ObjectMapper mapper = GuiceContext.objectMapper();
        resourceConfig.register(new ObjectMapperResolver(mapper));
        resourceConfig.register(JacksonJsonProvider.class);

        List<Module> modules = new ArrayList<>();

        modules.add(new HubBindings());
        String hubType = HubProperties.getProperty("hub.type", "aws");
        logger.info("starting with hub.type {}", hubType);
        switch (hubType) {
            case "aws":
                modules.add(new AwsBindings());
                //todo - gfm - 1/7/16 -
                //jerseyProps.put(PROPERTY_PACKAGES, AwsBindings.packages());
                break;
            case "nas":
            case "test":
                modules.add(new NasBindings());
                //todo - gfm - 1/7/16 -
                //jerseyProps.put(PROPERTY_PACKAGES, NasBindings.packages());
                break;
            default:
                throw new RuntimeException("unsupported hub.type " + hubType);
        }
        injector = Guice.createInjector(modules);
        HubProvider.setInjector(injector);
        HubServices.start(HubServices.TYPE.PRE_START);
        HubJettyServer server = new HubJettyServer();
        server.start(resourceConfig);
        logger.info("Hub server has been started.");
        //todo - gfm - 1/7/16 -
        //HubServices.start(HubServices.TYPE.INITIAL_POST_START);
        logger.info("completed initial post start");
        //todo - gfm - 1/7/16 -
        //HubServices.start(HubServices.TYPE.FINAL_POST_START);
        return server;
    }

    private static void startZookeeperIfSingle() {
        new Thread(() -> {
            String zkConfigFile = HubProperties.getProperty("runSingleZookeeperInternally", "");
            if ("singleNode".equals(zkConfigFile)) {
                warn("using single node zookeeper");
                ZookeeperMain.start();
            }
        }).start();
    }

    static void warn(String message) {
        logger.warn("**********************************************************");
        logger.warn("*** " + message);
        logger.warn("**********************************************************");
    }

    public static Injector getInjector() {
        return injector;
    }

    public static DateTime getStartTime() {
        return startTime;
    }
}
