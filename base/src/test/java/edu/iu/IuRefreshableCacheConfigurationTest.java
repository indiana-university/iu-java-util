/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 */
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Tests the default configuration contract independently of cache behavior. */
@SuppressWarnings("javadoc")
public class IuRefreshableCacheConfigurationTest {

	@Test
	public void testDefaults() {
		final IuRefreshableCacheConfiguration configuration = new IuRefreshableCacheConfiguration() {
		};
		assertEquals(IuRefreshableCacheConfiguration.REFRESH_TTL, configuration.getRefreshTtl());
		assertEquals(IuRefreshableCacheConfiguration.CACHE_TTL, configuration.getCacheTtl());
		assertEquals(IuRefreshableCacheConfiguration.CALL_TTL, configuration.getCallTtl());
		assertEquals(IuRefreshableCacheConfiguration.THREADS, configuration.getThreads());
		assertEquals(IuRefreshableCacheConfiguration.PENDING, configuration.getPending());
	}

	@Test
	public void testProvidedConfigurations() {
		assertEquals(IuRefreshableCacheConfiguration.REFRESH_TTL,
				IuRefreshableCacheConfiguration.DEFAULT.getRefreshTtl());
		assertNull(IuRefreshableCacheConfiguration.NO_CACHE.getRefreshTtl());
		assertEquals(IuRefreshableCacheConfiguration.CACHE_TTL,
				IuRefreshableCacheConfiguration.NO_CACHE.getCacheTtl());
		assertEquals(IuRefreshableCacheConfiguration.CALL_TTL,
				IuRefreshableCacheConfiguration.NO_CACHE.getCallTtl());
	}
}
