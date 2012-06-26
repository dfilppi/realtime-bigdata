package org.openspaces.persistence.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Properties;

import org.springframework.stereotype.Component;

import com.gigaspaces.datasource.BulkDataPersister;
import com.gigaspaces.datasource.BulkItem;
import com.gigaspaces.datasource.DataIterator;
import com.gigaspaces.datasource.DataSourceException;
import com.gigaspaces.datasource.ManagedDataSource;

@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface Persistent {
	Class<? extends BulkDataPersister>[] persistTo() default {NullBulkDataPersister.class};
	Class<? extends ManagedDataSource<?>> loadFrom() default NullManagedDataSource.class;
}

class NullBulkDataPersister implements BulkDataPersister{
	@Override
	public void executeBulk(List<BulkItem> arg0) throws DataSourceException {
	}
}

class NullManagedDataSource implements ManagedDataSource<Object>{
	@Override
	public void init(Properties arg0) throws DataSourceException {
	}
	@Override
	public DataIterator<Object> initialLoad() throws DataSourceException {
		return null;
	}
	@Override
	public void shutdown() throws DataSourceException {
	}
}

