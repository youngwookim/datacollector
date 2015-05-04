/*
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */

package com.streamsets.pipeline.stage.origin.omniture;

import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.OffsetCommitter;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.base.BaseSource;
import com.streamsets.pipeline.api.impl.Utils;
import com.streamsets.pipeline.config.JsonMode;
import com.streamsets.pipeline.lib.executor.SafeScheduledExecutorService;
import com.streamsets.pipeline.lib.parser.DataParser;
import com.streamsets.pipeline.lib.parser.DataParserException;
import com.streamsets.pipeline.lib.parser.DataParserFactory;
import com.streamsets.pipeline.lib.parser.DataParserFactoryBuilder;
import com.streamsets.pipeline.lib.parser.DataParserFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class OmnitureSource extends BaseSource {
  private static final int SLEEP_TIME_WAITING_FOR_BATCH_SIZE_MS = 100;
  private final static Logger LOG = LoggerFactory.getLogger(OmnitureSource.class);

  private final String resourceUrl;
  private final String reportDescription;
  private final long requestTimeoutMillis;

  /** WSSE Parameters */
  private final String username;
  private final String sharedSecret;

  private final long maxBatchWaitTime;
  private final int batchSize;

  private final HttpClientMode httpMode;
  private final long pollingInterval;

  private SafeScheduledExecutorService safeExecutor;

  private long recordCount;
  private DataParserFactory parserFactory;

  private BlockingQueue<String> entityQueue;
  private OmniturePollingConsumer httpConsumer;

  /**
   * Constructor for authenticated clients. Requires only the resource URL.
   * @param config Configuration for Omniture API requests
   */
  public OmnitureSource(final OmnitureConfig config) {
    this.httpMode = config.getHttpMode();
    this.resourceUrl = config.getResourceUrl();
    this.requestTimeoutMillis = config.getRequestTimeoutMillis();
    this.batchSize = config.getBatchSize();
    this.maxBatchWaitTime = config.getMaxBatchWaitTime();
    this.pollingInterval = config.getPollingInterval();
    this.username = config.getUsername();
    this.sharedSecret = config.getSharedSecret();
    this.reportDescription = config.getReportDescription();
  }

  @Override
  protected void init() throws StageException {
    super.init();

    // TODO: SDC-552 - Omniture origin should be recoverable
    entityQueue = new ArrayBlockingQueue<>(2 * batchSize);

    parserFactory = new DataParserFactoryBuilder(getContext(), DataParserFormat.JSON)
        .setMode(JsonMode.MULTIPLE_OBJECTS).setMaxDataLen(-1).build();

    httpConsumer = new OmniturePollingConsumer(
        resourceUrl,
        reportDescription,
        requestTimeoutMillis,
        username,
        sharedSecret,
        entityQueue
    );

    createPollingConsumer();
  }

  private void createPollingConsumer() {
    safeExecutor = new SafeScheduledExecutorService(1, getClass().getName());
    LOG.info("Scheduling consumer at polling period {}.", pollingInterval);
    safeExecutor.scheduleAtFixedRate(httpConsumer, 0L, pollingInterval, TimeUnit.MILLISECONDS);
  }

  @Override
  public void destroy() {
    super.destroy();
    switch (httpMode) {
      case POLLING:
        httpConsumer.stop();
        safeExecutor.shutdown();
        break;
      default:
        throw new IllegalStateException("Unrecognized httpMode " + httpMode);
    }
  }

  @Override
  public String produce(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
    long start = System.currentTimeMillis();
    int chunksToFetch = Math.min(batchSize, maxBatchSize);
    while ((((System.currentTimeMillis() - start) < maxBatchWaitTime))
        && (entityQueue.size() < chunksToFetch)) {
      try {
        Thread.sleep(SLEEP_TIME_WAITING_FOR_BATCH_SIZE_MS);
      } catch (InterruptedException ex) {
        break;
      }
    }

    List<String> chunks = new ArrayList<>(chunksToFetch);
    entityQueue.drainTo(chunks, chunksToFetch);
    for (String chunk : chunks) {
      String sourceId = getOffset();
      try (DataParser parser = parserFactory.getParser(sourceId, chunk.getBytes())) {
        Record record = parser.parse();
        if (record != null) {
          batchMaker.addRecord(record);
          recordCount++;
        }
      } catch (IOException | DataParserException ex) {
        switch (getContext().getOnErrorRecord()) {
          case DISCARD:
            break;
          case TO_ERROR:
            getContext().reportError(Errors.OMNITURE_00, sourceId, ex.getMessage(), ex);
            break;
          case STOP_PIPELINE:
            if (ex instanceof StageException) {
              throw (StageException) ex;
            } else {
              throw new StageException(Errors.OMNITURE_00, sourceId, ex.getMessage(), ex);
            }
          default:
            throw new IllegalStateException(Utils.format("It should never happen. OnError '{}'",
                getContext().getOnErrorRecord(), ex));
        }

      }
    }
    return getOffset();
  }

  private String getOffset() {
    return Long.toString(recordCount);
  }
}