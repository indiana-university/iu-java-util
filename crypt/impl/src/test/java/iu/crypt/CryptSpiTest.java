package iu.crypt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpResponse;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import iu.crypt.Jose.Extension;
import jakarta.json.JsonString;

@SuppressWarnings("javadoc")
public class CryptSpiTest {

	protected URI uri(String content) {
		final var response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(200);
		when(response.body()).thenReturn(new ByteArrayInputStream(IuText.utf8(content)));

		final var uri = URI.create("test://" + AUTH + '/' + IdGenerator.generateId());
		mockHttp.when(() -> IuHttp.send(eq(uri), any())).thenReturn(response);
		return uri;
	}

	@SuppressWarnings("unchecked")
	protected String ext() {
		final var extName = IdGenerator.generateId();
		final var ext = mock(Extension.class);
		when(ext.toJson(any())).thenAnswer(a -> IuJson.string((String) a.getArgument(0)));
		when(ext.fromJson(any())).thenAnswer(a -> ((JsonString) a.getArgument(0)).getString());
		Jose.register(extName, ext);
		return extName;
	}

}
