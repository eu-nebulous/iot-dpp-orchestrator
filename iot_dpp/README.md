# IoT data processing pipelines orchestration tool

The IoT data processing pipelines orchestration tool allows users to declaratively express data transformation pipelines by identifying their main components (data sources, transformation operations and data consumers) and interlace them to conform data transformation flows. Once defined by the user, the orchestration of these data transformation pipelines is handled by NebulOuS core, allocating/deallocating computational resources whenever needed to adapt to the current workload. The tool also offers the opportunity to the user to monitor the execution of these pipelines and detect any error occurring on them. 


## Documentation

The repository contains the source code for three ActiveMQ Artemis plugins necessary for implementing the NebulOuS IoT data processing pipelines orchestration tool.

### MessageGroupIDAnnotationPlugin

ActiveMQ Artemis plugin for extracting the value to be used as JMSXGroupID from the message.

The plugin expects a JSON file located at the path specified by NEB_IOT_DPP_GROUPID_EXTRACTION_CONFIG_PATH environment variable. This JSON file must contain,
for each topic 



### EMSQueuesMonitoringPlugin

Apache Artemis plugin that periodically collects usage metrics from the queues of the broker:
- messages_count: The number of pending messages for any queue.
- max_message_age: Age of the oldest pending message on a given queue.
- consumers_count: The number of active consumers subscribed to a queue.
- group_count: The number of message groupings for a queue. Messages on a queue are grouped by the value of the “JMSXGroupID” attribute associated to each message

The following parameters are needed for the plugin:

- monitored_queue_regex: A regex that matched against the queue names found on the broker to decide if monitoring data is to be generated for them.

- reporting_topic_prefix: The prefix of the topic where monitoring data for each monitored queue will be published (defaults to `monitoring`).

- query_interval_seconds: Frequency to report metrics (defaults to 3).

- ems_host: The hostname of the EMS server where to report the collected data (defaults to localhost).

- ems_port: The port of the EMS server (defaults to "61616).

- ems_user: The user to connect to the EMS server.

- ems_password: The password to connect to the EMS server.

```
  <broker-plugin class-name="eut.nebulouscloud.iot_dpp.monitoring.EMSQueuesMonitoringPlugin">
		   <property key="monitored_queue_regex" value="all\.iotdpp\."/>
		   <property key="reporting_topic_prefix" value="monitoring."/>		 
		   <property key="query_interval_seconds" value="10"/>		   
		   <property key="ems_host" value="localhost"/>
		   <property key="ems_port" value="61616"/>	   
		   <property key="ems_user" value="artemis"/>
		   <property key="ems_password" value="artemis"/>
	   </broker-plugin>
```	   


### EMSMessageLifecycleMonitoringPlugin

ActiveMQ Artemis plugin for tracking the lifecycle of messages inside an ActiveMQ cluster. On each step of the message lifecycle (a producer writes a message to the cluster, the message is delivered to a consumer, the consumer acknowledges the message), the plugin generates events with relevant information that can be used for understanding how IoT applications components on NebulOuS are communicating. On each of these lifecycle events, several raw parameters are reported (e.g., pub-sub node receiving/serving the message, id of the message, timestamp of the event, etc…).

<figure class="table op-uc-figure_align-center op-uc-figure"><table class="op-uc-table"><tbody><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border:1.0pt solid windowtext;padding:0cm 5.4pt;vertical-align:top;width:106.1pt;"><strong>Event name</strong></td><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:none;border-right-style:solid;border-top-style:solid;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:361.15pt;" colspan="3">Message published event</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:106.1pt;"><strong>Event description</strong></td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:361.15pt;" colspan="3">Event generated when a message is published to the pub/sub system</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:467.25pt;" colspan="4"><strong>Fields</strong></td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2"><strong>Name</strong></td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;"><strong>Type</strong></td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;"><strong>Description</strong></td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">messageId</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">Long</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Id of the message</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">timestamp</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">Long</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Timestamp when the event occurred.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">messageSize</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">Long</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Size of the message (in bytes).</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">messageAddress</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Address where the message is published.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">node</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">The name of the pub/sub cluster node where the message was published.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell"></td><td class="op-uc-p op-uc-table--cell"></td><td class="op-uc-p op-uc-table--cell"></td><td class="op-uc-p op-uc-table--cell"></td></tr></tbody></table></figure><figure class="table op-uc-figure_align-center op-uc-figure"><table class="op-uc-table"><tbody><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border:1.0pt solid windowtext;padding:0cm 5.4pt;vertical-align:top;width:106.1pt;"><strong>Event name</strong></td><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:none;border-right-style:solid;border-top-style:solid;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:361.15pt;" colspan="3">Message delivered event</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:106.1pt;"><strong>Event description</strong></td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:361.15pt;" colspan="3">Event generated when a message is delivered to a client of the pub/sub system</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:467.25pt;" colspan="4"><strong>Fields</strong></td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2"><strong>Name</strong></td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;"><strong>Type</strong></td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;"><strong>Description</strong></td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">messageId</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">Long</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Id of the message.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">timestamp</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">Long</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Timestamp when the event occurred.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">messageSize</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">Long</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Size of the message (in bytes).</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">messageAddress</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Address where the message is consumed.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">node</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Node where the client is connected.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">clientId</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Id of the client receiving the message.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">publishNode</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Node where the message was originally published.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">publishAddress</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Address where the message was originally published.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">publishClientId</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Id of the client that originally published the message.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">publishTimestamp</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Time when the message was published (milliseconds since epoch).</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell"></td><td class="op-uc-p op-uc-table--cell"></td><td class="op-uc-p op-uc-table--cell"></td><td class="op-uc-p op-uc-table--cell"></td></tr></tbody></table></figure><figure class="table op-uc-figure_align-center op-uc-figure"><table class="op-uc-table"><tbody><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border:1.0pt solid windowtext;padding:0cm 5.4pt;vertical-align:top;width:106.1pt;"><strong>Event name</strong></td><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:none;border-right-style:solid;border-top-style:solid;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:361.15pt;" colspan="3">Message acknowledged event</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:106.1pt;"><strong>Event description</strong></td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:361.15pt;" colspan="3">Event generated when a message is acknowledged by a client of the pub/sub system</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:467.25pt;" colspan="4"><strong>Fields</strong></td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2"><strong>Name</strong></td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;"><strong>Type</strong></td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;"><strong>Description</strong></td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">messageId</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">Long</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Id of the message.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">timestamp</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">Long</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Timestamp when the event occurred.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">messageSize</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">Long</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Size of the message (in bytes).</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">messageAddress</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Address where the message is published.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">node</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Node where the client is connected.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">clientId</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Id of the client receiving the message.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:155.8pt;" colspan="2">publishNode</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Node where the message was originally published.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:156.05pt;" colspan="2">publishAddress</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Address where the message was originally published.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:156.05pt;" colspan="2">publishClientId</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Id of the client that originally published the message.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:156.05pt;" colspan="2">publishTimestamp</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Time when the message was published (milliseconds since epoch).</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell" style="border-bottom-style:solid;border-color:windowtext;border-left-style:solid;border-right-style:solid;border-top-style:none;border-width:1.0pt;padding:0cm 5.4pt;vertical-align:top;width:156.05pt;" colspan="2">deliverTimestamp</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:63.7pt;">String</td><td class="op-uc-p op-uc-table--cell" style="border-bottom:1.0pt solid windowtext;border-left-style:none;border-right:1.0pt solid windowtext;border-top-style:none;padding:0cm 5.4pt;vertical-align:top;width:247.75pt;">Time when the message was delivered to the client.</td></tr><tr class="op-uc-table--row"><td class="op-uc-p op-uc-table--cell"></td><td class="op-uc-p op-uc-table--cell"></td><td class="op-uc-p op-uc-table--cell"></td><td class="op-uc-p op-uc-table--cell"></td></tr></tbody></table></figure>

[\[1\]](https://openproject.nebulouscloud.eu/#_ftnref1) [https://activemq.apache.org/components/artemis/documentation/](https://activemq.apache.org/components/artemis/documentation/)

[\[1\]](https://openproject.nebulouscloud.eu/#_ftnref1) [https://activemq.apache.org/components/artemis/](https://activemq.apache.org/components/artemis/)

[\[2\]](https://openproject.nebulouscloud.eu/#_ftnref2) [https://activemq.apache.org/components/artemis/documentation/](https://activemq.apache.org/components/artemis/documentation/)


Events are transformed into metrics sent to the EMS for further usage in the app scalability definition. The metrics generated are the following:

- eventType: MessagePublished
- metrics: Size

- eventType: MessageDelivered
- metrics: Size, Latency

- eventType: MessageAcknowledged
- metrics: Size, Latency

The metrics can be referenced in the metric model like:

`{messageAddress}_{eventType}_{metric}`

The following parameters are needed for the plugin:
- monitored_queue_regex: A regex that matched against the queue names found on the broker to decide if monitoring data is to be generated for them.
- reporting_topic_prefix: The prefix of the topic where monitoring data for each monitored queue will be published (defaults to `monitoring`).
- ems_host: The hostname of the EMS server where to report the collected data (defaults to localhost).
- ems_port: The port of the EMS server (defaults to "61616)
- ems_user: The user to connect to the EMS server.
- ems_password: The password to connect to the EMS server.

Example config in `broker.xml`
```
	<broker-plugin class-name="eut.nebulouscloud.iot_dpp.monitoring.EMSMessageLifecycleMonitoringPlugin">
	   <property key="monitored_queue_regex" value="[all\.]?iotdpp"/>
	   <property key="reporting_topic_prefix" value="monitoring."/>   	   
	   <property key="ems_host" value="localhost"/>
	   <property key="ems_port" value="61616"/>	  
	   <property key="ems_user" value="artemis"/>
	   <property key="ems_password" value="artemis"/>	   
	</broker-plugin> 
```


### IoTPipelineConfigurator 

Apache Artemis plugin responsible for parsing the IoT pipeline definition JSON and configure the local Artemis message broker to enact said pipeline.

NebulOuS provides specific semantics for modelling IoT data processing pipelines by identifying their main components (data sources, transformation operations and data consumers) and interlace them to conform data transformation flows. Once defined by the user, the orchestration of these data transformation pipelines is handled by NebulOuS core, allocating/deallocating computational resources whenever needed to adapt to the current workload.

To better understand better the NebulOuS approach, consider the vehicle fleet monitoring solution depicted in Figure 8: Vehicle fleet monitoring IoT data pipeline example.

![image](https://github.com/eu-nebulous/nebulous/assets/172278014/0a292058-fa67-4627-b530-9f2caff4bfc7)

Figure 8: Vehicle fleet monitoring IoT data pipeline example

Monitored vehicles are equipped with a data logger that collects information about GPS position of the vehicle and publishes it to the NebulOuS input queue. GPS monitoring is known to be error prone, so the user wants to perform an outlier detection on the raw data (filtering step). The applied algorithm needs to know the last N readings to classify the Nth+1 reading. As a result, this outlier detection can be parallelized up to one instance of the step per vehicle and must always be guaranteed that readings from a vehicle are processed by the same instance. Next step of the process is to detect if the vehicle leaves a delimited geographic area for longer than a certain period (geofencing step). Again, this operation can be parallelized but the events from one vehicle need to be processed by the same worker. If a vehicle leaves the designated area, a notification is to be sent to the manager of the fleet. This notification contains a human readable message about the location of the vehicle (e.g: street address). For this, a reverse geocoding process needs to be executed on the latitude, longitude data of the egress event (reverse geocoding step). This step can be parallelized infinitely, as each event can be processed separately. With the result of the reverse geocoding, a notification step is responsible for managing the actual sending of the notification via mail (notification step). This operation doesn`t allow for parallelization. After the filtering step, processed data is to be stored on a time-series database (storing step). The parallelization level of this step is limited by the number of concurrent connections to the underlying storage system (e.g., 5).

![image](https://github.com/eu-nebulous/nebulous/assets/172278014/75cb9681-4f48-4a41-a7e4-e8d39e8be080)

Figure 9: Vertical scalability of the IoT data pipeline example

One of the key requirements of such applications is the ability to scale vertically part of the pipeline depending on the workload. For instance, Figure 9: Vertical scalability of the IoT data pipeline example shows a situation where several steps of the application (Geofencing and Reverse geocoding) have been vertically scaled to parallelize the process of the step workload during a period of high pressure on these steps.

In NebulOuS, we understand a data processing pipeline as a directed acyclic graph of steps. A step is a logic block that receives data from an input stream, does some computation over it and produces results in one or more output streams. In the example above, five steps can be identified: i) filtering, ii) geofencing, iii) reverse geocoding, iv) notification v) storing.

The proposed semantics for modelling IoT data processing pipelines allow the user to indicate the structure of the pipeline, the level of parallelism allowed for each step of the flow and the workload distribution criteria among these parallel instances. On top of that, the user is also able to indicate the SLO constraints/optimization function that governs the actual deployment of the pipeline in terms of number of parallel instances of each step and number of resources (CPU/RAM/GPU, etc…) dedicated to these instances. With this, the user is capable of leveraging the capacity of the meta-OS to i) adjust the number of instances of each of the steps of the pipeline as well as the CPU/RAM dedicated to each of these instances depending on the current workload of the application (e.g. number of messages per second)  and ii) automate the deployment of the pipeline and re-configuration when the workload changes.

#### Definition of the data processing pipeline

Data processing pipelines (pipeline so forth) are defined as regular applications managed by NebulOuS. As any other application inside NebulOuS, the user needs to provide its application graph (Open Application Model or OAM), the service level objective (SLO) and the objective function.

The application graph of a pipeline consists of the set of steps.  A step is a logic block that receives data from an input stream does some computation over it and produces results in one or more output streams. The solution relies on ActiveMQ queues (offered by the IoT pub/sub mechanism described in 4.1) to articulate the communication between pipeline steps: passing of messages between them and collecting audit logs of their execution. Each step of this pipeline is mapped to a “component” in the OAM file. For each of these steps, the application owner can specify the constraints on the computing node that is going to host that step (max/min RAM, CPU, GPU, disk, etc..) as well as all the horizontal scalability of the step (range of number of instances that can run in parallel).

In order to control how messages are distributed across the different instances of each step. A configuration file is required to be provided by the application owner (devops). This file, in JSON format contains, for each step of the pipeline (identified by its component name in the OAM file), an object with the following information:

*   input\_stream: The name of the stream from where the step will consume data.
*   grouping\_key\_accessor: Information on how to extract the key for grouping messages.
    *   source: property|body\_json|body\_xml
    *   expression: string.
        *   In case of source == “property”. The grouping\_key for a message will be the value of the message property with key “expression”.
        *   In case of source == “body\_json”. The grouping\_key for a message will be the string serialized value of the JSON path “expression” of the message body.
        *   In case of source == “body\_xml”. The grouping\_key for a message will be the string serialized value of the XML path “expression” of the message body.

To define the SLO of each step, user can leverage the metrics automatically reported by the IoT/FOG Pub/Sub mechanism for each queue (messages\_count, max\_message\_age, consumers\_count, groups\_count) and/or metrics derived from the message lifecycle monitoring (wait time, processing time, volume of data exchanged, etc…) (as described in 4.1) to define independent SLOs for each of the pipeline steps. Usually, these SLOs would indicate the max processing latency of messages for a certain step or the max length of the input queue for the step.

```text
{
  "filtering_step": {
    "input_stream": "raw_data",
    "grouping_key_accessor": {
      "source": "body_json",
      "expression": "vehicle_id"
    }
  },
  "geofencing_step": {
    "input_stream": "filtering_step_output",
    "grouping_key_accessor": {
      "source": "body_json",
      "expression": "vehicle_id"
    }
  },
  "reverse_geocoding_step": {
    "input_stream": "geofencing_step_output"    
  }  
}
```

There, information on the input of each step and how messages are grouped is provided:

*   **filtering\_step**: that consumes messages published on the “raw\_data” address (there is where vehicles should publish the GPS readings). Filtering of incoming messages can be parallelized but it must be guaranteed that all messages from the same vehicle are processed by the same step instance. For this, this step states that messages should be grouped by the value of the attribute “vehicle\_id” found in the body of the messages.
*   **geo\_fencing\_step**: consumes from “filtering\_step\_output” address (that is where the workers from the “filtering\_step” publish their output). Again, multiple instances of  “geo\_fencing\_step” can exist, but messages from the same vehicle must always be processed by the same instance of the step. For this reason, the “grouping\_key\_accessor” is provided.
*   **reverse\_geocoding\_step:** consumes from &quot;geofencing\_step\_output&quot;. This time, no special requirements on the distribution of messages needs to be imposed, for this, “grouping\_key\_accessor&quot; is not informed.

With this configuration, once the application is deployed, the metrics and events described in section 4.1.2 Interfaces offered/required are published on the EMS and can be used to define the SLO for the application and the optimization criteria.


### Data Persistor Plugin
A plugin that handles the storage of messages sent to the message broker as data points in the InfluxDB instances deployed on the application cluster master (or any other InfluxDB instance). 

In InfluxDB, individual sensor readings or measurements are stored as data points stored in buckets. Each data point consists of three core components :
- Measurement Name: A string identifier that describes the type of data being collected (e.g., "temperature", "humidity", "cpu_usage"). This helps organize related data points into logical groups.
- Fields: Key-value pairs that contain the actual measurement data. Field keys are strings, and values can be either numeric or string types. For example, a temperature reading might include fields like "value: 23.5" or "unit: Celsius".
- Tags: Key-value pairs (both keys and values must be strings) that store metadata about the measurement. Tags are typically used for categorizing and filtering data points. For example, "sensor_id: temp_01" or "location: room_123".

The developed DataPersistorPlugin plugin accepts a configuration consisting of a collection of  MessageDataExtractorDefinition objects, each defining precise rules for message processing and storage. As illustrated in Figure 21, each MessageDataExtractorDefinition specifies how to transform incoming messages into InfluxDB data points, with the following properties that control filtering, data extraction, and storage parameters:

```
{
      filterExpression: ".*\.vehicles\..*\.location$|$|AND",
      bucketExpression: "vehicle_locations",
      measurementExpression: "locationv2",
      fieldExpressions: ["BODY|$|latitude|$.lat","BODY|$|longitude|$.lon"],
      tagExpressions:["BODY|$|latitude|$.lat","BODY|$|longitude|$.lon",  "ADDRESS|.*\.vehicles\.(.*)\.location$|vid:$1"],
      dateTimeExpression: "BODY|$.date|TIMESTAMP"
}			
```

Filter Expression: A composite expression that determines whether to generate a data point from an incoming message. It follows the format `<address_match_expression>|<body_match_expression>|<join_condition>` where: 
- The section `<address_match_expression>` contains a regular expression matched against the message address using Java  `String.match` method.
- The section `<body_match_expression>` contain a JSONPath expression that must return a non-null object to evaluate as true.
- The section `<join_condition>` specifies the logical operator ("AND" or "OR") between the two expressions.

Bucket Expression: A template string that determines the target storage bucket. It supports two parameter extraction methods:
- Body parameters, expressed with the template `{BODY|<jsonpath>}`, where `<jsonpath>` section contains a JSONPath expression to match against the body of the message. If must return a string value.
- Address parameters, expressed with the template `{ADDRESS|<match>|<template>}` where `<match>` section represents a Java regular expression to match against the address of the message and the `<template>` section is a string containing literals and references to the matched groups $[1...n] from the `<match>` expression.
The plugin evaluates the bucket expression, substituting all the body and address parameters occurrences with the result of evaluating these expressions.
Measurement Expression: A template string that follows the same templating rules as the bucket expression to determine the measurement name.

Field and Tag Expressions: Collections of expressions that extract key-value pairs for fields and tags. Both support three formats:

- Address-based extraction (`ADDRESS|<match>|<template>`) where `<match>` is a regular expression to be matched against the message address and `<template>` is a string containing literals and references to the matched groups ($1…n) from the `<match>` expression. The resulting string must conform to the structure `<key>:<value>` where both `<key>` and `<value>` can be literals or matched groups from the `<match>` expression.

- Body dictionary extraction: (`BODY|<context_jsonpath>|<key_values_jsonpath>`) where `<context_jsonpath>` is a JSONPath expression to limit the context of the query (`$` will match the full object) and `<key_values_jsonpath>` is a JSONPath expression that will return a dictionary of key values from the body.

- Separate keys and values extraction (`BODY|<context_jsonpath>|<keys_jsonpath>|<values_jsonpath>`) where `<context_jsonpath>` is a JSONPath expression to limit the context of the query (`$` will match the full object)  and `<keys_jsonpath>` is either a literal or a JSONPath expression that will return a key or list of keys and  `<values_jsonpath>` is a JSONPath expression that will return a value or  list of values.

DateTime Expression: Optional expression to extract the message timestamp. When specified, uses the format `BODY|<jsonpath>|<date_format>` where `<jsonpath>`  is a JSONPath that will extract a numeric/string value from the body and `<date_format>` is the format of the extracted value, being either `TIMESTAMP` meaning an integer value representing the EPOCH time in milliseconds or a date format parseable by Java SimpleDateFormat .

When the message broker receives a message, it processes it through the configured MessageDataExtractorDefinitions in sequence. Each definition first evaluates its filtering condition against the incoming message. Only messages that satisfy the filtering criteria proceed to the data point extraction phase, where the configured extraction rules transform the message content into InfluxDB data points according to the specified measurement, field, and tag definitions.


### Keycloak Integration Plugin
A plugin that configures the message broker to accept operations (read, send, create, etc...) on queues by users defined in an external Keycloak server.

The security model of Keycloak can be summarised in with the following core concepts:
- Users: Entities that can log into systems, each with attributes such as name, email, etc.
- Groups: Collections of users.
- Roles: Tags assigned to users or groups that determine their permissions within client applications.
- Clients: Applications that authenticate and authorize users.
- Realms: Isolated domains within a Keycloak instance that define their own users, clients, roles, and groups. A single Keycloak deployment can support multiple realms.

In this model, a user may be assigned roles directly or inherit them through group membership. These roles are later used by client applications to authorize specific actions such as reading, creating, or deleting resources.
Conversely, ActiveMQ Artemis defines a security model with the following concepts:
- Users: Clients that connect to the message broker to perform actions such as reading from or writing to queues and addresses.
- Roles: Labels associated with users that determine what operations they are allowed to perform.
- Security Settings: Configuration blocks that define:
  - A match pattern (an address or queue name pattern).
  - A list of permissions (e.g., send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue, deleteNonDurableQueue) and the roles authorized to perform each.

In this model, a user is assigned a list of roles, and the broker defines permissions over queues and addresses for these roles based on a pattern match (regex).

The developed plugin is responsible for synchronizing the roles defined in Keycloak with the corresponding roles and security settings in the ActiveMQ Artemis message broker. To align Keycloak`s role model with Artemis's role and permission structure, the plugin leverages role attributes assigned to each Keycloak role.

Only roles within the Keycloak realm that meet the following criteria are considered:
- The role must have the attribute is_nebulous_role set to true.
- The role must include a match attribute, which defines the target address or queue pattern in Artemis.
- The role must specify the allowed permissions, which may include:
send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue, and deleteNonDurableQueue.
The plugin periodically retrieves all roles from the designated Keycloak realm that satisfy these conditions. It then maps them into the corresponding role definitions and security settings in Artemis, ensuring that access control policies remain synchronized and up to date across both systems.


## Installation

Build the source code.

Configure your ActiveMQ Artemis to use the generated plugins. Follow the [documentation](https://activemq.apache.org/components/artemis/documentation/latest/broker-plugins.html)


## Authors

- [Robert Sanfeliu Prat (Eurecat)](robert.sanfeliu@eurecat.org)


## Acknowledgements

 - NebulOuS is a project Funded by the European Union. Views and opinions expressed are however those of the author(s) only and do not necessarily reflect those of the European Union or European Commission. Neither the European Union nor the granting authority can be held responsible for them. | Grant Agreement No.: 101070516
