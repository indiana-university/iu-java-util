package edu.iu.metrics;

import java.util.List;

/**
 * Defines configuration properties for a composite metric registry.
 */
public interface IuCompositeMetricsConfiguration extends IuMetricsConfiguration {

    /**
     * Gets the list of configurations for the component registries.
     * @return list of {@link IuMetricsConfiguration}
     */
    List<IuMetricsConfiguration> getComponentConfigurations();

}
