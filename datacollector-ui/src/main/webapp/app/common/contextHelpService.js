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

/**
 * Service for launching Contextual Help.
 */
angular.module('dataCollectorApp.common')
  .service('contextHelpService', function($rootScope, $q, configuration, api, pipelineConstant) {

    var helpIds = {},
      helpWindow;

    this.configInitPromise = $q.all([api.admin.getHelpRef(), configuration.init()]).then(function(results) {
      helpIds = results[0].data;
    });

    this.launchHelp = function(helpId) {
      this.configInitPromise.then(function() {
        var uiHelpBaseURL, helpURL,
          relativeURL = helpIds[helpId];

        if ($rootScope.$storage.helpLocation === pipelineConstant.HOSTED_HELP) {
          uiHelpBaseURL = configuration.getUIHostedHelpBaseURL();
        } else {
          uiHelpBaseURL = configuration.getUILocalHelpBaseURL();
        }

        helpURL = uiHelpBaseURL + '/' + (relativeURL || 'index.html');

        if(typeof(helpWindow) == 'undefined' || helpWindow.closed) {
          helpWindow = window.open(helpURL);
        } else {
          helpWindow.location.href = helpURL;
          helpWindow.focus();
        }

      });
    };

    this.launchHelpContents = function() {
      this.configInitPromise.then(function() {
        var uiHelpBaseURL, helpURL;
        if ($rootScope.$storage.helpLocation === pipelineConstant.HOSTED_HELP) {
          uiHelpBaseURL = configuration.getUIHostedHelpBaseURL();
        } else {
          uiHelpBaseURL = configuration.getUILocalHelpBaseURL();
        }

        helpURL = uiHelpBaseURL + '/index.html';

        if(typeof(helpWindow) == 'undefined' || helpWindow.closed) {
          helpWindow = window.open(helpURL);
        } else {
          helpWindow.location.href = helpURL;
          helpWindow.focus();
        }

      });
    };

  });