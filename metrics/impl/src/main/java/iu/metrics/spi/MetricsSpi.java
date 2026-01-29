package iu.metrics.spi;

import jakarta.annotation.Priority;
import jakarta.annotation.Resource;
import edu.iu.metrics.IuCompositeMetricsConfiguration;
import edu.iu.metrics.IuMetricRegistry;
import edu.iu.metrics.IuMetricsConfiguration;
import edu.iu.metrics.IuPrometheusMetricsConfiguration;
import edu.iu.metrics.spi.IuMetricsSpi;
import iu.metrics.CompositeMetricRegistry;
import iu.metrics.jmx.JmxMetricRegistry;
//...
import iu.metrics.prometheus.PrometheusMetricRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of Metrics service provider interface.
 */

@Priority(100)
@Resource
public class MetricsSpi implements IuMetricsSpi {

	/**
	 * Default constructor
	 */
	public MetricsSpi() {
	}

	@Override
	public IuMetricRegistry of(IuMetricsConfiguration config) {
		if (config instanceof IuCompositeMetricsConfiguration) {
			final var compositeConfig = (IuCompositeMetricsConfiguration) config;
			final List<IuMetricRegistry> registries = new ArrayList<>();
			for (final var componentConfig : compositeConfig.getComponentConfigurations())
				registries.add(of(componentConfig));
			return new CompositeMetricRegistry(registries);

		} else if (config instanceof IuPrometheusMetricsConfiguration)
			return new PrometheusMetricRegistry((IuPrometheusMetricsConfiguration) config);
		else 
			// TODO create JmxMetricConfiguration and check for it here
			return new JmxMetricRegistry(config);
		// TODO default to SimpleMetricRegistry when implemented
		
		
	}

}