Storm Nimbus Service Recipe

Single instance by design.  Requires existing Zookeeper cluster.  Prerequisites built during install, since they are platform specific.  Monitored by custom plugin that uses Nimbus' Thrift interface for grabbing cluster info.

LIMITATIONS

- No "details" plugin
- Uses defaults for almost everything, including "storm.local.dir".  Not optimal.
