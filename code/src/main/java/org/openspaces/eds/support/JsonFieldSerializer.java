package org.openspaces.eds.support;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import com.gigaspaces.document.SpaceDocument;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;

/**
 * Basic field serdes for non-java fields (complex objects).  Serializes to a
 * String.  Uses a special token to recognize serialized strings from normal
 * String fields.  Token strategy may not work for all applications (especially
 * those that write regular strings beginning with "{\"__DOC__" or "{\"__GS__",
 * which hopefully is rare.  Knows about POJOs and SpaceDocuments.
 * 
 * @author DeWayne
 *
 */
public class JsonFieldSerializer implements FieldSerializer {
	private static final Logger log=Logger.getLogger(JsonFieldSerializer.class.getName());
	private static final Pattern p=Pattern.compile("^\\{\"__GS__([^\"]*).*$");
	private XStream xs=new XStream(new JettisonMappedXmlDriver());
	
	public JsonFieldSerializer(){
		xs.setMode(XStream.NO_REFERENCES);
	}
	
	@Override
	public String serialize(Object obj) {
		String alias;
		
		if(obj instanceof SpaceDocument){
			alias="__DOC__";
		}
		else{
			alias="__GS__"+obj.getClass().getName();
		}
		xs.alias(alias,obj.getClass());
		return xs.toXML(obj);
	}

	@Override
	public Object deserialize(String data) {
		String alias=null;
		Class<?> clazz;
		
		if(data.startsWith("{\"__DOC__")){
			alias="__DOC__";
			clazz=SpaceDocument.class;
		}
		else if(data.startsWith("{\"__GS__")){
			Matcher m=p.matcher(data);
			if(m.matches()){
				try {
					clazz=Class.forName(m.group(1));
				} catch (ClassNotFoundException e) {
					throw new RuntimeException(e);
				}
			}
			else{
				//just a string
				return data;
			}
		}
		else{
			//just a string
			return data;
		}
		
		xs.alias(alias,clazz);
		log.fine("deserialized:"+xs.fromXML(data).getClass().getName());
		return xs.fromXML(data);
	}


}
