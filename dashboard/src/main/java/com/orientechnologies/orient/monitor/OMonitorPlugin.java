package com.orientechnologies.orient.monitor;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.orientechnologies.common.io.OIOUtils;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.common.profiler.OProfiler.METRIC_TYPE;
import com.orientechnologies.common.util.OPair;
import com.orientechnologies.orient.core.OConstants;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OClass.INDEX_TYPE;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.OServerMain;
import com.orientechnologies.orient.server.config.OServerParameterConfiguration;
import com.orientechnologies.orient.server.handler.OServerHandlerAbstract;

public class OMonitorPlugin extends OServerHandlerAbstract {
  public enum LOG_LEVEL {
    DEBUG, INFO, CONFIG, WARN, ERROR
  }

  public enum STATUS {
    OFFLINE, ONLINE, UNAUTHORIZED, PROFILEROFF
  }

  public static final String                VERSION           = OConstants.ORIENT_VERSION;
  static final String                       SYSTEM_CONFIG     = "system.config";

  static final String                       CLASS_SERVER      = "Server";
  static final String                       CLASS_LOG         = "Log";
  static final String                       CLASS_SNAPSHOT    = "Snapshot";
  static final String                       CLASS_METRIC      = "Metric";
  static final String                       CLASS_COUNTER     = "Counter";
  static final String                       CLASS_CHRONO      = "Chrono";
  static final String                       CLASS_STATISTIC   = "Statistic";
  static final String                       CLASS_INFORMATION = "Information";
  static final String                       CLASS_DICTIONARY  = "Dictionary";

  private OServer                           serverInstance;
  private long                              updateTimer;
  private String                            dbName            = "monitor";
  private String                            dbUser            = "admin";
  private String                            dbPassword        = "admin";
  ODatabaseDocumentTx                       db;
  Map<String, OMonitoredServer>             servers           = new HashMap<String, OMonitoredServer>();
  Map<String, OPair<String, METRIC_TYPE>>   dictionary;
  private Set<OServerConfigurationListener> listeners         = new HashSet<OServerConfigurationListener>();

  @Override
  public void config(OServer iServer, OServerParameterConfiguration[] iParams) {
    serverInstance = iServer;
    OLogManager.instance().info(this, "Installing OrientDB Enterprise MONITOR v.%s...", VERSION);

    for (OServerParameterConfiguration param : iParams) {
      if (param.name.equalsIgnoreCase("updateTimer"))
        updateTimer = OIOUtils.getTimeAsMillisecs(param.value);
      else if (param.name.equalsIgnoreCase("dbName")) {
        dbName = param.value;
        dbName = "local:" + OServerMain.server().getDatabaseDirectory() + dbName;
      } else if (param.name.equalsIgnoreCase("dbUser"))
        dbUser = param.value;
      else if (param.name.equalsIgnoreCase("dbPassword"))
        dbPassword = param.value;
    }
  }

  @Override
  public String getName() {
    return "monitor";
  }

  @Override
  public void startup() {
    db = new ODatabaseDocumentTx(dbName);
    if (db.exists())
      loadConfiguration();
    else
      createConfiguration();

    updateDictionary();

    Orient.instance().getTimer().schedule(new OMonitorTask(this), updateTimer, updateTimer);
  }

  public OMonitoredServer getMonitoredServer(final String iServer) {
    return servers.get(iServer);
  }

  public Set<Entry<String, OMonitoredServer>> getMonitoredServers() {
    return Collections.unmodifiableSet(servers.entrySet());
  }

  public void updateActiveServerList() {
    Map<String, OMonitoredServer> tmpServers = new HashMap<String, OMonitoredServer>();
    final List<ODocument> enabledServers = db.query(new OSQLSynchQuery<Object>("select from Server where enabled = true"));
    for (ODocument s : enabledServers) {
      final String serverName = s.field("name");

      OMonitoredServer serverCfg = servers.get(serverName);
      if (serverCfg == null) {
        serverCfg = new OMonitoredServer(this, s);
      }
      tmpServers.put(serverName, serverCfg);
    }
    this.servers = tmpServers;
  }

  public Collection<OServerConfigurationListener> getListeners() {
    return Collections.unmodifiableCollection(listeners);
  }

  public OMonitorPlugin addListeners(final OServerConfigurationListener iListener) {
    listeners.add(iListener);
    return this;
  }

  protected void loadConfiguration() {
    db.open(dbUser, dbPassword);

    // LOAD THE SERVERS CONFIGURATION
    updateActiveServerList();

    // UPDATE LAST CONNECTION FOR EACH SERVERS
    final List<ODocument> snapshotDates = db.query(new OSQLSynchQuery<Object>(
        "select server.name as serverName, max(dateTo) as date from Snapshot where server.enabled = true group by server"));

    for (ODocument snapshot : snapshotDates) {
      final String serverName = snapshot.field("serverName");

      final OMonitoredServer serverCfg = servers.get(serverName);
      if (serverCfg != null)
        serverCfg.setLastConnection((Date) snapshot.field("date"));
    }

    OLogManager.instance().info(this, "MONITOR loading server configuration (%d)...", servers.size());
    for (Entry<String, OMonitoredServer> serverEntry : servers.entrySet()) {
      OLogManager.instance().info(this, "MONITOR * server [%s] updated to: %s", serverEntry.getKey(),
          serverEntry.getValue().getLastConnection());
    }
  }

  protected void createConfiguration() {
    OLogManager.instance().info(this, "MONITOR creating %s database...", dbName);
    db.create();

    final OSchema schema = db.getMetadata().getSchema();

    final OClass server = schema.createClass(CLASS_SERVER);
    server.createProperty("name", OType.STRING);
    server.createProperty("url", OType.STRING);
    server.createProperty("user", OType.STRING);
    server.createProperty("password", OType.STRING);

    final OClass snapshot = schema.createClass(CLASS_SNAPSHOT);
    snapshot.createProperty("server", OType.LINK, server);
    snapshot.createProperty("dateFrom", OType.DATETIME);
    snapshot.createProperty("dateTo", OType.DATETIME);

    final OClass metric = schema.createAbstractClass(CLASS_METRIC);
    metric.createProperty("name", OType.STRING);
    metric.createProperty("snapshot", OType.LINK, snapshot);

    final OClass log = schema.createClass(CLASS_LOG);
    log.createProperty("date", OType.DATETIME);
    log.createProperty("level", OType.INTEGER);
    log.createProperty("server", OType.LINK, server);
    log.createProperty("message", OType.STRING);

    final OClass chrono = schema.createClass(CLASS_CHRONO).setSuperClass(metric);
    chrono.createProperty("entries", OType.LONG);
    chrono.createProperty("last", OType.LONG);
    chrono.createProperty("min", OType.LONG);
    chrono.createProperty("max", OType.LONG);
    chrono.createProperty("average", OType.LONG);
    chrono.createProperty("total", OType.LONG);

    final OClass counter = schema.createClass(CLASS_COUNTER).setSuperClass(metric);
    counter.createProperty("value", OType.LONG);

    final OClass statistics = schema.createClass(CLASS_STATISTIC).setSuperClass(metric);
    statistics.createProperty("value", OType.STRING);

    final OClass information = schema.createClass(CLASS_INFORMATION).setSuperClass(metric);
    information.createProperty("value", OType.STRING);
  }

  @Override
  public void shutdown() {
  }

  protected void updateDictionary() {
    final OSchema schema = db.getMetadata().getSchema();

    if (!schema.existsClass(CLASS_DICTIONARY)) {
      final OClass dictionary = schema.createClass(CLASS_DICTIONARY);
      final OProperty name = dictionary.createProperty("name", OType.STRING);
      name.createIndex(INDEX_TYPE.UNIQUE);
    }

    if (dictionary == null)
      dictionary = Orient.instance().getProfiler().getMetadata();

    for (Entry<String, OPair<String, METRIC_TYPE>> entry : dictionary.entrySet()) {
      try {
        final String key = entry.getKey();
        final OPair<String, METRIC_TYPE> value = entry.getValue();

        final ODocument doc = new ODocument(CLASS_DICTIONARY);
        doc.field("name", key);
        doc.field("description", value.getKey());
        doc.field("type", value.getValue());
        doc.field("enabled", Boolean.TRUE);
        doc.save();

      } catch (Exception e) {
        // IGNORE DUPLICATES
      }
    }
  }

  public Map<String, OPair<String, METRIC_TYPE>> getDictionary() {
    return dictionary;
  }
}