package com.orientechnologies.agent.services.metrics.cluster;

import com.orientechnologies.orient.server.distributed.task.ORemoteTask;

import java.util.HashSet;
import java.util.Set;

public class TracingData {

  private       long         messageId;
  private       String       nodeSource;
  private final String       databaseName;
  private final String       taskName;
  private       Long         receivedAt;
  private       Long         startedAt;
  private       Long         endedAt;
  private       Long         startLock      = 0l;
  private       Long         endLock        = 0l;
  private       Object       response;
  private       ORemoteTask  remoteTask;
  private       Set<Integer> involvedQueues = new HashSet<>();

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

  public void setReceivedAt(Long receivedAt) {
    this.receivedAt = receivedAt;
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

  public void setStartLock(Long startLock) {
    this.startLock = startLock;
  }

  public void setEndLock(Long endLock) {
    this.endLock = endLock;
  }

  public Long getStartLock() {
    return startLock;
  }

  public Long getEndLock() {
    return endLock;
  }

  public Set<Integer> getInvolvedQueues() {
    return involvedQueues;
  }
}
