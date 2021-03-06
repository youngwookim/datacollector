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
package com.streamsets.pipeline.stage.origin.logtail;

import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.Source;
import com.streamsets.pipeline.config.DataFormat;
import com.streamsets.pipeline.config.FileRollMode;
import com.streamsets.pipeline.config.LogMode;
import com.streamsets.pipeline.config.PostProcessingOptions;
import com.streamsets.pipeline.lib.parser.log.Constants;
import com.streamsets.pipeline.sdk.SourceRunner;
import com.streamsets.pipeline.sdk.StageRunner;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class TestFileTailSource {
  private final static int SCAN_INTERVAL = 0; //using zero forces synchronous file discovery

  @Test
  public void testInitDirDoesNotExist() throws Exception {
    File testDataDir = new File("target", UUID.randomUUID().toString());

    FileInfo fileInfo = new FileInfo();
    fileInfo.fileFullPath = testDataDir.getAbsolutePath() + "/logFile.txt";
    fileInfo.fileRollMode = FileRollMode.REVERSE_COUNTER;
    fileInfo.firstFile = "";
    fileInfo.patternForToken = "";
    FileTailSource source = new FileTailSource(DataFormat.TEXT, "", "UTF-8", false, 1024, 25, 1, Arrays.asList(fileInfo),
                                               PostProcessingOptions.NONE, null, null,
                                               false, null, null, null, null, null, false, null, SCAN_INTERVAL);
    SourceRunner runner = new SourceRunner.Builder(FileTailDSource.class, source)
        .addOutputLane("lane").addOutputLane("metadata")
        .build();
    runner.runInit();
    runner.runDestroy();
  }

  @Test
  public void testTailLog() throws Exception {
    File testDataDir = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(testDataDir.mkdirs());
    String logFile = new File(testDataDir, "logFile.txt").getAbsolutePath();
    InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("testLogFile.txt");
    OutputStream os = new FileOutputStream(logFile);
    IOUtils.copy(is, os);
    is.close();

    FileInfo fileInfo = new FileInfo();
    fileInfo.fileFullPath = testDataDir.getAbsolutePath() + "/logFile.txt";
    fileInfo.fileRollMode = FileRollMode.REVERSE_COUNTER;
    fileInfo.firstFile = "";
    fileInfo.patternForToken = "";
    FileTailSource source = new FileTailSource(DataFormat.TEXT, "", "UTF-8", false, 1024, 25, 1, Arrays.asList(fileInfo),
                                               PostProcessingOptions.NONE, null, null,
                                               false, null, null, null, null, null, false, null, SCAN_INTERVAL);
    SourceRunner runner = new SourceRunner.Builder(FileTailDSource.class, source)
        .addOutputLane("lane").addOutputLane("metadata")
        .build();
    runner.runInit();
    os.write("HELLO\n".getBytes());
    os.close();
    try {
      long start = System.currentTimeMillis();
      StageRunner.Output output = runner.runProduce(null, 1000);
      long end = System.currentTimeMillis();
      Assert.assertTrue(end - start >= 1000);
      Assert.assertNotNull(output.getNewOffset());
      Assert.assertEquals(3, output.getRecords().get("lane").size());
      Record record = output.getRecords().get("lane").get(0);
      Assert.assertEquals("FIRST", record.get("/text").getValueAsString());
      record = output.getRecords().get("lane").get(1);
      Assert.assertEquals("LAST", record.get("/text").getValueAsString());
      record = output.getRecords().get("lane").get(2);
      Assert.assertEquals("HELLO", record.get("/text").getValueAsString());
    } finally {
      runner.runDestroy();
    }
  }

  private static final Charset UTF8 = Charset.forName("UTF-8");

  @Test
  public void testTailLogSameFilesInSameDir() throws Exception {
    File testDataDir1 = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(testDataDir1.mkdirs());
    Files.write(new File(testDataDir1, "log1.txt").toPath(), Arrays.asList("Hello"), UTF8);
    Files.write(new File(testDataDir1, "log1.txt").toPath(), Arrays.asList("Hola"), UTF8);

    FileInfo fileInfo1 = new FileInfo();
    fileInfo1.fileFullPath = testDataDir1.getAbsolutePath() + "/log1.txt";
    fileInfo1.fileRollMode = FileRollMode.REVERSE_COUNTER;
    fileInfo1.firstFile = "";
    fileInfo1.patternForToken = "";
    FileInfo fileInfo2 = new FileInfo();
    fileInfo2.fileFullPath = testDataDir1.getAbsolutePath() + "/log1.txt";
    fileInfo2.fileRollMode = FileRollMode.REVERSE_COUNTER;
    fileInfo2.firstFile = "";
    fileInfo2.patternForToken = "";
    FileTailSource source = new FileTailSource(DataFormat.TEXT, "", "UTF-8", false, 1024, 25, 1,
                                               Arrays.asList(fileInfo1, fileInfo2), PostProcessingOptions.NONE, null,
                                               null, false, null, null, null, null, null, false, null, SCAN_INTERVAL);
    SourceRunner runner = new SourceRunner.Builder(FileTailDSource.class, source)
        .addOutputLane("lane").addOutputLane("metadata")
        .build();
    Assert.assertTrue(!runner.runValidateConfigs().isEmpty());
    Assert.assertTrue(runner.runValidateConfigs().get(0).toString().contains("TAIL_04"));
  }

    @Test
  public void testTailLogMultipleDirs() throws Exception {
    File testDataDir1 = new File("target", UUID.randomUUID().toString());
    File testDataDir2 = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(testDataDir1.mkdirs());
    Assert.assertTrue(testDataDir2.mkdirs());
    Files.write(new File(testDataDir1, "log1.txt").toPath(), Arrays.asList("Hello"), UTF8);
    Files.write(new File(testDataDir2, "log2.txt").toPath(), Arrays.asList("Hola"), UTF8);

    FileInfo fileInfo1 = new FileInfo();
    fileInfo1.tag = "tag1";
    fileInfo1.fileFullPath = testDataDir1.getAbsolutePath() + "/log1.txt";
    fileInfo1.fileRollMode = FileRollMode.REVERSE_COUNTER;
    fileInfo1.firstFile = "";
    fileInfo1.patternForToken = "";
    FileInfo fileInfo2 = new FileInfo();
    fileInfo2.tag = "";
    fileInfo2.fileFullPath = testDataDir2.getAbsolutePath() + "/log2.txt";
    fileInfo2.fileRollMode = FileRollMode.REVERSE_COUNTER;
    fileInfo2.firstFile = "";
    fileInfo2.patternForToken = "";
    FileTailSource source = new FileTailSource(DataFormat.TEXT, "", "UTF-8", false, 1024, 25, 1,
                                               Arrays.asList(fileInfo1, fileInfo2), PostProcessingOptions.NONE, null,
                                               null, false, null, null, null, null, null, false, null, SCAN_INTERVAL);
    SourceRunner runner = new SourceRunner.Builder(FileTailDSource.class, source)
        .addOutputLane("lane").addOutputLane("metadata")
        .build();
    runner.runInit();
    try {
      Date now = new Date();
      Thread.sleep(10);
      StageRunner.Output output = runner.runProduce(null, 1000);
      Assert.assertEquals(2, output.getRecords().get("lane").size());
      Record record = output.getRecords().get("lane").get(0);
      Assert.assertEquals("Hello", record.get("/text").getValueAsString());
      Assert.assertEquals("tag1", record.getHeader().getAttribute("tag"));
      Assert.assertEquals(fileInfo1.fileFullPath, record.getHeader().getAttribute("file"));
      record = output.getRecords().get("lane").get(1);
      Assert.assertEquals("Hola", record.get("/text").getValueAsString());
      Assert.assertNull(record.getHeader().getAttribute("tag"));
      Assert.assertEquals(fileInfo2.fileFullPath, record.getHeader().getAttribute("file"));

      Assert.assertEquals(2, output.getRecords().get("metadata").size());
      Record metadata = output.getRecords().get("metadata").get(0);
      Assert.assertEquals(new File(fileInfo1.fileFullPath).getAbsolutePath(),
                          metadata.get("/fileName").getValueAsString());
      Assert.assertEquals("START", metadata.get("/event").getValueAsString());
      Assert.assertTrue(now.compareTo(metadata.get("/time").getValueAsDate()) < 0);
      Assert.assertTrue(metadata.has("/inode"));
      metadata = output.getRecords().get("metadata").get(1);
      Assert.assertEquals(new File(fileInfo2.fileFullPath).getAbsolutePath(),
                          metadata.get("/fileName").getValueAsString());
      Assert.assertEquals("START", metadata.get("/event").getValueAsString());
      Assert.assertTrue(now.compareTo(metadata.get("/time").getValueAsDate()) < 0);
      Assert.assertTrue(metadata.has("/inode"));
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testTailLogOffset() throws Exception {
    File testDataDir = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(testDataDir.mkdirs());
    String logFile = new File(testDataDir, "logFile.txt").getAbsolutePath();
    InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("testLogFile.txt");
    OutputStream os = new FileOutputStream(logFile);
    IOUtils.copy(is, os);
    is.close();
    os.close();

    FileInfo fileInfo = new FileInfo();
    fileInfo.fileFullPath = testDataDir.getAbsolutePath() + "/logFile.txt";
    fileInfo.fileRollMode = FileRollMode.REVERSE_COUNTER;
    fileInfo.firstFile = "";
    fileInfo.patternForToken = "";
    FileTailSource source = new FileTailSource(DataFormat.TEXT, "", "UTF-8", false, 7, 1, 1, Arrays.asList(fileInfo),
                                               PostProcessingOptions.NONE, null, null,
                                               false, null, null, null, null, null, false, null, SCAN_INTERVAL);
    SourceRunner runner = new SourceRunner.Builder(FileTailDSource.class, source)
        .addOutputLane("lane").addOutputLane("metadata")
        .build();
    runner.runInit();
    try {
      StageRunner.Output output = runner.runProduce(null, 1000);
      Assert.assertNotNull(output.getNewOffset());
      Assert.assertEquals(1, output.getRecords().get("lane").size());
      Record record = output.getRecords().get("lane").get(0);
      Assert.assertEquals("FIRST", record.get("/text").getValueAsString());

      String offset = output.getNewOffset();
      Assert.assertNotNull(offset);
      output = runner.runProduce(offset, 1000);
      Assert.assertNotNull(output.getNewOffset());
      Assert.assertEquals(1, output.getRecords().get("lane").size());
      record = output.getRecords().get("lane").get(0);
      Assert.assertEquals("LAST", record.get("/text").getValueAsString());
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testTailLogTruncated() throws Exception {
    File testDataDir = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(testDataDir.mkdirs());
    String logFile = new File(testDataDir, "logFile.txt").getAbsolutePath();
    InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("testLogFileTruncated.txt");
    OutputStream os = new FileOutputStream(logFile);
    IOUtils.copy(is, os);
    is.close();
    os.close();

    FileInfo fileInfo = new FileInfo();
    fileInfo.fileFullPath = testDataDir.getAbsolutePath() + "/logFile.txt";
    fileInfo.fileRollMode = FileRollMode.REVERSE_COUNTER;
    fileInfo.firstFile = "";
    fileInfo.patternForToken = "";
    //Set max line length of 2 characters
    FileTailSource source = new FileTailSource(DataFormat.TEXT, "", "UTF-8", false, 3, 1, 1, Arrays.asList(fileInfo),
      PostProcessingOptions.NONE, null, null,
      false, null, null, null, null, null, false, null, SCAN_INTERVAL);
    SourceRunner runner = new SourceRunner.Builder(FileTailDSource.class, source)
      .addOutputLane("lane").addOutputLane("metadata")
      .build();
    runner.runInit();
    try {
      StageRunner.Output output = runner.runProduce(null, 1000);
      Assert.assertNotNull(output.getNewOffset());
      Assert.assertEquals(1, output.getRecords().get("lane").size());
      Record record = output.getRecords().get("lane").get(0);
      //max line length was set to 2 characters. So text has just 3 chars and there is also a boolean field truncated = true
      Assert.assertEquals("FIR", record.get("/text").getValueAsString());
      Assert.assertEquals(true, record.get("/truncated").getValueAsBoolean());

      String offset = output.getNewOffset();
      Assert.assertNotNull(offset);
      output = runner.runProduce(offset, 1000);
      Assert.assertNotNull(output.getNewOffset());
      Assert.assertEquals(1, output.getRecords().get("lane").size());
      record = output.getRecords().get("lane").get(0);
      //max line length was set to 2 characters. So text has just 3 chars and there is also a boolean field truncated = true
      Assert.assertEquals("LAS", record.get("/text").getValueAsString());
      Assert.assertEquals(true, record.get("/truncated").getValueAsBoolean());

      offset = output.getNewOffset();
      Assert.assertNotNull(offset);
      output = runner.runProduce(offset, 1000);
      Assert.assertNotNull(output.getNewOffset());
      Assert.assertEquals(1, output.getRecords().get("lane").size());
      record = output.getRecords().get("lane").get(0);
      Assert.assertEquals("NA", record.get("/text").getValueAsString());
      //No truncated field as there is no truncation
      Assert.assertEquals(false, record.has("/truncated"));

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testTailJson() throws Exception {
    File testDataDir = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(testDataDir.mkdirs());
    File logFile = new File(testDataDir, "logFile.txt");

    FileInfo fileInfo = new FileInfo();
    fileInfo.fileFullPath = testDataDir.getAbsolutePath() + "/logFile.txt";
    fileInfo.fileRollMode = FileRollMode.REVERSE_COUNTER;
    fileInfo.firstFile = "";
    fileInfo.patternForToken = "";
    FileTailSource source = new FileTailSource(DataFormat.JSON, "", "UTF-8", false, 1024, 25, 1, Arrays.asList(fileInfo),
                                               PostProcessingOptions.NONE, null, null,
                                               false, null, null, null, null, null, false, null, SCAN_INTERVAL);
    SourceRunner runner = new SourceRunner.Builder(FileTailDSource.class, source)
        .addOutputLane("lane").addOutputLane("metadata")
        .build();
    runner.runInit();
    Files.write(logFile.toPath(), Arrays.asList("{\"a\": 1}", "[{\"b\": 2}]"), Charset.forName("UTF-8"));
    try {
      long start = System.currentTimeMillis();
      StageRunner.Output output = runner.runProduce(null, 1000);
      long end = System.currentTimeMillis();
      Assert.assertTrue(end - start >= 1000);
      Assert.assertNotNull(output.getNewOffset());
      Assert.assertEquals(2, output.getRecords().get("lane").size());
      Record record = output.getRecords().get("lane").get(0);
      Assert.assertEquals(1, record.get("/a").getValue());
      record = output.getRecords().get("lane").get(1);
      Assert.assertEquals(2, record.get("[0]/b").getValue());
    } finally {
      runner.runDestroy();
    }
  }

  private final static String LINE1 = "2015-03-20 15:53:31,161 DEBUG PipelineConfigurationValidator - " +
    "Pipeline 'test:preview' validation. valid=true, canPreview=true, issuesCount=0";
  private final static String LINE2 = "2015-03-21 15:53:31,161 DEBUG PipelineConfigurationValidator - " +
    "Pipeline 'test:preview' validation. valid=true, canPreview=true, issuesCount=1";
  public static final String DATE_LEVEL_CLASS =
    "2015-03-24 17:49:16,808 ERROR ExceptionToHttpErrorProvider - ";

  public static final String ERROR_MSG_WITH_STACK_TRACE = "REST API call error: LOG_PARSER_01 - Error parsing log line '2015-03-24 12:38:05,206 DEBUG LogConfigurator - Log starting, from configuration: /Users/harikiran/Documents/workspace/streamsets/dev/dist/target/streamsets-datacollector-1.0.0b2-SNAPSHOT/streamsets-datacollector-1.0.0b2-SNAPSHOT/etc/log4j.properties', reason : 'LOG_PARSER_03 - Log line 2015-03-24 12:38:05,206 DEBUG LogConfigurator - Log starting, from configuration: /Users/harikiran/Documents/workspace/streamsets/dev/dist/target/streamsets-datacollector-1.0.0b2-SNAPSHOT/streamsets-datacollector-1.0.0b2-SNAPSHOT/etc/log4j.properties does not confirm to Log4j Log Format'\n" +
    "com.streamsets.pipeline.lib.parser.DataParserException: LOG_PARSER_01 - Error parsing log line '2015-03-24 12:38:05,206 DEBUG LogConfigurator - Log starting, from configuration: /Users/harikiran/Documents/workspace/streamsets/dev/dist/target/streamsets-datacollector-1.0.0b2-SNAPSHOT/streamsets-datacollector-1.0.0b2-SNAPSHOT/etc/log4j.properties', reason : 'LOG_PARSER_03 - Log line 2015-03-24 12:38:05,206 DEBUG LogConfigurator - Log starting, from configuration: /Users/harikiran/Documents/workspace/streamsets/dev/dist/target/streamsets-datacollector-1.0.0b2-SNAPSHOT/streamsets-datacollector-1.0.0b2-SNAPSHOT/etc/log4j.properties does not confirm to Log4j Log Format'\n" +
    "\tat com.streamsets.pipeline.lib.parser.log.LogDataParser.parse(LogDataParser.java:69)\n" +
    "\tat com.streamsets.pipeline.stage.origin.spooldir.SpoolDirSource.produce(SpoolDirSource.java:566)\n" +
    "\tat com.streamsets.pipeline.stage.origin.spooldir.SpoolDirSource.produce(SpoolDirSource.java:535)\n" +
    "\tat com.streamsets.pipeline.configurablestage.DSource.produce(DSource.java:24)\n" +
    "\tat com.streamsets.pipeline.runner.StageRuntime.execute(StageRuntime.java:149)\n" +
    "\tat com.streamsets.pipeline.runner.StagePipe.process(StagePipe.java:106)\n" +
    "\tat com.streamsets.pipeline.runner.preview.PreviewPipelineRunner.run(PreviewPipelineRunner.java:85)\n" +
    "\tat com.streamsets.pipeline.runner.Pipeline.run(Pipeline.java:98)\n" +
    "\tat com.streamsets.pipeline.runner.preview.PreviewPipeline.run(PreviewPipeline.java:38)\n" +
    "\tat com.streamsets.pipeline.restapi.PreviewResource.previewWithOverride(PreviewResource.java:105)\n" +
    "\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
    "\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)\n" +
    "\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n" +
    "\tat java.lang.reflect.Method.invoke(Method.java:606)\n" +
    "\tat org.glassfish.jersey.server.model.internal.ResourceMethodInvocationHandlerFactory$1.invoke(ResourceMethodInvocationHandlerFactory.java:81)\n" +
    "\tat org.glassfish.jersey.server.model.internal.AbstractJavaResourceMethodDispatcher$1.run(AbstractJavaResourceMethodDispatcher.java:151)\n" +
    "\tat org.glassfish.jersey.server.model.internal.AbstractJavaResourceMethodDispatcher.invoke(AbstractJavaResourceMethodDispatcher.java:171)\n" +
    "\tat org.glassfish.jersey.server.model.internal.JavaResourceMethodDispatcherProvider$ResponseOutInvoker.doDispatch(JavaResourceMethodDispatcherProvider.java:152)\n" +
    "\tat org.glassfish.jersey.server.model.internal.AbstractJavaResourceMethodDispatcher.dispatch(AbstractJavaResourceMethodDispatcher.java:104)\n" +
    "\tat org.glassfish.jersey.server.model.ResourceMethodInvoker.invoke(ResourceMethodInvoker.java:384)\n" +
    "\tat org.glassfish.jersey.server.model.ResourceMethodInvoker.apply(ResourceMethodInvoker.java:342)\n" +
    "\tat org.glassfish.jersey.server.model.ResourceMethodInvoker.apply(ResourceMethodInvoker.java:101)\n" +
    "\tat org.glassfish.jersey.server.ServerRuntime$1.run(ServerRuntime.java:271)\n" +
    "\tat org.glassfish.jersey.internal.Errors$1.call(Errors.java:271)\n" +
    "\tat org.glassfish.jersey.internal.Errors$1.call(Errors.java:267)\n" +
    "\tat org.glassfish.jersey.internal.Errors.process(Errors.java:315)\n" +
    "\tat org.glassfish.jersey.internal.Errors.process(Errors.java:297)\n" +
    "\tat org.glassfish.jersey.internal.Errors.process(Errors.java:267)\n" +
    "\tat org.glassfish.jersey.process.internal.RequestScope.runInScope(RequestScope.java:297)\n" +
    "\tat org.glassfish.jersey.server.ServerRuntime.process(ServerRuntime.java:254)\n" +
    "\tat org.glassfish.jersey.server.ApplicationHandler.handle(ApplicationHandler.java:1030)\n" +
    "\tat org.glassfish.jersey.servlet.WebComponent.service(WebComponent.java:373)\n" +
    "\tat org.glassfish.jersey.servlet.ServletContainer.service(ServletContainer.java:381)\n" +
    "\tat org.glassfish.jersey.servlet.ServletContainer.service(ServletContainer.java:344)\n" +
    "\tat org.glassfish.jersey.servlet.ServletContainer.service(ServletContainer.java:221)\n" +
    "\tat org.eclipse.jetty.servlet.ServletHolder.handle(ServletHolder.java:769)\n" +
    "\tat org.eclipse.jetty.servlet.ServletHandler$CachedChain.doFilter(ServletHandler.java:1667)\n" +
    "\tat com.streamsets.pipeline.http.LocaleDetectorFilter.doFilter(LocaleDetectorFilter.java:29)\n" +
    "\tat org.eclipse.jetty.servlet.ServletHandler$CachedChain.doFilter(ServletHandler.java:1650)\n" +
    "\tat org.eclipse.jetty.servlets.UserAgentFilter.doFilter(UserAgentFilter.java:83)\n" +
    "\tat org.eclipse.jetty.servlets.GzipFilter.doFilter(GzipFilter.java:300)\n" +
    "\tat org.eclipse.jetty.servlet.ServletHandler$CachedChain.doFilter(ServletHandler.java:1650)\n" +
    "\tat org.eclipse.jetty.servlet.ServletHandler.doHandle(ServletHandler.java:583)\n" +
    "\tat org.eclipse.jetty.server.handler.ScopedHandler.handle(ScopedHandler.java:143)\n" +
    "\tat org.eclipse.jetty.security.SecurityHandler.handle(SecurityHandler.java:542)\n" +
    "\tat org.eclipse.jetty.server.session.SessionHandler.doHandle(SessionHandler.java:223)\n" +
    "\tat org.eclipse.jetty.server.handler.ContextHandler.doHandle(ContextHandler.java:1125)\n" +
    "\tat org.eclipse.jetty.servlet.ServletHandler.doScope(ServletHandler.java:515)\n" +
    "\tat org.eclipse.jetty.server.session.SessionHandler.doScope(SessionHandler.java:185)\n" +
    "\tat org.eclipse.jetty.server.handler.ContextHandler.doScope(ContextHandler.java:1059)\n" +
    "\tat org.eclipse.jetty.server.handler.ScopedHandler.handle(ScopedHandler.java:141)\n" +
    "\tat org.eclipse.jetty.server.handler.HandlerWrapper.handle(HandlerWrapper.java:97)\n" +
    "\tat org.eclipse.jetty.rewrite.handler.RewriteHandler.handle(RewriteHandler.java:309)\n" +
    "\tat org.eclipse.jetty.server.handler.HandlerCollection.handle(HandlerCollection.java:110)\n" +
    "\tat org.eclipse.jetty.server.handler.HandlerWrapper.handle(HandlerWrapper.java:97)\n" +
    "\tat org.eclipse.jetty.server.Server.handle(Server.java:497)\n" +
    "\tat org.eclipse.jetty.server.HttpChannel.handle(HttpChannel.java:311)\n" +
    "\tat org.eclipse.jetty.server.HttpConnection.onFillable(HttpConnection.java:248)\n" +
    "\tat org.eclipse.jetty.io.AbstractConnection$2.run(AbstractConnection.java:540)\n" +
    "\tat org.eclipse.jetty.util.thread.QueuedThreadPool.runJob(QueuedThreadPool.java:610)\n" +
    "\tat org.eclipse.jetty.util.thread.QueuedThreadPool$3.run(QueuedThreadPool.java:539)\n" +
    "\tat java.lang.Thread.run(Thread.java:745)\n" +
    "Caused by: com.streamsets.pipeline.lib.parser.DataParserException: LOG_PARSER_03 - Log line 2015-03-24 12:38:05,206 DEBUG LogConfigurator - Log starting, from configuration: /Users/harikiran/Documents/workspace/streamsets/dev/dist/target/streamsets-datacollector-1.0.0b2-SNAPSHOT/streamsets-datacollector-1.0.0b2-SNAPSHOT/etc/log4j.properties does not confirm to Log4j Log Format\n" +
    "\tat com.streamsets.pipeline.lib.parser.log.Log4jParser.handleNoMatch(Log4jParser.java:30)\n" +
    "\tat com.streamsets.pipeline.lib.parser.log.GrokParser.parseLogLine(GrokParser.java:51)\n" +
    "\tat com.streamsets.pipeline.lib.parser.log.LogDataParser.parse(LogDataParser.java:67)\n" +
    "\t... 61 more";

  public static final String LOG_LINE_WITH_STACK_TRACE = DATE_LEVEL_CLASS + ERROR_MSG_WITH_STACK_TRACE;


  @Test
  public void testTailLogFormat() throws Exception {
    File testDataDir = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(testDataDir.mkdirs());
    File logFile = new File(testDataDir, "logFile.txt");

    FileInfo fileInfo = new FileInfo();
    fileInfo.fileFullPath = testDataDir.getAbsolutePath() + "/logFile.txt";
    fileInfo.fileRollMode = FileRollMode.REVERSE_COUNTER;
    fileInfo.firstFile = "";
    fileInfo.patternForToken = "";
    FileTailSource source = new FileTailSource(DataFormat.LOG, "", "UTF-8", false, 1024, 25, 1, Arrays.asList(fileInfo),
                                               PostProcessingOptions.NONE, null,
                                               LogMode.LOG4J, true, null, null, null, null, null, false, null, SCAN_INTERVAL);
    SourceRunner runner = new SourceRunner.Builder(FileTailDSource.class, source)
      .addOutputLane("lane").addOutputLane("metadata")
      .build();
    runner.runInit();
    Files.write(logFile.toPath(), Arrays.asList(LINE1, LINE2), Charset.forName("UTF-8"));
    try {
      long start = System.currentTimeMillis();
      StageRunner.Output output = runner.runProduce(null, 10);
      long end = System.currentTimeMillis();
      Assert.assertTrue(end - start >= 1000);
      Assert.assertNotNull(output.getNewOffset());
      List<Record> records = output.getRecords().get("lane");
      Assert.assertEquals(2, records.size());
      Assert.assertFalse(records.get(0).has("/truncated"));

      Record record = records.get(0);

      Assert.assertEquals(LINE1, record.get().getValueAsMap().get("originalLine").getValueAsString());

      Assert.assertFalse(record.has("/truncated"));

      Assert.assertTrue(record.has("/" + Constants.TIMESTAMP));
      Assert.assertEquals("2015-03-20 15:53:31,161", record.get("/" + Constants.TIMESTAMP).getValueAsString());

      Assert.assertTrue(record.has("/" + Constants.SEVERITY));
      Assert.assertEquals("DEBUG", record.get("/" + Constants.SEVERITY).getValueAsString());

      Assert.assertTrue(record.has("/" + Constants.CATEGORY));
      Assert.assertEquals("PipelineConfigurationValidator", record.get("/" + Constants.CATEGORY).getValueAsString());

      Assert.assertTrue(record.has("/" + Constants.MESSAGE));
      Assert.assertEquals("Pipeline 'test:preview' validation. valid=true, canPreview=true, issuesCount=0",
        record.get("/" + Constants.MESSAGE).getValueAsString());

      record = records.get(1);

      Assert.assertEquals(LINE2, record.get().getValueAsMap().get("originalLine").getValueAsString());
      Assert.assertFalse(record.has("/truncated"));

      Assert.assertTrue(record.has("/" + Constants.TIMESTAMP));
      Assert.assertEquals("2015-03-21 15:53:31,161", record.get("/" + Constants.TIMESTAMP).getValueAsString());

      Assert.assertTrue(record.has("/" + Constants.SEVERITY));
      Assert.assertEquals("DEBUG", record.get("/" + Constants.SEVERITY).getValueAsString());

      Assert.assertTrue(record.has("/" + Constants.CATEGORY));
      Assert.assertEquals("PipelineConfigurationValidator", record.get("/" + Constants.CATEGORY).getValueAsString());

      Assert.assertTrue(record.has("/" + Constants.MESSAGE));
      Assert.assertEquals("Pipeline 'test:preview' validation. valid=true, canPreview=true, issuesCount=1",
        record.get("/" + Constants.MESSAGE).getValueAsString());

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testTailLogFormatStackTrace() throws Exception {
    File testDataDir = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(testDataDir.mkdirs());
    File logFile = new File(testDataDir, "logFile.txt");

    FileInfo fileInfo = new FileInfo();
    fileInfo.fileFullPath = testDataDir.getAbsolutePath() + "/logFile.txt";
    fileInfo.fileRollMode = FileRollMode.REVERSE_COUNTER;
    fileInfo.firstFile = "";
    fileInfo.patternForToken = "";
    FileTailSource source = new FileTailSource(DataFormat.TEXT, "^[0-9].*", "UTF-8", false, 2048, 2, 1000,
                                               Arrays.asList(fileInfo), PostProcessingOptions.NONE, null,
                                               LogMode.LOG4J, true, null, null, null, null, null, false, null, SCAN_INTERVAL);
    SourceRunner runner = new SourceRunner.Builder(FileTailDSource.class, source)
      .addOutputLane("lane").addOutputLane("metadata")
      .build();
    runner.runInit();
    Files.write(logFile.toPath(), Arrays.asList(LINE1, LOG_LINE_WITH_STACK_TRACE, LINE2), Charset.forName("UTF-8"));
    try {
      StageRunner.Output out = runner.runProduce(null, 100);
      Assert.assertEquals(2, out.getRecords().get("lane").size());
      Assert.assertEquals(LINE1, out.getRecords().get("lane").get(0).get("/text").getValueAsString().trim());
      Assert.assertEquals(LOG_LINE_WITH_STACK_TRACE, out.getRecords().get("lane").get(1).get("/text").getValueAsString().trim());
    } finally {
      runner.runDestroy();
    }
  }


  @Test
  public void testTailFilesDeletion() throws Exception {
    File testDataDir = new File("target", UUID.randomUUID().toString());
    File testDataDir1 = new File(testDataDir, UUID.randomUUID().toString());
    File testDataDir2 = new File(testDataDir, UUID.randomUUID().toString());
    Assert.assertTrue(testDataDir1.mkdirs());
    Assert.assertTrue(testDataDir2.mkdirs());
    Path file1 = new File(testDataDir1, "log1.txt").toPath();
    Path file2 = new File(testDataDir2, "log2.txt").toPath();
    Files.write(file1, Arrays.asList("Hello"), UTF8);
    Files.write(file2, Arrays.asList("Hola"), UTF8);

    FileInfo fileInfo1 = new FileInfo();
    fileInfo1.fileFullPath = testDataDir.getAbsolutePath() + "/*/log*.txt";
    fileInfo1.fileRollMode = FileRollMode.REVERSE_COUNTER;
    fileInfo1.firstFile = "";
    fileInfo1.patternForToken = "";
    FileTailSource source = new FileTailSource(DataFormat.TEXT, "", "UTF-8", false, 1024, 25, 1,
                                               Arrays.asList(fileInfo1), PostProcessingOptions.NONE, null,
                                               null, false, null, null, null, null, null, false, null, SCAN_INTERVAL);
    SourceRunner runner = new SourceRunner.Builder(FileTailDSource.class, source)
        .addOutputLane("lane").addOutputLane("metadata")
        .build();
    try {
      runner.runInit();
      StageRunner.Output output = runner.runProduce(null, 10);
      output = runner.runProduce(output.getNewOffset(), 10);
      Assert.assertTrue(output.getNewOffset().contains("log1.txt"));
      Assert.assertTrue(output.getNewOffset().contains("log2.txt"));
      Files.delete(file1);
      Files.delete(testDataDir1.toPath());
      output = runner.runProduce(output.getNewOffset(), 10);
      output = runner.runProduce(output.getNewOffset(), 10);
      Assert.assertFalse(output.getNewOffset().contains("log1.txt"));
      Assert.assertTrue(output.getNewOffset().contains("log2.txt"));
    } finally {
      runner.runDestroy();
    }
  }


  @Test
  public void testPeriodicFiles() throws Exception {
    File testDataDir = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(testDataDir.mkdirs());
    String logFile = new File(testDataDir, "logFile.txt-1").getAbsolutePath();
    InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("testLogFile.txt");
    OutputStream os = new FileOutputStream(logFile);
    IOUtils.copy(is, os);
    is.close();
    os.close();

    FileInfo fileInfo = new FileInfo();
    fileInfo.fileFullPath = testDataDir.getAbsolutePath() + "/logFile.txt-${PATTERN}";
    fileInfo.fileRollMode = FileRollMode.PATTERN;
    fileInfo.firstFile = "";
    fileInfo.patternForToken = "[0-9]";
    FileTailSource source = new FileTailSource(DataFormat.TEXT, "", "UTF-8", false, 1024, 25, 1, Arrays.asList(fileInfo),
                                               PostProcessingOptions.NONE, null, null,
                                               false, null, null, null, null, null, false, null, SCAN_INTERVAL);
    SourceRunner runner = new SourceRunner.Builder(FileTailDSource.class, source)
        .addOutputLane("lane").addOutputLane("metadata")
        .build();
    runner.runInit();
    try {
      long start = System.currentTimeMillis();
      StageRunner.Output output = runner.runProduce(null, 1000);
      long end = System.currentTimeMillis();
      Assert.assertTrue(end - start >= 1000);
      Assert.assertNotNull(output.getNewOffset());
      Assert.assertEquals(2, output.getRecords().get("lane").size());
      Record record = output.getRecords().get("lane").get(0);
      Assert.assertEquals("FIRST", record.get("/text").getValueAsString());
      record = output.getRecords().get("lane").get(1);
      Assert.assertEquals("LAST", record.get("/text").getValueAsString());
    } finally {
      runner.runDestroy();
    }
  }

  private Source createSourceForPeriodicFile(String filePathWithPattern, String pattern) {
    FileInfo fileInfo = new FileInfo();
    fileInfo.fileFullPath = filePathWithPattern;
    fileInfo.fileRollMode = FileRollMode.PATTERN;
    fileInfo.firstFile = "";
    fileInfo.patternForToken = pattern;
    return new FileTailSource(DataFormat.TEXT, "", "UTF-8", false, 1024, 25, 1, Arrays.asList(fileInfo),
                                               PostProcessingOptions.NONE, null, null,
                                               false, null, null, null, null, null, false, null, SCAN_INTERVAL);
  }

  private SourceRunner createRunner(Source source) {
    return new SourceRunner.Builder(FileTailDSource.class, source).addOutputLane("lane").addOutputLane("meta").build();
  }

  @Test
  public void testFileTruncatedBetweenRuns() throws Exception {
    File testDataDir = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(testDataDir.mkdirs());
    File file = new File(testDataDir, "file.txt-1");
    Files.write(file.toPath(), Arrays.asList("A", "B", "C"), StandardCharsets.UTF_8);

    FileInfo fileInfo = new FileInfo();
    fileInfo.fileFullPath = testDataDir.getAbsolutePath() + "/file.txt-${PATTERN}";
    fileInfo.fileRollMode = FileRollMode.PATTERN;
    fileInfo.firstFile = "";
    fileInfo.patternForToken = "[0-9]";
    Source source = createSourceForPeriodicFile(testDataDir.getAbsolutePath() + "/file.txt-${PATTERN}", "[0-9]");
    SourceRunner runner = createRunner(source);
    try {
      // run till current end and stop pipeline
      runner.runInit();
      StageRunner.Output output = runner.runProduce(null, 10);
      Assert.assertEquals(3, output.getRecords().get("lane").size());
      runner.runDestroy();

      // truncate file
      FileChannel channel = new FileOutputStream(file, true).getChannel();
      channel.truncate(2);
      channel.close();

      // run again, no new data, no error
      source = createSourceForPeriodicFile(testDataDir.getAbsolutePath() + "/file.txt-${PATTERN}", "[0-9]");
      runner = createRunner(source);
      runner.runInit();
      output = runner.runProduce(output.getNewOffset(), 10);
      Assert.assertEquals(0, output.getRecords().get("lane").size());
      runner.runDestroy();

      file = new File(testDataDir, "file.txt-2");
      Files.write(file.toPath(), Arrays.asList("A", "B"), StandardCharsets.UTF_8);

      // run again, new file
      source = createSourceForPeriodicFile(testDataDir.getAbsolutePath() + "/file.txt-${PATTERN}", "[0-9]");
      runner = createRunner(source);
      runner.runInit();
      output = runner.runProduce(output.getNewOffset(), 10);
      Assert.assertEquals(2, output.getRecords().get("lane").size());

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testFileDeletedBetweenRuns() throws Exception {
    File testDataDir = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(testDataDir.mkdirs());
    File file = new File(testDataDir, "file.txt-1");
    Files.write(file.toPath(), Arrays.asList("A", "B", "C"), StandardCharsets.UTF_8);

    FileInfo fileInfo = new FileInfo();
    fileInfo.fileFullPath = testDataDir.getAbsolutePath() + "/file.txt-${PATTERN}";
    fileInfo.fileRollMode = FileRollMode.PATTERN;
    fileInfo.firstFile = "";
    fileInfo.patternForToken = "[0-9]";
    Source source = createSourceForPeriodicFile(testDataDir.getAbsolutePath() + "/file.txt-${PATTERN}", "[0-9]");
    SourceRunner runner = createRunner(source);
    try {
      // run till current end and stop pipeline
      runner.runInit();
      StageRunner.Output output = runner.runProduce(null, 10);
      Assert.assertEquals(3, output.getRecords().get("lane").size());
      runner.runDestroy();

      Files.delete(file.toPath());

      // run again, no new data, no error
      source = createSourceForPeriodicFile(testDataDir.getAbsolutePath() + "/file.txt-${PATTERN}", "[0-9]");
      runner = createRunner(source);
      runner.runInit();
      output = runner.runProduce(output.getNewOffset(), 10);
      Assert.assertEquals(0, output.getRecords().get("lane").size());
      runner.runDestroy();

      file = new File(testDataDir, "file.txt-2");
      Files.write(file.toPath(), Arrays.asList("A", "B"), StandardCharsets.UTF_8);

      // run again, new file
      source = createSourceForPeriodicFile(testDataDir.getAbsolutePath() + "/file.txt-${PATTERN}", "[0-9]");
      runner = createRunner(source);
      runner.runInit();
      output = runner.runProduce(output.getNewOffset(), 10);
      Assert.assertEquals(2, output.getRecords().get("lane").size());

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testFileDeletedWhileRunning() throws Exception {
    File testDataDir = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(testDataDir.mkdirs());
    File file = new File(testDataDir, "file.txt-1");
    Files.write(file.toPath(), Arrays.asList("A", "B", "C"), StandardCharsets.UTF_8);

    FileInfo fileInfo = new FileInfo();
    fileInfo.fileFullPath = testDataDir.getAbsolutePath() + "/file.txt-${PATTERN}";
    fileInfo.fileRollMode = FileRollMode.PATTERN;
    fileInfo.firstFile = "";
    fileInfo.patternForToken = "[0-9]";
    Source source = createSourceForPeriodicFile(testDataDir.getAbsolutePath() + "/file.txt-${PATTERN}", "[0-9]");
    SourceRunner runner = createRunner(source);
    try {
      // run till current end and stop pipeline
      runner.runInit();
      StageRunner.Output output = runner.runProduce(null, 10);
      Assert.assertEquals(3, output.getRecords().get("lane").size());

      Files.delete(file.toPath());

      output = runner.runProduce(output.getNewOffset(), 10);
      Assert.assertEquals(0, output.getRecords().get("lane").size());

      file = new File(testDataDir, "file.txt-2");
      Files.write(file.toPath(), Arrays.asList("A", "B"), StandardCharsets.UTF_8);

      output = runner.runProduce(output.getNewOffset(), 10);
      Assert.assertEquals(2, output.getRecords().get("lane").size());

    } finally {
      runner.runDestroy();
    }
  }

}
