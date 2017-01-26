package com.flightstats.hub.dao;

import com.flightstats.hub.dao.aws.S3LargeContentDao;
import com.flightstats.hub.dao.aws.S3SingleContentDao;
import com.flightstats.hub.model.*;
import com.google.common.base.Optional;
import com.google.inject.Inject;

import java.util.Collection;
import java.util.SortedSet;
import java.util.function.Consumer;

/**
 * This type of channel stores all of it's data in S3.
 * These channels do not offer timing guarantees???
 */
public class LargePayloadContentService implements ContentService {

    @Inject
    private S3SingleContentDao s3SingleContentDao;
    @Inject
    private S3LargeContentDao s3LargeContentDao;


    @Override
    public ContentKey insert(String channelName, Content content) throws Exception {
        ContentKey largeItemKey = s3LargeContentDao.insert(channelName, content);
        //todo - gfm - write index
        return largeItemKey;
    }

    @Override
    public Collection<ContentKey> insert(BulkContent bulkContent) throws Exception {
        throw new UnsupportedOperationException("bulk inserts are not supported by LargePayload channels");
    }

    @Override
    public boolean historicalInsert(String channelName, Content content) throws Exception {
        throw new UnsupportedOperationException("historical inserts are not (yet) supported by LargePayload channels");
    }

    @Override
    public Optional<Content> get(String channelName, ContentKey key) {
        //todo - gfm - this already buffers the stream
        return null;
    }

    @Override
    public void get(String channel, SortedSet<ContentKey> keys, Consumer<Content> callback) {
        //todo - gfm - supported?
    }

    @Override
    public Collection<ContentKey> queryByTime(TimeQuery timeQuery) {
        //todo - gfm -
        return null;
    }

    @Override
    public void delete(String channelName) {
        //todo - gfm -
    }

    @Override
    public void delete(String channelName, ContentKey contentKey) {
        //todo - gfm -
    }

    @Override
    public Collection<ContentKey> queryDirection(DirectionQuery query) {
        //todo - gfm -
        return null;
    }

    @Override
    public Optional<ContentKey> getLatest(DirectionQuery query) {
        //todo - gfm - this should cache the value somewhere
        return null;
    }
}
