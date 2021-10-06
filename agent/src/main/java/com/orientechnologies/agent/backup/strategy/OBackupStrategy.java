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

package com.orientechnologies.agent.backup.strategy;

import com.orientechnologies.agent.backup.OBackupConfig;
import com.orientechnologies.agent.backup.OBackupListener;
import com.orientechnologies.agent.backup.OBackupTask;
import com.orientechnologies.agent.backup.log.*;
import com.orientechnologies.backup.uploader.OLocalBackupUploader;
import com.orientechnologies.backup.uploader.OUploadMetadata;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.exception.OInvalidInstanceIdException;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.handler.OAutomaticBackup;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Created by Enrico Risa on 25/03/16.
 */
public abstract class OBackupStrategy {

  protected ODocument                      cfg;
  protected OBackupLogger                  logger;
  protected Optional<OLocalBackupUploader> uploader;

  public OBackupStrategy(ODocument cfg, OBackupLogger logger) {
    this.cfg = cfg;
    this.logger = logger;
    this.uploader = OLocalBackupUploader.from(cfg.field("upload"));
  }

  public OBackupStartedLog startBackup() throws IOException {

    OBackupLog last = logger.findLast(OBackupLogType.BACKUP_SCHEDULED, getUUID());

    long txId;
    long unitId;
    if (last != null) {
      unitId = last.getUnitId();
      txId = last.getTxId();
    } else {
      unitId = logger.nextOpId();
      txId = logger.nextOpId();
    }
    return new OBackupStartedLog(unitId, txId, getUUID(), getDbName(), getMode().toString());
  }

  public OBackupFinishedLog endBackup(long unitId, long opsId) {
    return new OBackupFinishedLog(unitId, opsId, getUUID(), getDbName(), getMode().toString());
  }

  // Backup
  public void doBackup(OBackupListener listener) throws IOException {

    OBackupStartedLog start = startBackup();
    logger.log(start);
    listener.onEvent(cfg, start);

    OBackupFinishedLog end;
    try {
      end = doBackup(start);
      OBackupFinishedLog finishedLog = (OBackupFinishedLog) logger.log(end);
      listener.onEvent(cfg, finishedLog);
      doUpload(listener, finishedLog);
    } catch (OInvalidInstanceIdException ex) {
      //schedule full backup
    } catch (Exception e) {
      OBackupErrorLog error = new OBackupErrorLog(start.getUnitId(), start.getTxId(), getUUID(), getDbName(), getMode().toString());
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      error.setMessage(e.getMessage());
      error.setStackTrace(sw.toString());
      logger.log(error);
      listener.onEvent(cfg, error);
      return;
    }

  }

  public void doUpload(final OBackupListener listener, OBackupFinishedLog log) {

    uploader.ifPresent((uploader) -> {
      OBackupUploadStartedLog uploadStarted = new OBackupUploadStartedLog(log.getUnitId(), log.getTxId(), getUUID(), getDbName(),
          getMode().toString());

      uploadStarted.setFileName(log.getFileName());
      uploadStarted.setPath(log.getPath());

      logger.log(uploadStarted);

      String[] fragments = log.getPath().split(File.separator);
      try {
        OUploadMetadata metadata = uploader
            .executeUpload(log.getPath() + File.separator + log.getFileName(), log.getFileName(), fragments[fragments.length - 1]);
        OBackupUploadFinishedLog finishedLog = new OBackupUploadFinishedLog(uploadStarted.getUnitId(), uploadStarted.getTxId(),
            getUUID(), getDbName(), getMode().toString());

        finishedLog.setElapsedTime(metadata.getElapsedTime());
        finishedLog.setFileSize(log.getFileSize());
        finishedLog.setFileName(log.getFileName());
        finishedLog.setMetadata(metadata.getMetadata());
        finishedLog.setUploadType(metadata.getType());
        OBackupUploadFinishedLog uploadLog = (OBackupUploadFinishedLog) logger.log(finishedLog);

        log.setUpload(uploadLog);
        logger.updateLog(log);

        listener.onEvent(cfg, uploadLog);

      } catch (Exception e) {
        OBackupUploadErrorLog error = new OBackupUploadErrorLog(log.getUnitId(), log.getTxId(), getUUID(), getDbName(),
            getMode().toString());
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        error.setMessage(e.getMessage());
        error.setStackTrace(sw.toString());
        logger.log(error);
      }

    });
  }

  public void doRestore(final OBackupListener listener, ODocument doc) {

    final String databaseName = doc.field("target");
    Long unitId = doc.field("unitId");

    ODatabaseDocument database = null;

    if (logger.getServer().existsDatabase(databaseName)) {
      throw new IllegalArgumentException("Cannot restore the backup to an existing database (" + databaseName + ").");
    }
    try {

      final OBackupFinishedLog finished = (OBackupFinishedLog) logger.findLast(OBackupLogType.BACKUP_FINISHED, getUUID(), unitId);

      validateBackup(finished);

      new Thread(() -> startRestoreBackup(logger.getServer(), finished, databaseName, listener)).start();
    } catch (IOException e) {
      OLogManager.instance().error(this, "Error finding backup log for " + getUUID(), e);
    }
  }

  private void validateBackup(OBackupFinishedLog finished) {

    if (finished.getUpload() == null) {
      File f = new File(finished.getPath());
      if (!f.exists()) {
        throw new IllegalArgumentException("Cannot restore the backup from path (" + finished.getPath() + ").");
      }
    }
  }

  private void startRestoreBackup(OServer server, OBackupFinishedLog finished, String databaseName, OBackupListener listener) {
    ORestoreStartedLog restoreStartedLog = null;
    try {

      restoreStartedLog = new ORestoreStartedLog(finished.getUnitId(), logger.nextOpId(), getUUID(), getDbName(),
          finished.getMode());
      logger.log(restoreStartedLog);

      if (finished.getUpload() != null) {
        OBackupUploadFinishedLog upload = finished.getUpload();
        ORestoreStartedLog finalRestoreStartedLog = restoreStartedLog;
        uploader.ifPresent((u) -> {
          String path = u.executeDownload(upload);
          doRestore(server, finished, path, databaseName, listener, finalRestoreStartedLog, (log) -> {
            log.setPath(path);
            log.setMetadata(upload.getMetadata());
          });
        });
      } else {
        doRestore(server, finished, finished.getPath(), databaseName, listener, restoreStartedLog, (log) -> {
          log.setPath(finished.getPath());
        });
      }

    } catch (Exception e) {

      if (restoreStartedLog != null) {
        ORestoreErrorLog error = new ORestoreErrorLog(restoreStartedLog.getUnitId(), restoreStartedLog.getTxId(), getUUID(),
            getDbName(), getMode().toString());
        error.setMessage(e.getMessage());
        logger.log(error);
        listener.onEvent(cfg, error);
      }
    } finally {

    }
  }

  private void doRestore(OServer server, OBackupFinishedLog finished, String path, String databaseName, OBackupListener listener,
      ORestoreStartedLog restoreStartedLog, Consumer<ORestoreFinishedLog> consumer) {
    server.restore(databaseName, path);
    ORestoreFinishedLog finishedLog = new ORestoreFinishedLog(restoreStartedLog.getUnitId(), restoreStartedLog.getTxId(), getUUID(),
        getDbName(), restoreStartedLog.getMode());

    finishedLog.setElapsedTime(finishedLog.getTimestamp().getTime() - restoreStartedLog.getTimestamp().getTime());
    finishedLog.setTargetDB(databaseName);
    finishedLog.setRestoreUnitId(finished.getUnitId());
    consumer.accept(finishedLog);

    logger.log(finishedLog);

    listener.onEvent(cfg, finishedLog);
  }

  protected OBackupFinishedLog doBackup(OBackupStartedLog start) {

    ODatabaseDocument db = null;
    try {
      db = getDatabase();
      db.activateOnCurrentThread();
      String path = calculatePath(db);
      String fName = db.incrementalBackup(path);
      OBackupFinishedLog end = endBackup(start.getUnitId(), start.getTxId());
      end.setFileName(fName);
      end.setPath(path);
      end.setFileSize(calculateFileSize(path + File.separator + fName));
      end.setElapsedTime(end.getTimestamp().getTime() - start.getTimestamp().getTime());
      return end;
    } finally {
      if (db != null) {
        db.close();
      }
    }
  }

  protected long calculateFileSize(String path) {
    File f = new File(path);
    return f.length();
  }

  public abstract OAutomaticBackup.MODE getMode();

  protected abstract String calculatePath(ODatabaseDocument db);

  public abstract Date scheduleNextExecution(OBackupListener listener);

  public OBackupLogger getLogger() {
    return logger;
  }

  protected ODatabaseDocument getDatabase() {

    String dbName = cfg.field(OBackupConfig.DBNAME);

    OServer server = logger.getServer();

    ODatabaseDocumentInternal db = server.getDatabases().openNoAuthenticate(dbName, null);

    return db;
  }

  public String getUUID() {
    return cfg.field(OBackupConfig.ID);
  }

  public String getDbName() {
    return cfg.field(OBackupConfig.DBNAME);
  }

  public Boolean isEnabled() {
    return cfg.field(OBackupConfig.ENABLED);
  }

  public Integer getRetentionDays() {
    return cfg.field(OBackupConfig.RETENTION_DAYS);
  }

  protected OBackupScheduledLog lastUnfiredSchedule() {
    OBackupLog lastSchedule = null;
    try {
      lastSchedule = logger.findLast(OBackupLogType.BACKUP_SCHEDULED, getUUID());
      if (lastSchedule != null) {
        OBackupLog lastBackup = logger.findLast(OBackupLogType.BACKUP_FINISHED, getUUID());
        if (lastBackup != null && lastBackup.getTxId() == lastSchedule.getTxId()) {
          lastSchedule = null;
        }
      }
    } catch (Exception e) {
      OLogManager.instance().error(this, "Error finding last unfired schedule for UUID : " + getUUID(), e);
    }
    return (OBackupScheduledLog) lastSchedule;
  }

  public void doDeleteBackup(OBackupTask oBackupTask, Long unitId, Long tx) {
    try {
      logger.deleteByUUIDAndUnitIdAndTx(getUUID(), unitId, tx);
    } catch (IOException e) {
      OLogManager.instance().error(this, "Error deleting backups for UUID : " + getUUID(), e);
    }
  }

  public void retainLogs() {

    Integer retentionDays = getRetentionDays();
    if (retentionDays != null && retentionDays > 0) {
      retainLogs(retentionDays);
    }
  }

  public void retainLogs(int retentionDays) {

    Calendar c = Calendar.getInstance();
    c.setTime(new Date());
    c.add(Calendar.DATE, (-1) * retentionDays);

    Long time = c.getTime().getTime();
    try {
      logger.deleteByUUIDAndTimestamp(getUUID(), time);
    } catch (IOException e) {
      OLogManager.instance().error(this, "Error deleting backups for UUID : " + getUUID(), e);
    }
  }

  public ODocument getCfg() {
    return cfg;
  }

  public void deleteLastScheduled() {

    OBackupScheduledLog scheduled = lastUnfiredSchedule();
    if (scheduled != null) {
      logger.deleteLog(scheduled);
    }
  }

  @Override
  public boolean equals(Object obj) {
    return this.getClass().isInstance(obj);
  }

  public void markLastBackup() {

    try {
      OBackupFinishedLog lastCompleted = (OBackupFinishedLog) logger.findLast(OBackupLogType.BACKUP_FINISHED, getUUID());

      if (lastCompleted != null) {
        lastCompleted.setPrevChange(true);
        logger.updateLog(lastCompleted);
      }

    } catch (IOException e) {
      OLogManager.instance().error(this, "Error updating lob backups for UUID : " + getUUID(), e);
    }

  }

  public ODocument mergeSecret(ODocument newCfg, ODocument oldCfg) {

    if (uploader.isPresent()) {
      return uploader.get().mergeSecret(newCfg, oldCfg);
    }
    return newCfg;
  }

}
