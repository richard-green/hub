package com.flightstats.hub.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightstats.hub.app.HubHost;
import com.flightstats.hub.app.HubProvider;
import com.flightstats.hub.cluster.CuratorCluster;
import com.flightstats.hub.model.*;
import com.flightstats.hub.webhook.Webhook;
import com.google.common.base.Optional;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

@Singleton
public class HubUtils {

    private final static Logger logger = LoggerFactory.getLogger(HubUtils.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Client noRedirectsClient;
    private final Client followClient;

    @Inject
    public HubUtils(@Named("NoRedirects") Client noRedirectsClient, Client followClient) {
        this.noRedirectsClient = noRedirectsClient;
        this.followClient = followClient;
    }

    public static void close(ClientResponse response) {
        if (response != null) {
            try {
                response.close();
            } catch (Exception e) {
                logger.warn("unable to close " + e);
            }
        }
    }

    public Optional<String> getLatest(String channelUrl) {
        ClientResponse response = null;
        try {
            response = noRedirectsClient.resource(appendSlash(channelUrl) + "latest")
                    .accept(MediaType.WILDCARD_TYPE)
                    .head();
            if (response.getStatus() != Response.Status.SEE_OTHER.getStatusCode()) {
                logger.info("latest not found for " + channelUrl + " " + response);
                return Optional.absent();
            }
            return Optional.of(response.getLocation().toString());
        } finally {
            HubUtils.close(response);
        }
    }

    private String appendSlash(String channelUrl) {
        if (!channelUrl.endsWith("/")) {
            channelUrl += "/";
        }
        return channelUrl;
    }

    public ClientResponse startWebhook(Webhook webhook) {
        String groupUrl = getSourceUrl(webhook.getChannelUrl()) + "/group/" + webhook.getName();
        String json = webhook.toJson();
        logger.info("starting {} with {}", groupUrl, json);
        ClientResponse response = null;
        try {
            response = followClient.resource(groupUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .type(MediaType.APPLICATION_JSON)
                    .put(ClientResponse.class, json);
            logger.info("start group response {}", response);
            return response;
        } finally {
            HubUtils.close(response);
        }
    }

    public void stopGroupCallback(String groupName, String sourceChannel) {
        String groupUrl = getSourceUrl(sourceChannel) + "/group/" + groupName;
        logger.info("stopping {} ", groupUrl);
        ClientResponse response = null;
        try {
            response = followClient.resource(groupUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .type(MediaType.APPLICATION_JSON)
                    .delete(ClientResponse.class);
        } finally {
            HubUtils.close(response);
        }
        logger.debug("stop group response {}", response);

    }

    private String getSourceUrl(String sourceChannel) {
        return StringUtils.substringBefore(sourceChannel, "/channel/");
    }

    public boolean putChannel(String channelUrl, ChannelConfig channelConfig) {
        logger.debug("putting {} {}", channelUrl, channelConfig);
        ClientResponse response = null;
        try {
            response = followClient.resource(channelUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .type(MediaType.APPLICATION_JSON)
                    .put(ClientResponse.class, channelConfig.toJson());
            logger.info("put channel response {} {}", channelConfig, response);
            return response.getStatus() < 400;
        } finally {
            HubUtils.close(response);
        }
    }

    public ChannelConfig getChannel(String channelUrl) {
        logger.debug("getting {} {}", channelUrl);
        ClientResponse response = null;
        try {
            response = followClient.resource(channelUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .type(MediaType.APPLICATION_JSON)
                    .get(ClientResponse.class);
            logger.debug("get channel response {} {}", response);
            if (response.getStatus() >= 400) {
                return null;
            } else {
                return ChannelConfigFactory.fromJson(response.getEntity(String.class));
            }
        } finally {
            HubUtils.close(response);
        }
    }

    public ContentKey insert(String channelUrl, Content content) {
        WebResource.Builder resource = followClient.resource(channelUrl).getRequestBuilder();
        if (content.getContentType().isPresent()) {
            resource = resource.type(content.getContentType().get());
        }
        ClientResponse response = null;
        try {
            response = resource.post(ClientResponse.class, content.getData());
            logger.trace("got repsonse {}", response);
            if (response.getStatus() == 201) {
                return ContentKey.fromFullUrl(response.getLocation().toString());
            } else {
                return null;
            }
        } finally {
            HubUtils.close(response);
        }
    }

    public Content get(String channelUrl, ContentKey contentKey) {
        ClientResponse response = null;
        try {
            response = followClient.resource(channelUrl + "/" + contentKey.toUrl())
                    .get(ClientResponse.class);
            if (response.getStatus() == 200) {
                Content.Builder builder = Content.builder()
                        .withStream(response.getEntityInputStream())
                        .withContentKey(contentKey);
                MultivaluedMap<String, String> headers = response.getHeaders();
                if (headers.containsKey("Content-Type")) {
                    builder.withContentType(headers.getFirst("Content-Type"));
                }
                Content content = builder.build();
                //load the data into the Content before closing response
                content.getData();
                return content;
            } else {
                logger.info("unable to get {} {} {}", channelUrl, contentKey, response);
                return null;
            }
        } finally {
            HubUtils.close(response);
        }
    }

    public Collection<ContentKey> insert(String channelUrl, BulkContent content) {
        ClientResponse response = null;
        try {
            response = followClient.resource(channelUrl + "/bulk")
                    .type(content.getContentType())
                    .post(ClientResponse.class, ByteStreams.toByteArray(content.getStream()));
            logger.trace("got response {}", response);
            if (response.getStatus() == 201) {
                return parseContentKeys(response);
            }
        } catch (IOException e) {
            logger.warn("unable to insert bulk " + channelUrl, e);
        } finally {
            HubUtils.close(response);
        }
        return Collections.emptyList();
    }

    private Collection<ContentKey> parseContentKeys(ClientResponse response) throws IOException {
        Set<ContentKey> keys = new TreeSet<>();
        String entity = response.getEntity(String.class);
        JsonNode rootNode = mapper.readTree(entity);
        JsonNode uris = rootNode.get("_links").get("uris");
        for (JsonNode uri : uris) {
            keys.add(ContentKey.fromFullUrl(uri.asText()));
        }
        return keys;
    }

    public Collection<ContentKey> query(String channelUrl, Query query) {
        try {
            String queryUrl = channelUrl + query.getUrlPath();
            logger.debug("calling {}", queryUrl);
            ClientResponse response = followClient.resource(queryUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .get(ClientResponse.class);
            logger.trace("got response {}", response);
            if (response.getStatus() == 200) {
                return parseContentKeys(response);
            }
        } catch (IOException e) {
            logger.warn("unable to query" + channelUrl + " " + query, e);
        }
        return Collections.emptyList();
    }

    public boolean delete(String channelUrl) {
        ClientResponse response = null;
        try {
            logger.info("deleting {}", channelUrl);
            response = followClient.resource(channelUrl).delete(ClientResponse.class);
            logger.trace("got response {}", response);
            if (response.getStatus() == 202) {
                return true;
            }
        } catch (Exception e) {
            logger.warn("unable to delete " + channelUrl, e);
        } finally {
            HubUtils.close(response);
        }
        return false;
    }

    public ObjectNode refreshAll() {
        CuratorCluster spokeCuratorCluster = HubProvider.getInstance(CuratorCluster.class, "SpokeCuratorCluster");
        ObjectNode root = mapper.createObjectNode();
        Set<String> servers = spokeCuratorCluster.getServers();
        for (String server : servers) {
            String url = HubHost.getScheme() + server + "/internal/channel/refresh?all=false";
            ClientResponse response = followClient.resource(url).get(ClientResponse.class);
            if (response.getStatus() == 200) {
                root.put(response.getEntity(String.class), "success");
            } else {
                root.put(response.getEntity(String.class), "failure");
            }
        }
        return root;
    }
}
