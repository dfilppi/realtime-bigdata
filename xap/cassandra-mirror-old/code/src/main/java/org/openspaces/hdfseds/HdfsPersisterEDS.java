package org.openspaces.hdfseds;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.openspaces.eds.support.AbstractAnnotationDrivenEDS;

import com.gigaspaces.datasource.BulkDataPersister;
import com.gigaspaces.datasource.BulkItem;
import com.gigaspaces.datasource.DataIterator;
import com.gigaspaces.datasource.DataSourceException;
import com.gigaspaces.datasource.ManagedDataSource;
import com.gigaspaces.metadata.SpaceTypeDescriptor;
import com.j_spaces.core.IGSEntry;

/**
 * An HDFS persister (EDS) that writes to HDFS.  Like HDFS itself, it
 * is an append only persister.  TODO HDFS file naming strategy should be pluggable.
 * 
 * @author DeWayne
 *
 */
public class HdfsPersisterEDS extends AbstractAnnotationDrivenEDS implements ManagedDataSource<Object>,BulkDataPersister {
	private static final Logger log=Logger.getLogger(HdfsPersisterEDS.class.getName());
	private final FileSystem fileSystem;
	private final String basePath;
	private final int maxRecords;
	private FSDataOutputStream output=null;;
	private AtomicInteger recCnt=new AtomicInteger(0);
	private SimpleDateFormat dateFormat=new SimpleDateFormat("yyyyMMdd-HH-mm-ss");

	public HdfsPersisterEDS(SpaceTypeDescriptor[] doctypes,Configuration cfg, String basePath, int maxRecords){
		try{
			this.setDoctypes(doctypes);
			Map.Entry<String,String> entry=null;
			fileSystem=FileSystem.get(cfg);
			this.basePath=basePath;
			if(this.basePath==null)basePath="/";
			this.maxRecords=maxRecords;
			Path newpath=newFilePath();
			log.info("creating hdfs file="+newpath.toString());
			output=fileSystem.create(newpath);
		}
		catch(Exception e){
			if(e instanceof RuntimeException)throw (RuntimeException)e;
			else throw new RuntimeException(e);
		}
	}

	public HdfsPersisterEDS(List<Class<?>> classes,Configuration cfg, String basePath, int maxRecords){
		try{
			this.setClasses(classes);
			Map.Entry<String,String> entry=null;
			fileSystem=FileSystem.get(cfg);
			this.basePath=basePath;
			if(this.basePath==null)basePath="/";
			this.maxRecords=maxRecords;
			Path newpath=newFilePath();
			log.info("creating hdfs file="+newpath.toString());
			output=fileSystem.create(newpath);
		}
		catch(Exception e){
			if(e instanceof RuntimeException)throw (RuntimeException)e;
			else throw new RuntimeException(e);
		}
	}
	
	@Override
	public void executeBulk(List<BulkItem> bulkItems) throws DataSourceException {
		log.fine("executeBulk called");

		//need new output file?
		if(bulkItems.size()>0){
			if(recCnt.get()>=maxRecords){
				try{
					output.close();
				}catch(Exception e){}
				try {
					Path newpath=newFilePath();
					log.info("creating hdfs file="+newpath.toString());
					output=fileSystem.create(newFilePath());
					recCnt.set(0);
				} catch (IOException e) {
					throw new DataSourceException(e);
				}
			}
		}

		for (BulkItem bulkItem : bulkItems) {
			IGSEntry item = (IGSEntry) bulkItem.getItem();
			
			if((isPojoConfigured() && isClassPersistable(bulkItem.getTypeName())) ||
					!isPojoConfigured() && isDocPersistable(bulkItem.getTypeName())){
				
				switch (bulkItem.getOperation()) {
				case BulkItem.REMOVE:
					break;

				case BulkItem.WRITE:
				case BulkItem.UPDATE:
					String rec=createRecord(item);
					log.fine("writing hdfs rec="+rec);
					try{
						output.writeBytes(rec);
						output.flush();
					}catch(Exception e){
						if( e instanceof RuntimeException)throw (RuntimeException)e;
						else throw new RuntimeException(e);
					}
					recCnt.addAndGet(1);
					break;

				}
			}
		}
	}

	/**
	 * Create hdfs record.  Super simplistic.  No comma escaping 
	 *  
	 * @param item
	 * @return
	 */
	private String createRecord(IGSEntry item) {
		StringBuilder sb=new StringBuilder();
		for(Object val:item.getFieldsValues()){
			sb.append(val.toString()).append(",");
		}
		if(sb.length()>0)sb.deleteCharAt(sb.length()-1);
		sb.append("\n");
		return sb.toString();
	}


	@Override
	public void init(Properties props) throws DataSourceException {

	}

	@Override
	public DataIterator<Object> initialLoad() throws DataSourceException {
		return null;
	}

	@Override
	public void shutdown() throws DataSourceException {

	}

	private Path newFilePath(){
		return new Path(basePath+"/space-"+dateFormat.format(new Date()));
	}


}


