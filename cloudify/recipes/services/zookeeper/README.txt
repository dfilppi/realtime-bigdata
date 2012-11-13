Zookeeper recipe notes:

* Supports a clustered deployment
* Not elastic.  Zookeeper by nature (currently) cannot be altered once started.
* To tailor the daemon config, change the templates/zoo.cfg file
* *nix only


TESTED

Standalone: CentOS/Windows 7
EC2: Amazon Linux beta.  Clustered


LIMITATIONS

-Using default transaction log directory
-Using default log directory. No attempt to autoclean it.
-Data and transaction logs not compacted.  See: http://zookeeper.apache.org/doc/r3.3.3/zookeeperAdmin.html#Ongoing+Data+Directory+Cleanup
