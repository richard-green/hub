package com.flightstats.hub.dao.aws;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.flightstats.hub.app.HubProperties;
import com.flightstats.hub.dao.ContentDao;
import com.flightstats.hub.metrics.ActiveTraces;
import com.flightstats.hub.metrics.MetricsService;
import com.flightstats.hub.metrics.Traces;
import com.flightstats.hub.model.Content;
import com.flightstats.hub.model.ContentKey;
import com.flightstats.hub.model.DirectionQuery;
import com.flightstats.hub.model.TimeQuery;
import com.flightstats.hub.util.ChunkOutputStream;
import com.flightstats.hub.util.TimeUtil;
import com.google.common.base.Optional;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

@SuppressWarnings("Duplicates")
@Singleton
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class S3LargeContentDao implements ContentDao {

    private final static Logger logger = LoggerFactory.getLogger(S3LargeContentDao.class);

    private final boolean useEncrypted = HubProperties.isAppEncrypted();

    public static final String CONTENT_TYPE = "application/hub";
    @Inject
    private MetricsService metricsService;
    @Inject
    private AmazonS3 s3Client;
    @Inject
    //todo - gfm - should this use a new bucket?
    private S3BucketName s3BucketName;

    public void initialize() {
        S3Util.initialize(s3BucketName.getS3BucketName(), s3Client);
    }

    @Override
    public Optional<ContentKey> getLatest(String channel, ContentKey limitKey, Traces traces) {
        throw new UnsupportedOperationException("use query interface");
    }

    public ContentKey insert(String channelName, Content content) {
        content.keyAndStart(TimeUtil.now());
        ContentKey key = content.getContentKey().get();
        ActiveTraces.getLocal().add("S3LargeContentDao.write ", key);
        long start = System.currentTimeMillis();
        int length = 0;
        List<PartETag> partETags = new ArrayList<>();
        try {
            String s3Key = getS3ContentKey(channelName, key);
            String name = s3BucketName.getS3BucketName();
            ObjectMetadata metadata = new ObjectMetadata();
            if (content.getContentType().isPresent()) {
                metadata.setContentType(content.getContentType().get());
                metadata.addUserMetadata("type", content.getContentType().get());
            } else {
                metadata.addUserMetadata("type", "none");
            }
            if (useEncrypted) {
                metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
            }
            InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(name, s3Key, metadata);
            InitiateMultipartUploadResult initResponse = s3Client.initiateMultipartUpload(initRequest);
            ChunkOutputStream outputStream = new ChunkOutputStream(chunk -> {
                try {
                    byte[] bytes = chunk.getBytes();
                    logger.info("got bytes {} {}", s3Key, bytes.length);
                    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                    UploadPartRequest uploadRequest = new UploadPartRequest()
                            .withBucketName(name)
                            .withKey(s3Key)
                            .withUploadId(initResponse.getUploadId())
                            .withPartNumber(chunk.getCount())
                            .withInputStream(bais)
                            .withPartSize(bytes.length);
                    UploadPartResult uploadPart = s3Client.uploadPart(uploadRequest);
                    partETags.add(uploadPart.getPartETag());
                    logger.info("wrote bytes {} {} {}", s3Key, bytes.length, chunk.getCount());
                    //todo - gfm - not sure we need to return this
                    return "ok";
                } catch (Exception e) {
                    logger.warn("what happened POST to " + channelName + " for chunk " + chunk.getCount(), e);
                    throw e;
                }
            });

            InputStream stream = content.getStream();
            long copied = IOUtils.copyLarge(stream, outputStream);
            ActiveTraces.getLocal().add("S3LargeContentDao.write processed", copied);
            outputStream.close();
            CompleteMultipartUploadRequest compRequest =
                    new CompleteMultipartUploadRequest(name, s3Key, initResponse.getUploadId(), partETags);
            s3Client.completeMultipartUpload(compRequest);
            content.setSize(copied);
            return key;
        } catch (Exception e) {
            logger.warn("unable to write large item to S3 " + channelName + " " + key, e);
            //todo - gfm - delete the uncompleted item in S3.
            throw new RuntimeException(e);
        } finally {
            metricsService.time(channelName, "s3.put", start, length, "type:large");
            ActiveTraces.getLocal().add("S3LargeContentDao.write completed");
        }
    }

    @Override
    public void delete(String channelName, ContentKey key) {
        //todo - gfm - figure this out
    }

    //todo - gfm - refactor dupes
    public Content get(final String channelName, final ContentKey key) {
        ActiveTraces.getLocal().add("S3SingleContentDao.read", key);
        try {
            return getS3Object(channelName, key);
        } catch (SocketTimeoutException e) {
            logger.warn("SocketTimeoutException : unable to read " + channelName + " " + key);
            try {
                return getS3Object(channelName, key);
            } catch (Exception e2) {
                logger.warn("unable to read second time " + channelName + " " + key + " " + e.getMessage(), e2);
                return null;
            }
        } catch (Exception e) {
            logger.warn("unable to read " + channelName + " " + key, e);
            return null;
        } finally {
            ActiveTraces.getLocal().add("S3SingleContentDao.read completed");
        }
    }

    private Content getS3Object(String channelName, ContentKey key) throws IOException {
        long start = System.currentTimeMillis();
        try (S3Object object = s3Client.getObject(s3BucketName.getS3BucketName(), getS3ContentKey(channelName, key))) {
            byte[] bytes = ByteStreams.toByteArray(object.getObjectContent());
            ObjectMetadata metadata = object.getObjectMetadata();
            Map<String, String> userData = metadata.getUserMetadata();
            /*if (userData.containsKey("compressed")) {
                //todo - gfm -
            }*/
            Content.Builder builder = Content.builder();
            String type = userData.get("type");
            if (!type.equals("none")) {
                builder.withContentType(type);
            }
            builder.withContentKey(key);
            builder.withData(bytes);
            return builder.build();
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() != 404) {
                logger.warn("AmazonS3Exception : unable to read " + channelName + " " + key, e);
            }
            return null;
        } finally {
            metricsService.time(channelName, "s3.get", start, "type:single");
        }
    }

    @Override
    public SortedSet<ContentKey> queryByTime(TimeQuery query) {
        throw new UnsupportedOperationException("the large dao only deals with large objects, queries are tracked using the small dao");
    }

    @Override
    public SortedSet<ContentKey> query(DirectionQuery query) {
        throw new UnsupportedOperationException("the large dao only deals with large objects, queries are tracked using the small dao");
    }

    //todo - gfm - change this
    private String getS3ContentKey(String channelName, ContentKey key) {
        return channelName + "/large/" + key.toUrl();
    }

    @Override
    public void deleteBefore(String channel, ContentKey limitKey) {
        //todo - gfm - what should this look like?
        /*try {
            S3Util.delete(channel + "/", limitKey, s3BucketName.getS3BucketName(), s3Client);
            logger.info("completed deletion of " + channel);
        } catch (Exception e) {
            logger.warn("unable to delete " + channel + " in " + s3BucketName.getS3BucketName(), e);
        }*/
    }

    @Override
    public ContentKey insertHistorical(String channelName, Content content) throws Exception {
        //todo gfm - stream directly into S3 using the new multipart api??
        return insert(channelName, content);
    }

    public void delete(String channel) {
        //todo - gfm -
    }

}