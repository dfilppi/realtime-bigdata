package org.openspaces.eds.support;

/**
 * Implementors supply a simple means of serializing java classes to 
 * and from a string 
 * 
 * @author DeWayne
 *
 */
public interface FieldSerializer {
	String serialize(Object obj);

	Object deserialize(String data);
}
