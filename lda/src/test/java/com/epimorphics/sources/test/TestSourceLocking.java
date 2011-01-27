package com.epimorphics.sources.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.epimorphics.lda.sources.Source;
import com.epimorphics.lda.sources.SourceBase;
import com.epimorphics.util.CollectionUtils;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.shared.Lock;

public class TestSourceLocking {

	static class TestSource extends SourceBase implements Source {

		final List<String> history = new ArrayList<String>();

		Lock lock = new Lock() {

			@Override public void enterCriticalSection( boolean readLockRequested ) {
				history.add( "LOCK-" + readLockRequested );
			}
	
			@Override public void leaveCriticalSection() {
				history.add( "UNLOCK" );
			}
			
		};
		
		final Model model = ModelFactory.createDefaultModel();
		
		@Override public void addMetadata( Resource meta ) {			
		}

		@Override public QueryExecution execute( Query q ) {
			QueryExecution qe = QueryExecutionFactory.create( q, model );
			history.add( "QE_CREATE" );
			return wrapped( qe );
		}

		public QueryExecution wrapped( final QueryExecution qe ) {
			return new QueryExecutionWithHistory( qe, history );
		}

		@Override public Lock getLock() {
			return lock;
		}
	}
	
	@Test public void testConstruct() {
		TestSource s = new TestSource();
		Query q = QueryFactory.create( "CONSTRUCT {?s ?p ?o} WHERE {?s ?p ?o}" );
		s.executeConstruct( q );
		assertEquals( CollectionUtils.list( "LOCK-true", "QE_CREATE", "CONSTRUCT", "UNLOCK" ), s.history );
	}
	
	@Test public void testDescribe() {
		TestSource s = new TestSource();
		Query q = QueryFactory.create( "DESCRIBE ?s WHERE {?s ?p ?o}" );
		s.executeDescribe( q );
		assertEquals( CollectionUtils.list( "LOCK-true", "QE_CREATE", "DESCRIBE", "UNLOCK" ), s.history );
	}
}
