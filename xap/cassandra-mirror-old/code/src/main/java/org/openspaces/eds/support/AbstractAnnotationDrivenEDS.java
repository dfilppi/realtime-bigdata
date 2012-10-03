package org.openspaces.eds.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.openspaces.document.annotations.SpaceDocument;
import org.openspaces.persistence.annotations.Persistent;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.gigaspaces.metadata.SpaceTypeDescriptor;

/**
 * Utility subclass providing classpath scanning for persistence annotations.
 * Subclasses must set either the doctypes or classes List.
 * 
 * @author DeWayne
 *
 */
public abstract class AbstractAnnotationDrivenEDS implements ApplicationContextAware {
	private static Logger log=Logger.getLogger(AbstractAnnotationDrivenEDS.class.getName());
	protected ApplicationContext ctx=null;
	protected List<Class<?>> classes=null;
	protected Set<String> classSet=new HashSet<String>();
	protected List<SpaceTypeDescriptor> doctypes=null;
	protected Set<String> doctypesSet=new HashSet<String>();
	
	public void setClasses(List<Class<?>> classes){
		if(doctypes!=null)throw new RuntimeException("only docs OR pojos, not both allowed");
		this.classes=classes;
	}
	
	public void setDoctypes(SpaceTypeDescriptor[] desc){
		if(classes!=null)throw new RuntimeException("only docs OR pojos, not both allowed");
		this.doctypes=new ArrayList<SpaceTypeDescriptor>();
		for(SpaceTypeDescriptor doc:desc){
			this.doctypes.add(doc);
		}
	}
	
	public boolean isPojoConfigured(){
		if(doctypes==null && classes==null)throw new RuntimeException("no persistables found");
		return doctypes==null;
	}
	
	public boolean isClassPersistable(String name){
		if(!isPojoConfigured())return false;
		return classSet.contains(name);
	}
	
	public boolean isDocPersistable(String name){
		if(isPojoConfigured())return false;
		return doctypesSet.contains(name);
	}
	
	@Override
	public void setApplicationContext(ApplicationContext ctx)
			throws BeansException {
		if(this.ctx!=null)return;
		this.ctx=ctx;
		
		//grab pojos defined if any
		Map<String,Object> beans=ctx.getBeansWithAnnotation(Persistent.class);
		log.info("found persistable #:"+beans.size());
	
		//create list, filtering out classes that target other persisters
		List<String> persistabledocs=new ArrayList<String>();
		for(Object obj:beans.values()){
			Persistent p=obj.getClass().getAnnotation(Persistent.class);
			boolean persist=false;
			for(Class<?> c:p.persistTo()){
				if(c.equals(this.getClass())){
					persist=true;break;
				}
			}
			if(persist || p.loadFrom().equals(this.getClass())){
				if(obj.getClass().isAnnotationPresent(SpaceDocument.class)){
					//persistent document
					log.fine("found persistable doc named:"+obj.getClass().getAnnotation(SpaceDocument.class).name());
					persistabledocs.add(obj.getClass().getAnnotation(SpaceDocument.class).name());
				}
				else{
					if(doctypes!=null)throw new RuntimeException("can't have both docs and pojos");
					classes.add(obj.getClass());
					classSet.add(obj.getClass().getName());
				}
			}
		}
		//filter out unpersistable docs if needed
		if(doctypes!=null){
			//yes inefficient, but normally small and only once
			for(int i=doctypes.size()-1;i>=0;i--){
				//assumes no dups
				boolean found=false;
				for(String name:persistabledocs){
					if(doctypes.get(i).getTypeName().equals(name)){
						found=true;
						break;
					}
				}
				if(!found){
					log.fine("removing doc:"+doctypes.get(i).getTypeName());
					doctypes.remove(i);
				}
				else{
					log.info("adding doctype="+doctypes.get(i).getTypeName());
					doctypesSet.add(doctypes.get(i).getTypeName());
				}
			}
		}
		
	}

}
