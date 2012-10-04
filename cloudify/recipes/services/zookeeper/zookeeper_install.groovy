/*******************************************************************************
* Copyright (c) 2011 GigaSpaces Technologies Ltd. All rights reserved
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/

def config = new ConfigSlurper().parse(new File("zookeeper.properties").toURL())

new AntBuilder().sequential {
	mkdir(dir:config.installDir)
	get(src:config.downloadPath, dest:"${config.installDir}/${config.zipName}", skipexisting:true)
	untar(src:"${config.installDir}/${config.zipName}", dest:config.installDir, overwrite:true, compression:"gzip")
	//dos2unix on the linux script files
	fixcrlf(srcDir:"${config.installDir}/${config.name}/bin", eol:"lf", eof:"remove", excludes:"*.bat *.jar")
	chmod(dir:"${config.installDir}/${config.name}/bin", perm:'+x', excludes:"*.bat *.jar")
	delete(file:"${config.installDir}/${config.zipName}")


   //use default config
	move(file:"${config.installDir}/${config.name}/conf/zoo_sample.cfg", tofile:"${config.installDir}/${config.name}/conf/zoo.cfg");

   //overwrite start scripts
	move(file:"overwrite/zkServer.cmd", todir:"${config.installDir}/${config.name}/bin")
	move(file:"overwrite/zkServer.sh", todir:"${config.installDir}/${config.name}/bin")
}
