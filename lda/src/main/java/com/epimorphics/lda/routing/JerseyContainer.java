package com.epimorphics.lda.routing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.epimorphics.lda.Version;
import com.epimorphics.lda.core.ModelLoader;
import com.epimorphics.lda.exceptions.APIException;
import com.epimorphics.lda.exceptions.APISecurityException;
import com.epimorphics.lda.specmanager.SpecManagerFactory;
import com.epimorphics.lda.specmanager.SpecManagerImpl;
import com.epimorphics.lda.support.LARQManager;
import com.epimorphics.lda.support.MapMatching;
import com.epimorphics.lda.support.TDBManager;
import com.epimorphics.lda.vocabularies.EXTRAS;
import com.epimorphics.vocabs.API;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.vocabulary.RDF;
import com.sun.jersey.spi.container.servlet.ServletContainer;

public class JerseyContainer extends ServletContainer {
	
	private static final long serialVersionUID = 1L;

	static Logger log = LoggerFactory.getLogger( JerseyContainer.class );

	String baseFilePath = "";

	String contextPath = "";
	
	ModelLoader modelLoader;
	
    @Override public void init() throws ServletException { 
    	super.init();
    	// configureLog4J();
    	String name = getServletName();
		log.info( "Starting servlet " + name + " for Elda " + Version.string );
    	Router router = new DefaultRouter();
    	routers.put( name,  router );
    	baseFilePath = withTrailingSlash( getServletContext().getRealPath( "/" ) );
    	contextPath = getServletContext().getContextPath();
    	modelLoader = new APIModelLoader( baseFilePath );
    	FileManager.get().addLocatorFile( baseFilePath );
    	setupLARQandTDB();
		SpecManagerFactory.set( new SpecManagerImpl( router, modelLoader ) );
    	for (String spec : getSpecNamesFromContext()) loadSpecFromFile( spec );
    } 

    
	/**
	    The spec names can come from the init parameter set in the web.xml,
	    or they may preferentially be set from system properties. 
	 
	 	@return 
	*/
	private Set<String> getSpecNamesFromContext() {
		Set<String> found = specNamesFromSystemProperties();
		return found.size() > 0 ? found : specNamesFromInitParam();
	}
	
	public Set<String> specNamesFromSystemProperties() {
		Properties p = System.getProperties();
		return MapMatching.allValuesWithMatchingKey( Loader.ELDA_SPEC_SYSTEM_PROPERTY_NAME, p );
	}
	
	private Set<String> specNamesFromInitParam() {
		return new HashSet<String>( Arrays.asList( safeSplit(getInitParameter( Loader.INITIAL_SPECS_PARAM_NAME ) ) ) );
	}
    
    public void osgiInit(String filepath) {
        baseFilePath = filepath;
        modelLoader = new APIModelLoader(baseFilePath);
//        FileManager.get().addLocatorFile( baseFilePath );
//        modelLoader = new APIModelLoader(baseFilePath);
        FileManager.get().addLocatorFile( baseFilePath );
        SpecManagerFactory.set( new SpecManagerImpl(RouterFactory.getDefaultRouter(), modelLoader) );
    }

	public void loadSpecFromFile( String spec ) {
		log.info( "Loading spec file from " + spec );
		Model init = getSpecModel( spec );
		addLoadedFrom( init, spec );
		log.info( "Loaded " + spec + ": " + init.size() + " statements" );
		registerModel( init );
	}

	private void addLoadedFrom( Model m, String name ) {
		List<Statement> toAdd = new ArrayList<Statement>();
		List<Resource> apis = m
			.listStatements( null, RDF.type, API.API )
			.mapWith(Statement.Util.getSubject)
			.toList()
			;
		for (Resource api: apis) toAdd.add( m.createStatement( api, EXTRAS.loadedFrom, name ) );
		m.add( toAdd );
	}


//	// Putting log4j.properties in the classes root as normal doesn't
//    // seem to work in WTP even though it does for normal tomcat usage
//    // This is an attempt to force logging configuration to be loaded
//    private void configureLog4J() throws FactoryConfigurationError {
//        String file = getInitParameter(LOG4J_PARAM_NAME);
//        if (file == null) file = "log4j.properties";
//        if (file != null) {
//            if (file.endsWith( ".xml" )) {
//                DOMConfigurator.configure( baseFilePath + file );
//            }
//            else {
//                PropertyConfigurator.configure(baseFilePath + file);
//            }
//        }
//	}

    private String withTrailingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    private String[] safeSplit(String s) {
        return s == null || s.equals("") 
        	? new String[] {} 
        	: s.replaceAll( "[ \n\t]", "" ).split(",")
        	;
    }

    public static final String DATASTORE_KEY = "com.epimorphics.api.dataStoreDirectory";

    private void setupLARQandTDB() {
        String locStore = getInitParameter( DATASTORE_KEY );
        String defaultTDB = locStore + "/tdb", defaultLARQ = locStore + "/larq";
        String givenTDB = getInitParameter( TDBManager.TDB_BASE_DIRECTORY );
        String givenLARQ =  getInitParameter( LARQManager.LARQ_DIRECTORY_KEY );
        TDBManager.setBaseTDBPath( expandLocal( givenTDB == null ? defaultTDB : givenTDB ) );
        LARQManager.setLARQIndexDirectory( expandLocal( givenLARQ == null ? defaultLARQ : givenLARQ ) );
    }

    private String expandLocal( String s ) {
//        return s.replaceFirst( "^" + LOCAL_PREFIX, baseFilePath );
//        Reg version blows up with a char out of range
        return s.replace( Loader.LOCAL_PREFIX, baseFilePath );
    }

    private Model getSpecModel( String initialSpec ) {
        return modelLoader.loadModel( initialSpec );
    }

    /**
     * Register all API endpoints specified in the given model with the
     * router.
     * @param model
     */
    public static void registerModel( Model model ) {
        for (ResIterator ri = model.listSubjectsWithProperty( RDF.type, API.API ); ri.hasNext();) {
            Resource api = ri.next();
            try {
                SpecManagerFactory.get().addSpec(api.getURI(), "", model);
            } catch (APISecurityException e) {
                throw new APIException( "Internal error. Got security exception duing bootstrap. Not possible!", e );
            }
        }
    }

    class APIModelLoader implements ModelLoader {

        String baseFilePathLocal;

        APIModelLoader(String base) {
            baseFilePathLocal = base;
        }

        @Override public Model loadModel(String uri) {
            log.info( "loadModel: " + uri );
            if (uri.startsWith(Loader.LOCAL_PREFIX)) {
                String specFile = "file:///" + baseFilePathLocal + uri.substring(Loader.LOCAL_PREFIX.length());
                return FileManager.get().loadModel( specFile );

            } else if (uri.startsWith( TDBManager.PREFIX )) {
                String modelName = uri.substring( TDBManager.PREFIX.length() );
                Model tdb = TDBManager.getTDBModelNamed( modelName );
                log.info( "get TDB model " + modelName );
                if (tdb.isEmpty()) log.warn( "the TDB model at " + modelName + " is empty -- has it been initialised?" );
                if (tdb.isEmpty()) throw new APIException( "the TDB model at " + modelName + " is empty -- has it been initialised?" );
                return tdb;

            } else {
                return FileManager.get().loadModel( uri );
            }
        }
    }

    static Map<String, Router> routers = new HashMap<String, Router>();
    
	public static Router routerForServlet(String name) {
		return routers.get(name);
	}

}
