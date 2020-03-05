package com.orientechnologies.agent.profiler;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.orientechnologies.agent.OEnterpriseAgent;
import com.orientechnologies.agent.profiler.metrics.OHistogram;
import com.orientechnologies.agent.services.metrics.OrientDBMetricsService;
import com.orientechnologies.agent.services.metrics.OrientDBMetricsSettings;
import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.ODatabaseType;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import com.orientechnologies.orient.core.sql.executor.OResultSet;
import com.orientechnologies.orient.server.OServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.SortedMap;
import java.util.regex.Pattern;

public class QueryProfilerFilterTest {

  private OServer server;

  private Integer          pageSize;
  private OEnterpriseAgent agent;

  @Before
  public void init() throws Exception {

    server = OServer.startFromClasspathConfig("orientdb-server-config.xml");
    server.getContext().create(QueryProfilerFilterTest.class.getSimpleName(), ODatabaseType.PLOCAL);

    agent = server.getPluginByClass(OEnterpriseAgent.class);
  }

  @Test
  public void testFilterQueries() throws Exception {

    Optional<OrientDBMetricsService> serviceByClass = agent.getServiceByClass(OrientDBMetricsService.class);

    OrientDBMetricsSettings settings = new OrientDBMetricsSettings();

    settings.enabled = true;

    settings.database.enabled = true;

    OrientDBMetricsService metrics = serviceByClass.get();
    metrics.changeSettings(settings);

    OrientDB context = new OrientDB("remote:localhost", OrientDBConfig.defaultConfig());

    ODatabaseSession local = context.open(QueryProfilerFilterTest.class.getSimpleName(), "admin", "admin");

    OResultSet result = local.query("select from OUser \n where name = 'admin'");

    local.close();

    SortedMap<String, OHistogram> histograms = metrics.getRegistry().getHistograms((name, f) -> name.matches("(?s)db.*.query.*"));

    OHistogram histogram = histograms.get(
        String.format("db.%s.query.sql.select from OUser \n where name = 'admin'", QueryProfilerFilterTest.class.getSimpleName()));

    Assert.assertNotNull(histogram);

  }

  @After
  public void deInit() {

    server.getContext().drop(QueryProfilerFilterTest.class.getSimpleName());

    server.shutdown();
  }
}
