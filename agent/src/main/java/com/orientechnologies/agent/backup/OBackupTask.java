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

package com.orientechnologies.agent.backup;

import com.orientechnologies.agent.backup.log.OBackupLog;
import com.orientechnologies.agent.backup.log.OBackupLogType;
import com.orientechnologies.agent.backup.strategy.OBackupStrategy;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.record.impl.ODocument;

import java.io.IOException;
import java.util.Date;
import java.util.TimerTask;

/**
 * Created by Enrico Risa on 25/03/16.
 */
public class OBackupTask implements OBackupListener {

  private OBackupStrategy strategy;
  private TimerTask       task;
  private OBackupListener listener;

  public OBackupTask(OBackupStrategy strategy) {
    this.strategy = strategy;
    schedule();
  }

  protected void schedule() {

    if (strategy.isEnabled()) {
      Date nextExecution = strategy.scheduleNextExecution(this);

      task = Orient.instance().scheduleTask(() -> {
        try {
          strategy.doBackup(OBackupTask.this);
        } catch (IOException e) {
          e.printStackTrace();
        }

      }, nextExecution, 0);

      OLogManager.instance().info(this,
          "Scheduled [" + strategy.getMode() + "] task : " + strategy.getUUID() + ". Next execution will be " + nextExecution);

      strategy.retainLogs();
    }
  }

  public ODocument mergeSecret(ODocument newCfg, ODocument oldCfg) {
    return strategy.mergeSecret(newCfg, oldCfg);
  }

  public OBackupStrategy getStrategy() {
    return strategy;
  }

  public void changeConfig(OBackupConfig config, ODocument doc) {
    if (task != null) {
      task.cancel();
    }
    strategy.deleteLastScheduled();

    OBackupStrategy strategy = config.strategy(doc, this.strategy.getLogger());

    if (!this.strategy.equals(strategy)) {
      strategy.markLastBackup();
    }

    this.strategy = strategy;

    schedule();
  }

  @Override
  public Boolean onEvent(ODocument cfg, OBackupLog log) {

    Boolean canContinue = invokeListener(cfg, log);
    if (OBackupLogType.BACKUP_FINISHED.equals(log.getType())) {
      if (canContinue) {
        schedule();
      }
    }
    return true;
  }

  private Boolean invokeListener(ODocument cfg, OBackupLog log) {
    if (listener != null) {
      try {
        return listener.onEvent(cfg, log);
      } catch (Exception e) {
        OLogManager.instance().info(this, "Error invoking listener on event  [" + log.getType() + "] ");
      }
    }
    return true;
  }

  public void stop() {
    if (task != null) {
      task.cancel();
      OLogManager.instance().info(this, "Cancelled schedule backup on database  [" + strategy.getDbName() + "] ");
    }
  }

  public void registerListener(OBackupListener listener) {
    this.listener = listener;
  }

  public void restore(ODocument doc) {
    strategy.doRestore(this, doc);
  }

  public void deleteBackup(Long unitId, Long timestamp) {
    strategy.doDeleteBackup(this, unitId, timestamp);
  }
}