package iu.metrics;


import java.util.List;

import edu.iu.IuException;
import edu.iu.metrics.IuMetricRegistry;

/**
 * A metric registry that is composed of other metric registries.
 */
public class CompositeMetricRegistry implements IuMetricRegistry {

	private final List<IuMetricRegistry> registries;
	private volatile boolean closed;

	/**
	 * @param registries2 
	 * @param registries the registries to compose
	 */
	public CompositeMetricRegistry(List<IuMetricRegistry> registries) {
		this.registries = registries;
	}

	@Override
	public synchronized void close() {
		Throwable error = null;

		if (!closed) {
			closed = true;
			for (IuMetricRegistry registry : registries) {
				 error = IuException.suppress(error, registry::close);
			}
		}

		if (error != null)
			throw IuException.unchecked(error);

	}

}
