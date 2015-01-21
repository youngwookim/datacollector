/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.hdfs.writer;

import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.hdfs.HdfsFileType;
import com.streamsets.pipeline.lib.recordserialization.RecordToString;
import com.streamsets.pipeline.sdk.RecordCreator;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

public class TestActiveRecordWriters {
  private static Path testDir;

  public static class DummyRecordToString implements RecordToString {
    @Override
    public void setFieldPathToNameMapping(Map<String, String> fieldPathToNameMap) {

    }

    @Override
    public String toString(Record record) {
      return record.get().getValueAsString();
    }
  }

  @BeforeClass
  public static void setUpClass() {
    File dir = new File("target", UUID.randomUUID().toString()).getAbsoluteFile();
    Assert.assertTrue(dir.mkdirs());
    testDir = new Path(dir.getAbsolutePath());
  }

  private Path getTestDir() {
    return testDir;
  }

  @Test
  public void testWritersLifecycle() throws Exception {
    URI uri = new URI("file:///");
    Configuration conf = new HdfsConfiguration();
    String prefix = "prefix";
    String template = getTestDir().toString() + "/${YYYY}/${MM}/${DD}/${hh}/${mm}/${ss}/${record:value('/')}";
    TimeZone timeZone = TimeZone.getTimeZone("UTC");
    long cutOffSecs = 2;
    long cutOffSize = 10000;
    long cutOffRecords = 2;
    HdfsFileType fileType = HdfsFileType.SEQUENCE_FILE;
    DefaultCodec compressionCodec = new DefaultCodec();
    compressionCodec.setConf(conf);
    SequenceFile.CompressionType compressionType = SequenceFile.CompressionType.BLOCK;
    String keyEL = "uuid()";
    RecordToString toString = new DummyRecordToString();
    RecordWriterManager mgr = new RecordWriterManager(uri, conf, prefix, template, timeZone, cutOffSecs, cutOffSize,
                                                      cutOffRecords, fileType, compressionCodec , compressionType,
                                                      keyEL, toString);
    ActiveRecordWriters writers = new ActiveRecordWriters(mgr);

    Date now = new Date();

    // record older than cut off
    Date recordDate = new Date(now.getTime() - 3 * 1000 - 1);
    Record record = RecordCreator.create();
    record.set(Field.create("a"));
    Assert.assertNull(writers.get(now, recordDate, record));

    recordDate = new Date(now.getTime());
    RecordWriter writer = writers.get(now, recordDate, record);
    Assert.assertNotNull(writer);
    Path tempPath = writer.getPath();
    writer.write(record);
    writers.release(writer);
    //writer should still be open
    Assert.assertFalse(writer.isClosed());

    writer = writers.get(now, recordDate, record);
    writer.write(record);
    writers.release(writer);
    //writer should be close because of going over record count threshold
    Assert.assertTrue(writer.isClosed());

    //we should be able to get a new writer as the cutoff didn't kick in yet
    writer = writers.get(now, recordDate, record);
    Assert.assertNotNull(writer);
    writers.purge();
    //purging should not close the writer as the cutoff didn't kick in yet
    Assert.assertFalse(writer.isClosed());

    Thread.sleep(3001);
    writers.purge();
    //purging should  close the writer as the cutoff kicked in yet
    Assert.assertTrue(writer.isClosed());

    //verifying closeAll() closes writers
    writer = writers.get(new Date(), new Date(), record);
    Assert.assertNotNull(writer);
    writers.closeAll();
    Assert.assertTrue(writer.isClosed());
  }

}