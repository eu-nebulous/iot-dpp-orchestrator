This repository contains the source code of a set of Apache ActiveMQ Artemis plugins that support the [IoT Fog data management](https://github.com/eu-nebulous/nebulous/wiki/3.3-IoT-Fog-data-management) capabilities of nebulous.
More precisely, it contains:
1. [Metrics Collector Plugin](https://github.com/eu-nebulous/nebulous/wiki/3.3.1--IoT-Fog-message-broker): Extracts usage patterns and forwards them to the EMS (EPA)
2. [Process Pipeline Management Plugin](https://github.com/eu-nebulous/nebulous/wiki/3.3.2-IoT-data-flow-pipelines-orchestration-tool): Orchestrates data transformation pipelines
3. [Data Persistor Plugin](https://github.com/eu-nebulous/nebulous/wiki/3.3.3-IoT-data-persistor): Manages data storage in InfluxDB
4. [Keycloak Integration Plugin](https://github.com/eu-nebulous/nebulous/wiki/3.3.4-Message-broker-keycloack): Synchronizes users with an external Keycloak server

Additionally, contains a [demo](https://github.com/eu-nebulous/iot-dpp-orchestrator/tree/main/demo) to exemplify the use of the IoT data processing pipelines orchestration tool The demo consists of a docker-compose with an instance of Apache Artemis message broker with the IoTPipelineConfigurator and EMSQueuesMonitoringPlugin plugins registered. 


## Authors

- [Robert Sanfeliu Prat (Eurecat)](robert.sanfeliu@eurecat.org)


## Acknowledgements

 - NebulOuS is a project Funded by the European Union. Views and opinions expressed are however those of the author(s) only and do not necessarily reflect those of the European Union or European Commission. Neither the European Union nor the granting authority can be held responsible for them. | Grant Agreement No.: 101070516
