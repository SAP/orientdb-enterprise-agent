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
package com.orientechnologies.agent;

import com.orientechnologies.agent.backup.OBackupManager;
import com.orientechnologies.agent.functions.OAgentFunctionFactory;
import com.orientechnologies.agent.ha.OEnterpriseDistributedStrategy;
import com.orientechnologies.agent.http.command.*;
import com.orientechnologies.agent.operation.NodesManager;
import com.orientechnologies.agent.plugins.OEventPlugin;
import com.orientechnologies.agent.profiler.OEnterpriseProfiler;
import com.orientechnologies.agent.profiler.OEnterpriseProfilerListener;
import com.orientechnologies.agent.services.OEnterpriseService;
import com.orientechnologies.agent.services.metrics.OrientDBMetricsService;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.common.profiler.OAbstractProfiler;
import com.orientechnologies.common.profiler.OAbstractProfiler.OProfilerHookValue;
import com.orientechnologies.common.profiler.OProfiler;
import com.orientechnologies.common.profiler.OProfilerStub;
import com.orientechnologies.enterprise.server.OEnterpriseServer;
import com.orientechnologies.enterprise.server.OEnterpriseServerImpl;
import com.orientechnologies.orient.core.OConstants;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.ODatabaseInternal;
import com.orientechnologies.orient.core.db.ODatabaseLifecycleListener;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.engine.OEngine;
import com.orientechnologies.orient.core.enterprise.OEnterpriseEndpoint;
import com.orientechnologies.orient.core.exception.OCommandExecutionException;
import com.orientechnologies.orient.core.exception.OConfigurationException;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.metadata.security.ORule;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.storage.impl.local.OAbstractPaginatedStorage;
import com.orientechnologies.orient.enterprise.channel.binary.OChannelBinaryProtocol;
import com.orientechnologies.orient.enterprise.channel.binary.ODistributedRedirectException;
import com.orientechnologies.orient.server.OClientConnection;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.OServerLifecycleListener;
import com.orientechnologies.orient.server.config.OServerParameterConfiguration;
import com.orientechnologies.orient.server.distributed.ODistributedConfiguration;
import com.orientechnologies.orient.server.distributed.ODistributedServerManager;
import com.orientechnologies.orient.server.distributed.OModifiableDistributedConfiguration;
import com.orientechnologies.orient.server.distributed.impl.ODatabaseDocumentDistributed;
import com.orientechnologies.orient.server.distributed.impl.ODistributedAbstractPlugin;
import com.orientechnologies.orient.server.hazelcast.OHazelcastPlugin;
import com.orientechnologies.orient.server.network.OServerNetworkListener;
import com.orientechnologies.orient.server.network.protocol.http.ONetworkProtocolHttpAbstract;
import com.orientechnologies.orient.server.plugin.OPluginLifecycleListener;
import com.orientechnologies.orient.server.plugin.OServerPlugin;
import com.orientechnologies.orient.server.plugin.OServerPluginAbstract;
import com.orientechnologies.orient.server.plugin.OServerPluginInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class OEnterpriseAgent extends OServerPluginAbstract
    implements ODatabaseLifecycleListener, OPluginLifecycleListener, OServerLifecycleListener, OEnterpriseEndpoint {
  public static final String     EE                = "ee.";
  private             String     enterpriseVersion = "";
  public              OServer    server;
  private             String     license;
  public static final String     TOKEN;
  private             Properties properties        = new Properties();

  private List<OEnterpriseService> services = new ArrayList<>();

  private OEnterpriseServer enterpriseServer;

  static {
    String t = null;
    try {
      t = OL.encrypt(UUID.randomUUID().toString());

    } catch (Exception e) {
      e.printStackTrace();
    }
    TOKEN = t;
  }

  private   OBackupManager      backupManager;
  protected OEnterpriseProfiler profiler;

  private NodesManager nodesManager;

  public OEnterpriseAgent() {
  }

  @Override
  public void config(OServer oServer, OServerParameterConfiguration[] iParams) {
    enabled = false;
    server = oServer;

    enterpriseServer = new OEnterpriseServerImpl(server, this);
    for (OServerParameterConfiguration p : iParams) {
      if (p.name.equals("license"))
        license = p.value;
    }

    if (oServer.getPluginManager() != null) {
      oServer.getPluginManager().registerLifecycleListener(this);
    }

    registerAndInitServices();

  }

  private void registerAndInitServices() {

    this.services.add(new OrientDBMetricsService());

    this.services.add(new OAgentFunctionFactory());

    this.services.forEach((s) -> s.init(this.enterpriseServer));

  }

  @Override
  public String getName() {
    return "enterprise-agent";
  }

  @Override
  public void startup() {

    try {
      loadProperties();

      if (checkLicense() && checkVersion()) {
        server.registerLifecycleListener(this);
        enabled = true;
        installProfiler();
        installBackupManager();

        installPlugins();

        registerSecurityComponents();

        installCommands();

        Thread installer = new Thread(() -> {

          int retry = 0;
          while (true) {
            ODistributedServerManager manager = server.getDistributedManager();
            if (manager == null) {
              if (retry == 5) {
                break;
              }
              try {
                Thread.sleep(2000);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              retry++;
              continue;
            } else {
              OHazelcastPlugin plugin = (OHazelcastPlugin) manager;
              nodesManager = new NodesManager(plugin);
              try {
                plugin.waitUntilNodeOnline();
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              Map<String, Object> map = manager.getConfigurationMap();
              map.put(EE + manager.getLocalNodeName(), TOKEN);
              manager.registerLifecycleListener(profiler);
              break;
            }

          }

        });

        installer.setDaemon(true);
        installer.start();
        Orient.instance().addDbLifecycleListener(this);

      }
    } catch (Exception e) {
      OLogManager.instance().warn(this, "Error loading agent.properties file. EE will be disabled: %s", e.getMessage());
    }

  }

  @Override
  public void shutdown() {
    if (enabled) {

      unregisterSecurityComponents();
      uninstallBackupManager();
      uninstallCommands();
      uninstallProfiler();

      if (server.getPluginManager() != null) {
        server.getPluginManager().unregisterLifecycleListener(this);
      }

      Orient.instance().removeDbLifecycleListener(this);

    }
  }

  public OBackupManager getBackupManager() {
    return backupManager;
  }

  public NodesManager getNodesManager() {
    return nodesManager;
  }

  @Override
  public PRIORITY getPriority() {
    return PRIORITY.LAST;
  }

  /**
   * Auto register myself as hook.
   */
  @Override
  public void onOpen(final ODatabaseInternal iDatabase) {

  }

  @Override
  public void onCreate(ODatabaseInternal iDatabase) {
    onOpen(iDatabase);
  }

  /**
   * Remove myself as hook.
   */
  @Override
  public void onClose(final ODatabaseInternal iDatabase) {

  }

  @Override
  public void onDrop(final ODatabaseInternal iDatabase) {

  }

  @Override
  public void onCreateClass(final ODatabaseInternal iDatabase, final OClass iClass) {

  }

  @Override
  public void onDropClass(final ODatabaseInternal iDatabase, final OClass iClass) {

  }

  // TODO SEND CPU METRICS ON configuration request;
  @Override
  public void onLocalNodeConfigurationRequest(ODocument iConfiguration) {

    OProfiler profiler = Orient.instance().getProfiler();

    OEngine plocal = Orient.instance().getEngine("plocal");

    if (profiler instanceof OEnterpriseProfiler) {
      iConfiguration.field("cpu", ((OEnterpriseProfiler) profiler).cpuUsage());
    }

  }

  public void installCommands() {
    final OServerNetworkListener listener = server.getListenerByProtocol(ONetworkProtocolHttpAbstract.class);
    if (listener == null)
      throw new OConfigurationException("HTTP listener not found");

    listener.registerStatelessCommand(new OServerCommandGetProfiler());
    listener.registerStatelessCommand(new OServerCommandDistributedManager());
    listener.registerStatelessCommand(new OServerCommandGetLog());
    listener.registerStatelessCommand(new OServerCommandConfiguration());
    listener.registerStatelessCommand(new OServerCommandPostBackupDatabase());
    listener.registerStatelessCommand(new OServerCommandGetDeployDb());
    listener.registerStatelessCommand(new OServerCommandGetSQLProfiler());
    listener.registerStatelessCommand(new OServerCommandPluginManager());
    listener.registerStatelessCommand(new OServerCommandGetNode());
    listener.registerStatelessCommand(new OServerCommandQueryCacheManager());
    listener.registerStatelessCommand(new OServerCommandAuditing(server));
    listener.registerStatelessCommand(new OServerCommandGetSecurityConfig(server.getSecurity()));
    listener.registerStatelessCommand(new OServerCommandPostSecurityReload(server.getSecurity()));
    listener.registerStatelessCommand(new OServerCommandBackupManager(backupManager));
  }

  private void uninstallCommands() {
    final OServerNetworkListener listener = server.getListenerByProtocol(ONetworkProtocolHttpAbstract.class);
    if (listener == null)
      throw new OConfigurationException("HTTP listener not found");

    listener.unregisterStatelessCommand(OServerCommandGetProfiler.class);
    listener.unregisterStatelessCommand(OServerCommandDistributedManager.class);
    listener.unregisterStatelessCommand(OServerCommandGetLog.class);
    listener.unregisterStatelessCommand(OServerCommandConfiguration.class);
    listener.unregisterStatelessCommand(OServerCommandPostBackupDatabase.class);
    listener.unregisterStatelessCommand(OServerCommandGetDeployDb.class);
    listener.unregisterStatelessCommand(OServerCommandGetSQLProfiler.class);
    listener.unregisterStatelessCommand(OServerCommandPluginManager.class);
    listener.unregisterStatelessCommand(OServerCommandGetNode.class);
    listener.unregisterStatelessCommand(OServerCommandQueryCacheManager.class);
    listener.unregisterStatelessCommand(OServerCommandAuditing.class);
    listener.unregisterStatelessCommand(OServerCommandBackupManager.class);
    listener.unregisterStatelessCommand(OServerCommandGetSecurityConfig.class);
    listener.unregisterStatelessCommand(OServerCommandPostSecurityReload.class);
  }

  private void installRegistry() {

  }

  protected void installProfiler() {
    final OAbstractProfiler currentProfiler = (OAbstractProfiler) Orient.instance().getProfiler();

    profiler = new OEnterpriseProfiler(60, currentProfiler, server, this);

    Orient.instance().setProfiler(profiler);
    Orient.instance().getProfiler().startup();
    if (currentProfiler.isRecording())
      profiler.startRecording();

    currentProfiler.shutdown();

  }

  public void registerListener(OEnterpriseProfilerListener listener) {
    if (profiler != null) {
      profiler.registerProfilerListener(listener);
    }
  }

  public void unregisterListener(OEnterpriseProfilerListener listener) {
    if (profiler != null) {
      profiler.unregisterProfilerListener(listener);
    }
  }

  private void uninstallProfiler() {
    final OProfiler currentProfiler = Orient.instance().getProfiler();

    Orient.instance().setProfiler(new OProfilerStub((OAbstractProfiler) currentProfiler));
    Orient.instance().getProfiler().startup();

    currentProfiler.shutdown();
    profiler = null;
  }

  private boolean checkLicense() {

    OLogManager.instance().info(this, "");
    OLogManager.instance().info(this, "*****************************************************************************");
    OLogManager.instance().info(this, "*                     ORIENTDB  -  ENTERPRISE EDITION                       *");
    OLogManager.instance().info(this, "*****************************************************************************");
    OLogManager.instance().info(this, "* If you are in Production or Test, you must purchase a commercial license. *");
    OLogManager.instance().info(this, "* For more information look at: http://orientdb.com/orientdb-enterprise/    *");
    OLogManager.instance().info(this, "*****************************************************************************");
    OLogManager.instance().info(this, "");

    Orient.instance().getProfiler()
        .registerHookValue(Orient.instance().getProfiler().getSystemMetric("config.agentVersion"), "Enterprise License",
            OProfiler.METRIC_TYPE.TEXT, new OProfilerHookValue() {

              @Override
              public Object getValue() {
                return enterpriseVersion;
              }
            });

    return true;
  }

  private void loadProperties() throws IOException {
    final InputStream inputStream = OEnterpriseAgent.class.getResourceAsStream("/com/orientechnologies/agent.properties");

    try {
      properties.load(inputStream);
      enterpriseVersion = properties.getProperty("version");

      if (enterpriseVersion == null || enterpriseVersion.isEmpty()) {
        throw new IllegalArgumentException("Cannot read the agent version from the agent config file");
      }
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException ignore) {
          // Ignore
        }
      }
    }
  }

  private boolean checkVersion() {

    if (!OConstants.getRawVersion().equalsIgnoreCase(enterpriseVersion)) {

      OLogManager.instance()
          .warn(this, "The current agent version %s is not compatible with OrientDB %s. Please use the same version.",
              enterpriseVersion, OConstants.getVersion());
      return false;
    }
    return true;
  }

  private void installBackupManager() {
    backupManager = new OBackupManager(server);
  }

  private void uninstallBackupManager() {
    if (backupManager != null) {
      backupManager.shutdown();
      backupManager = null;
    }
  }

  private void installPlugins() {

    try {

      final OEventPlugin eventPlugin = new OEventPlugin();
      eventPlugin.config(server, null);
      eventPlugin.startup();
      server.getPluginManager()
          .registerPlugin(new OServerPluginInfo(eventPlugin.getName(), null, null, null, eventPlugin, null, 0, null));
    } catch (Exception ex) {
    }
  }

  private void installComponents() {
    if (server.getDistributedManager() != null) {
      server.getDistributedManager().setDistributedStrategy(new OEnterpriseDistributedStrategy());
    }
  }

  // OPluginLifecycleListener
  public void onBeforeConfig(final OServerPlugin plugin, final OServerParameterConfiguration[] cfg) {
  }

  public void onAfterConfig(final OServerPlugin plugin, final OServerParameterConfiguration[] cfg) {
  }

  public void onBeforeStartup(final OServerPlugin plugin) {
    if (plugin instanceof ODistributedServerManager) {
      installComponents();
    }
  }

  public void onAfterStartup(final OServerPlugin plugin) {

    System.out.println("");
  }

  public void onBeforeShutdown(final OServerPlugin plugin) {

  }

  public void onAfterShutdown(final OServerPlugin plugin) {
  }

  @Override
  public void onBeforeClientRequest(final OClientConnection iConnection, final byte iRequestType) {

  }

  private void registerSecurityComponents() {
    try {
      if (server.getSecurity() != null) {
        server.getSecurity()
            .registerSecurityClass(com.orientechnologies.agent.security.authenticator.OSecuritySymmetricKeyAuth.class);
        server.getSecurity()
            .registerSecurityClass(com.orientechnologies.agent.security.authenticator.OSystemSymmetricKeyAuth.class);
      }
    } catch (Throwable th) {
      OLogManager.instance().error(this, "registerSecurityComponents()", th);
    }
  }

  private void unregisterSecurityComponents() {
    try {
      if (server.getSecurity() != null) {
        server.getSecurity()
            .unregisterSecurityClass(com.orientechnologies.agent.security.authenticator.OSecuritySymmetricKeyAuth.class);
        server.getSecurity()
            .unregisterSecurityClass(com.orientechnologies.agent.security.authenticator.OSystemSymmetricKeyAuth.class);
      }
    } catch (Throwable th) {
      OLogManager.instance().error(this, "unregisterSecurityComponents()", th);
    }
  }

  public boolean isDistributed() {
    return server.getDistributedManager() != null;
  }

  public ODistributedServerManager getDistributedManager() {

    return server.getDistributedManager();
  }

  public String getNodeName() {
    return isDistributed() ? server.getDistributedManager().getLocalNodeName() : "orientdb";
  }

  @Override
  public void haSetDbStatus(ODatabaseDocument database, String nodeName, String status) {

    database.checkSecurity(ORule.ResourceGeneric.SERVER, "status", ORole.PERMISSION_UPDATE);

    if (!(database instanceof ODatabaseDocumentDistributed)) {
      throw new OCommandExecutionException("OrientDB is not started in distributed mode");
    }

    final OHazelcastPlugin dManager = (OHazelcastPlugin) ((ODatabaseDocumentDistributed) database).getStorageDistributed()
        .getDistributedManager();
    if (dManager == null || !dManager.isEnabled())
      throw new OCommandExecutionException("OrientDB is not started in distributed mode");

    final String databaseName = database.getName();

    final ODistributedConfiguration cfg = dManager.getDatabaseConfiguration(databaseName);

    dManager.setDatabaseStatus(nodeName, databaseName, ODistributedServerManager.DB_STATUS.valueOf(status));

  }

  @Override
  public void haSetRole(ODatabaseDocument database, String serverName, String role) {
    database.checkSecurity(ORule.ResourceGeneric.SERVER, "status", ORole.PERMISSION_UPDATE);

    if (!(database instanceof ODatabaseDocumentDistributed)) {
      throw new OCommandExecutionException("OrientDB is not started in distributed mode");
    }

    final OHazelcastPlugin dManager = (OHazelcastPlugin) ((ODatabaseDocumentDistributed) database).getStorageDistributed()
        .getDistributedManager();
    if (dManager == null || !dManager.isEnabled())
      throw new OCommandExecutionException("OrientDB is not started in distributed mode");

    final String databaseName = database.getName();

    final ODistributedConfiguration cfg = dManager.getDatabaseConfiguration(databaseName);

    final OModifiableDistributedConfiguration newCfg = cfg.modify();
    newCfg.setServerRole(serverName, ODistributedConfiguration.ROLES.valueOf(role));
    dManager.updateCachedDatabaseConfiguration(databaseName, newCfg, true);
  }

  @Override
  public void haSetOwner(ODatabaseDocument database, String clusterName, String owner) {
    database.checkSecurity(ORule.ResourceGeneric.SERVER, "status", ORole.PERMISSION_UPDATE);

    if (!(database instanceof ODatabaseDocumentDistributed)) {
      throw new OCommandExecutionException("OrientDB is not started in distributed mode");
    }

    final OHazelcastPlugin dManager = (OHazelcastPlugin) ((ODatabaseDocumentDistributed) database).getStorageDistributed()
        .getDistributedManager();
    if (dManager == null || !dManager.isEnabled())
      throw new OCommandExecutionException("OrientDB is not started in distributed mode");

    final String databaseName = database.getName();

    final ODistributedConfiguration cfg = dManager.getDatabaseConfiguration(databaseName);

    final OModifiableDistributedConfiguration newCfg = cfg.modify();
    newCfg.setServerOwner(clusterName, owner);
    dManager.updateCachedDatabaseConfiguration(databaseName, newCfg, true);

  }

  @Override
  public void onBeforeActivate() {

  }

  @Override
  public void onAfterActivate() {
    services.forEach((s) -> s.start());
  }

  @Override
  public void onBeforeDeactivate() {
    services.forEach((s) -> {
      s.stop();
    });
  }

  @Override
  public void onAfterDeactivate() {

  }

  @Override
  public void onAfterClientRequest(OClientConnection iConnection, byte iRequestType) {
    super.onAfterClientRequest(iConnection, iRequestType);
  }

  public <T extends OEnterpriseService> Optional<T> getServiceByClass(Class<T> klass) {
    return (Optional<T>) this.services.stream().filter(c -> c.getClass().equals(klass)).findFirst();
  }
}
