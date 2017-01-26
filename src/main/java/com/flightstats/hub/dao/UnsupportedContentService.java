package com.flightstats.hub.dao;

import com.flightstats.hub.model.*;
import com.google.common.base.Optional;

import java.util.Collection;
import java.util.SortedSet;
import java.util.function.Consumer;

public class UnsupportedContentService implements ContentService {
    @Override
    public ContentKey insert(String channelName, Content content) throws Exception {
        return null;
    }

    @Override
    public Collection<ContentKey> insert(BulkContent bulkContent) throws Exception {
        return null;
    }

    @Override
    public boolean historicalInsert(String channelName, Content content) throws Exception {
        return false;
    }

    @Override
    public Optional<Content> get(String channelName, ContentKey key) {
        return null;
    }

    @Override
    public void get(String channel, SortedSet<ContentKey> keys, Consumer<Content> callback) {

    }

    @Override
    public Collection<ContentKey> queryByTime(TimeQuery timeQuery) {
        return null;
    }

    @Override
    public void delete(String channelName) {

    }

    @Override
    public void delete(String channelName, ContentKey contentKey) {

    }

    @Override
    public Collection<ContentKey> queryDirection(DirectionQuery query) {
        return null;
    }

    @Override
    public Optional<ContentKey> getLatest(DirectionQuery query) {
        return null;
    }
}
