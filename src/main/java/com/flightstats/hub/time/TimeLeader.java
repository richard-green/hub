package com.flightstats.hub.time;

import com.flightstats.hub.cluster.CuratorLeader;
import com.flightstats.hub.cluster.Leader;
import com.flightstats.hub.cluster.Leadership;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeLeader implements Leader {
    private final static Logger logger = LoggerFactory.getLogger(TimeLeader.class);

    private CuratorLeader leader;

    public TimeLeader() {
        leader = new CuratorLeader("/TimeService/TimeLeader", this);
        leader.start();
    }

    @Override
    public void takeLeadership(Leadership leadership) {
        try {
            logger.info("has leadership");
            //todo gfm - write value in /TimeService/leaderIp


        } finally {
            logger.info("lost leadership");
            //todo gfm - clear value in /TimeService/leaderIp
            //todo gfm - set value in previousTime
            logger.info("setting values ...");
        }
    }

    //todo gfm - close ???

}
