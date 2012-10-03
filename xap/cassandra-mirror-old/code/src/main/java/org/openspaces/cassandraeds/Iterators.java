package org.openspaces.cassandraeds;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.openspaces.eds.support.FieldSerializer;
import org.openspaces.eds.support.Util;

import com.gigaspaces.datasource.DataIterator;
import com.gigaspaces.document.SpaceDocument;
import com.gigaspaces.metadata.SpaceTypeDescriptor;
import com.j_spaces.core.client.SQLQuery;

/**
 * Factories for iterators
 * 
 * @author DeWayne
 *
 */
public class Iterators {
	private static final Logger log=Logger.getLogger(Iterators.class.getName());

	public static DataIterator<Object> newMultiClassIterator(List<Class<?>> classes,FieldSerializer fieldSerializer,Connection cn){
		return new MultiClassIterator(classes,fieldSerializer,cn);
	}

	public static <T> SQLIterator<T> newSQLIterator(SQLQuery<T> query,FieldSerializer fieldSerializer,List<Class<?>> classes,Connection cn){
		return new SQLIterator<T>(query,fieldSerializer,classes,cn);
	}
	
	public static SQLDocumentIterator newSQLDocumentIterator(SQLQuery<Object> query,FieldSerializer fieldSerializer,List<SpaceTypeDescriptor> desc,Connection cn){
		return new SQLDocumentIterator(query,fieldSerializer,desc,cn);
	}
	
	public static DataIterator<Object> newMultiDocumentIterator(
			List<SpaceTypeDescriptor> doctypes,
			FieldSerializer fieldSerializer, Connection cn) {
		return new MultiDocumentIterator(doctypes,fieldSerializer,cn);
	}
	
	public static class SQLDocumentIterator implements DataIterator<Object>
	{
		private ResultSet rs;
		private FieldSerializer fieldSerializer;
		private Connection conn;
		private List<SpaceTypeDescriptor> desc;
		private int curIndex=0;

		public SQLDocumentIterator(SQLQuery<Object> query, FieldSerializer fieldSerializer, List<SpaceTypeDescriptor> desc,Connection cn){
			try{
				this.fieldSerializer=fieldSerializer;
				this.conn=cn;
				this.desc=desc;
			}
			catch(Exception e){
				if(e instanceof RuntimeException)throw (RuntimeException)e;
				else throw new RuntimeException(e);
			}
		}

		@Override
		public boolean hasNext() {
			try{
				if(curIndex<desc.size()-1)return false;
				return !rs.isLast();
			}catch(SQLException e){
				throw new RuntimeException(e);
			}
		}

		@Override
		public SpaceDocument next() {
			try{
				while(rs.next()){
					SpaceDocument res=Util.deserializeDocument(desc.get(curIndex),rs,fieldSerializer);
					if(res!=null)return res;
				}
				//find next resultset with data
				while(true){
					rs=nextResultset();
					if(rs==null)return null;
					if(!rs.next())continue;
					while(rs.next()){
						SpaceDocument res=Util.deserializeDocument(desc.get(curIndex),rs,fieldSerializer);
						if(res!=null)return res;
					}
				}
			}
			catch(SQLException e){
				throw new RuntimeException(e);
			}

		}

		@Override
		public void remove() {
			// NOOP
		}

		@Override
		public void close() {
			try{
				if(conn!=null)conn.close();
			}catch(Exception e){
			}
		}
		
		private ResultSet nextResultset(){
			curIndex++;
			if(curIndex>=desc.size())return null;

			try{ rs.close();}catch(Exception e){}

			try{
				Statement statement = conn.createStatement();
				ResultSet rs=statement.executeQuery("select * from "+desc.get(curIndex).getTypeName());
				statement.close();
				return rs;
			}
			catch(Exception e){
				if(e instanceof RuntimeException)throw((RuntimeException)e);
				else throw new RuntimeException(e);
			}
		}

	}

	public static class SQLIterator<T> implements DataIterator<T>{
		private Class<T> clazz;
		private List<Class<?>> classes=null;
		private ResultSet rs;
		private FieldSerializer fieldSerializer;
		private Connection conn;
		private int curIndex=0;

		@SuppressWarnings("unchecked")
		public SQLIterator(SQLQuery<T> query, FieldSerializer fieldSerializer, List<Class<?>> classes,Connection cn){
			try{
				this.fieldSerializer=fieldSerializer;
				this.conn=cn;
				this.clazz=(Class<T>)Class.forName(query.getTypeName());
				this.classes=new ArrayList<Class<?>>();
				for(Class<?> c:classes){
					if(clazz.isAssignableFrom(c)){
						this.classes.add(c);
					}
				}
				this.rs=sqlquery(query);
			}
			catch(Exception e){
				if(e instanceof RuntimeException)throw (RuntimeException)e;
				else throw new RuntimeException(e);
			}
		}

		//Note: always returns pojo (fails for document type query)
		private ResultSet sqlquery(SQLQuery<T> query) {
			try {
				String where=query.getQuery();

				StringBuilder sb=new StringBuilder("select * from ");
				sb.append(classes.get(0).getName().replaceAll("\\.", "_"));
				if(where.length()>0){
					sb.append(" where ").append(query.getQuery());
				}

				Statement st=conn.createStatement();

				Object[] preparedValues = query.getParameters();
				int qindex=0;
				if (preparedValues != null) {
					for (int i = 0; i < preparedValues.length; i++) {
						//st.setObject(i+1, preparedValues[i]);
						//Should use above "setObject" (with PreparedStatement), but it doesn't work
						qindex=sb.indexOf("?",qindex);
						if(qindex==-1)break;
						sb.deleteCharAt(qindex);
						if(preparedValues[i] instanceof String)sb.insert(qindex++,"'");
						sb.insert(qindex,preparedValues[i].toString());
						qindex+=preparedValues[i].toString().length();
						if(preparedValues[i] instanceof String)sb.insert(qindex,"'");
					}
				}

				log.fine("query="+sb.toString());
				return st.executeQuery(sb.toString());
			} catch (Exception e) {
				if(e instanceof RuntimeException)throw((RuntimeException)e);
				else throw new RuntimeException(e);
			}
		}

		@Override
		public boolean hasNext() {
			try{
				if(curIndex<classes.size()-1)return false;
				return !rs.isLast();
			}catch(SQLException e){
				throw new RuntimeException(e);
			}
		}

		@Override
		public T next() {

			try{
				while(rs.next()){
					T res=(T)Util.deserializeObject(classes.get(curIndex),rs,fieldSerializer);
					if(res!=null)return res;
				}
				//find next resultset with data
				while(true){
					rs=nextResultset();
					if(rs==null)return null;
					if(!rs.next())continue;
					while(rs.next()){
						T res=(T)Util.deserializeObject(classes.get(curIndex),rs,fieldSerializer);
						if(res!=null)return res;
					}
				}
			}
			catch(SQLException e){
				throw new RuntimeException(e);
			}

		}

		public void remove() {
			// NOOP
		}

		public void close() {
			try{
				//if(rs!=null)rs.close();
				if(conn!=null)conn.close();
			}catch(Exception e){
			}
		}

		private ResultSet nextResultset(){
			curIndex++;
			if(curIndex>=classes.size())return null;

			try{ rs.close();}catch(Exception e){}

			try{
				Statement statement = conn.createStatement();
				ResultSet rs=statement.executeQuery("select * from "+classes.get(curIndex).getName().replaceAll("\\.", "_"));
				statement.close();
				return rs;
			}
			catch(Exception e){
				if(e instanceof RuntimeException)throw((RuntimeException)e);
				else throw new RuntimeException(e);
			}
		}

	}

	/**
	 * Iterates over all listed classes 
	 * 
	 * @author DeWayne
	 *
	 * @param <T>
	 */
	public static class MultiClassIterator implements DataIterator<Object>{
		private static final Logger log=Logger.getLogger(MultiClassIterator.class.getName());
		private List<Class<?>> classes;
		private FieldSerializer fieldSerializer;
		private Connection cn;
		private ResultSet rs;
		private int curIndex=-1;

		public MultiClassIterator(List<Class<?>> classes,FieldSerializer fieldSerializer,Connection cn){
			this.classes=classes;
			this.fieldSerializer=fieldSerializer;
			this.cn=cn;
			this.rs=nextResultset();
		}

		public boolean hasNext() {
			try{
				if(curIndex<classes.size()-1)return false;
				return !rs.isLast();
			}catch(SQLException e){
				throw new RuntimeException(e);
			}
		}

		public Object next() {
			try{
				while(rs.next()){
					Object res=Util.deserializeObject(classes.get(curIndex),rs,fieldSerializer);
					if(res!=null)return res;
				}

				//find next resultset with data
				while(true){
					rs=nextResultset();
					if(rs==null)return null;
					if(!rs.next())continue;
					while(rs.next()){
						Object res=Util.deserializeObject(classes.get(curIndex),rs,fieldSerializer);
						if(res!=null)return res;
					}
				}
			}
			catch(SQLException e){
				throw new RuntimeException(e);
			}
		}

		public void remove() {
			//NOOP
		}

		public void close() {
			try{
				if(rs!=null)rs.close();
				if(cn!=null)cn.close();
			}catch(Exception e){
			}
		}

		private ResultSet nextResultset(){
			curIndex++;
			if(curIndex>=classes.size())return null;

			try{
				Statement statement = cn.createStatement();
				ResultSet rs=statement.executeQuery("select * from "+classes.get(curIndex).getName().replaceAll("\\.", "_"));
				statement.close();
				return rs;
			}
			catch(Exception e){
				if(e instanceof RuntimeException)throw((RuntimeException)e);
				else throw new RuntimeException(e);
			}
		}

	}

	/**
	 * Makes the assumption that type names that include "." are java classes
	 * @author DeWayne
	 *
	 */
	public static class MultiDocumentIterator implements DataIterator<Object>{
		private static final Logger log=Logger.getLogger(MultiClassIterator.class.getName());
		private List<SpaceTypeDescriptor> doctypes=null;
		private final FieldSerializer fieldSerializer;
		private final Connection cn;
		private ResultSet rs;
		private int curIndex=-1;
		
		public MultiDocumentIterator(
				List<SpaceTypeDescriptor> doctypes,
				FieldSerializer fieldSerializer, Connection cn) {
			this.doctypes=doctypes;
			this.fieldSerializer=fieldSerializer;
			this.cn=cn;
			this.rs=nextResultset();
		}

		public boolean hasNext() {
			try{
				if(curIndex<doctypes.size()-1)return false;
				return !rs.isLast();
			}catch(SQLException e){
				throw new RuntimeException(e);
			}
		}

		public Object next() {
			try{
				while(rs.next()){
					Object res=Util.deserializeDocument(doctypes.get(curIndex),rs,fieldSerializer);
					if(res!=null)return res;
				}

				//find next resultset with data
				while(true){
					rs=nextResultset();
					if(rs==null)return null;
					if(!rs.next())continue;
					while(rs.next()){
						Object res=Util.deserializeDocument(doctypes.get(curIndex),rs,fieldSerializer);
						if(res!=null)return res;
					}
				}
			}
			catch(SQLException e){
				throw new RuntimeException(e);
			}
		}

		public void remove() {
			//NOOP
		}

		public void close() {
			try{
				if(rs!=null)rs.close();
				if(cn!=null)cn.close();
			}catch(Exception e){
			}
		}

		private ResultSet nextResultset(){
			curIndex++;
			if(curIndex>=doctypes.size())return null;

			try{
				Statement statement = cn.createStatement();
				log.info("query="+"select * from "+doctypes.get(curIndex).getTypeName());
				ResultSet rs=statement.executeQuery("select * from "+doctypes.get(curIndex).getTypeName());
				statement.close();
				return rs;
			}
			catch(Exception e){
				if(e instanceof RuntimeException)throw((RuntimeException)e);
				else throw new RuntimeException(e);
			}
		}

	}

}
