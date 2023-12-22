# IoT data processing pipelines orchestration tool

The IoT data processing pipelines orchestration tool allows users to declaratively express data transformation pipelines by identifying their main components (data sources, transformation operations and data consumers) and interlace them to conform data transformation flows. Once defined by the user, the orchestration of these data transformation pipelines is handled by NebulOuS core, allocating/deallocating computational resources whenever needed to adapt to the current workload. The tool also offers the opportunity to the user to monitor the execution of these pipelines and detect any error occurring on them. 


## Documentation

The repository contains the source code for two ActiveMQ Artemis plugins necessary for implementing the NebulOuS IoT data processing pipelines orchestration tool.

### MessageGroupIDAnnotationPlugin

ActiveMQ Artemis plugin for extracting the value to be used as JMSXGroupID from the message.

### MessageLifecycleMonitoringPlugin

ActiveMQ Artemis plugin for tracking the lifecycle of messages inside an ActiveMQ cluster. On each step of the message lifecycle (a producer writes a message to the cluster, the message is delivered to a consumer, the consumer ACKs the message), the plugin generates events with relevant information that can be used for understanding how IoT applications components on NebulOuS are communicating.
## Installation

Build the source code.

Configure your ActiveMQ Artemis to use the generated plugins. Follow the [documentation](https://activemq.apache.org/components/artemis/documentation/latest/broker-plugins.html)


## Authors

- [Robert Sanfeliu Prat (Eurecat)](robert.sanfeliu@eurecat.org)


## Acknowledgements

 - NebulOuS is a project Funded by the European Union. Views and opinions expressed are however those of the author(s) only and do not necessarily reflect those of the European Union or European Commission. Neither the European Union nor the granting authority can be held responsible for them. | Grant Agreement No.: 101070516
