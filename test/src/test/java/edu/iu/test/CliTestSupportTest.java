package edu.iu.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IdGenerator;
import edu.iu.IuObject;
import edu.iu.IuProcess;

@SuppressWarnings("javadoc")
@ExtendWith(CliTestSupport.class)
public class CliTestSupportTest {

	@Test
	void testRead() throws Throwable {
		final var in = IdGenerator.generateId();
		final var run = new Runnable() {
			private volatile Throwable error;
			private volatile boolean done;

			@Override
			public void run() {
				try {
					Thread.sleep(500L);
					CliTestSupport.input(in);
				} catch (Throwable e) {
					error = e;
				} finally {
					done = true;
					synchronized (this) {
						this.notifyAll();
					}
				}
			}
		};
		new Thread(run).start();

		assertEquals(in, IuProcess.read());

		IuObject.waitFor(run, () -> run.done, Duration.ofSeconds(5L));
		if (run.error != null)
			throw run.error;
	}

}
