import stomp
import os
import logging
import threading
import queue
import time
import datetime
import json
from uuid import uuid4
import sys
import traceback

# Initialize shared queue for messages
shared_stack = queue.Queue()

# STOMP Broker details
stomp_broker_address = os.getenv("stomp_ip")
stomp_port = int(os.getenv("stomp_port"))
stomp_user = os.getenv("stomp_user")
stomp_password = os.getenv("stomp_password")
iotdpp_topic_prefix = "iotdpp."
previous_step_name = os.getenv("previous_step_name")
step_name = os.getenv("step_name")
worker_id = str(uuid4())

print(f"Starting step {step_name} worker {worker_id}")

# Define STOMP connection
conn = stomp.Connection([(stomp_broker_address, stomp_port)], heartbeats=(3000, 3000))
conn.set_listener('', stomp.PrintingListener())

def map_value(old_value, old_min, old_max, new_min, new_max):
    return ((old_value - old_min) / (old_max - old_min)) * (new_max - new_min) + new_min

class MyListener(stomp.ConnectionListener):

    def on_disconnect(self, client, userdata, rc):
         print(f"on_disconnect: {rc}")
         sys.exit(1)
    
    def on_error(self, frame):
        print(f"Error: {frame.body}")
        sys.exit(1)

    def on_message(self, frame):
        try:
            print("Received STOMP message", frame)
            self.process_message(frame.body)
            conn.ack(frame.headers['message-id'],frame.headers['subscription'])

        except Exception as e:
            print("Error", e)
            print(traceback.format_exc())
            sys.exit(1)

    def process_message(self,payload):
        try:
            payload = json.loads(payload)
            print("Processing", payload)  
            workload_duration = payload[step_name]["duration"]
            print(f"Proceed to simulate a workload of duration: {workload_duration}s")
            time.sleep(int(workload_duration))
            print("Proceed to publish output")
            
            pipeline_ingress_time = datetime.datetime.fromisoformat(payload["src"]['completion_timestamp'])
            accumulated_latency = int((datetime.datetime.now(datetime.timezone.utc) - pipeline_ingress_time).total_seconds())  
            
            step_ingress_time = datetime.datetime.fromisoformat(payload[previous_step_name]['completion_timestamp'])
            step_latency = int((datetime.datetime.now(datetime.timezone.utc) - step_ingress_time).total_seconds())            
            print(f"accumulated_latency: {accumulated_latency}s; step_latency: {step_latency}s")
            
            # Update the payload with new metadata
            payload[step_name]["worker_id"] = worker_id
            payload[step_name]["step_latency"] = step_latency
            payload[step_name]["accumulated_latency"] = accumulated_latency
            payload[step_name]["completion_timestamp"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
            
            # Publish to the STOMP broker
            #https://activemq.apache.org/components/artemis/documentation/latest/stomp.html#sending
            conn.send(body=json.dumps(payload), destination=f"{iotdpp_topic_prefix}{step_name}.output",headers={"destination-type":"MULTICAST"})
        except Exception as e:
            print("Error", e)
            print(traceback.format_exc())

    def process_messages(self,payload):
        while True:
            try:
                # Get message from the shared stack
                payload = shared_stack.get()           
                self.process_messages(payload)
            except Exception as e:
                print("Error", e)
                print(traceback.format_exc())

# Connecting to the STOMP broker
print("Connecting to STOMP")

conn.set_listener('message_listener', MyListener())
conn.connect(stomp_user, stomp_password, wait=True)


# Subscribe to the topic
stomp_topic = f"all.{iotdpp_topic_prefix}{step_name}.input.{previous_step_name}"
print(f"Subscribing to {stomp_topic}")
https://activemq.apache.org/components/artemis/documentation/latest/stomp.html#subscribing
conn.subscribe(destination=stomp_topic,id=worker_id+"b",ack="client",headers={"subscription-type":"ANYCAST"})


print("Done")
#https://activemq.apache.org/components/artemis/documentation/latest/stomp.html#stomp
while True:    
    time.sleep(4)