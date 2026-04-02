package edu.iu.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.logging.Level;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import edu.iu.IuException;
import edu.iu.IuText;

/**
 * Replaces {@link System#out} and {@link System#err} with an
 * {@link ByteArrayOutputStream} for inspecting console output, and provides
 * {@link #input(String)} for providing console input, for CLI utility testing.
 */
public class CliTestSupport implements BeforeEachCallback {

	/**
	 * Replacement buffer for {@link System#out}
	 */
	public static final ByteArrayOutputStream OUT = new ByteArrayOutputStream();

	/**
	 * Replacement buffer for {@link System#err}
	 */
	public static final ByteArrayOutputStream ERR = new ByteArrayOutputStream();

	private static volatile int inPos = -1;
	private static volatile byte[] in;

	static {
		System.setIn(new InputStream() {
			@Override
			public int read() throws IOException {
				synchronized (CliTestSupport.class) {
					while (inPos < 0)
						IuException.unchecked(() -> CliTestSupport.class.wait(250L));
				}
				if (inPos >= in.length)
					return -1;
				else
					return in[inPos++];
			}
		});
	}

	/**
	 * Default constructor.
	 */
	public CliTestSupport() {
	}

	/**
	 * Provides text to {@link System#in}
	 * 
	 * @param text mock console input
	 */
	public static synchronized void input(String text) {
		in = IuText.utf8(text);
		inPos = 0;
		CliTestSupport.class.notifyAll();
	}

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		OUT.reset();
		ERR.reset();
		System.setOut(new PrintStream(OUT));
		System.setErr(new PrintStream(ERR));
		inPos = -1;
		in = null;

		synchronized (CliTestSupport.class) {
			CliTestSupport.class.notifyAll();
		}

		assertEquals("", ERR.toString());
		IuTestLogger.allow("edu.iu.crypt", Level.CONFIG);
	}

}
