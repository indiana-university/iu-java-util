package iu.auth.saml;

import java.io.InputStream;
import java.io.Reader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import edu.iu.IuException;
import net.shibboleth.shared.xml.ParserPool;
import net.shibboleth.shared.xml.XMLParserException;

/**
 * Implemented custom parser pool to support SAML Parser pool
 *
 */
public class SamlParserPool implements ParserPool {

	private final DocumentBuilderFactory builderFactory;

	/**
	 * Initialize the pool.
	 * 
	 * @throws ParserConfigurationException when DocumentBuilderFactory or the
	 *                                      DocumentBuilders it creates cannot
	 *                                      support this feature.
	 */
	public SamlParserPool() throws ParserConfigurationException {
		DocumentBuilderFactory newFactory = DocumentBuilderFactory.newInstance();
		newFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		newFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

		newFactory.setCoalescing(true);
		newFactory.setExpandEntityReferences(false);
		newFactory.setIgnoringComments(true);
		newFactory.setIgnoringElementContentWhitespace(true);
		newFactory.setNamespaceAware(true);
		newFactory.setSchema(null);
		newFactory.setValidating(false);
		newFactory.setXIncludeAware(false);

		builderFactory = newFactory;

	}

	@Override
	public DocumentBuilder getBuilder() {
		return IuException.unchecked(() -> builderFactory.newDocumentBuilder());
	}

	@Override
	public void returnBuilder(DocumentBuilder builder) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Document newDocument() throws XMLParserException {
		return getBuilder().newDocument();
	}

	@Override
	public Document parse(InputStream input)  {
		return IuException.unchecked(()-> getBuilder().parse(input));
	}

	@Override
	public Document parse(Reader input) {
		return IuException.unchecked(() -> getBuilder().parse(new InputSource(input)));
	}

}
