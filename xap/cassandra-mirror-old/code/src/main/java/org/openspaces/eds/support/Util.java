package org.openspaces.eds.support;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.hadoop.conf.Configuration;

import com.gigaspaces.document.SpaceDocument;
import com.gigaspaces.metadata.SpacePropertyDescriptor;
import com.gigaspaces.metadata.SpaceTypeDescriptor;

public class Util {
	private static final Logger log=Logger.getLogger(Util.class.getName());
	private static final FieldInfoCache fieldCache=new FieldInfoCache();

	//So config can be set in Spring
	public static Configuration newConfiguration(Properties props){
		Configuration config=new Configuration();

		for(Map.Entry<Object,Object> entry:props.entrySet()){
			config.set(entry.getKey().toString(),entry.getValue().toString());
		}
		return config;
	}

	public static List<FieldInfo> getFields(Class<?> cls){
		return fieldCache.getFields(cls);
	}

	public static <T> T deserializeObject(Class<T> clazz,ResultSet rs,FieldSerializer fieldSerializer){
		try{
			T obj=clazz.newInstance();
			int colcnt=rs.getMetaData().getColumnCount();
			if(colcnt==1)return null;
			for(FieldInfo f:fieldCache.getFields(clazz)){
				try{
					// Basic deserialization from string: assume class has valueOf method
					if(f.getFieldType()==FieldInfo.FieldType.JAVA_LANG){
						f.setValueFromString(obj, rs.getString(f.getField().getName()));
					}
					else if(f.getFieldType()==FieldInfo.FieldType.USER){
						//try the serializer
						if(log.isLoggable(Level.FINE))log.fine("deserializing field:"+f.getField().getName());
						if(log.isLoggable(Level.FINE))log.fine("val="+rs.getString(f.getField().getName()));
						String fieldVal=rs.getString(f.getField().getName());
						Object subobj=null;
						if(fieldVal!=null){
							subobj=fieldSerializer.deserialize(fieldVal);
						}
						f.setFieldValue(obj,subobj);
					}
					else{
						//Ignore primitive (non-class) fields
						log.fine("deser: ignoring primitive field:"+f.getField().getName());
					}
				}
				catch(java.sql.SQLSyntaxErrorException e){
					//Ignores null columns in db
				}
			}
			return obj;

		}
		catch(Exception e){
			if(e instanceof RuntimeException){
				throw (RuntimeException)e;
			}
			else{
				throw new RuntimeException(e);
			}
		}

	}

	public static SpaceDocument deserializeDocument(SpaceTypeDescriptor desc,ResultSet rs,FieldSerializer fieldSerializer){

		try{

			ResultSetMetaData rsm=rs.getMetaData();
			int colcnt=rsm.getColumnCount();
			if(colcnt==1)return null;

			//TODO : Cache this
			Map<String,SpacePropertyDescriptor> fixed=new HashMap<String,SpacePropertyDescriptor>();
			for(int i=0;i<desc.getNumOfFixedProperties();i++){
				SpacePropertyDescriptor d=desc.getFixedProperty(i);
				fixed.put(d.getName(),d);
			}

			SpaceDocument doc=new SpaceDocument(desc.getTypeName());

			for(int i=1;i<=rsm.getColumnCount();i++){
				SpacePropertyDescriptor spd=fixed.get(rsm.getColumnName(i));
				//EXPLICIT
				if(spd!=null){
					//Relying on JDBC conversion
					doc.setProperty(rsm.getColumnName(i), rs.getObject(i));
				}
				//INFER
				else{
					if(rsm.getColumnType(i)==Types.CHAR ||
							rsm.getColumnType(i)==Types.LONGNVARCHAR ||
							rsm.getColumnType(i)==Types.LONGVARCHAR ||
							rsm.getColumnType(i)==Types.NCHAR ||
							rsm.getColumnType(i)==Types.VARCHAR
					){
						doc.setProperty(rsm.getColumnName(i),fieldSerializer.deserialize(rs.getString(i)));
					}
					else if(rsm.getColumnType(i)==Types.REAL || 
							rsm.getColumnType(i)==Types.FLOAT ||
							rsm.getColumnType(i)==Types.DOUBLE ||
							rsm.getColumnType(i)==Types.DECIMAL ||
							rsm.getColumnType(i)==Types.NUMERIC 
					){
						doc.setProperty(rsm.getColumnName(i),rs.getDouble(i));
					}
					else if(rsm.getColumnType(i)==Types.INTEGER ||
							rsm.getColumnType(i)==Types.SMALLINT ||
							rsm.getColumnType(i)==Types.TINYINT){
						doc.setProperty(rsm.getColumnName(i),rs.getInt(i));
					}
				}
			}

			return doc;

		}
		catch(Exception e){
			if(e instanceof RuntimeException){
				throw (RuntimeException)e;
			}
			else{
				throw new RuntimeException(e);
			}
		}

	}
}

/**
 * A cache to avoid repeated introspection 
 * Valid fields for the purposes of this EDS are public ones with setters
 * 
 */

class FieldInfoCache{
	private Map<Class<?>,List<FieldInfo>> fieldCache=new ConcurrentHashMap<Class<?>,List<FieldInfo>>();

	public List<FieldInfo> getFields(Class<?> clazz){
		List<FieldInfo> flist=fieldCache.get(clazz);
		if(flist!=null){
			return flist;
		}
		else{
			flist=FieldInfo.fromClass(clazz);
			fieldCache.put(clazz,flist);
		}
		return flist;
	}
}

class FieldInfo{
	private Field field=null;
	private FieldType fieldType=null;
	private Method stringValueOf=null;
	private Class<?> fieldClass=null;
	private Method setter=null;
	public enum FieldType{
		JAVA_LANG,
		PRIMITIVE,
		USER
	};

	public String toString(){
		StringBuilder sb=new StringBuilder();
		if(field==null)return "{null field}";
		sb.append("{name=").append(field.getName()).append(",");
		sb.append("fieldType=").append(fieldType).append(",");
		sb.append("fieldClass=");
		if(fieldClass==null)sb.append("null");
		else sb.append(fieldClass.getName());
		sb.append("}");
		return sb.toString();
	}

	public static List<FieldInfo> fromClass(Class<?> clazz){
		List<FieldInfo> fil=new ArrayList<FieldInfo>();

		for(Field f:clazz.getDeclaredFields()){
			FieldInfo fi=fromField(f);
			if(fi!=null)fil.add(fi);
		}
		return fil;
	}

	public static FieldInfo fromField(Field f){			
		for(Method m:f.getDeclaringClass().getDeclaredMethods()){
			if(m.getName().equals("set"+(f.getName().substring(0,1).toUpperCase()+f.getName().substring(1))) && ((m.getModifiers()&Modifier.PUBLIC)!=0)){
				try{
					FieldInfo fi=new FieldInfo();
					fi.field=f;

					//figure out field type
					String[] toks=f.getType().toString().split(" ");
					if(toks==null||toks[0]==null||(!toks[0].equals("class"))){
						fi.fieldType=FieldType.PRIMITIVE;
					}
					else{
						Class<?> fc=Class.forName(toks[1]);
						if(fc.getName().startsWith("java.lang")){
							fi.fieldType=FieldType.JAVA_LANG;
							if(!fc.getName().equals("java.lang.String")){
								fi.stringValueOf=fc.getMethod("valueOf", String.class);
							}
						}
						else {
							fi.fieldType=FieldType.USER;
						}
						fi.fieldClass=fc;
					}
					fi.setter=m;
					return fi;
				}
				catch(Exception e){
					//ignore
					e.printStackTrace();
				}
			}
		}
		return null;
	}

	public void setValueFromString(Object obj,String strval)throws Exception{
		Object res;
		if(fieldClass.getName().equals("java.lang.String")){
			res=strval;
		}
		else{
			res=stringValueOf.invoke(fieldClass, strval);
		}
		setFieldValue(obj,res);
	}

	public Class<?> getFieldClass(){
		return this.fieldClass;
	}

	public void setFieldValue(Object obj,Object value) throws Exception{
		setter.invoke(obj, value);
	}

	public Field getField() {
		return field;
	}

	public Method getStringValueOf() {
		return stringValueOf;
	}

	public Method getSetter() {
		return setter;
	}

	public FieldType getFieldType() {
		return fieldType;
	}

}

