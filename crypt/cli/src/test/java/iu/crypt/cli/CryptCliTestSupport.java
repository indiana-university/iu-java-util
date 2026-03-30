package iu.crypt.cli;

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
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class CryptCliTestSupport implements BeforeEachCallback {

	static final ByteArrayOutputStream OUT = new ByteArrayOutputStream();
	static final ByteArrayOutputStream ERR = new ByteArrayOutputStream();

	private static volatile int inPos;
	private static volatile byte[] in;

	static {
		System.setIn(new InputStream() {
			@Override
			public int read() throws IOException {
				synchronized (CryptCliTestSupport.class) {
					while (inPos == -1)
						IuException.unchecked(() -> CryptCliTestSupport.class.wait(250L));
				}
				if (in == null || inPos >= in.length)
					return -1;
				else
					return in[inPos++];
			}
		});
		System.setProperty("iu.crypt.pki.org", "/C=US/ST=Indiana/L=Bloomington/O=Indiana University/OU=Unit Testing");
	}

	static synchronized void input(String text) {
		in = IuText.utf8(text);
		inPos = 0;
		CryptCliTestSupport.class.notifyAll();
	}

	void reset() {
		OUT.reset();
		ERR.reset();
		System.setOut(new PrintStream(OUT));
		System.setErr(new PrintStream(ERR));
		inPos = 0;
		in = null;
		synchronized (CryptCliTestSupport.class) {
			CryptCliTestSupport.class.notifyAll();
		}
	}

	@SuppressWarnings("exports")
	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		reset();
		assertEquals("", ERR.toString());
		IuTestLogger.allow("edu.iu.crypt", Level.CONFIG);
	}

}
