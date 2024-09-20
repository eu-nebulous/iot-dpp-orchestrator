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

# STOMP Broker details
stomp_broker_address = os.getenv("stomp_ip")
stomp_port = int(os.getenv("stomp_port"))
stomp_user = os.getenv("stomp_user")
stomp_password = os.getenv("stomp_password")
iotdpp_topic_prefix = "new_job"
worker_id = str(uuid4())

# Define STOMP connection
conn = stomp.Connection([(stomp_broker_address, stomp_port)], heartbeats=(3000, 3000))
conn.set_listener('', stomp.PrintingListener())

class MyListener(stomp.ConnectionListener):
    def on_error(self, frame):
        print(f"Error: {frame.body}")

    def on_message(self, frame):
        try:
            print("Received STOMP message", frame)
            conn.ack(frame.headers['message-id'],frame.headers['subscription'])

        except Exception as e:
            print("Error", e)
            print(traceback.format_exc())
            sys.exit(1)

# Connecting to the STOMP broker
print("Connecting to STOMP")

conn.set_listener('message_listener', MyListener())
conn.connect(stomp_user, stomp_password, wait=True)


# Subscribe to the topic
stomp_topic = f"{iotdpp_topic_prefix}"
print(f"Subscribing to {stomp_topic}")
conn.subscribe(destination=stomp_topic,id=worker_id+"b",ack="client",headers={"subscription-type":"ANYCAST"})


print("Done")
#https://activemq.apache.org/components/artemis/documentation/latest/stomp.html#stomp
# Wait forever to keep the connection alive
while True:    
    #conn.send("iotdpp.stepA.input.src",body="",headers={"destination-type":"MULTICAST"})
    #conn.send("iotdpp.src.output",body="",headers={"destination-type":"MULTICAST"})    	
    #print("Send")
    time.sleep(4)