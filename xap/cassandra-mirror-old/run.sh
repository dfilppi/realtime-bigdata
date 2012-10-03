
#!/bin/bash
export JSHOMEDIR=/export/home/gigaspaces-xap-premium-9.0.0-ga
export JAVA_HOME=/usr/local/lib/jvm/jdk1.6.0_23
export CASSANDRA_HOME=/export/home/apache-cassandra-0.8.4

. $JSHOMEDIR/bin/setenv.sh

$JAVA_HOME/bin/java  -classpath $GS_JARS:"./cassandra-mirror/target/cassandra-mirror-1.0-SNAPSHOT/lib/*" com.test.CassandraEDSTest
