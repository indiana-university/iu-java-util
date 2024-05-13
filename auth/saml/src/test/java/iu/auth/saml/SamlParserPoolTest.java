package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;

import net.shibboleth.shared.xml.XMLParserException;

public class SamlParserPoolTest {

	@Test
	public void testParserPool() throws XMLParserException, ParserConfigurationException {
		SamlParserPool parserPool = new SamlParserPool();
		DocumentBuilder builder = parserPool.getBuilder();
		assertNotNull(builder);
	}

	@Test
	public void testParserConfigurationException()
			throws XMLParserException, ParserConfigurationException, FileNotFoundException, URISyntaxException {
		DocumentBuilderFactory mockDbf = mock(DocumentBuilderFactory.class);
		// TODO cover ParserConfigurationException
		doThrow(ParserConfigurationException.class).when(mockDbf).newDocumentBuilder();

		SamlParserPool parserPool = new SamlParserPool();
		DocumentBuilder builder = parserPool.getBuilder();
		assertNotNull(parserPool.newDocument());
		assertNotNull(builder);
		assertThrows(XMLParserException.class, () -> parserPool.parse(new InputStream() {

			@Override
			public int read() throws IOException {
				return 0;
			}
		}));

		File file = new File("src/test/resource/foo.xml");

		InputStream inputStream = new FileInputStream(file);

		assertNotNull(parserPool.parse(inputStream));
		assertNotNull(parserPool.parse(new FileReader("src/test/resource/foo.xml")));
		assertThrows(XMLParserException.class, () -> parserPool.parse(new FileReader("src/test/resource/invalid.xml")));
	}

}
