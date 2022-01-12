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
import com.orientechnologies.agent.backup.log.*;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.db.ODatabaseInternal;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.schedule.OCronExpression;
import com.orientechnologies.orient.core.storage.impl.local.paginated.OEnterpriseLocalPaginatedStorage;
import com.orientechnologies.orient.server.handler.OAutomaticBackup;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

/**
 * Created by Enrico Risa on 25/03/16.
 */
public class OBackupStrategyIncrementalBackup extends OBackupStrategy {

  public OBackupStrategyIncrementalBackup(ODocument cfg, OBackupLogger logger) {
    super(cfg, logger);
  }

  @Override
  public OAutomaticBackup.MODE getMode() {
    return OAutomaticBackup.MODE.INCREMENTAL_BACKUP;
  }

  protected String calculatePath(ODatabaseDocument db) {

    OBackupFinishedLog last = null;
    try {
      last = (OBackupFinishedLog) logger.findLast(OBackupLogType.BACKUP_FINISHED, getUUID());
    } catch (Exception e) {
      e.printStackTrace();
    }

    if (last != null && !Boolean.TRUE.equals(last.getPrevChange())) {
      String path = last.getPath();

      try {
        if(((OEnterpriseLocalPaginatedStorage) ((ODatabaseInternal) db).getStorage().getUnderlying()).isLastBackupCompatibleWithUUID(new File(path))){
          return last.getPath();
        }
      } catch (IOException e) {
        OLogManager.instance().warn(this, "Error checking backup compatibility", e);
        return last.getPath();
      }
    }

    long begin = System.currentTimeMillis();
    try {
      OBackupLog lastScheduled = logger.findLast(OBackupLogType.BACKUP_SCHEDULED, getUUID());
      if (last != null) {
        begin = lastScheduled.getUnitId();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    String basePath = cfg.field(OBackupConfig.DIRECTORY);
    String dbName = cfg.field(OBackupConfig.DBNAME);
    return basePath + File.separator + dbName + "-incremental-" + begin;

  }

  @Override
  public Date scheduleNextExecution(OBackupListener listener) {

    OBackupScheduledLog last = lastUnfiredSchedule();

    if (last == null) {
      ODocument full = (ODocument) cfg.eval(OBackupConfig.MODES + "." + OAutomaticBackup.MODE.INCREMENTAL_BACKUP);
      String when = full.field(OBackupConfig.WHEN);
      try {
        OCronExpression expression = new OCronExpression(when);
        Date nextExecution = expression.getNextValidTimeAfter(new Date());
        Long unitId = logger.nextOpId();
        try {
          OBackupFinishedLog lastCompleted = (OBackupFinishedLog) logger.findLast(OBackupLogType.BACKUP_FINISHED, getUUID());
          if (lastCompleted != null && !Boolean.TRUE.equals(lastCompleted.getPrevChange())) {
            unitId = lastCompleted.getUnitId();
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        OBackupScheduledLog log = new OBackupScheduledLog(unitId, logger.nextOpId(), getUUID(), getDbName(), getMode().toString());
        log.nextExecution = nextExecution.getTime();
        getLogger().log(log);
        listener.onEvent(cfg, log);
        return nextExecution;
      } catch (ParseException e) {
        e.printStackTrace();
      }

    } else {
      return new Date(last.nextExecution);
    }

    return null;
  }
}
