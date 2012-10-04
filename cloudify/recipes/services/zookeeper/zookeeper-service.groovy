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


//
//SIMPLE NON-CLUSTERED RECIPE


service {

	name "zookeeper"
	type "DATABASE"
	icon "zookeeper-small.gif"

	lifecycle{
		init "zookeeper_install.groovy"
		start "zookeeper_start.groovy"
//		preStop "zookeeper_stop.groovy"
	}
	plugins([
		plugin {
			name "portLiveness"
			className "org.cloudifysource.usm.liveness.PortLivenessDetector"
			config ([
						"Port" : [2181],
						"TimeoutInSeconds" : 60,
						"Host" : "127.0.0.1"
					])
		},
		plugin {
			name "jmx"
			className "org.cloudifysource.usm.jmx.JmxMonitor"
			config([
						"Outstanding Requests": [
							"org.apache.ZooKeeperService:name0=StandaloneServer_port-1",
							"OutstandingRequests"
						],
						"Packets Received": [
							"org.apache.ZooKeeperService:name0=StandaloneServer_port-1",
							"PacketsReceived"
						],
						"Packets Sent": [
							"org.apache.ZooKeeperService:name0=StandaloneServer_port-1",
							"PacketsSent"
						],
						port: 9999
					])
		}
	])


	userInterface {
		metricGroups = ([
			metricGroup {

				name "server"

				metrics([
					"Outstanding Requests",
					"Packets Received",
					"Packets Sent",
				])
			},
		]
		)

		widgetGroups = ([
			widgetGroup {
				name "Outstanding Requests"
				widgets ([
					barLineChart{
						metric "OutStanding Requests"
						axisYUnit Unit.REGULAR
					}
				])
			},
			widgetGroup {
				name "Packets Received"
				widgets ([
					barLineChart{
						metric "Packets Received"
						axisYUnit Unit.REGULAR
					}
				])
			},
			widgetGroup {
				name "Packets Sent"
				widgets ([
					barLineChart{
						metric "Packets Sent"
						axisYUnit Unit.REGULAR
					}
				])
			},
		]
		)
	}
}


