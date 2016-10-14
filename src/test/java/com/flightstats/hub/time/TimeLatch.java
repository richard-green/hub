package com.flightstats.hub.time;

import com.flightstats.hub.app.HubBindings;
import com.flightstats.hub.cluster.ZooKeeperState;
import com.flightstats.hub.util.Sleeper;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderLatch;

public class TimeLatch {
    public static void main(String[] args) throws Exception {
        CuratorFramework curator = HubBindings.buildCurator("hub", "test", "localhost:2181", new ZooKeeperState());
        LeaderLatch leaderLatch = new LeaderLatch(curator, "/TimeLatch",
                RandomStringUtils.randomAlphabetic(6), LeaderLatch.CloseMode.NOTIFY_LEADER);
        leaderLatch.start();
        leaderLatch.await();
        //todo gfm - do work
        Sleeper.sleep(10 * 60 * 1000);
        //todo gfm - close
        leaderLatch.close();
    }
}
