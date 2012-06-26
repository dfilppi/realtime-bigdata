package org.openspaces.cassandraeds;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openspaces.eds.support.AbstractAnnotationDrivenEDS;
import org.openspaces.eds.support.FieldSerializer;

import com.gigaspaces.datasource.BulkDataPersister;
import com.gigaspaces.datasource.BulkItem;
import com.gigaspaces.datasource.DataIterator;
import com.gigaspaces.datasource.DataSourceException;
import com.gigaspaces.datasource.ManagedDataSource;
import com.gigaspaces.datasource.SQLDataProvider;
import com.gigaspaces.metadata.SpaceTypeDescriptor;
import com.j_spaces.core.IGSEntry;
import com.j_spaces.core.client.SQLQuery;
import com.jolbox.bonecp.BoneCPDataSource;

/**
 * Cassandra EDS. Pure annotation config only.
 * 
 * @author DeWayne
 *
 */
public class CassandraEDS extends AbstractAnnotationDrivenEDS implements ManagedDataSource<Object>,BulkDataPersister,SQLDataProvider<Object>{
	private static final Logger log=Logger.getLogger(CassandraEDS.class.getName());
	private final BoneCPDataSource connectionPool;
	private final FieldSerializer fieldSerializer;

	ConcurrentHashMap<String, String> insertSQLCache = new ConcurrentHashMap<String, String> ();
	ConcurrentHashMap<String, String> updateSQLCache = new ConcurrentHashMap<String, String> ();

	/**
	 * Constructor for document-based EDS
	 * 
	 */
	public CassandraEDS(
			BoneCPDataSource connectionPool,
			FieldSerializer fs,
			SpaceTypeDescriptor[] doctypes
	){

		if(classes!=null)throw new RuntimeException("can't have both docs and pojos");
		this.connectionPool=connectionPool;
		this.fieldSerializer=fs;
		setDoctypes(doctypes);
	}

	/**
	 * Constructor for pojo-based EDS
	 * 
	 */
	public CassandraEDS(
			BoneCPDataSource connectionPool,
			FieldSerializer fs,
			List<Class<?>> classes
	){

		if(doctypes!=null)throw new RuntimeException("can't have both docs and pojos");
		this.connectionPool=connectionPool;
		this.fieldSerializer=fs;
		setClasses(classes);
	}


	/**
	 * Checks to see if Cassandra is listening.
	 */
	public void init(Properties properties) throws DataSourceException {
		try{
			Connection test=connectionPool.getConnection();
			if(test==null){
				throw new DataSourceException("no connections available");
			}
			test.close();
		}
		catch(Exception e){
			throw new DataSourceException(e);
		}
	}

	@Override
	public void shutdown() throws DataSourceException {
		connectionPool.close();
	}


	@Override
	public void executeBulk(List<BulkItem> bulkItems)
	throws DataSourceException {
		Connection con = null;
		log.fine("execute bulk called: itemcnt="+bulkItems.size());
		
		try {
			con = connectionPool.getConnection();
			for (BulkItem bulkItem : bulkItems) {

				if((isPojoConfigured() && isClassPersistable(bulkItem.getTypeName())) ||
						!isPojoConfigured() && isDocPersistable(bulkItem.getTypeName())){

					IGSEntry item = (IGSEntry) bulkItem.getItem();
					String clazzName = item.getClassName().replaceAll("\\.", "_");

					String ID=item.getFieldValue(item.getPrimaryKeyName()).toString();

					if(item.getFieldType(item.getPrimaryKeyName()).equals("java.lang.String")){
						ID="'"+ID+"'";
					}

					switch (bulkItem.getOperation()) {
					case BulkItem.REMOVE:
						String deleteQL = "DELETE FROM " + clazzName
						+ " WHERE KEY = " + ID;
						try {
							log.info("removing :  "+deleteQL);
							executeCQL(con, deleteQL);
						} catch (Exception e) {
							e.printStackTrace();
						}

						break;
					case BulkItem.WRITE:

						String insertQL = null;
						int fldCount = item.getFieldsNames().length;
						StringBuilder insertQLBuf=new StringBuilder();
						insertQL=insertSQLCache.get(clazzName);
						if (insertQL==null)
						{
							insertQLBuf.append( "INSERT INTO " ).append( clazzName).append(" (KEY,");
							for (int i = 0; i < fldCount ; i++) {
								insertQLBuf.append( " '").append( item.getFieldsNames()[i]).append( "',");
							}
							insertQLBuf.deleteCharAt(insertQLBuf.length()-1);
							insertSQLCache.put(clazzName,insertQLBuf.toString());
						}
						else{
							insertQLBuf.append(insertQL);
						}

						insertQLBuf.append( ") VALUES (").append( ID).append(",");
						for (int i = 0; i < fldCount; i++) {
							insertQLBuf=
								insertQLBuf.append(getValue(item.getFieldsNames()[i],item.getFieldValue(i)))
								.append(",");
						}
						insertQL = insertQLBuf.deleteCharAt(insertQLBuf.length()-1).append(")").toString();
						try {
							log.info("inserting: "+insertQL);
							executeCQL(con, insertQL);
						} catch (Exception e) {
							e.printStackTrace();
						}
						break;

					case BulkItem.UPDATE:
						fldCount = item.getFieldsNames().length;
						StringBuilder updateQL=new StringBuilder("");
						updateQL.append("UPDATE ").append(clazzName).append(" SET");
						for (int i = 0; i < fldCount ; i++) {
							updateQL.
							append(" '").
							append(item.getFieldsNames()[i]).
							append("'=").
							append(getValue(item.getFieldsNames()[i],item.getFieldsValues()[i])).
							append(",");
						}
						updateQL.deleteCharAt(updateQL.length()-1);

						updateQL.append(" WHERE KEY=").append(ID);
						try {
							log.info("updating: "+updateQL.toString());

							executeCQL(con, updateQL.toString());
						} catch (Exception e) {
							e.printStackTrace();
						}
						break;
					}
				}
			}
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		finally{
			try {
				con.close();
			} catch (SQLException e) {
				log.log(Level.WARNING,e.getMessage(),e);
			}
		}
	}

	@Override
	public DataIterator<Object> initialLoad() throws DataSourceException {
		Connection cn=null;
		try {
			cn=connectionPool.getConnection();

			if (doctypes==null) return Iterators.newMultiClassIterator(classes,fieldSerializer,cn);
			else return Iterators.newMultiDocumentIterator(doctypes,fieldSerializer,cn);
		} catch (SQLException e) {
			throw new DataSourceException(e);
		}
	}


	@Override
	public DataIterator<Object> iterator(SQLQuery<Object> query)throws DataSourceException {
		Connection cn=null;
		try{
			cn=connectionPool.getConnection();
		}
		catch(Exception e){
			if(e instanceof RuntimeException)throw (RuntimeException)e;
			else throw new RuntimeException(e);
		}
		if (doctypes==null)return Iterators.newSQLIterator(query, fieldSerializer,classes, cn); 
		else return Iterators.newSQLDocumentIterator(query,fieldSerializer,doctypes,cn);
	}


	/**
	 * Gets the string value of a field.  For non-java types,
	 * uses supplied serializer.  Presumes only a single level
	 * of object nesting.
	 * 
	 * @param fieldName the field to get the value of
	 * @param val the object value of the field to be converted to string
	 * @return a java.lang.String value of the field
	 */
	private String getValue(String fieldName,Object val)
	{
		if (val == null)
			return "''";
		else
		{
			//Note - presumes non-collection fields
			log.fine("getValue val type:"+val.getClass().getName());
			if(val.getClass().getName().startsWith("java.lang.")){
				String str = val.toString();
				if (str.indexOf("'") > 0)
				{
					return "'" +str.replaceAll("'", "''") + "'" ;
				}
				else
					return "'" + str + "'";
			}
			else{
				return "'"+fieldSerializer.serialize(val)+"'";
			}
		}
	}


	private void executeCQL(Connection con, String cql) throws SQLException {
		Statement statement = con.createStatement();
		statement.execute(cql);
		statement.close();
	}

}


