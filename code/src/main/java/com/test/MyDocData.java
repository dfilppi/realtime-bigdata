package com.test;

import org.openspaces.cassandraeds.CassandraEDS;
import org.openspaces.document.annotations.DocumentFixedField;
import org.openspaces.document.annotations.DocumentId;
import org.openspaces.document.annotations.SpaceDocument;
import org.openspaces.hdfseds.HdfsPersisterEDS;
import org.openspaces.persistence.annotations.Persistent;

/**
 * Dynamic "document" data.  Using java to define document
 * allows use of handy annotations and some type safety
 * 
 * @author DeWayne
 *
 */
@SpaceDocument(name="com_test_MyData")
@Persistent(persistTo={CassandraEDS.class,HdfsPersisterEDS.class}, loadFrom=CassandraEDS.class)
public class MyDocData {
	@DocumentFixedField
	String first;
	@DocumentFixedField
	String last;
	@DocumentFixedField
	Integer age;
	@DocumentId
	Long id;
	
}

