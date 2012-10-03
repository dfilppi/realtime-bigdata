package com.test;

import com.gigaspaces.annotation.pojo.SpaceId;

//Below commented because can't mix Pojos and documents
//@Persistent(persistTo=CassandraEDS.class, loadFrom=CassandraEDS.class)
public class MyData {

	String first;
	String last;
	Integer age;
	Long id;
	
	public String getFirst() {
		return first;
	}
	public void setFirst(String first) {
		this.first = first;
	}
	public String getLast() {
		return last;
	}
	public void setLast(String last) {
		this.last = last;
	}
	public Integer getAge() {
		return age;
	}
	public void setAge(Integer age) {
		this.age = age;
	}
	
	@SpaceId (autoGenerate = false)
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
}
