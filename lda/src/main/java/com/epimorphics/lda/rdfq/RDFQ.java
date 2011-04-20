/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
    $Id$
*/
package com.epimorphics.lda.rdfq;

import com.hp.hpl.jena.vocabulary.RDF;


/**
    A skinny set of classes for representing SPARQL atomic terms,
    triples, and infix expressions.
    
 	@author chris
*/
public class RDFQ
	{
	public static final URINode RDF_TYPE = uri( RDF.type.getURI() );
	
	public static class Triple 
		{
		public final Any S, P, O;
		public final boolean optional;
		
		public Triple( Any S, Any P, Any O )
			{ this( S, P, O, false ); }
		
		public Triple( Any S, Any P, Any O, boolean optional )
			{ this.S = S; this.P = P; this.O = O; this.optional = optional; }
		
		public boolean isOptional()
			{ return optional; }
		
		@Override public String toString()
			{ return asSparqlTriple(); }
		
		public String asSparqlTriple()
			{ 
			String SPO = S.asSparqlTerm() + " " + P.asSparqlTerm() + " " + O.asSparqlTerm();
			return optional ? "OPTIONAL {" + SPO + "}": SPO; 
			}
		}
	
	public static LiteralNode literal( double d )
		{
		String spelling = Double.toString( d );
		return new LiteralNode( spelling, "", "" ) 
			{
			@Override public String asSparqlTerm() { return spelling; }
			};
		}
	
	public static Apply apply( String f, RenderExpression X ) 
		{ return new Apply( f, X ); }
	
	public static Infix infix( RenderExpression L, String op, RenderExpression R ) 
		{ return new Infix( L, op, R ); }
	
	public static URINode uri( String URI ) 
		{ return new URINode( URI ); }
	
	public static LiteralNode literal( String spelling ) 
		{ return new LiteralNode( spelling ); }
	
	public static LiteralNode literal( String spelling, String language, String datatype ) 
		{ return new LiteralNode( spelling, language, datatype ); }
	
	public static Variable var( String name ) 
		{ return new Variable( name ); }
	
	public static Triple triple( Any S, Any P, Any O ) 
		{ return new Triple( S, P, O ); }
	
	public static Triple triple( Any S, Any P, Any O, boolean optional ) 
		{ return new Triple( S, P, O, optional ); }

	}