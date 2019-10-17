package com.orientechnologies.agent.services.metrics.cluster;

import com.orientechnologies.orient.server.distributed.task.ORemoteTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TracingData {

  private       long   messageId;
  private       String nodeSource;
  private final String databaseName;
  private final String taskName;
  private       Long   receivedAt;
  private       Long   startedAt;
  private       Long   endedAt;

  private Map<String, TracingTiming> opTimings = new HashMap<>();

  private Object       response;
  private Object       payload;
  private ORemoteTask  remoteTask;
  private Set<Integer> involvedQueues = new HashSet<>();

  public TracingData(long messageId, String nodeSource, String databaseName, String taskName, Long receivedAt) {
    this.messageId = messageId;
    this.nodeSource = nodeSource;
    this.databaseName = databaseName;
    this.taskName = taskName;
    this.receivedAt = receivedAt;
  }

  public Long getReceivedAt() {
    return receivedAt;
  }

  public Long getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Long startedAt) {
    this.startedAt = startedAt;
  }

  public Long getEndedAt() {
    return endedAt;
  }

  public void setEndedAt(Long endedAt) {
    this.endedAt = endedAt;
  }

  public String getNodeSource() {
    return nodeSource;
  }

  public long getMessageId() {
    return messageId;
  }

  public String getDatabaseName() {
    return databaseName;
  }

  public String getTaskName() {
    return taskName;
  }

  public void setRemoteTask(ORemoteTask remoteTask) {
    this.remoteTask = remoteTask;
  }

  public ORemoteTask getRemoteTask() {
    return remoteTask;
  }

  public void setResponse(Object response) {
    this.response = response;
  }

  public Object getResponse() {
    return response;
  }

  public void setInvolvedQueues(Set<Integer> involvedWorkerQueues) {
    this.involvedQueues = involvedWorkerQueues;
  }

  public Set<Integer> getInvolvedQueues() {
    return involvedQueues;
  }

  public Map<String, TracingTiming> getOpTimings() {
    return opTimings;
  }

  public void setPayload(Object payload) {
    this.payload = payload;
  }

  public Object getPayload() {
    return payload;
  }
}
