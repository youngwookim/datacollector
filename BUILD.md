<!---
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License. See accompanying LICENSE file.
--->

[StreamSets](http://streamsets.com)

# Building StreamSets Data Collector

To build the Data Collector you will need the following software :

- Git 1.9+
- JDK 1.7.*
- Maven 3.2.3+
- Node 0.10.32+1  (OSX, avail via macports: sudo install nodejs)
 - npm            (OSX, avail via macports: sudo install npm)
 - bower          (avail via npm          : sudo npm -g install bower)
 - grunt-cli      (avail via npm          : sudo npm -g install grunt-cli)

Follow these instructions to build the Data Collector :

- Get the latest code from github

`git clone http://github.com/streamsets/datacollector`

## Development build

From within the Data Collector directory, execute:

`mvn package -Pdist,ui -DskipTests`

To start the Data Collector, execute:

`dist/target/streamsets-datacollector-1.2.0-SNAPSHOT/streamsets-datacollector-1.2.0-SNAPSHOT/bin/streamsets dc`

For Data Collector CLI, execute:

`dist/target/streamsets-datacollector-1.2.0-SNAPSHOT/streamsets-datacollector-1.2.0-SNAPSHOT/bin/streamsets cli`

## Release build

From within the Data Collector directory, execute:

`mvn package -Drelease -DskipTests`

The release tarball will be created at:

`release/target/tar/streamsets-datacollector-1.2.0-SNAPSHOT.tgz`

Untar the tarball in your prefered location :

`tar xvzf streamsets-datacollector-1.2.0-SNAPSHOT.tgz`

To start the DataCollector, execute:

`streamsets-datacollector-1.2.0-SNAPSHOT/bin/streamsets dc`
