package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;

import net.shibboleth.shared.xml.XMLParserException;

@SuppressWarnings("javadoc")
public class SamlParserPoolTest {

	@Test
	public void testParserPool() throws ParserConfigurationException {
		SamlParserPool parserPool = new SamlParserPool();
		DocumentBuilder builder = parserPool.getBuilder();
		assertNotNull(builder);
	}

	@Test
	public void testParserConfigurationException()
			throws XMLParserException, ParserConfigurationException, FileNotFoundException, URISyntaxException {
		DocumentBuilderFactory mockDbf = mock(DocumentBuilderFactory.class);
		doThrow(ParserConfigurationException.class).when(mockDbf).newDocumentBuilder();

		SamlParserPool parserPool = new SamlParserPool();
		DocumentBuilder builder = parserPool.getBuilder();
		assertNotNull(parserPool.newDocument());
		assertNotNull(builder);
		assertThrows(IllegalStateException.class, () -> parserPool.parse(new InputStream() {

			@Override
			public int read() throws IOException {
				return 0;
			}
		}));

		String initialString = "<foo><bar>baz</bar></foo>";
	    InputStream targetStream = new ByteArrayInputStream(initialString.getBytes());
	   
	    
		assertNotNull(parserPool.parse(targetStream));
		assertNotNull(parserPool.parse( new StringReader(initialString)));
		
	}

	@Test
	public void testReturnBuilder() throws ParserConfigurationException {
		SamlParserPool parserPool = new SamlParserPool();
		assertThrows(UnsupportedOperationException.class, () -> parserPool.returnBuilder(null));
	}

}
