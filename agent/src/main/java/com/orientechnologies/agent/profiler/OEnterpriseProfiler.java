/*
 * Copyright 2010-2013 Orient Technologies LTD (info--at--orientechnologies.com)
 * All Rights Reserved. Commercial License.
 * 
 * NOTICE:  All information contained herein is, and remains the property of
 * Orient Technologies LTD and its suppliers, if any.  The intellectual and
 * technical concepts contained herein are proprietary to
 * Orient Technologies LTD and its suppliers and may be covered by United
 * Kingdom and Foreign Patents, patents in process, and are protected by trade
 * secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Orient Technologies LTD.
 * 
 * For more information: http://www.orientechnologies.com
 */

package com.orientechnologies.agent.profiler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.common.profiler.OAbstractProfiler;
import com.orientechnologies.common.profiler.OProfilerEntry;
import com.orientechnologies.common.profiler.OProfilerMBean;

/**
 * Profiling utility class. Handles chronos (times), statistics and counters. By default it's used as Singleton but you can create
 * any instances you want for separate profiling contexts.
 * 
 * To start the recording use call startRecording(). By default record is turned off to avoid a run-time execution cost.
 * 
 * @author Luca Garulli
 * @copyrights Orient Technologies.com
 */
public class OEnterpriseProfiler extends OAbstractProfiler implements OProfilerMBean {
  protected Date                      lastReset               = new Date();

  protected final List<OProfilerData> snapshots               = new ArrayList<OProfilerData>();
  protected OProfilerData             realTime                = new OProfilerData();
  protected OProfilerData             lastSnapshot;

  protected int                       elapsedToCreateSnapshot = 0;
  protected int                       maxSnapshots            = 0;
  protected final static Timer        timer                   = new Timer(true);
  protected TimerTask                 archiverTask;
  protected final int                 metricProcessors        = Runtime.getRuntime().availableProcessors();

  public OEnterpriseProfiler() {
    init();
  }

  public OEnterpriseProfiler(final int iElapsedToCreateSnapshot, final int iMaxSnapshot, final OAbstractProfiler iParentProfiler) {
    super(iParentProfiler);
    elapsedToCreateSnapshot = iElapsedToCreateSnapshot;
    maxSnapshots = iMaxSnapshot;
    init();
  }

  public void configure(final String iConfiguration) {
    if (iConfiguration == null || iConfiguration.length() == 0)
      return;

    final String[] parts = iConfiguration.split(",");
    elapsedToCreateSnapshot = Integer.parseInt(parts[0].trim());
    maxSnapshots = Integer.parseInt(parts[1].trim());

    if (isRecording())
      stopRecording();

    startRecording();
  }

  /**
   * Frees the memory removing profiling information
   */
  public void memoryUsageLow(final long iFreeMemory, final long iFreeMemoryPercentage) {
    synchronized (snapshots) {
      snapshots.clear();
    }
  }

  public void shutdown() {
    super.shutdown();
    hooks.clear();

    synchronized (snapshots) {
      snapshots.clear();
    }
  }

  public boolean startRecording() {
    if (!super.startRecording())
      return false;

    acquireExclusiveLock();
    try {
      OLogManager.instance().info(this, "Profiler is recording metrics with configuration: %d,%d", elapsedToCreateSnapshot,
          maxSnapshots);

      if (elapsedToCreateSnapshot > 0) {
        lastSnapshot = new OProfilerData();

        if (archiverTask != null)
          archiverTask.cancel();

        archiverTask = new TimerTask() {
          @Override
          public void run() {
            createSnapshot();
          }
        };
        timer.schedule(archiverTask, elapsedToCreateSnapshot * 1000, elapsedToCreateSnapshot * 1000);
      }

    } finally {
      releaseExclusiveLock();
    }

    return true;
  }

  public boolean stopRecording() {
    if (!super.stopRecording())
      return false;

    acquireExclusiveLock();
    try {
      OLogManager.instance().config(this, "Profiler has stopped recording metrics");

      lastSnapshot = null;
      realTime.clear();
      dictionary.clear();
      types.clear();

      if (archiverTask != null)
        archiverTask.cancel();

    } finally {
      releaseExclusiveLock();
    }

    return true;
  }

  public void createSnapshot() {
    if (lastSnapshot == null)
      return;

    final Map<String, Object> hookValuesSnapshots = archiveHooks();

    acquireExclusiveLock();
    try {

      synchronized (snapshots) {
        // ARCHIVE IT
        lastSnapshot.setHookValues(hookValuesSnapshots);
        lastSnapshot.endRecording();
        snapshots.add(lastSnapshot);

        lastSnapshot = new OProfilerData();

        while (snapshots.size() >= maxSnapshots && maxSnapshots > 0) {
          // REMOVE THE OLDEST SNAPSHOT
          snapshots.remove(0);
        }
      }

    } finally {
      releaseExclusiveLock();
    }
  }

  public void updateCounter(final String iName, final String iDescription, final long iPlus) {
    updateCounter(iName, iDescription, iPlus, iName);
  }

  public void updateCounter(final String iName, final String iDescription, final long iPlus, final String iMetadata) {
    if (iName == null || recordingFrom < 0)
      return;

    updateMetadata(iMetadata, iDescription, METRIC_TYPE.COUNTER);

    acquireSharedLock();
    try {
      if (lastSnapshot != null)
        lastSnapshot.updateCounter(iName, iPlus);
      realTime.updateCounter(iName, iPlus);
    } finally {
      releaseSharedLock();
    }
  }

  public long getCounter(final String iStatName) {
    if (iStatName == null || recordingFrom < 0)
      return -1;

    acquireSharedLock();
    try {
      return realTime.getCounter(iStatName);
    } finally {
      releaseSharedLock();
    }
  }

  public void resetRealtime(final String iText) {
    realTime.clear(iText);
  }

  public String toJSON(final String iQuery, final String iPar1) {
    final StringBuilder buffer = new StringBuilder();

    Map<String, Object> hookValuesSnapshots = null;

    if (iQuery.equals("realtime") || iQuery.equals("last"))
      // GET LATETS HOOK VALUES
      hookValuesSnapshots = archiveHooks();

    buffer.append("{ \"" + iQuery + "\":");

    acquireSharedLock();
    try {
      if (iQuery.equals("realtime")) {
        realTime.setHookValues(hookValuesSnapshots);
        realTime.toJSON(buffer, iPar1);

      } else if (iQuery.equals("last")) {
        if (lastSnapshot != null) {
          lastSnapshot.setHookValues(hookValuesSnapshots);
          lastSnapshot.toJSON(buffer, iPar1);
        }

      } else {
        // GET THE RANGES
        if (iPar1 == null)
          throw new IllegalArgumentException("Invalid range format. Use: <from>, where * means any");

        final long from = iPar1.equals("*") ? 0 : Long.parseLong(iPar1);

        boolean firstItem = true;
        buffer.append("[");
        if (iQuery.equals("archive")) {
          // ARCHIVE
          for (int i = 0; i < snapshots.size(); ++i) {
            final OProfilerData a = snapshots.get(i);

            if (a.getRecordingFrom() < from) {
              // ALREADY READ, REMOVE IT
              snapshots.remove(i);
              i--;
              continue;
            }

            if (firstItem)
              firstItem = false;
            else
              buffer.append(',');

            a.toJSON(buffer, null);
          }

        } else
          throw new IllegalArgumentException("Invalid archive query: use realtime|last|archive");

        buffer.append("]");
      }

      buffer.append("}");

    } finally {
      releaseSharedLock();
    }

    return buffer.toString();
  }

  public String metadataToJSON() {
    final StringBuilder buffer = new StringBuilder();

    buffer.append("{ \"metadata\": {\n  ");
    boolean first = true;
    for (Entry<String, String> entry : dictionary.entrySet()) {
      final String key = entry.getKey();

      if (first)
        first = false;
      else
        buffer.append(",\n  ");
      buffer.append('"');
      buffer.append(key);
      buffer.append("\":{\"description\":\"");
      buffer.append(entry.getValue());
      buffer.append("\",\"type\":\"");
      buffer.append(types.get(key));
      buffer.append("\"}");
    }
    buffer.append("} }");

    return buffer.toString();
  }

  public String dump() {
    final float maxMem = Runtime.getRuntime().maxMemory() / 1000000f;
    final float totMem = Runtime.getRuntime().totalMemory() / 1000000f;
    final float freeMem = maxMem - totMem;

    final long now = System.currentTimeMillis();

    acquireSharedLock();
    try {

      final StringBuilder buffer = new StringBuilder();
      buffer.append("\nOrientDB profiler dump of ");
      buffer.append(new Date(now));
      buffer.append(" after ");
      buffer.append((now - recordingFrom) / 1000);
      buffer.append(" secs of profiling");
      buffer.append(String.format("\nFree memory: %2.2fMb (%2.2f%%) - Total memory: %2.2fMb - Max memory: %2.2fMb - CPUs: %d",
          freeMem, (freeMem * 100 / (float) maxMem), totMem, maxMem, Runtime.getRuntime().availableProcessors()));
      buffer.append("\n");
      buffer.append(dumpHookValues());
      buffer.append("\n");
      buffer.append(dumpCounters());
      buffer.append("\n\n");
      buffer.append(dumpStats());
      buffer.append("\n\n");
      buffer.append(dumpChronos());
      return buffer.toString();

    } finally {
      releaseSharedLock();
    }
  }

  public long startChrono() {
    // CHECK IF CHRONOS ARE ACTIVED
    if (recordingFrom < 0)
      return -1;

    return System.currentTimeMillis();
  }

  public long stopChrono(final String iName, final String iDescription, final long iStartTime) {
    return stopChrono(iName, iDescription, iStartTime, iName, null);
  }

  public long stopChrono(final String iName, final String iDescription, final long iStartTime, final String iDictionaryName) {
    return stopChrono(iName, iDescription, iStartTime, iDictionaryName, null);
  }

  public long stopChrono(final String iName, final String iDescription, final long iStartTime, final String iDictionaryName,
      final String iPayload) {
    // CHECK IF CHRONOS ARE ACTIVED
    if (recordingFrom < 0)
      return -1;

    updateMetadata(iDictionaryName, iDescription, METRIC_TYPE.CHRONO);

    acquireSharedLock();
    try {

      if (lastSnapshot != null)
        lastSnapshot.stopChrono(iName, iStartTime, iPayload);
      return realTime.stopChrono(iName, iStartTime, iPayload);

    } finally {
      releaseSharedLock();
    }
  }

  public long updateStat(final String iName, final String iDescription, final long iValue) {
    // CHECK IF CHRONOS ARE ACTIVED
    if (recordingFrom < 0)
      return -1;

    updateMetadata(iName, iDescription, METRIC_TYPE.STAT);

    acquireSharedLock();
    try {

      if (lastSnapshot != null)
        lastSnapshot.updateStat(iName, iValue);
      return realTime.updateStat(iName, iValue);

    } finally {
      releaseSharedLock();
    }
  }

  public String dumpCounters() {
    // CHECK IF STATISTICS ARE ACTIVED
    if (recordingFrom < 0)
      return "Counters: <no recording>";

    acquireSharedLock();
    try {
      return realTime.dumpCounters();
    } finally {
      releaseSharedLock();
    }
  }

  public String dumpChronos() {
    acquireSharedLock();
    try {
      return realTime.dumpChronos();
    } finally {
      releaseSharedLock();
    }
  }

  public String dumpStats() {
    acquireSharedLock();
    try {
      return realTime.dumpStats();
    } finally {
      releaseSharedLock();
    }
  }

  public String dumpHookValues() {
    if (recordingFrom < 0)
      return "HookValues: <no recording>";

    final StringBuilder buffer = new StringBuilder();

    acquireSharedLock();
    try {

      if (hooks.size() == 0)
        return "";

      buffer.append("HOOK VALUES:");

      buffer.append(String.format("\n%50s +-------------------------------------------------------------------+", ""));
      buffer.append(String.format("\n%50s | Value                                                             |", "Name"));
      buffer.append(String.format("\n%50s +-------------------------------------------------------------------+", ""));

      final List<String> names = new ArrayList<String>(hooks.keySet());
      Collections.sort(names);

      for (String k : names) {
        final OProfilerHookValue v = hooks.get(k);
        if (v != null) {
          final Object hookValue = v.getValue();
          buffer.append(String.format("\n%-50s | %-65s |", k, hookValue != null ? hookValue.toString() : "null"));
        }
      }

    } finally {
      releaseSharedLock();
    }

    buffer.append(String.format("\n%50s +-------------------------------------------------------------------+", ""));
    return buffer.toString();
  }

  public Object getHookValue(final String iName) {
    final OProfilerHookValue v = hooks.get(iName);
    return v != null ? v.getValue() : null;
  }

  public String[] getCountersAsString() {
    acquireSharedLock();
    try {
      return realTime.getCountersAsString();
    } finally {
      releaseSharedLock();
    }
  }

  public String[] getChronosAsString() {
    acquireSharedLock();
    try {
      return realTime.getChronosAsString();
    } finally {
      releaseSharedLock();
    }
  }

  public String[] getStatsAsString() {
    acquireSharedLock();
    try {
      return realTime.getStatsAsString();
    } finally {
      releaseSharedLock();
    }
  }

  public Date getLastReset() {
    return lastReset;
  }

  public List<String> getCounters() {
    acquireSharedLock();
    try {
      return realTime.getCounters();
    } finally {
      releaseSharedLock();
    }
  }

  public OProfilerEntry getStat(final String iStatName) {
    acquireSharedLock();
    try {
      return realTime.getStat(iStatName);
    } finally {
      releaseSharedLock();
    }
  }

  public OProfilerEntry getChrono(final String iChronoName) {
    acquireSharedLock();
    try {
      return realTime.getChrono(iChronoName);
    } finally {
      releaseSharedLock();
    }
  }

  public void setAutoDump(final int iSeconds) {
    if (iSeconds > 0) {
      final int ms = iSeconds * 1000;

      timer.schedule(new TimerTask() {

        @Override
        public void run() {
          System.out.println(dump());
        }
      }, ms, ms);
    }
  }

  /**
   * Must be not called inside a lock.
   */
  protected Map<String, Object> archiveHooks() {
    if (!isRecording())
      return null;

    final Map<String, Object> result = new HashMap<String, Object>();

    for (Map.Entry<String, OProfilerHookValue> v : hooks.entrySet())
      result.put(v.getKey(), v.getValue().getValue());

    return result;
  }

  protected void init() {
    registerHookValue(getSystemMetric("config.cpus"), "Number of CPUs", METRIC_TYPE.SIZE, new OProfilerHookValue() {
      @Override
      public Object getValue() {
        return metricProcessors;
      }
    });
    registerHookValue(getSystemMetric("config.os.name"), "Operative System name", METRIC_TYPE.TEXT, new OProfilerHookValue() {
      @Override
      public Object getValue() {
        return System.getProperty("os.name");
      }
    });
    registerHookValue(getSystemMetric("config.os.version"), "Operative System version", METRIC_TYPE.TEXT, new OProfilerHookValue() {
      @Override
      public Object getValue() {
        return System.getProperty("os.version");
      }
    });
    registerHookValue(getSystemMetric("config.os.arch"), "Operative System architecture", METRIC_TYPE.TEXT,
        new OProfilerHookValue() {
          @Override
          public Object getValue() {
            return System.getProperty("os.arch");
          }
        });
    registerHookValue(getSystemMetric("config.java.vendor"), "Java vendor", METRIC_TYPE.TEXT, new OProfilerHookValue() {
      @Override
      public Object getValue() {
        return System.getProperty("java.vendor");
      }
    });
    registerHookValue(getSystemMetric("config.java.version"), "Java version", METRIC_TYPE.TEXT, new OProfilerHookValue() {
      @Override
      public Object getValue() {
        return System.getProperty("java.version");
      }
    });
    registerHookValue(getProcessMetric("runtime.availableMemory"), "Available memory for the process", METRIC_TYPE.SIZE,
        new OProfilerHookValue() {
          @Override
          public Object getValue() {
            return Runtime.getRuntime().freeMemory();
          }
        });
    registerHookValue(getProcessMetric("runtime.maxMemory"), "Maximum memory usable for the process", METRIC_TYPE.SIZE,
        new OProfilerHookValue() {
          @Override
          public Object getValue() {
            return Runtime.getRuntime().maxMemory();
          }
        });
    registerHookValue(getProcessMetric("runtime.totalMemory"), "Total memory used by the process", METRIC_TYPE.SIZE,
        new OProfilerHookValue() {
          @Override
          public Object getValue() {
            return Runtime.getRuntime().totalMemory();
          }
        });

    final File[] roots = File.listRoots();
    for (final File root : roots) {
      String volumeName = root.getAbsolutePath();
      int pos = volumeName.indexOf(":\\");
      if (pos > -1)
        volumeName = volumeName.substring(0, pos);

      final String metricPrefix = "system.disk." + volumeName;

      registerHookValue(metricPrefix + ".totalSpace", "Total used disk space", METRIC_TYPE.SIZE, new OProfilerHookValue() {
        @Override
        public Object getValue() {
          return root.getTotalSpace();
        }
      });

      registerHookValue(metricPrefix + ".freeSpace", "Total free disk space", METRIC_TYPE.SIZE, new OProfilerHookValue() {
        @Override
        public Object getValue() {
          return root.getFreeSpace();
        }
      });

      registerHookValue(metricPrefix + ".usableSpace", "Total usable disk space", METRIC_TYPE.SIZE, new OProfilerHookValue() {
        @Override
        public Object getValue() {
          return root.getUsableSpace();
        }
      });
    }
  }
}