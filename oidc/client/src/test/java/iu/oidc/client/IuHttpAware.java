package iu.oidc.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.net.URI;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.client.IuHttp;

@SuppressWarnings({ "javadoc" })
public class IuHttpAware implements BeforeEachCallback, AfterEachCallback {

	static URI TEST_URI = URI.create("test://" + IdGenerator.generateId());

	static {
		System.setProperty("iu.http.allowedUri", TEST_URI.toString());
		IuException.unchecked(() -> Class.forName(IuHttp.class.getName()));
		System.clearProperty("iu.http.allowedUri");
	}

	static MockedStatic<IuHttp> mock;

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		mock = mockStatic(IuHttp.class);
		mock.when(() -> IuHttp.validate(any(), any())).thenCallRealMethod();
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		mock.close();
	}

}
