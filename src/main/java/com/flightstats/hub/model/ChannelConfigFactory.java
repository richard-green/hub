package com.flightstats.hub.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightstats.hub.exception.InvalidRequestException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ChannelConfigFactory {

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Date.class, new HubDateTypeAdapter())
            .registerTypeAdapter(DateTime.class, new HubDateTimeTypeAdapter())
            .create();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String toJson(ChannelConfig channelConfig) {
        return gson.toJson(channelConfig);
    }

    public static ChannelConfig fromJson(String json) {
        if (StringUtils.isEmpty(json)) {
            throw new InvalidRequestException("this method requires at least a json name");
        } else {
            return gson.fromJson(json, ChannelConfig.ChannelConfigBuilder.class).build();
        }
    }

    public static ChannelConfig fromJson(String json, String name) {
        if (StringUtils.isEmpty(json)) {
            return ChannelConfig.builder().name(name).build();
        } else {
            return gson.fromJson(json, ChannelConfig.ChannelConfigBuilder.class).name(name).build();
        }
    }

    public static ChannelConfig fromJson(ChannelConfig config, String json) {
        ChannelConfig.ChannelConfigBuilder builder = config.toBuilder();
        JsonNode rootNode = readJSON(json);

        if (rootNode.has("owner")) builder.owner(getString(rootNode.get("owner")));
        if (rootNode.has("description")) builder.description(getString(rootNode.get("description")));
        if (rootNode.has("ttlDays")) builder.ttlDays(rootNode.get("ttlDays").asLong());
        if (rootNode.has("maxItems")) builder.maxItems(rootNode.get("maxItems").asLong());
        if (rootNode.has("tags")) builder.tags(getSet(rootNode.get("tags")));
        if (rootNode.has("replicationSource")) builder.replicationSource(getString(rootNode.get("replicationSource")));
        if (rootNode.has("strategy")) {
            builder.strategy(getString(rootNode.get("strategy")));
        } else if (rootNode.has("storage")) {
            builder.strategy(getString(rootNode.get("storage")));
        }
        if (rootNode.has("global")) builder.global(GlobalConfig.parseJson(rootNode.get("global")));
        if (rootNode.has("protect")) builder.protect(rootNode.get("protect").asBoolean());
        if (rootNode.has("mutableTime")) {
            builder.mutableTime(HubDateTimeTypeAdapter.deserialize(rootNode.get("mutableTime").asText()));
        }
        return builder.build();
    }

    private static JsonNode readJSON(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new InvalidRequestException("couldn't read json: " + json);
        }
    }

    private static String getString(JsonNode node) {
        String value = node.asText();
        if (value.equals("null")) {
            value = "";
        }
        return value;
    }

    private static Set<String> getSet(JsonNode node) {
        if (!node.isArray()) throw new InvalidRequestException("json node is not an array: " + node.toString());
        return StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toSet());
    }
}
