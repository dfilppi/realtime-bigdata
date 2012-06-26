set CASSANDRA_HOME=D:\apache-cassandra-0.8.4
set GS_HOME=D:\gigaspaces-xap-premium-9.0.0-ga
call %GS_HOME%\bin\setenv.bat
%JAVA_HOME%\bin\java -classpath %GS_JARS%;".\cassandra-mirror\target\cassandra-mirror-1.0-SNAPSHOT\lib\*" com.test.CassandraEDSTest


pause

