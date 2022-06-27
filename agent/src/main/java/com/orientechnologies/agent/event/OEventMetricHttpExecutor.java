/*
 * Copyright 2015 OrientDB LTD (info(at)orientdb.com)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *   For more information: http://www.orientdb.com
 */
package com.orientechnologies.agent.event;

import com.orientechnologies.agent.event.metric.OEventMetricExecutor;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.record.impl.ODocument;

import java.net.MalformedURLException;
import java.util.Map;

@EventConfig(when = "MetricWhen", what = "HttpWhat")
public class OEventMetricHttpExecutor extends OEventMetricExecutor {

  public OEventMetricHttpExecutor() {

  }

  @Override
  public void execute(ODocument source, ODocument when, ODocument what) {

    // pre-conditions
    if (canExecute(source, when)) {
      Map<String, Object> mapResolve = fillMapResolve(source, when);
      try {
        executeHttp(what, mapResolve);
      } catch (MalformedURLException e) {
        OLogManager.instance().error(this, "Error executing HTTP request", e);
      }
    }
  }

  private void executeHttp(ODocument what, Map<String, Object> mapResolve) throws MalformedURLException {
    OLogManager.instance().info(this, "HTTP executing: %s", what);

    EventHelper.executeHttpRequest(what, mapResolve);
  }
}