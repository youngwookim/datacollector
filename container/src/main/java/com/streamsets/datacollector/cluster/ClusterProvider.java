/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.datacollector.cluster;

import com.streamsets.datacollector.config.PipelineConfiguration;
import com.streamsets.datacollector.stagelibrary.StageLibraryTask;
import com.streamsets.datacollector.util.SystemProcessFactory;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public interface ClusterProvider {

  void killPipeline(SystemProcessFactory systemProcessFactory, File sparkManager, File tempDir,
                                   String appId, PipelineConfiguration pipelineConfiguration) throws TimeoutException,
                                   IOException;

  ClusterPipelineStatus getStatus(SystemProcessFactory systemProcessFactory, File sparkManager, File tempDir,
                                   String appId, PipelineConfiguration pipelineConfiguration) throws TimeoutException, IOException;


  ApplicationState startPipeline(SystemProcessFactory systemProcessFactory, File sparkManager, File tempDir,
                                      Map<String, String> environment, Map<String, String> sourceInfo,
                                      PipelineConfiguration pipelineConfiguration, StageLibraryTask stageLibrary,
                                      File etcDir, File resourcesDir, File staticWebDir, File bootstrapDir,
                                      URLClassLoader apiCL, URLClassLoader containerCL, long timeToWaitForFailure)
    throws TimeoutException, IOException;
}