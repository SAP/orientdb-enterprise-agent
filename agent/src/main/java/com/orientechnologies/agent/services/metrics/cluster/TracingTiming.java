package com.orientechnologies.agent.services.metrics.cluster;

import com.orientechnologies.orient.server.distributed.task.ORemoteTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TracingTiming {

  private Long start = 0l;
  private Long end   = 0l;

  public TracingTiming(Long start) {
    this.start = start;
  }

  public void setEnd(Long end) {
    this.end = end;
  }

  public Long getStart() {
    return start;
  }

  public Long getEnd() {
    return end;
  }
}
