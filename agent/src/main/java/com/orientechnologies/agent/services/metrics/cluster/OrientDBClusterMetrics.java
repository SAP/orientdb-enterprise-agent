package com.orientechnologies.agent.services.metrics.cluster;

import com.orientechnologies.agent.profiler.OMetricsRegistry;
import com.orientechnologies.agent.services.metrics.OrientDBMetric;
import com.orientechnologies.agent.services.metrics.OrientDBMetricsSettings;
import com.orientechnologies.enterprise.server.OEnterpriseServer;
import com.orientechnologies.orient.server.distributed.ODistributedLifecycleListener;
import com.orientechnologies.orient.server.distributed.ODistributedRequest;
import com.orientechnologies.orient.server.distributed.ODistributedServerManager;

public class OrientDBClusterMetrics implements OrientDBMetric {

  private OEnterpriseServer         server;
  private OMetricsRegistry          registry;
  private OrientDBMetricsSettings   clusterSettings;
  private ODistributedServerManager distributedManager;

  public OrientDBClusterMetrics(OEnterpriseServer server, OMetricsRegistry registry, OrientDBMetricsSettings settings) {
    this.server = server;
    this.registry = registry;
    this.clusterSettings = settings;
  }

  @Override
  public void start() {

    distributedManager = server.getDistributedManager();

    if (distributedManager != null) {

      if (clusterSettings.cluster.requestTracing.enabled && clusterSettings.reporters.csv.enabled) {

        distributedManager.registerLifecycleListener(
            new OrientDBRequestTracing(clusterSettings.cluster.requestTracing, clusterSettings.reporters.csv.directory));
      }
    }

  }

  @Override
  public void stop() {

  }

}
