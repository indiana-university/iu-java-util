package iu.auth.saml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
	 */
	public SamlParserPool() {
		DocumentBuilderFactory newFactory = DocumentBuilderFactory.newInstance();
		try {
			newFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			newFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException(e);
		}

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
	public DocumentBuilder getBuilder() throws XMLParserException {
		try {
			return builderFactory.newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			throw new XMLParserException(e);
		}
	}

	@Override
	public void returnBuilder(DocumentBuilder builder) {
		// TODO Auto-generated method stub

	}

	@Override
	public Document newDocument() throws XMLParserException {
		return getBuilder().newDocument();
	}

	@Override
	public Document parse(InputStream input) throws XMLParserException {
		try {
			return getBuilder().parse(input);
		} catch (SAXException | IOException e) {
			throw new XMLParserException(e);
		}
	}

	@Override
	public Document parse(Reader input) throws XMLParserException {
		try {
			return getBuilder().parse(new InputSource(input));
		} catch (SAXException | IOException e) {
			throw new XMLParserException(e);
		}
	}

}
