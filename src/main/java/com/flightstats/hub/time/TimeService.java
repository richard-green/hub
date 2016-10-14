package com.flightstats.hub.time;

import com.flightstats.hub.app.HubHost;
import com.flightstats.hub.app.HubServices;
import com.flightstats.hub.cluster.CuratorCluster;
import com.flightstats.hub.cluster.CuratorLeader;
import com.flightstats.hub.cluster.Leader;
import com.flightstats.hub.cluster.Leadership;
import com.flightstats.hub.rest.RestClient;
import com.flightstats.hub.spoke.RemoteSpokeStore;
import com.flightstats.hub.util.Sleeper;
import com.flightstats.hub.util.TimeUtil;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;

@Singleton
public class TimeService {

    private final static Logger logger = LoggerFactory.getLogger(TimeService.class);

    private final static Client client = RestClient.createClient(1, 5, true, false);
    private final TimeServiceRegister service;

    @Inject
    @Named("HubCuratorCluster")
    private CuratorCluster cluster;

    public TimeService() {
        service = new TimeServiceRegister();
        HubServices.register(service);
    }

    public DateTime getNow() {
        if (service.leadership.hasLeadership()) {
            return TimeUtil.now();
        }
        DateTime millis = getRemoteNow();
        if (millis != null) {
            return millis;
        }
        logger.warn("unable to get external time, using local!");
        return TimeUtil.now();
    }

    DateTime getRemoteNow() {
        //todo gfm - this should only call the leader.  how do we know who the leader is?

        for (String server : cluster.getRandomRemoteServers()) {
            ClientResponse response = null;
            try {
                response = client.resource(HubHost.getScheme() + server + "/internal/time/millis")
                        .get(ClientResponse.class);
                if (response.getStatus() == 200) {
                    Long millis = Long.parseLong(response.getEntity(String.class));
                    logger.trace("using remote time {} from {}", millis, server);
                    return new DateTime(millis, DateTimeZone.UTC);
                }
            } catch (ClientHandlerException e) {
                if (e.getCause() != null && e.getCause() instanceof ConnectException) {
                    logger.warn("connection exception " + server);
                } else {
                    logger.warn("unable to get time " + server, e);
                }
            } catch (Exception e) {
                logger.warn("unable to get time " + server, e);
            } finally {
                RemoteSpokeStore.close(response);
            }
        }
        return null;
    }

    private class TimeServiceRegister extends AbstractIdleService implements Leader {

        private static final String leaderPath = "/TimeLeader";
        private Leadership leadership;

        @Override
        protected void startUp() throws Exception {
            CuratorLeader curatorLeader = new CuratorLeader(leaderPath, this);
            curatorLeader.start();
        }

        @Override
        protected void shutDown() throws Exception {
            //do nothing
        }

        @Override
        public void takeLeadership(Leadership leadership) {
            logger.info("taking leadership");
            this.leadership = leadership;
            Sleeper.sleep(Long.MAX_VALUE);
            logger.info("lost leadership");
        }
    }
}
