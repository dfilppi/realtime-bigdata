package org.openspaces.eds.support;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.gigaspaces.datasource.BulkDataPersister;
import com.gigaspaces.datasource.BulkItem;
import com.gigaspaces.datasource.DataIterator;
import com.gigaspaces.datasource.DataSourceException;
import com.gigaspaces.datasource.ManagedDataSource;
import com.gigaspaces.datasource.SQLDataProvider;
import com.j_spaces.core.client.SQLQuery;


/**
 * Simple multi-mirror. Just wraps individual EDS implementations.  Assumes that
 * supporting EDSs are smart about handling Documents vs Objects.
 * 
 * There is one reader (which initial loads and performs persistent space operations)
 * and any number of mirror EDSs (which are assumed to be identified by the
 * Persistent annotation
 * 
 * @author DeWayne
 *
 */
public class MultiEDS implements ManagedDataSource<Object>,BulkDataPersister,SQLDataProvider<Object>{
	private static final java.util.logging.Logger log=Logger.getLogger(MultiEDS.class.getName());
	private SQLDataProvider<Object> reader=null;
	private List<BulkDataPersister> persisters=null;
	private ExecutorService executor=Executors.newCachedThreadPool();
	private ExecutorCompletionService<Boolean> compSvc=new ExecutorCompletionService<Boolean>(executor);
	
	public SQLDataProvider<Object> getReader() {
		return reader;
	}

	public void setReader(SQLDataProvider<Object> reader) {
		this.reader = reader;
	}

	public List<BulkDataPersister> getPersisters() {
		return persisters;
	}

	public void setPersisters(List<BulkDataPersister> persisters) {
		this.persisters = persisters;
	}

	@Override
	public DataIterator<Object> iterator(SQLQuery<Object> query)
			throws DataSourceException {

		if(reader==null)return null;
		return reader.iterator(query);
	}

	@Override
	public void executeBulk(List<BulkItem> items) throws DataSourceException {
		//persists to all writers async with join
		for(BulkDataPersister p:persisters){
			compSvc.submit(new BulkWriter(p,items,log));
		}
		for(int i=0;i<persisters.size();i++){
			try {
				compSvc.take();
			} catch (InterruptedException e) {
			}
		}
	}

	@Override
	public void init(Properties props) throws DataSourceException {
		if(reader!=null)reader.init(props);
		for(BulkDataPersister p:persisters){
			if(p instanceof ManagedDataSource){
				((ManagedDataSource)p).init(props);
			}
		}
	}

	@Override
	public DataIterator<Object> initialLoad() throws DataSourceException {
		if(reader==null)return null;
		return reader.initialLoad();
	}

	@Override
	public void shutdown() throws DataSourceException {
		if(reader!=null)reader.shutdown();
		for(BulkDataPersister p:persisters){
			if(p instanceof ManagedDataSource){
				((ManagedDataSource)p).shutdown();
			}
		}
	}

}

class BulkWriter implements Callable<Boolean>{
	private BulkDataPersister writer;
	private List<BulkItem> items;
	private Logger log;
	
	public BulkWriter(BulkDataPersister writer,List<BulkItem> items, Logger log){
		this.writer=writer;
		this.items=items;
		this.log=log;
	}
	
	@Override
	public Boolean call() throws Exception {
		try{
			writer.executeBulk(items);
		}
		catch(Exception e){
			log.log(Level.SEVERE,"exception caught in mirror",e);
			return false;
		}
		return true;
	}
	
}
