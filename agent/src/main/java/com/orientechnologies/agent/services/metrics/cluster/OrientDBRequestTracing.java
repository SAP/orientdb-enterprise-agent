package com.orientechnologies.agent.services.metrics.cluster;

import com.opencsv.CSVWriter;
import com.orientechnologies.agent.services.metrics.OrientDBMetricsSettings;
import com.orientechnologies.orient.client.remote.message.tx.ORecordOperationRequest;
import com.orientechnologies.orient.core.db.record.ORecordOperation;
import com.orientechnologies.orient.server.distributed.ODistributedLifecycleListener;
import com.orientechnologies.orient.server.distributed.ODistributedRequest;
import com.orientechnologies.orient.server.distributed.ODistributedRequestId;
import com.orientechnologies.orient.server.distributed.ODistributedServerManager;
import com.orientechnologies.orient.server.distributed.impl.OWaitPartitionsReadyTask;
import com.orientechnologies.orient.server.distributed.impl.task.OTransactionPhase1Task;
import com.orientechnologies.orient.server.distributed.impl.task.OTransactionPhase2Task;
import com.orientechnologies.orient.server.distributed.task.ORemoteTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class OrientDBRequestTracing extends Thread implements ODistributedLifecycleListener {

  private static final String DEFAULT_SEPARATOR = ",";

  private ConcurrentHashMap<ODistributedRequestId, TracingData>             requests = new ConcurrentHashMap<>();
  private OrientDBMetricsSettings.ClusterMetricsSettings.DistributedTracing requestTracing;
  private String                                                            directory;

  private ArrayBlockingQueue<TracingData> tracingData = new ArrayBlockingQueue<TracingData>(100000);

  public OrientDBRequestTracing(OrientDBMetricsSettings.ClusterMetricsSettings.DistributedTracing requestTracing,
      String directory) {

    this.requestTracing = requestTracing;
    this.directory = directory;
    start();
  }

  @Override
  public boolean onNodeJoining(String iNode) {
    return true;
  }

  @Override
  public void onNodeJoined(String iNode) {

  }

  @Override
  public void onNodeLeft(String iNode) {

  }

  @Override
  public void onDatabaseChangeStatus(String iNode, String iDatabaseName, ODistributedServerManager.DB_STATUS iNewStatus) {

  }

  @Override
  public void onMessageReceived(ODistributedRequest request) {
    requests.putIfAbsent(request.getId(),
        new TracingData(request.getId().getMessageId(), request.getTask().getNodeSource(), request.getDatabaseName(),
            request.getTask().getName(), System.currentTimeMillis()));
  }

  @Override
  public void onMessagePartitionCalculated(ODistributedRequest request, Set<Integer> involvedWorkerQueues) {

    if (involvedWorkerQueues != null && involvedWorkerQueues.size() > 0) {
      TracingData data = requests.get(request.getId());
      data.setInvolvedQueues(involvedWorkerQueues);
    }

  }

  @Override
  public void onMessageProcessEnd(ODistributedRequest iRequest, Object responsePayload) {
    TracingData data = requests.remove(iRequest.getId());

    if (data != null) {
      data.setEndedAt(System.currentTimeMillis());
      data.setRemoteTask(iRequest.getTask());
      data.setResponse(responsePayload);

      long end = data.getEndedAt() - data.getReceivedAt();

      if (end >= requestTracing.minExecution) {

        tracingData.offer(data);

      }
    }
  }

  @Override
  public void run() {

    TracingData data;
    File f = Paths.get(directory + File.separator + "cluster.requestTracing.csv").toFile();
    CSVWriter writer = null;
    try {

      boolean exists = f.exists();
      if (exists) {
        f.delete();
      }
      f.createNewFile();
      writer = new CSVWriter(new FileWriter(f));
      if (!exists) {
        writer.writeNext(("id,nodeSource,database,receivedAt,task,queueTime,executionTime,partitions,debug").split(DEFAULT_SEPARATOR));
        writer.flush();
      }
      do {
        try {
          data = tracingData.take();
        } catch (InterruptedException e) {
          break;
        }

        List<Object> values = new ArrayList<Object>();
        values.add(data.getMessageId());
        values.add(data.getNodeSource());
        values.add(data.getDatabaseName());
        values.add(data.getReceivedAt());
        values.add(data.getTaskName());
        values.add(data.getStartedAt() - data.getReceivedAt());
        values.add(data.getEndedAt() - data.getStartedAt());
        values.add(formatPayload(data.getRemoteTask(), data.getInvolvedQueues()));

        report(writer, values);
      } while (data != null);
    } catch (IOException e) {
      e.printStackTrace();

    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private String formatPayload(ORemoteTask remoteTask, Set<Integer> partitions) {

    if (remoteTask instanceof OWaitPartitionsReadyTask) {
      return formatPayload(((OWaitPartitionsReadyTask) remoteTask).getInternal(), partitions);
    }
    if (remoteTask instanceof OTransactionPhase1Task) {
      OTransactionPhase1Task t = (OTransactionPhase1Task) remoteTask;
      return format(t, partitions);
    }

    if (remoteTask instanceof OTransactionPhase2Task) {
      OTransactionPhase2Task t = (OTransactionPhase2Task) remoteTask;
      return format(t, partitions);
    }

    return remoteTask.getClass().getSimpleName();
  }

  private String format(OTransactionPhase2Task task, Set<Integer> partitions) {
    return String
        .format("OTransactionPhase2Task{ phase1Id: %d, retryCount: %d , partitions: %s }", task.getTransactionId().getMessageId(),
            task.getRetryCount(), partitions);
  }

  private String format(OTransactionPhase1Task task, Set<Integer> partitions) {
    long created = 0;
    long updated = 0;
    long deleted = 0;
    for (ORecordOperationRequest operation : task.getOperations()) {

      switch (operation.getType()) {
      case ORecordOperation.CREATED:
        created++;
        break;
      case ORecordOperation.UPDATED:
        updated++;
        break;
      case ORecordOperation.DELETED:
        deleted++;
        break;
      }
    }
    for (ORecordOperation op : task.getOps()) {
      switch (op.getType()) {
      case ORecordOperation.CREATED:
        created++;
        break;
      case ORecordOperation.UPDATED:
        updated++;
        break;
      case ORecordOperation.DELETED:
        deleted++;
        break;
      }

    }

    return String
        .format("OTransactionPhase1Task{ retry: %d, created: %d, updated: %d, deleted:%d , partitions: %s }", task.getRetryCount(),
            created, updated, deleted, partitions);
  }

  private String fromatResponse(Object response) {
    return "";
  }

  @Override
  public void onMessageProcessStart(ODistributedRequest message) {
    TracingData data = requests.get(message.getId());
    if (data != null) {
      data.setStartedAt(System.currentTimeMillis());
    }
  }

  private void report(CSVWriter writer, List<Object> values) {
    try {

      String[] val = values.stream().map(v -> v.toString()).toArray(size -> new String[size]);
      writer.writeNext(val);
      writer.flush();
    } catch (Exception e) {
      e.printStackTrace();
    }

  }
}
