package org.openspaces.document.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

import com.gigaspaces.annotation.pojo.FifoSupport;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Component
public @interface SpaceDocument {
	String name() default "";
	FifoSupport fifoSupport() default FifoSupport.OPERATION;
	boolean replicable() default true;
	boolean dynamicProperties() default true;
	boolean optimisticLocking() default false;
}
