package com.test;

import java.util.Random;

import org.openspaces.core.GigaSpace;
import org.openspaces.core.GigaSpaceConfigurer;
import org.openspaces.core.space.UrlSpaceConfigurer;

import com.gigaspaces.document.SpaceDocument;
import com.gigaspaces.metadata.SpaceTypeDescriptorBuilder;

public class CassandraEDSTest implements Runnable{

	static GigaSpace gigaspace ;
	static long MAX_WRITE = 5000;
	static long MAX_BATCH_WRITE = 500;
	public static void main(String[] args) throws Exception {
		
		gigaspace = new GigaSpaceConfigurer(new UrlSpaceConfigurer("jini://*/*/mySpace")).gigaSpace();

		Thread.sleep(2000);

//		simpleTest();
		
//		complexTest();
		
		new CassandraEDSTest().documentTest();
		
	}


	static void complexTest() throws Exception
	{
		Thread tr[] = new Thread[4];
		for (int i=0;i<4;i++)
		{
			tr[i] = new Thread(new CassandraEDSTest());
		}
		for (int i=0;i<4;i++)
		{
			tr[i].start();
		}
		for (int i=0;i<4;i++)
		{
			tr[i].join();
		}
	}

	static void simpleTest()
	{
		MAX_WRITE  = 30;
		MAX_BATCH_WRITE = 2;
		new CassandraEDSTest().run();
	}
	
	public void documentTest(){
		/*gigaspace.getTypeManager().registerTypeDescriptor(new SpaceTypeDescriptorBuilder("com_test_MyData").
				idProperty("id").
				addFixedProperty("fixed",String.class).
				create()
				);*/
		
		SpaceDocument doc=new SpaceDocument("com_test_MyData");
		doc.setProperty("id", 9999);
		doc.setProperty("first","f1");
		doc.setProperty("last","l1");
		doc.setProperty("age",30);
		
		gigaspace.write(doc);
		
		
		if(true)return;
		
	}
	
	@Override
	public void run() {
		

		MyData o = new MyData();
				
		Random rand = new Random();
		long offset = Thread.currentThread().getId() * 100; 
		
		// Testing Write
		System.out.println("Thread ID:" + Thread.currentThread().getId() + " Testing Write");
		for (long i=offset ;i<MAX_WRITE + offset ;i++)
		{
			o.setAge(10 + rand .nextInt(10));
			o.setFirst("first" + i);
			o.setLast("last" + i);
			o.setId(i);
			gigaspace.write(o);
		}

		// Testing Update
		System.out.println("Thread ID:" + Thread.currentThread().getId() + " Testing Update");
		for (long i=offset;i<(MAX_WRITE / 10)+offset;i++)
		{
			o.setAge(10 + rand .nextInt(10));
			o.setFirst("firstXX" + i);
			o.setLast("lastYY" + i);
			o.setId(i);
			gigaspace.write(o);
		}

		// Testing Take
		System.out.println("Thread ID:" + Thread.currentThread().getId() + " Testing Take");
		o = new MyData();
		for (long i=offset+10;i<offset+20;i++)
		{
			o.setId(i);
			gigaspace.take(o);
		}
		
		// Testing WriteMultiple/UpdateMultiple
		System.out.println("Thread ID:" + Thread.currentThread().getId() + " Testing WriteMultiple/UpdateMultiple");
		for (int j=0;j<MAX_BATCH_WRITE ;j++)
		{
			MyData arry[] = new MyData[10];
			int count = 0;
			for (long i=offset+1000;i<offset+1010;i++)
			{
				arry[count]  = new MyData();
				arry[count].setAge(10 + rand .nextInt(10));
				arry[count].setFirst("first" + i);
				arry[count].setLast("last" + i);
				arry[count].setId(i);
				count++;
			}
			gigaspace.writeMultiple(arry);
		}
	}
}
