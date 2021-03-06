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
package com.streamsets.datacollector.restapi;

import com.streamsets.datacollector.main.RuntimeInfo;
import com.streamsets.datacollector.store.PipelineStoreException;
import com.streamsets.datacollector.util.AuthzRole;
import com.streamsets.pipeline.lib.util.ThreadUtil;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;

import javax.annotation.security.DenyAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/v1/system")
@Api(value = "system")
@DenyAll
public class AdminResource {

  private final RuntimeInfo runtimeInfo;

  @Inject
  public AdminResource(RuntimeInfo runtimeInfo) {
    this.runtimeInfo = runtimeInfo;
  }

  @POST
  @Path("/shutdown")
  @ApiOperation(value = "Shutdown SDC", authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(AuthzRole.ADMIN)
  public Response shutdown() throws PipelineStoreException {
    Thread thread = new Thread("Shutdown Request") {
      @Override
      public void run() {
        // sleeping  500ms to allow the HTTP response to go back
        ThreadUtil.sleep(500);
        runtimeInfo.shutdown();
      }
    };
    thread.setDaemon(true);
    thread.start();
    return Response.ok().build();
  }

  @GET
  @Path("/threads")
  @ApiOperation(value = "Returns Thread Dump along with stack trace", response = Map.class, responseContainer = "List",
    authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(AuthzRole.ADMIN)
  public Response getThreadsDump() throws IOException {
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    ThreadInfo[] threads = threadMXBean.dumpAllThreads(true, true);
    List<Map> augmented = new ArrayList<>(threads.length);
    for (ThreadInfo thread : threads) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("threadInfo", thread);
      map.put("userTimeNanosecs", threadMXBean.getThreadUserTime(thread.getThreadId()));
      map.put("cpuTimeNanosecs", threadMXBean.getThreadCpuTime(thread.getThreadId()));
      augmented.add(map);
    }
    return Response.ok(augmented).build();
  }


  @GET
  @Path("/directories")
  @ApiOperation(value = "Returns SDC Directories", response = Map.class, authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(AuthzRole.ADMIN)
  public Response getSDCDirectories() throws IOException {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("runtimeDir", runtimeInfo.getRuntimeDir());
    map.put("configDir", runtimeInfo.getConfigDir());
    map.put("dataDir", runtimeInfo.getDataDir());
    map.put("logDir", runtimeInfo.getLogDir());
    map.put("resourcesDir", runtimeInfo.getResourcesDir());
    return Response.ok(map).build();
  }
}
