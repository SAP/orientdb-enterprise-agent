package com.orientechnologies.agent.profiler;

import com.orientechnologies.agent.profiler.metrics.*;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Created by Enrico Risa on 09/07/2018.
 */
public interface OMetricsRegistry {

  default String name(String name, String... names) {
    return name.join(".", names);
  }

  default String name(Class<?> klass, String... names) {
    return klass.getName().join(".", names);
  }

  OCounter counter(String name, String description);

  OMeter meter(String name, String description);

  <T> OGauge<T> gauge(String name, String description, Supplier<T> valueFunction);

  <T> OGauge<T> newGauge(String name, String description, Supplier<T> valueFunction);

  OHistogram histogram(String name, String description);

  OTimer timer(String name, String description);

  Map<String, OMetric> getMetrics();

  <T extends OMetric> T register(String name, String description, Class<T> klass);

  <T extends OMetric> T register(String name, T metric);

  void registerAll(OMetricSet metricSet);

  void registerAll(String prefix, OMetricSet metricSet);

  boolean remove(String name);
}
