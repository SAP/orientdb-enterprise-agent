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

import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.exception.OCommandExecutionException;
import com.orientechnologies.orient.core.exception.OConfigurationException;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.storage.OStorage;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.distributed.ODistributedConfiguration;
import com.orientechnologies.orient.server.distributed.ODistributedException;
import com.orientechnologies.orient.server.distributed.ODistributedServerManager;
import com.orientechnologies.orient.server.distributed.impl.ODistributedAbstractPlugin;
import com.orientechnologies.orient.server.distributed.impl.ODistributedStorage;
import com.orientechnologies.orient.server.distributed.sql.OCommandExecutorSQLHASyncCluster;
import com.orientechnologies.orient.server.hazelcast.OHazelcastPlugin;
import com.orientechnologies.orient.server.network.OServerNetworkListener;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.orientechnologies.orient.server.network.protocol.http.OHttpResponse;
import com.orientechnologies.orient.server.network.protocol.http.OHttpUtils;

import java.io.IOException;
import java.util.*;

public class OServerCommandDistributedManager extends OServerCommandDistributedScope {

  private static final String[] NAMES = { "GET|distributed/*", "PUT|distributed/*", "POST|distributed/*" };

  public OServerCommandDistributedManager() {
    super("server.profiler");
  }

  @Override
  public boolean execute(final OHttpRequest iRequest, OHttpResponse iResponse) throws Exception {
    iRequest.data.commandInfo = "Distributed information";
    return super.execute(iRequest, iResponse);
  }

  private void doPut(final OHttpRequest iRequest, final OHttpResponse iResponse, final String[] parts) throws IOException {

    final String command = parts[1];
    final String id = parts.length > 2 ? parts[2] : null;

    if (command.equalsIgnoreCase("database")) {

      String jsonContent = iRequest.content;

      changeConfig(server, id, jsonContent);

      iResponse.send(OHttpUtils.STATUS_OK_CODE, null, null, OHttpUtils.STATUS_OK_DESCRIPTION, null);

    }
  }

  @Override
  protected void doPost(OHttpRequest iRequest, OHttpResponse iResponse) throws IOException {
    final String[] parts = checkSyntax(iRequest.getUrl(), 2, "Syntax error: distributed/<command>/[<id>]");
    doPost(iRequest, iResponse, parts);
  }

  @Override
  protected void doPut(OHttpRequest iRequest, OHttpResponse iResponse) throws IOException {
    final String[] parts = checkSyntax(iRequest.getUrl(), 2, "Syntax error: distributed/<command>/[<id>]");
    doPut(iRequest, iResponse, parts);
  }

  @Override
  protected void doGet(OHttpRequest iRequest, OHttpResponse iResponse) throws IOException {
    final String[] parts = checkSyntax(iRequest.getUrl(), 2, "Syntax error: distributed/<command>/[<id>]");
    doGet(iRequest, iResponse, parts);
  }

  protected void doPost(final OHttpRequest iRequest, final OHttpResponse iResponse, final String[] parts) throws IOException {

    final String command = parts[1];

    if (command.equalsIgnoreCase("stop")) {
      if (parts.length < 2)
        throw new IllegalArgumentException("Cannot stop the server: missing server name to stop");

      if (server.getDistributedManager() == null)
        throw new OConfigurationException("Cannot stop the server: local server is not distributed");

      server.getDistributedManager().removeServer(parts[2]);

      iResponse.send(OHttpUtils.STATUS_OK_CODE, null, null, OHttpUtils.STATUS_OK_DESCRIPTION, null);

    } else if (command.equalsIgnoreCase("restart")) {
      if (parts.length < 2)
        throw new IllegalArgumentException("Cannot restart the server: missing server name to restart");

      if (server.getDistributedManager() == null)
        throw new OConfigurationException("Cannot restart the server: local server is not distributed");

      final OHazelcastPlugin dManager = ((OHazelcastPlugin) server.getDistributedManager());
      dManager.restartNode(parts[2]);

      iResponse.send(OHttpUtils.STATUS_OK_CODE, null, null, OHttpUtils.STATUS_OK_DESCRIPTION, null);
    } else if (command.equalsIgnoreCase("syncCluster")) {

      if (parts.length < 3)
        throw new IllegalArgumentException("Cannot sync cluster: missing database or cluster name ");

      if (server.getDistributedManager() == null)
        throw new OConfigurationException("Cannot sync cluster: local server is not distributed");

      String database = parts[2];
      String cluster = parts[3];

      // Ugly workaround

      ODatabaseDocumentTx db = server.openDatabase(database, "", "", null, true);
      ODatabaseRecordThreadLocal.INSTANCE.set(db);

      Object result = OCommandExecutorSQLHASyncCluster.replaceCluster((ODistributedAbstractPlugin) server.getDistributedManager(),
          server, database, cluster);
      ODocument document = new ODocument().field("result", result);
      iResponse.send(OHttpUtils.STATUS_OK_CODE, "OK", OHttpUtils.CONTENT_JSON, document.toJSON(""), null);
    } else if (command.equalsIgnoreCase("syncDatabase")) {

      if (parts.length < 3)
        throw new IllegalArgumentException("Cannot sync database: missing database name");

      if (server.getDistributedManager() == null)
        throw new OConfigurationException("Cannot sync database: local server is not distributed");

      String database = parts[2];

      ODatabaseDocumentTx db = server.openDatabase(database, "", "", null, true);

      final OStorage stg = db.getStorage();
      if (!(stg instanceof ODistributedStorage))
        throw new ODistributedException("SYNC DATABASE command cannot be executed against a non distributed server");

      final ODistributedStorage dStg = (ODistributedStorage) stg;

      final OHazelcastPlugin dManager = (OHazelcastPlugin) dStg.getDistributedManager();
      if (dManager == null || !dManager.isEnabled())
        throw new OCommandExecutionException("OrientDB is not started in distributed mode");

      boolean installDatabase = dManager.installDatabase(true, database, dStg.getDistributedConfiguration().getDocument(), false,
          true);

      ODocument document = new ODocument().field("result", installDatabase);
      iResponse.send(OHttpUtils.STATUS_OK_CODE, "OK", OHttpUtils.CONTENT_JSON, document.toJSON(""), null);
    }

  }

  public void changeConfig(OServer server, String database, final String jsonContent) {
    final OHazelcastPlugin manager = (OHazelcastPlugin) server.getDistributedManager();
    ODistributedConfiguration databaseConfiguration = manager.getDatabaseConfiguration(database);
    ODocument cfg = databaseConfiguration.getDocument().fromJSON(jsonContent, "noMap");
    cfg.field("version", (Integer) cfg.field("version") + 1);
    manager.updateCachedDatabaseConfiguration(database, cfg, true, true);
  }

  private void doGet(OHttpRequest iRequest, OHttpResponse iResponse, String[] parts) throws IOException {
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

        if (manager != null) {
          ODocument clusterStats = (ODocument) manager.getConfigurationMap().get("clusterStats");
          if (clusterStats == null) {
            iResponse.send(OHttpUtils.STATUS_OK_NOCONTENT_CODE, "OK", OHttpUtils.CONTENT_JSON, "{}", null);
            return;
          }
          doc = new ODocument().fromMap(clusterStats.<Map<String, Object>> field(id));
          doc.field("member", getMemberConfig(manager.getClusterConfiguration(), id));
        } else {
          doc = new ODocument().fromJSON(Orient.instance().getProfiler().toJSON("realtime", null));
        }
      } else {
        if (manager != null) {
          doc = manager.getClusterConfiguration();
          doc.field("clusterStats", manager.getConfigurationMap().get("clusterStats"));
        } else {
          throw new OConfigurationException("Seems that the server is not running in distributed mode");
        }

      }

    } else {
      throw new IllegalArgumentException("Command '" + command + "' not supported");
    }
    iResponse.send(OHttpUtils.STATUS_OK_CODE, "OK", OHttpUtils.CONTENT_JSON, doc.toJSON(""), null);
  }

  public ODocument doGetDatabaseInfo(OServer server, String id) {
    ODocument doc;
    ODistributedConfiguration cfg = server.getDistributedManager().getDatabaseConfiguration(id);
    doc = cfg.getDocument();
    return doc;
  }

  private ODocument doGetNodeConfig(ODistributedServerManager manager) {
    ODocument doc;
    if (manager != null) {
      doc = manager.getClusterConfiguration();
      enhanceStats(doc, (ODocument) manager.getConfigurationMap().get("clusterStats"));
    } else {
      doc = new ODocument();

      final ODocument member = new ODocument();

      member.field("name", "orientdb");
      member.field("status", "ONLINE");

      List<Map<String, Object>> listeners = new ArrayList<Map<String, Object>>();

      member.field("listeners", listeners, OType.EMBEDDEDLIST);

      String realtime = Orient.instance().getProfiler().toJSON("realtime", "system.config.");
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
      doc.field("members", new ArrayList<ODocument>() {
        {
          add(member);
        }
      });
    }
    return doc;
  }

  private void addConfiguration(String path, ODocument member, ODocument cfg) {
    Map<String, Object> eval = (Map) cfg.eval(path);

    ODocument configuration = member.field("configuration");

    if (configuration == null) {
      configuration = new ODocument();
      member.field("configuration", configuration);
    }
    for (String key : eval.keySet()) {
      if (key.startsWith("system.config.")) {
        configuration.field(key.replace("system.config.", "").replace(".", "_"), eval.get(key));
      }
    }
  }

  private void enhanceStats(ODocument doc, ODocument clusterStats) {

    Collection<ODocument> documents = doc.field("members");

    if (clusterStats != null) {
      for (ODocument document : documents) {
        String name = document.field("name");

        Map<String, Object> profilerStats = clusterStats.field(name);
        ODocument dStat = new ODocument().fromMap(profilerStats);

        addConfiguration("realtime.sizes", document, dStat);
        addConfiguration("realtime.texts", document, dStat);
      }
    }
  }

  private ODocument getMemberConfig(ODocument doc, String node) {

    Collection<ODocument> documents = doc.field("members");

    ODocument member = null;
    for (ODocument document : documents) {
      String name = document.field("name");
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
