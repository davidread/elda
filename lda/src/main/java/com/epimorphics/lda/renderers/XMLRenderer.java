/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
*/
package com.epimorphics.lda.renderers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.hp.hpl.jena.rdf.model.*;
import com.epimorphics.util.DOMUtils;
import com.epimorphics.util.MediaType;
import com.hp.hpl.jena.shared.WrappedException;
import com.epimorphics.lda.bindings.Bindings;
import com.epimorphics.lda.core.APIResultSet;
import com.epimorphics.lda.core.APIResultSet.MergedModels;
import com.epimorphics.lda.shortnames.ShortnameService;
import com.epimorphics.lda.support.Times;

public class XMLRenderer implements Renderer {
	
    static Logger log = LoggerFactory.getLogger(XMLRenderer.class);
	
	final ShortnameService sns;
	final String transformFilePath;
	final MediaType mt;
	
	public XMLRenderer( ShortnameService sns ) {
		this( sns, MediaType.TEXT_XML, null );
	}
	
	public XMLRenderer( ShortnameService sns, MediaType mt, String transformFilePath ) {
		this.sns = sns;
		this.mt = mt;
		this.transformFilePath = transformFilePath;
	}
	
	@Override public MediaType getMediaType( Bindings irrelevant ) {
		return mt;
	}

    @Override public String getPreferredSuffix() {
    	return "xml";
    }

	@Override public synchronized Renderer.BytesOut render( Times t, Bindings rc, APIResultSet results ) {
		Resource root = results.getRoot();
		boolean suppressIPTO = rc.getAsString( "_suppress_ipto", "no" ).equals( "yes" );
		Document d = DOMUtils.newDocument();
		renderInto( root, results.getModels(), d, suppressIPTO );
		return DOMUtils.renderNodeToBytesOut( t, d, rc, results.getModelPrefixes(), transformFilePath );
	}

	public void renderInto( Resource root, MergedModels mm, Document d, boolean suppressIPTO ) {
		XMLRendering r = new XMLRendering( mm.getMergedModel(), sns, suppressIPTO, d );
		Element result = d.createElement( "result" );
		result.setAttribute( "format", "linked-data-api" );
		result.setAttribute( "version", "0.2" );
		r.addResourceToElement( result, root, mm );
		d.appendChild( result );            
	//
		try {	
			// save the xml for later analysis or use in gold tests.
			if (false) {		
				new File("/tmp/gold" ).mkdirs();
				System.err.println( ">> saving rendering to /tmp/gold/*" );
				
				writeModel( mm.getObjectModel(), "/tmp/gold/object_model" );
				writeModel( mm.getMetaModel(), "/tmp/gold/meta_model" );
				writeResource( root, "/tmp/gold/root.uri" );
				writeBoolean( suppressIPTO, "/tmp/gold/suppress_ipto.bool" );
				
				writeShortnames( sns, mm.getMergedModel(), "/tmp/gold/names.sns" );
				
				TransformerFactory tFactory = TransformerFactory.newInstance();
				Transformer transformer = tFactory.newTransformer();
//				transformer.setOutputProperty( OutputKeys.INDENT, "yes" );
//				transformer.setOutputProperty( "{http://xml.apache.org/xslt}indent-amount", "2");
				DOMSource source = new DOMSource( d );
				OutputStream out = new FileOutputStream( "/tmp/gold/xml-rendering.xml" );
				StreamResult stream = new StreamResult( out );
				transformer.transform( source, stream );
				out.close();
			}
		} catch (Exception e) {
			throw new WrappedException( e );
		}
	}

	private void writeShortnames( ShortnameService sns, Model m, String fileName ) throws IOException {
		OutputStream os = new FileOutputStream( new File( fileName ) );
		PrintStream ps = new PrintStream( os );
		
		
		Map<String, String> uriToName = sns.constructURItoShortnameMap( m, m );
		
		for (String uri: uriToName.keySet()) {
			ps.print( uriToName.get( uri ) );
			ps.print( "=" );
			ps.print( uri );
			ps.println();
		}
		
		ps.flush();
		os.close();
	}

	private void writeBoolean( boolean suppressIPTO, String fileName ) throws IOException {
		OutputStream os = new FileOutputStream( new File( fileName ) );
		PrintStream ps = new PrintStream( os );
		ps.println( suppressIPTO ? "true" : "false" );
		ps.flush();
		os.close();
	}

	private void writeResource(Resource root, String fileName) throws IOException {
		OutputStream os = new FileOutputStream( new File( fileName ) );
		PrintStream ps = new PrintStream( os );
		ps.println( root.getURI() );
		ps.flush();
		os.close();
	}

	private void writeModel(Model objectModel, String fileName) throws IOException {
		OutputStream os = new FileOutputStream( new File( fileName + ".ttl" ) );
		objectModel.write( os, "TTL" );
		os.close();
	}
	
}
