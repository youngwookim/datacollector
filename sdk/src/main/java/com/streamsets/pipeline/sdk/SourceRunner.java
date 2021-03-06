/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.sdk;

import com.streamsets.datacollector.config.StageType;
import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.OffsetCommitter;
import com.streamsets.pipeline.api.OnRecordError;
import com.streamsets.pipeline.api.Source;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.impl.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;

public class SourceRunner extends StageRunner<Source> {
  private static final Logger LOG = LoggerFactory.getLogger(SourceRunner.class);

  public SourceRunner(Class<Source> sourceClass, Source source, Map<String, Object> configuration,
                      List<String> outputLanes, boolean isPreview, OnRecordError onRecordError,
                      Map<String, Object> constants, boolean isClusterMode, String resourcesDir) {
    super(sourceClass, source, StageType.SOURCE, configuration, outputLanes, isPreview, onRecordError, constants,
      isClusterMode, resourcesDir);
  }

  public SourceRunner(Class<Source> sourceClass, Map<String, Object> configuration, List<String> outputLanes,
      boolean isPreview, OnRecordError onRecordError, Map<String, Object> constants, boolean isClusterMode,
      String resourcesDir) {
    super(sourceClass, StageType.SOURCE, configuration, outputLanes, isPreview, onRecordError, constants,
      isClusterMode, resourcesDir);
  }

  public Output runProduce(String lastOffset, int maxBatchSize) throws StageException {
    try {
      LOG.debug("Stage '{}' produce starts", getInfo().getInstanceName());
      ensureStatus(Status.INITIALIZED);
      BatchMakerImpl batchMaker = new BatchMakerImpl(((Source.Context) getContext()).getOutputLanes());
      String newOffset = getStage().produce(lastOffset, maxBatchSize, batchMaker);
      if (getStage() instanceof OffsetCommitter) {
        ((OffsetCommitter)getStage()).commit(newOffset);
      }
      return new Output(newOffset, batchMaker.getOutput());
    } finally {
      LOG.debug("Stage '{}' produce ends", getInfo().getInstanceName());
    }
  }

  public static class Builder extends StageRunner.Builder<Source, SourceRunner, Builder> {

    public Builder(Class<? extends Source> sourceClass,  Source source) {
      super((Class<Source>)sourceClass, source);
    }

    @SuppressWarnings("unchecked")
    public Builder(Class<? extends Source> sourceClass) {
      super((Class<Source>) sourceClass);
    }

    @Override
    public SourceRunner build() {
      Utils.checkState(!outputLanes.isEmpty(), "A Source must have at least one output stream");
      return  (stage != null) ?
        new SourceRunner(stageClass, stage, configs, outputLanes, isPreview, onRecordError, constants, isClusterMode,
                         resourcesDir)
        : new SourceRunner(stageClass, configs, outputLanes, isPreview, onRecordError, constants, isClusterMode,
                           resourcesDir);
    }

  }

  public static BatchMaker createTestBatchMaker(String... outputLanes) {
    return StageRunner.createTestBatchMaker(outputLanes);
  }

  public static Output getOutput(BatchMaker batchMaker) {
    return StageRunner.getOutput(batchMaker);
  }

}
