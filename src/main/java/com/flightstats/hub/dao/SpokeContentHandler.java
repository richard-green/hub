package com.flightstats.hub.dao;

import com.flightstats.hub.app.HubProvider;
import com.flightstats.hub.metrics.ActiveTraces;
import com.flightstats.hub.model.Content;
import com.flightstats.hub.time.TimeService;

import java.io.IOException;

public class SpokeContentHandler {

    private static TimeService timeService = HubProvider.getInstance(TimeService.class);

    public static void handle(Content content) throws IOException {
        content.packageStream();
        ActiveTraces.getLocal().add("SpokeContentHandler marshalled");
        content.keyAndStart(timeService.getNow());
    }
}
