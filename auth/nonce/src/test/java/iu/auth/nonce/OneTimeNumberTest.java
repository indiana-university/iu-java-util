/*
 * Copyright © 2024 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.auth.nonce;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuDigest;
import edu.iu.IuParallelWorkloadController;
import edu.iu.IuRateLimitter;
import edu.iu.IuText;
import edu.iu.auth.IuAuthorizationChallenge;
import edu.iu.auth.IuOneTimeNumber;
import edu.iu.auth.IuOneTimeNumberConfig;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class OneTimeNumberTest {

	private class Config implements IuOneTimeNumberConfig {
		private IuAuthorizationChallenge challenge;

		@Override
		public Duration getTimeToLive() {
			return Duration.ofMillis(750L);
		}

		@Override
		public void subscribe(Consumer<IuAuthorizationChallenge> challengeSubscriber) {
			OneTimeNumberTest.this.challengeSubscriber = challengeSubscriber;
		}

		@Override
		public void publish(IuAuthorizationChallenge challenge) {
			final var thumbprint = challenge.getClientThumbprint();
			if (thumbprint != null)
				this.challenge = challenge;
			else
				assertEquals(this.challenge.getNonce(), challenge.getNonce());
		}
	}

	private String addr;
	private String agent;
	private Config config;
	private IuOneTimeNumber oneTimeNumber;
	private Consumer<IuAuthorizationChallenge> challengeSubscriber;
	private Map<String, IuAuthorizationChallenge> challenge;

	@SuppressWarnings("unchecked")
	@BeforeEach
	public void setup() throws Exception {
		addr = "127.0.0.1";
		agent = IdGenerator.generateId();
		config = new Config();
		oneTimeNumber = IuOneTimeNumber.initialize(config);
		assertNotNull(challengeSubscriber);

		Field f;
		f = OneTimeNumber.class.getDeclaredField("challenge");
		f.setAccessible(true);
		challenge = (Map<String, IuAuthorizationChallenge>) f.get(oneTimeNumber);
		assertNotNull(challenge);
	}

	@AfterEach
	public void teardown() throws Exception {
		oneTimeNumber.close();
	}

	@Test
	public void testKnock() throws Exception {
		final var nonce = oneTimeNumber.create(addr, agent);
		assertSame(config.challenge, challenge.get(nonce));
		assertDoesNotThrow(() -> oneTimeNumber.validate(addr, agent, nonce));
		assertNull(challenge.get(config.challenge.getNonce()));
		assertThrows(NullPointerException.class, () -> oneTimeNumber.validate(addr, agent, nonce));

		challengeSubscriber.accept(config.challenge);
		assertSame(config.challenge, challenge.get(nonce));
		assertThrows(IllegalArgumentException.class,
				() -> oneTimeNumber.validate(addr, IdGenerator.generateId(), nonce));
		assertThrows(NullPointerException.class, () -> oneTimeNumber.validate(addr, agent, nonce));

		challengeSubscriber.accept(config.challenge);
		assertSame(config.challenge, challenge.get(nonce));
		final var removeChallenge = new UsedChallenge(config.challenge.getNonce());
		challengeSubscriber.accept(removeChallenge);
		assertNull(challenge.get(config.challenge.getNonce()));

		challengeSubscriber.accept(config.challenge);
		assertSame(config.challenge, challenge.get(nonce));

		Thread.sleep(2000L);
		assertNull(challenge.get(config.challenge.getNonce()));
	}

	@Test
	public void testKnockKnock() throws Exception {
		final var agent2 = IdGenerator.generateId();

		final Set<String> nonces = new HashSet<>();
		for (var i = 0; i < 100; i++)
			nonces.add(oneTimeNumber.create(addr, i % 2 == 0 ? agent : agent2));
		nonces.retainAll(challenge.keySet());
		assertTrue(nonces.size() <= 20, nonces::toString);
	}

	@Test
	public void testKnockKnockParallel() throws Exception {
		IuTestLogger.allow(IuParallelWorkloadController.class.getName(), Level.CONFIG);
		IuTestLogger.allow(OneTimeNumber.class.getName(), Level.INFO);
		final var workload = new IuParallelWorkloadController("", 100, Duration.ofSeconds(5L));

		// DOS prevention SLA = 5000 requests
		// , single IP address
		// , random user agent per request
		// , 200 threads
		// , PT5S max workload runtime
		// , Target failure rate: < 50%

		// Uncomment to troubleshoot parallel workload
//		final var logger = Logger.getAnonymousLogger();
//		logger.setUseParentHandlers(false);
//		final var handler = new ConsoleHandler();
//		handler.setLevel(Level.FINE);
//		logger.setLevel(Level.FINE);
//		logger.addHandler(handler);
//		workload.setLog(logger);

		class Box {
			volatile int success;

			synchronized void success() {
				success++;
			}
		}

		final var box = new Box();
		final var thumbprint = IuDigest.sha256(IuText.utf8(addr + agent));
		final var rateLimit = new IuRateLimitter(200, Duration.ofSeconds(5L));
		for (var i = 0; i < 500 || i == box.success; i++)
			rateLimit.accept(workload.apply(task -> {
				final var nonce = oneTimeNumber.create(addr, agent);
				IdGenerator.verifyId(nonce, 5000L);

				final var challenge = this.challenge.values().stream().filter(a -> nonce.equals(a.getNonce()))
						.findFirst();
				if (challenge.isPresent()) {
					assertArrayEquals(thumbprint, challenge.get().getClientThumbprint());
					box.success();
				}
			}));

		rateLimit.join();
		workload.await();
		workload.close();
		assertTrue(box.success > 10, () -> Integer.toString(box.success));
	}

	@Test
	public void testInvalidInputs() throws Exception {
		IuTestLogger.expect(OneTimeNumber.class.getName(), Level.INFO, "Discarding nonce", NullPointerException.class);
		final var nonce = oneTimeNumber.create(null, null);
		assertDoesNotThrow(() -> IdGenerator.verifyId(nonce, 1000L));
		assertThrows(NullPointerException.class, () -> oneTimeNumber.validate(null, null, nonce));
	}

	@Test
	public void testClosed() throws Exception {
		oneTimeNumber.close();
		final var nonce = oneTimeNumber.create(null, null);
		assertDoesNotThrow(() -> IdGenerator.verifyId(nonce, 1000L));
		assertThrows(IllegalStateException.class, () -> oneTimeNumber.validate(null, null, nonce));
		assertThrows(IllegalStateException.class,
				() -> challengeSubscriber.accept(mock(IuAuthorizationChallenge.class)));
	}

}
