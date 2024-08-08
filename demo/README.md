Demo to exemplify the use of the IoT data processing pipelines orchestration tool
The demo consists of a docker-compose with an instance of Apache Artemis message broker with the IoTPipelineConfigurator and EMSQueuesMonitoringPlugin plugins registered. The IoTPipelineConfigurator is configured to create an IoT pipeline composed of steps A, B, C, and D. Steps A, B, C are chained ( A -> B -> C). Steps A and D are chained (A -> D).

To simulate the logic of each step, a dummy worker implementation is provided. This worker receives a job from the message broker (via MQTT), and sleeps for a period of time indicated in the job. After this, modifies the received message to include the ID of the worker, the completion and sends it to the output queue of the step.

A web client ("client.html") is provided to send jobs to the pipeline and observe the KPIs of the system (latency, pending messages, etc...).


Usage:

To start the IoT pipeline (message broker and workers) navigate to the root of the demo folder and execute:

`docker-compose up`

After sucessful startup, open the file "client.html" with a web browser. Click the "Connect" button, bar on top of the UI should become green. 
Once connected, you can send Job requets by filling the form "Send JSON Payload". This form contains an input the duration of each step (time the worker will sleep) can be provided. Click "Send" and the request will be sent to the IoT pipeline.
If you want messages to be automatically submited on a given period, modify the "Send interval" field and click "Start" button.


If you want, you can modify the configuration files of the Message broker found in the folder 'broker\etc'. Once modified, you need to delete the activemq container (and volume) and create it again. To do so, use the following commands:

`docker-compose down -v activemq`
`docker-compose up --force-recreate activemq`