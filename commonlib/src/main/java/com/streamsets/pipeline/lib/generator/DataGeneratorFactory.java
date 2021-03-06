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
package com.streamsets.pipeline.lib.generator;


import com.streamsets.pipeline.lib.data.DataFactory;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public abstract class DataGeneratorFactory extends DataFactory {

  protected DataGeneratorFactory(Settings settings) {
    super(settings);
  }

  public DataGenerator getGenerator(File file) throws IOException {
    FileOutputStream fileOutputStream = null;
    try {
      fileOutputStream = new FileOutputStream(file);
      return getGenerator(fileOutputStream);
    } catch (IOException ex) {
      IOUtils.closeQuietly(fileOutputStream);
      throw ex;
    }
  }

  public abstract DataGenerator getGenerator(OutputStream os) throws IOException;

  public Writer createWriter(OutputStream os) {
    return new OutputStreamWriter(os, getSettings().getCharset());
  }

}
