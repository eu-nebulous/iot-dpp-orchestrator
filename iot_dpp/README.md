# IoT data processing pipelines orchestration tool

The IoT data processing pipelines orchestration tool allows users to declaratively express data transformation pipelines by identifying their main components (data sources, transformation operations and data consumers) and interlace them to conform data transformation flows. Once defined by the user, the orchestration of these data transformation pipelines is handled by NebulOuS core, allocating/deallocating computational resources whenever needed to adapt to the current workload. The tool also offers the opportunity to the user to monitor the execution of these pipelines and detect any error occurring on them. 


## Documentation

The repository contains the source code for three ActiveMQ Artemis plugins necessary for implementing the NebulOuS IoT data processing pipelines orchestration tool.

### MessageGroupIDAnnotationPlugin

ActiveMQ Artemis plugin for extracting the value to be used as JMSXGroupID from the message.

The plugin expects a JSON file located at the path specified by NEB_IOT_DPP_GROUPID_EXTRACTION_CONFIG_PATH environment variable. This JSON file must contain,
for each topic 

### EMSMessageLifecycleMonitoringPlugin

ActiveMQ Artemis plugin for tracking the lifecycle of messages inside an ActiveMQ cluster. On each step of the message lifecycle (a producer writes a message to the cluster, the message is delivered to a consumer, the consumer ACKs the message), the plugin generates events with relevant information that can be used for understanding how IoT applications components on NebulOuS are communicating.

The following parameters are needed for the plugin:

- ems_url (defaults to "tcp://localhost:61616")

- ems_user (defaults to "aaa")

- ems_password (defaults to "111")


### EMSQueuesMonitoringPlugin

Apache Artemis plugin that periodically collects usage metrics from the queues of the broker:
- messages_count: The number of pending messages for any queue.
- max_message_age: Age of the oldest pending message on a given queue.
- consumers_count: The number of active consumers subscribed to a queue.
- group_count: The number of message groupings for a queue. Messages on a queue are grouped by the value of the “JMSXGroupID” attribute associated to each message

The following parameters are needed for the plugin:

- topic_prefix 

- query_interval_seconds (defaults to 3)

- ems_url (defaults to "tcp://localhost:61616")

- ems_user (defaults to "aaa")

- ems_password (defaults to "111")



### IoTPipelineConfigurator 

Apache Artemis plugin responsible for parsing the IoT pipeline definition JSON and configure the local Artemis message broker to enact said pipeline.


The following parameters are needed for the pluggin:

- IOT_DPP_PIPELINE_STEPS: A JSON encoded string (or a path to a JSON file ending in '.json') containing the configuration for the IoT pipeline. This configuration is a `Map<String, IoTPipelineStepConfiguration>` where keys are names of the pipeline steps and the values are `IoTPipelineStepConfiguration` providing: 
	- inputStream (String): The input stream from the pipeline step.
	- groupingKeyAccessor (GroupIDExtractionParameters): The config to extract group ID for each input message of the step. 
	
At startup, IoTPipelineConfigurator checks the local Artemis broker for existing diverts. Removes any existing one, and creates the diverts as specified in the coniguration.
For each step with name <step_B> and input stream <step_A>, a non exclusinve divert from topic iotdpp.<step_A>.output is created. This divert sends messages to the topic iotdpp.<step_B>.input.<step_A>.


## Installation

Build the source code.

Configure your ActiveMQ Artemis to use the generated plugins. Follow the [documentation](https://activemq.apache.org/components/artemis/documentation/latest/broker-plugins.html)


## Authors

- [Robert Sanfeliu Prat (Eurecat)](robert.sanfeliu@eurecat.org)


## Acknowledgements

 - NebulOuS is a project Funded by the European Union. Views and opinions expressed are however those of the author(s) only and do not necessarily reflect those of the European Union or European Commission. Neither the European Union nor the granting authority can be held responsible for them. | Grant Agreement No.: 101070516
