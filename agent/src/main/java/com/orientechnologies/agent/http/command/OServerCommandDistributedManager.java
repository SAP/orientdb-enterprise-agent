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
package com.orientechnologies.agent.http.command;

import com.orientechnologies.agent.EnterprisePermissions;
import com.orientechnologies.common.collection.OMultiValue;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.enterprise.server.OEnterpriseServer;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.exception.OCommandExecutionException;
import com.orientechnologies.orient.core.exception.OConfigurationException;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.storage.OStorage;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.OSystemDatabase;
import com.orientechnologies.orient.server.distributed.*;
import com.orientechnologies.orient.server.distributed.impl.ODistributedStorage;
import com.orientechnologies.orient.server.distributed.impl.task.OEnterpriseStatsTask;
import com.orientechnologies.orient.server.hazelcast.OHazelcastPlugin;
import com.orientechnologies.orient.server.network.OServerNetworkListener;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.orientechnologies.orient.server.network.protocol.http.OHttpResponse;
import com.orientechnologies.orient.server.network.protocol.http.OHttpUtils;
import java.io.IOException;
import java.util.*;

public class OServerCommandDistributedManager extends OServerCommandDistributedScope {

  private static final String[] NAMES = {
    "GET|distributed/*", "PUT|distributed/*", "POST|distributed/*"
  };

  public OServerCommandDistributedManager(OEnterpriseServer server) {
    super(EnterprisePermissions.SERVER_DISTRIBUTED.toString(), server);
  }

  @Override
  public boolean execute(final OHttpRequest iRequest, OHttpResponse iResponse) throws Exception {
    iRequest.getData().commandInfo = "Distributed information";
    return super.execute(iRequest, iResponse);
  }

  private void doPut(
      final OHttpRequest iRequest, final OHttpResponse iResponse, final String[] parts)
      throws IOException {

    final String command = parts[1];
    final String id = parts.length > 2 ? parts[2] : null;

    if (command.equalsIgnoreCase("database")) {

      String jsonContent = iRequest.getContent();

      changeConfig(server, id, jsonContent);

      iResponse.send(OHttpUtils.STATUS_OK_CODE, null, null, OHttpUtils.STATUS_OK_DESCRIPTION, null);
    }
  }

  @Override
  protected void doPost(final OHttpRequest iRequest, final OHttpResponse iResponse)
      throws IOException {
    final String[] parts =
        checkSyntax(iRequest.getUrl(), 2, "Syntax error: distributed/<command>/[<id>]");
    doPost(iRequest, iResponse, parts);
  }

  @Override
  protected void doPut(final OHttpRequest iRequest, final OHttpResponse iResponse)
      throws IOException {
    final String[] parts =
        checkSyntax(iRequest.getUrl(), 2, "Syntax error: distributed/<command>/[<id>]");
    doPut(iRequest, iResponse, parts);
  }

  @Override
  protected void doGet(final OHttpRequest iRequest, final OHttpResponse iResponse)
      throws IOException {
    final String[] parts =
        checkSyntax(iRequest.getUrl(), 2, "Syntax error: distributed/<command>/[<id>]");
    doGet(iRequest, iResponse, parts);
  }

  protected void doPost(
      final OHttpRequest iRequest, final OHttpResponse iResponse, final String[] parts)
      throws IOException {

    final String command = parts[1];

    if (command.equalsIgnoreCase("stop")) {
      if (parts.length < 2)
        throw new IllegalArgumentException("Cannot stop the server: missing server name to stop");

      if (server.getDistributedManager() == null)
        throw new OConfigurationException(
            "Cannot stop the server: local server is not distributed");

      server.getDistributedManager().removeServer(parts[2], false);

      iResponse.send(OHttpUtils.STATUS_OK_CODE, null, null, OHttpUtils.STATUS_OK_DESCRIPTION, null);

    } else if (command.equalsIgnoreCase("restart")) {
      if (parts.length < 2)
        throw new IllegalArgumentException(
            "Cannot restart the server: missing server name to restart");

      if (server.getDistributedManager() == null)
        throw new OConfigurationException(
            "Cannot restart the server: local server is not distributed");

      final OHazelcastPlugin dManager = ((OHazelcastPlugin) server.getDistributedManager());
      dManager.restartNode(parts[2]);

      iResponse.send(OHttpUtils.STATUS_OK_CODE, null, null, OHttpUtils.STATUS_OK_DESCRIPTION, null);
    } else if (command.equalsIgnoreCase("syncCluster")) {
      synchCluster(iResponse, parts);
    } else if (command.equalsIgnoreCase("syncDatabase")) {
      syncDatabase(iResponse, parts);
    } else {
      throw new IllegalArgumentException(String.format("Command %s not supported", command));
    }
  }

  private void synchCluster(final OHttpResponse iResponse, final String[] parts)
      throws IOException {
    if (parts.length < 3)
      throw new IllegalArgumentException("Cannot sync cluster: missing database or cluster name ");

    if (server.getDistributedManager() == null)
      throw new OConfigurationException("Cannot sync cluster: local server is not distributed");

    final String database = parts[2];
    final String cluster = parts[3];

    ODatabaseDocumentInternal db = server.openDatabase(database, "", "", null, true);
    try {
      Object result =
          db.command(new OCommandSQL(String.format("ha sync cluster  %s ", cluster))).execute();
      final ODocument document = new ODocument().field("result", result);
      iResponse.send(
          OHttpUtils.STATUS_OK_CODE, "OK", OHttpUtils.CONTENT_JSON, document.toJSON(""), null);
    } finally {
      db.close();
    }
  }

  private void syncDatabase(final OHttpResponse iResponse, final String[] parts)
      throws IOException {
    if (parts.length < 3)
      throw new IllegalArgumentException("Cannot sync database: missing database name");

    if (server.getDistributedManager() == null)
      throw new OConfigurationException("Cannot sync database: local server is not distributed");

    final String database = parts[2];

    ODatabaseDocumentInternal db = server.openDatabase(database, "", "", null, true);
    try {
      final OStorage stg = db.getStorage();
      if (!(stg instanceof ODistributedStorage))
        throw new ODistributedException(
            "SYNC DATABASE command cannot be executed against a non distributed server");

      final ODistributedStorage dStg = (ODistributedStorage) stg;

      final OHazelcastPlugin dManager = (OHazelcastPlugin) dStg.getDistributedManager();
      if (dManager == null || !dManager.isEnabled())
        throw new OCommandExecutionException("OrientDB is not started in distributed mode");

      boolean installDatabase =
          dManager.installDatabase(
              true,
              database,
              false,
              OGlobalConfiguration.DISTRIBUTED_BACKUP_TRY_INCREMENTAL_FIRST.getValueAsBoolean());

      ODocument document = new ODocument().field("result", installDatabase);
      iResponse.send(
          OHttpUtils.STATUS_OK_CODE, "OK", OHttpUtils.CONTENT_JSON, document.toJSON(""), null);
    } finally {
      db.close();
    }
  }

  public void changeConfig(final OServer server, final String database, final String jsonContent) {
    final OHazelcastPlugin manager = (OHazelcastPlugin) server.getDistributedManager();

    final OModifiableDistributedConfiguration databaseConfiguration =
        manager.getDatabaseConfiguration(database).modify();
    final ODocument cfg = databaseConfiguration.getDocument().fromJSON(jsonContent, "noMap");
    cfg.field("version", (Integer) cfg.field("version") + 1);

    OModifiableDistributedConfiguration config = new OModifiableDistributedConfiguration(cfg);
    manager.updateCachedDatabaseConfiguration(database, config, true);
  }

  private void doGet(
      final OHttpRequest iRequest, final OHttpResponse iResponse, final String[] parts)
      throws IOException {
    final ODistributedServerManager manager = server.getDistributedManager();

    final String command = parts[1];
    final String id = parts.length > 2 ? parts[2] : null;

    final ODocument doc;

    // NODE CONFIG
    if (command.equalsIgnoreCase("node")) {

      doc = doGetNodeConfig(manager);

    } else if (command.equalsIgnoreCase("database")) {

      doc = doGetDatabaseInfo(server, id);

    } else if (command.equalsIgnoreCase("stats")) {

      if (id != null) {

        doc = singleNodeStats(manager, id);

      } else {
        if (manager != null) {
          doc = getClusterConfig(manager);
        } else {
          throw new OConfigurationException(
              "Seems that the server is not running in distributed mode");
        }
      }

    } else {
      throw new IllegalArgumentException("Command '" + command + "' not supported");
    }
    iResponse.send(OHttpUtils.STATUS_OK_CODE, "OK", OHttpUtils.CONTENT_JSON, doc.toJSON(""), null);
  }

  private ODocument singleNodeStats(final ODistributedServerManager manager, final String id) {
    final ODocument doc;

    if (manager != null) {
      final ODistributedResponse dResponse =
          manager.sendRequest(
              OSystemDatabase.SYSTEM_DB_NAME,
              null,
              OMultiValue.getSingletonList(id),
              new OEnterpriseStatsTask(),
              manager.getNextMessageIdCounter(),
              ODistributedRequest.EXECUTION_MODE.RESPONSE,
              null,
              null,
              null);
      final Object payload = dResponse.getPayload();

      if (payload != null && payload instanceof Map) {
        doc = (ODocument) ((Map<String, Object>) payload).get(id);
        doc.field("member", getMemberConfig(manager.getClusterConfiguration(), id));
      } else doc = new ODocument();

    } else {
      doc = new ODocument().fromJSON(Orient.instance().getProfiler().toJSON("realtime", null));
    }

    return doc;
  }

  public ODocument getClusterConfig(final ODistributedServerManager manager) {
    final ODocument doc = manager.getClusterConfiguration();

    final Collection<ODocument> documents = doc.field("members");
    List<String> servers = new ArrayList<String>(documents.size());
    for (ODocument document : documents) servers.add((String) document.field("name"));

    Set<String> databases = manager.getServerInstance().listDatabases();
    if (databases.isEmpty()) {
      OLogManager.instance().warn(this, "Cannot load stats, no databases on this server");
      return doc;
    }

    final ODistributedResponse dResponse =
        manager.sendRequest(
            databases.iterator().next(),
            null,
            servers,
            new OEnterpriseStatsTask(),
            manager.getNextMessageIdCounter(),
            ODistributedRequest.EXECUTION_MODE.RESPONSE,
            null,
            null,
            null);
    final Object payload = dResponse.getPayload();

    if (payload != null && payload instanceof Map) {
      doc.field("clusterStats", payload);
    }

    doc.field("databasesStatus", calculateDBStatus(manager, doc));
    return doc;
  }

  private ODocument calculateDBStatus(
      final ODistributedServerManager manager, final ODocument cfg) {

    final ODocument doc = new ODocument();
    final Collection<ODocument> members = cfg.field("members");

    Set<String> databases = new HashSet<String>();
    for (ODocument m : members) {
      final Collection<String> dbs = m.field("databases");
      for (String db : dbs) {
        databases.add(db);
      }
    }
    for (String database : databases) {
      doc.field(database, singleDBStatus(manager, database));
    }
    return doc;
  }

  private ODocument singleDBStatus(ODistributedServerManager manager, String database) {
    final ODocument entries = new ODocument();
    final ODistributedConfiguration dbCfg = manager.getDatabaseConfiguration(database, false);
    final Set<String> servers = dbCfg.getAllConfiguredServers();
    for (String serverName : servers) {
      final ODistributedServerManager.DB_STATUS databaseStatus =
          manager.getDatabaseStatus(serverName, database);
      entries.field(serverName, databaseStatus.toString());
    }
    return entries;
  }

  public ODocument doGetDatabaseInfo(final OServer server, final String id) {
    final ODistributedConfiguration cfg =
        server.getDistributedManager().getDatabaseConfiguration(id);
    return cfg.getDocument();
  }

  public ODocument doGetNodeConfig(final ODistributedServerManager manager) {
    ODocument doc;
    if (manager != null) {
      doc = manager.getClusterConfiguration();

      final Collection<ODocument> documents = doc.field("members");
      List<String> servers = new ArrayList<String>(documents.size());
      for (ODocument document : documents) servers.add((String) document.field("name"));

      final ODistributedResponse dResponse =
          manager.sendRequest(
              OSystemDatabase.SYSTEM_DB_NAME,
              null,
              servers,
              new OEnterpriseStatsTask(),
              manager.getNextMessageIdCounter(),
              ODistributedRequest.EXECUTION_MODE.RESPONSE,
              null,
              null,
              null);
      final Object payload = dResponse.getPayload();

      if (payload != null && payload instanceof Map) {
        for (ODocument document : documents) {
          final String serverName = (String) document.field("name");
          final ODocument dStat = (ODocument) ((Map<String, Object>) payload).get(serverName);
          addConfiguration("realtime.sizes", document, dStat);
          addConfiguration("realtime.texts", document, dStat);
        }
      }

    } else {
      doc = new ODocument();

      final ODocument member = new ODocument();

      member.field("name", "orientdb");
      member.field("status", "ONLINE");

      final List<Map<String, Object>> listeners = new ArrayList<Map<String, Object>>();

      member.field("listeners", listeners, OType.EMBEDDEDLIST);

      final String realtime = Orient.instance().getProfiler().toJSON("realtime", "system.config.");
      ODocument cfg = new ODocument().fromJSON(realtime);

      addConfiguration("realtime.sizes", member, cfg);
      addConfiguration("realtime.texts", member, cfg);

      for (OServerNetworkListener listener : server.getNetworkListeners()) {
        final Map<String, Object> listenerCfg = new HashMap<String, Object>();
        listeners.add(listenerCfg);

        listenerCfg.put("protocol", listener.getProtocolType().getSimpleName());
        listenerCfg.put("listen", listener.getListeningAddress(true));
      }
      member.field("databases", server.getAvailableStorageNames().keySet());
      doc.field(
          "members",
          new ArrayList<ODocument>() {
            {
              add(member);
            }
          });
    }
    return doc;
  }

  private void addConfiguration(final String path, final ODocument member, final ODocument cfg) {
    final Map<String, Object> eval = (Map) cfg.eval(path);

    ODocument configuration = member.field("configuration");

    if (configuration == null) {
      configuration = new ODocument();
      member.field("configuration", configuration);
    }
    if (eval != null) {
      for (String key : eval.keySet()) {
        if (key.startsWith("system.config.")) {
          configuration.field(key.replace("system.config.", "").replace(".", "_"), eval.get(key));
        }
      }
    }
  }

  private ODocument getMemberConfig(final ODocument doc, final String node) {

    final Collection<ODocument> documents = doc.field("members");

    ODocument member = null;
    for (ODocument document : documents) {
      final String name = document.field("name");
      if (name.equalsIgnoreCase(node)) {
        member = document;
        break;
      }
    }
    return member;
  }

  @Override
  public String[] getNames() {
    return NAMES;
  }
}
