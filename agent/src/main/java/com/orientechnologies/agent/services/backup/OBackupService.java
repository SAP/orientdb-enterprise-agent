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

package com.orientechnologies.agent.services.backup;

import com.orientechnologies.agent.http.command.OServerCommandBackupManager;
import com.orientechnologies.agent.services.OEnterpriseService;
import com.orientechnologies.agent.services.backup.log.OBackupDBLogger;
import com.orientechnologies.agent.services.backup.log.OBackupLog;
import com.orientechnologies.agent.services.backup.log.OBackupLogger;
import com.orientechnologies.agent.services.backup.strategy.OBackupStrategy;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.enterprise.server.OEnterpriseServer;
import com.orientechnologies.orient.core.record.impl.ODocument;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Enrico Risa on 22/03/16.
 */
public class OBackupService implements OEnterpriseService {

  private   OEnterpriseServer        server;
  private   OBackupConfig            config;
  private   OBackupLogger            logger;
  protected Map<String, OBackupTask> tasks = new ConcurrentHashMap<String, OBackupTask>();

  private void initTasks() {
    Collection<ODocument> backups = config.backups();
    for (ODocument backup : backups) {
      OBackupStrategy strategy = config.strategy(backup, logger);
      tasks.put((String) backup.field(OBackupConfig.ID), new OBackupTask(strategy));
    }
  }

  private void initLogger() {
    if (server.getSystemDatabase().exists()) {
      logger = new OBackupDBLogger(server);
    } else {

      throw new UnsupportedOperationException("Cannot use Enterprise incremental backup without a system database");
    }
  }

  public OBackupService() {

  }

  public ODocument getConfiguration() {
    return config.getConfig();
  }

  public ODocument addBackup(ODocument doc) {
    ODocument backup = config.addBackup(doc);
    OBackupStrategy strategy = config.strategy(backup, logger);
    tasks.put((String) doc.field(OBackupConfig.ID), new OBackupTask(strategy));
    return backup;
  }

  public void restoreBackup(String uuid, ODocument doc) {

    OBackupTask oBackupTask = tasks.get(uuid);

    oBackupTask.restore(doc);

  }

  public OBackupTask getTask(String uuid) {
    return tasks.get(uuid);
  }

  public void changeBackup(String uuid, ODocument doc) {

    OBackupTask oBackupTask = tasks.get(uuid);
    config.changeBackup(uuid, doc, oBackupTask::mergeSecret);
    oBackupTask.changeConfig(config, doc);
  }

  public void removeBackup(String uuid) {
    config.removeBackup(uuid);
  }

  public void removeAndStopBackup(String uuid) {
    removeBackup(uuid);
    OBackupTask task = tasks.get(uuid);
    task.stop();

  }

  public ODocument logs(String uuid, int page, int pageSize, Map<String, String> params) {
    ODocument history = new ODocument();
    try {

      List<OBackupLog> byUUID = logger.findByUUID(uuid, page, pageSize, params);
      List<ODocument> docs = new ArrayList<ODocument>();
      for (OBackupLog oBackupLog : byUUID) {
        docs.add(oBackupLog.toDoc());
      }
      history.field("logs", docs);
    } catch (IOException e) {
      OLogManager.instance().error(this, "Error " + e.getMessage(), e);
    }
    return history;
  }

  public List<OBackupLog> findLogs(String uuid, int page, int pageSize, Map<String, String> params) {
    try {
      return logger.findByUUID(uuid, page, pageSize, params);
    } catch (IOException e) {
      OLogManager.instance().error(this, "Cannot find logs", e);
      return Collections.emptyList();
    }
  }

  public List<OBackupLog> findLogs(String uuid, Long unitId, int page, int pageSize, Map<String, String> params) {
    try {
      return logger.findByUUIDAndUnitId(uuid, unitId, page, pageSize, params);
    } catch (IOException e) {
      OLogManager.instance().error(this, "Cannot find logs", e);
      return Collections.emptyList();
    }
  }

  public ODocument logs(String uuid, Long unitId, int page, int pageSize, Map<String, String> params) {
    ODocument history = new ODocument();
    try {
      List<OBackupLog> byUUID = logger.findByUUIDAndUnitId(uuid, unitId, page, pageSize, params);
      List<ODocument> docs = new ArrayList<ODocument>();
      for (OBackupLog oBackupLog : byUUID) {
        docs.add(oBackupLog.toDoc());
      }
      history.field("logs", docs);
    } catch (IOException e) {
      OLogManager.instance().error(this, "Error " + e.getMessage(), e);
    }
    return history;
  }

  public void deleteBackup(String uuid, Long unitId, Long timestamp) {
    OBackupTask oBackupTask = tasks.get(uuid);

    oBackupTask.deleteBackup(unitId, timestamp);
  }

  @Override
  public void init(OEnterpriseServer server) {
    this.server = server;
  }

  @Override
  public void start() {
    this.config = new OBackupConfig().load();
    initLogger();
    initTasks();
    server.registerStatelessCommand(new OServerCommandBackupManager(this, server));
  }

  @Override
  public void stop() {
    tasks.values().stream().forEach((t) -> t.stop());
    server.unregisterStatelessCommand(OServerCommandBackupManager.class);
  }
}