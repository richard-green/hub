package com.flightstats.hub.model;

import com.flightstats.hub.app.HubProperties;
import com.flightstats.hub.util.TimeUtil;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.flightstats.hub.model.BuiltInTag.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Value
@Builder(toBuilder = true)
public class ChannelConfig implements Serializable, NamedType {

    public static final String SINGLE = "SINGLE";
    public static final String BATCH = "BATCH";
    public static final String BOTH = "BOTH";

    private static final long serialVersionUID = 1L;

    private final String name;
    private final String owner;
    private final Date creationDate;
    private final long ttlDays;
    private final long maxItems;
    private final String description;
    private final Set<String> tags;
    private final String replicationSource;
    private final String storage;
    private final String strategy;
    private final GlobalConfig global;
    private final boolean protect;
    private final DateTime mutableTime;

    private ChannelConfig(String name, String owner, Date creationDate, long ttlDays, long maxItems, String description,
                          Set<String> tags, String replicationSource, String storage, String strategy, GlobalConfig global,
                          boolean protect, DateTime mutableTime) {
        this.name = StringUtils.trim(name);
        this.owner = StringUtils.trim(owner);
        this.creationDate = creationDate;
        this.description = description;
        this.tags = tags;
        this.replicationSource = replicationSource;
        this.mutableTime = mutableTime;

        if (maxItems == 0 && ttlDays == 0 && mutableTime == null) {
            this.ttlDays = 120;
            this.maxItems = 0;
        } else {
            this.ttlDays = ttlDays;
            this.maxItems = maxItems;
        }

        if (isNotBlank(strategy)) {
            this.strategy = StringUtils.upperCase(strategy);
        } else if (isNotBlank(storage)) {
            this.strategy = StringUtils.upperCase(storage);
        } else {
            this.strategy = SINGLE;
        }
        this.storage = this.strategy;

        if (global != null) {
            this.global = global.cleanup();
        } else {
            this.global = null;
        }

        addTagIf(!isBlank(replicationSource), REPLICATED);
        addTagIf(isGlobal(), GLOBAL);
        addTagIf(isHistorical(), HISTORICAL);

        if (HubProperties.isProtected()) {
            this.protect = true;
        } else {
            this.protect = protect;
        }
    }

    private void addTagIf(boolean shouldBeTagged, BuiltInTag tag) {
        if (shouldBeTagged) {
            tags.add(tag.toString());
        } else {
            tags.remove(tag.toString());
        }
    }

    //force access through strategy
    private String getStorage() {
        return storage;
    }

    public String toJson() {
        return ChannelConfigFactory.toJson(this);
    }

    public DateTime getTtlTime() {
        return TimeUtil.getEarliestTime(ttlDays);
    }

    public boolean isGlobal() {
        return global != null;
    }

    public boolean isGlobalMaster() {
        return isGlobal() && global.isMaster();
    }

    public boolean isGlobalSatellite() {
        return isGlobal() && !global.isMaster();
    }

    public boolean isReplicating() {
        return StringUtils.isNotBlank(replicationSource) || isGlobalSatellite();
    }

    public boolean isLive() {
        return !isReplicating();
    }

    public boolean isValidStorage() {
        return storage.equals(SINGLE) || storage.equals(BATCH) || storage.equals(BOTH);
    }

    public boolean isSingle() {
        return storage.equals(SINGLE);
    }

    public boolean isBatch() {
        return storage.equals(BATCH);
    }

    public boolean isBoth() {
        return storage.equals(BOTH);
    }

    public boolean isHistorical() {
        return mutableTime != null;
    }

    @SuppressWarnings("unused")
    public static class ChannelConfigBuilder {
        private String owner = "";
        private Date creationDate = new Date();
        private String description = "";
        private TreeSet<String> tags = new TreeSet<>();
        private String replicationSource = "";
        private String storage = "";
        private String strategy = "";
        private boolean protect = HubProperties.isProtected();

        public ChannelConfigBuilder tags(List<String> tagList) {
            this.tags.clear();
            this.tags.addAll(tagList.stream().map(Function.identity()).collect(Collectors.toSet()));
            return this;
        }

        public ChannelConfigBuilder tags(Set<String> tagSet) {
            this.tags.clear();
            this.tags.addAll(tagSet);
            return this;
        }
    }
}
