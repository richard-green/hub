package com.flightstats.hub.channel;

import com.flightstats.hub.dao.ChannelService;
import com.flightstats.hub.exception.ConflictException;
import com.flightstats.hub.exception.ForbiddenRequestException;
import com.flightstats.hub.exception.InvalidRequestException;
import com.flightstats.hub.model.ChannelConfig;
import com.flightstats.hub.model.GlobalConfig;
import com.flightstats.hub.util.TimeUtil;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;

public class ChannelValidator {
    public static final String VALID_NAME = "^[a-zA-Z0-9_-]+$";
    private final ChannelService channelService;

    @Inject
    public ChannelValidator(ChannelService channelService) {
        this.channelService = channelService;
    }

    public void validate(ChannelConfig config, ChannelConfig oldConfig, boolean isLocalHost) throws InvalidRequestException, ConflictException {
        Optional<String> channelNameOptional = Optional.absent();
        if (config != null) {
            channelNameOptional = Optional.fromNullable(config.getName());
        }

        validateNameWasGiven(channelNameOptional);
        String channelName = channelNameOptional.get().trim();
        ensureNotAllBlank(channelName);
        ensureSize(channelName, "name");
        ensureSize(config.getOwner(), "owner");
        checkForInvalidCharacters(channelName);
        if (oldConfig == null) {
            validateChannelUniqueness(channelName);
        }
        validateTTL(config, oldConfig);
        validateDescription(config);
        validateTags(config);
        validateStorage(config);
        validateGlobal(config);
        if (!isLocalHost) {
            preventDataLoss(config, oldConfig);
        }
    }

    private void preventDataLoss(ChannelConfig config, ChannelConfig oldConfig) {
        if (oldConfig == null) {
            return;
        }
        if (oldConfig.isProtect() && !config.isProtect()) {
            throw new ForbiddenRequestException("{\"error\": \"protect can not be switched from true.\"}");
        }
        if (config.isProtect()) {
            if (!config.getStorage().equals(oldConfig.getStorage())) {
                if (!config.getStorage().equals(ChannelConfig.BOTH)) {
                    throw new ForbiddenRequestException("{\"error\": \"A channels storage is not allowed to remove a storage source in this environment\"}");
                }
            }
            if (!config.getTags().containsAll(oldConfig.getTags())) {
                throw new ForbiddenRequestException("{\"error\": \"A channels tags are not allowed to be removed in this environment\"}");
            }

            if (config.getMaxItems() < oldConfig.getMaxItems()) {
                throw new ForbiddenRequestException("{\"error\": \"A channels max items are not allowed to decrease in this environment\"}");
            }
            if (config.getTtlDays() < oldConfig.getTtlDays()) {
                throw new ForbiddenRequestException("{\"error\": \"A channels ttlDays is not allowed to decrease in this environment\"}");
            }
            if (!StringUtils.isEmpty(oldConfig.getReplicationSource())
                    && !config.getReplicationSource().equals(oldConfig.getReplicationSource())) {
                throw new ForbiddenRequestException("{\"error\": \"A channels replication source is not allowed to change in this environment\"}");
            }
            if (config.isGlobal()) {
                if (oldConfig.isGlobal()) {
                    GlobalConfig configGlobal = config.getGlobal();
                    GlobalConfig oldConfigGlobal = oldConfig.getGlobal();
                    if (!StringUtils.equals(configGlobal.getMaster(), oldConfigGlobal.getMaster())) {
                        throw new ForbiddenRequestException("{\"error\": \"A channels global master is not allowed to change in this environment\"}");
                    }
                    if (!configGlobal.getSatellites().containsAll(oldConfigGlobal.getSatellites())) {
                        throw new ForbiddenRequestException("{\"error\": \"A channels global satellites are not allowed to be removed in this environment\"}");
                    }
                }
            } else {
                if (oldConfig.isGlobal()) {
                    throw new ForbiddenRequestException("{\"error\": \"A channels global configuration is not allowed to be removed in this environment\"}");
                }
            }
        }
    }

    private void validateGlobal(ChannelConfig config) {
        if (config.isGlobal()) {
            GlobalConfig global = config.getGlobal();
            String master = global.getMaster();
            if (master == null) {
                throw new InvalidRequestException("{\"error\": \"A Master must exist\"}");
            }
            if (global.getSatellites().isEmpty()) {
                throw new InvalidRequestException("{\"error\": \"At least one Satellite must exist\"}");
            }
            if (global.getSatellites().contains(master)) {
                throw new InvalidRequestException("{\"error\": \"A Master can not also be a Satellite\"}");
            }
            if (!startsWithHttp(master)) {
                throw new InvalidRequestException("{\"error\": \"A Master must start with http or https\"}");
            }
            for (String satellite : global.getSatellites()) {
                if (!startsWithHttp(satellite)) {
                    throw new InvalidRequestException("{\"error\": \"Satellites must start with http or https\"}");
                }
            }
        }
    }

    private boolean startsWithHttp(String master) {
        return master.startsWith("http://") || master.startsWith("https://");
    }

    private void validateStorage(ChannelConfig config) {
        if (!config.isValidStorage()) {
            throw new InvalidRequestException("{\"error\": \"Valid storage values are SINGLE, BATCH and BOTH\"}");
        }
    }

    private void validateTags(ChannelConfig request) {
        if (request.getTags().size() > 20) {
            throw new InvalidRequestException("{\"error\": \"Channels are limited to 20 tags\"}");
        }
        for (String tag : request.getTags()) {
            if (!tag.matches("^[a-zA-Z0-9\\:\\-]+$")) {
                throw new InvalidRequestException("{\"error\": \"Tags must only contain characters a-z, A-Z, and 0-9\"}");
            }
            if (tag.length() > 48) {
                throw new InvalidRequestException("{\"error\": \"Tags must be less than 48 bytes. \"}");
            }
        }
    }

    private void validateDescription(ChannelConfig request) {
        if (request.getDescription().length() > 1024) {
            throw new InvalidRequestException("{\"error\": \"Description must be less than 1024 bytes. \"}");
        }
    }

    private void validateTTL(ChannelConfig request, ChannelConfig oldConfig) throws InvalidRequestException {
        if (request.getTtlDays() == 0 && request.getMaxItems() == 0 && request.getMutableTime() == null) {
            throw new InvalidRequestException("{\"error\": \"ttlDays, maxItems or mutableTime must be set \"}");
        }
        if ((request.getTtlDays() > 0 && request.getMaxItems() > 0)
                || (request.getTtlDays() > 0 && request.getMutableTime() != null)
                || (request.getMaxItems() > 0 && request.getMutableTime() != null)
                ) {
            throw new InvalidRequestException("{\"error\": \"Only one of ttlDays, maxItems and mutableTime can be defined \"}");
        }
        if (request.getMutableTime() != null) {
            if (request.getMutableTime().isAfter(TimeUtil.now())) {
                throw new InvalidRequestException("{\"error\": \"mutableTime must be in the past. \"}");
            }
            if (oldConfig != null && oldConfig.isHistorical()) {
                if (oldConfig.getMutableTime().isBefore(request.getMutableTime())) {
                    throw new InvalidRequestException("{\"error\": \"mutableTime can not move forward. \"}");
                }
            }
        }
        if (request.getMaxItems() > 5000) {
            throw new InvalidRequestException("{\"error\": \"maxItems must be less than 5000 \"}");
        }
    }

    private void validateNameWasGiven(Optional<String> channelName) throws InvalidRequestException {
        if ((channelName == null) || !channelName.isPresent()) {
            throw new InvalidRequestException("{\"error\": \"A channel has no name\"}");
        }
    }

    private void ensureSize(String value, String title) throws InvalidRequestException {
        int maxLength = 48;
        if (value == null) {
            return;
        }
        if (value.length() > maxLength) {
            throw new InvalidRequestException("{\"error\": \"Channel " + title + " is too long " + value + "\"}");
        }
    }

    private void ensureNotAllBlank(String channelName) throws InvalidRequestException {
        if (Strings.nullToEmpty(channelName).trim().isEmpty()) {
            throw new InvalidRequestException("{\"error\": \"Channel name cannot be blank\"}");
        }
    }

    private void checkForInvalidCharacters(String channelName) throws InvalidRequestException {
        if (!channelName.matches(VALID_NAME)) {
            throw new InvalidRequestException("{\"error\": \"Channel name " + channelName + "must only contain characters a-z, A-Z, and 0-9\"}");
        }
    }

    private void validateChannelUniqueness(String channelName) throws ConflictException {
        if (channelService.channelExists(channelName)) {
            throw new ConflictException("{\"error\": \"Channel name " + channelName + " already exists\"}");
        }
    }
}
