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
package com.streamsets.pipeline.stage.processor.selector;

import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.ConfigGroups;
import com.streamsets.pipeline.api.GenerateResourceBundle;
import com.streamsets.pipeline.api.PredicateModel;
import com.streamsets.pipeline.api.Processor;
import com.streamsets.pipeline.api.StageDef;
import com.streamsets.pipeline.configurablestage.DProcessor;
import com.streamsets.pipeline.lib.el.RecordEL;

import java.util.List;
import java.util.Map;

@StageDef(
    version = 1,
    label = "Stream Selector",
    description = "Passes records to streams based on conditions",
    icon="laneSelector.png",
    outputStreams = StageDef.VariableOutputStreams.class,
    outputStreamsDrivenByConfig = "lanePredicates")
@ConfigGroups(Groups.class)
@GenerateResourceBundle
public class SelectorDProcessor extends DProcessor {

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      label = "Condition",
      description = "Records that match the condition pass to the stream",
      displayPosition = 10,
      group = "CONDITIONS",
      evaluation = ConfigDef.Evaluation.EXPLICIT,
      elDefs = {RecordEL.class}
  )
  @PredicateModel
  public List<Map<String, String>> lanePredicates;

  @Override
  protected Processor createProcessor() {
    return new SelectorProcessor(lanePredicates);
  }

}
