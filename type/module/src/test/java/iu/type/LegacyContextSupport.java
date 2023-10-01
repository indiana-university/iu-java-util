package iu.type;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

@SuppressWarnings("javadoc")
public class LegacyContextSupport implements BeforeEachCallback, AfterEachCallback {

	private static final ThreadLocal<LegacyClassLoader> LEGACY_CONTEXT = new ThreadLocal<>();

	static LegacyClassLoader get() {
		return LEGACY_CONTEXT.get();
	}

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		LEGACY_CONTEXT.set(new LegacyClassLoader(false, TestArchives.getClassPath("testlegacy"), null));
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		var legacyContext = get();
		LEGACY_CONTEXT.remove();
		if (legacyContext != null)
			legacyContext.close();
	}

}
