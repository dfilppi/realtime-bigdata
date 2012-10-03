package org.openspaces.eds.support;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openspaces.core.GigaSpace;
import org.openspaces.core.GigaSpaceTypeManager;
import org.openspaces.document.annotations.DocumentFifoGrouping;
import org.openspaces.document.annotations.DocumentFifoGroupingIndex;
import org.openspaces.document.annotations.DocumentFixedField;
import org.openspaces.document.annotations.DocumentId;
import org.openspaces.document.annotations.DocumentIndex;
import org.openspaces.document.annotations.DocumentRouting;
import org.openspaces.document.annotations.SpaceDocument;
import org.openspaces.document.annotations.DocumentIndex.IndexType;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.gigaspaces.metadata.SpaceTypeDescriptor;
import com.gigaspaces.metadata.SpaceTypeDescriptorBuilder;
import com.gigaspaces.metadata.index.SpaceIndexType;

/**
 * Registers space document types based on annotation configuration.
 * Minimal first cut.  For example, doesn't support storage types.
 * Can be used as a Spring bean, or called directly.
 * 
 * @author DeWayne
 *
 */
public class SpaceTypeMaker implements ApplicationContextAware{
	private static java.util.logging.Logger log=java.util.logging.Logger.getLogger(SpaceTypeMaker.class.getName());
	private ApplicationContext ctx;
	private static final Object lock=new Object();
	private static SpaceTypeDescriptor[] cache;

	public SpaceTypeMaker(){}


	@Override
	public void setApplicationContext(ApplicationContext ctx)
	throws BeansException {
		this.ctx=ctx;
	}

	public SpaceTypeDescriptor[] getSpaceTypes(){
		synchronized(lock){
			if(cache==null){
				List<SpaceTypeDescriptor> results=new ArrayList<SpaceTypeDescriptor>();
				Map<String,Object> beans=ctx.getBeansWithAnnotation(SpaceDocument.class);

				for(Object obj:beans.values()){
					//name
					SpaceDocument doc=obj.getClass().getAnnotation(SpaceDocument.class);
					String name=doc.name();
					if(name.length()==0)name=obj.getClass().getName();
					SpaceTypeDescriptorBuilder stb=new SpaceTypeDescriptorBuilder(name);

					//Document annotation
					stb.supportsDynamicProperties(doc.dynamicProperties());
					stb.replicable(doc.replicable());
					stb.supportsOptimisticLocking(doc.optimisticLocking());
					stb.fifoSupport(doc.fifoSupport());

					//Fields
					for(Field f:obj.getClass().getDeclaredFields()){
						f.setAccessible(true);

						//id
						if(f.isAnnotationPresent(DocumentId.class)){
							stb.idProperty(f.getName(), f.getAnnotation(DocumentId.class).generate(), indexTypeFromField(f));
						}

						//routing
						if(f.isAnnotationPresent(DocumentRouting.class)){
							stb.routingProperty(f.getName());
							SpaceIndexType it=indexTypeFromField(f);
							if(it!=SpaceIndexType.NONE)stb.addPropertyIndex(f.getName(),it);
						}

						//fifo grouping: naive.  Doesn't support nested properties
						if(f.isAnnotationPresent(DocumentFifoGrouping.class)){
							stb.fifoGroupingProperty(f.getName());
							if(f.isAnnotationPresent(DocumentFifoGroupingIndex.class)){
								String path=f.getAnnotation(DocumentFifoGroupingIndex.class).indexPath();
								if(path.length()==0){
									path=f.getName();
								}
								stb.addFifoGroupingIndex(path);
							}
						}

						//fixed properties.  Underimplemented.  Naive.
						if(f.isAnnotationPresent(DocumentFixedField.class)){
							stb.addFixedProperty(f.getName(),f.getType());
						}

					}
					SpaceTypeDescriptor desc=stb.create();
					log.info("adding document type= "+desc.getTypeName());
					results.add(desc);
				}
				cache=results.toArray(new SpaceTypeDescriptor[]{});
			}
			return cache;
		}
	}

	//Utility to get index for field (if any)
	private SpaceIndexType indexTypeFromField(Field f){
		SpaceIndexType it=SpaceIndexType.NONE;
		if(f.isAnnotationPresent(DocumentIndex.class)){
			DocumentIndex.IndexType dit=f.getAnnotation(DocumentIndex.class).indexType();
			if(dit==IndexType.BASIC)it=SpaceIndexType.BASIC;
			if(dit==IndexType.EXTENDED)it=SpaceIndexType.EXTENDED;
		}
		return it;

	}

}
