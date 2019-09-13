package com.orientechnologies.agent.services.metrics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.orientechnologies.agent.services.metrics.reporters.Reporters;

/**
 * Created by Enrico Risa on 13/07/2018.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrientDBMetricsSettings {

  public Boolean enabled = false;

  public ServerMetricsSettings   server    = new ServerMetricsSettings();
  public DatabaseMetricsSettings database  = new DatabaseMetricsSettings();
  public ClusterMetricsSettings  cluster   = new ClusterMetricsSettings();
  public Reporters               reporters = new Reporters();

  public OrientDBMetricsSettings() {

  }

  class ServerMetricsSettings {
    public Boolean enabled = false;

    public ServerMetricsSettings() {
    }
  }

  class DatabaseMetricsSettings {
    public Boolean enabled = false;

    public DatabaseMetricsSettings() {
    }
  }

  public class ClusterMetricsSettings {
    public Boolean enabled = false;

    public DistributedTracing requestTracing = new DistributedTracing();

    public class DistributedTracing {
      public Boolean enabled      = false;
      public Long    minExecution = 0l;
    }

    public ClusterMetricsSettings() {
    }
  }

}
