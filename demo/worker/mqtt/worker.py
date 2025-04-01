import paho.mqtt.client as mqtt
import os.path
import logging
import threading
import queue
import time
import datetime
import json
from uuid import uuid4
import sys
import traceback


shared_stack = queue.Queue()
# MQTT Broker details
mqtt_broker_address = os.getenv("mqtt_ip")
mqtt_port = int(os.getenv("mqtt_port"))
mqtt_user = os.getenv("mqtt_user")
mqtt_password = os.getenv("mqtt_password")
iotdpp_topic_prefix = "iotdpp/"
previous_step_name = os.getenv("previous_step_name")
step_name = os.getenv("step_name")
worker_id = 1
print(f"Starting step {step_name} worker {worker_id}")
mqtt_client = mqtt.Client()
mqtt_client.max_inflight_messages=1
mqtt_client.username_pw_set(username=mqtt_user, password=mqtt_password)
def map_value(old_value, old_min, old_max, new_min, new_max):
    return ( (old_value - old_min) / (old_max - old_min) ) * (new_max - new_min) + new_min

def on_disconnect(client, userdata, rc):
    print(f"Disconnected {rc}")
    sys.exit(1)
    
def on_connect(client, userdata, flags, rc):
    if rc != 0:
        print(f"Failed to connect: RC:{rc}. loop_forever() will retry connection")
    else:
        print("Connected")
        mqtt_topic = f"$share/all/{iotdpp_topic_prefix}{step_name}/input/{previous_step_name}"
        print(f"Subscribing to {mqtt_topic}")
        mqtt_client.subscribe(mqtt_topic)

def on_message(client, userdata, message):
    try:
        payload = message.payload.decode("utf-8")
        print("Recieved MQTT message",payload)
        print("Message added to stack. Current length:",shared_stack.qsize())
        shared_stack.put(payload)
        backpressure = map_value(min(shared_stack.qsize(),10),0,10,0,2)
        print("Backpressure: ",backpressure)
        if backpressure>0:
            time.sleep(backpressure)
    except Exception as e:
        print("Error",e)
        print(traceback.format_exc())
        sys.exit(1)

def process_messages():
    while True:
        try:
            # Get message from the shared stack
            payload = shared_stack.get()
            payload = json.loads(payload)
            print("Processing ",payload)        
            workload_duration = payload[step_name]["duration"]
            print(f"Proceed to simulate a workload of duration: {workload_duration}s")
            time.sleep(int(workload_duration))
            print("Proceed to publish output")
            pipeline_ingress_time = datetime.datetime.fromisoformat(payload["src"]['completion_timestamp'])
            accumulated_latency = int((datetime.datetime.now(datetime.timezone.utc) - pipeline_ingress_time).total_seconds())  
            
            step_ingress_time = datetime.datetime.fromisoformat(payload[previous_step_name]['completion_timestamp'])
            step_latency = int((datetime.datetime.now(datetime.timezone.utc) - step_ingress_time).total_seconds())            
            print(f"accumulated_latency: {accumulated_latency}s; step_latency: {step_latency}s")
            
            payload[step_name]["worker_id"]=worker_id
            payload[step_name]["step_latency"] = step_latency
            payload[step_name]["accumulated_latency"] = accumulated_latency
            payload[step_name]["completion_timestamp"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
            mqtt_client.publish(f"{iotdpp_topic_prefix}{step_name}/output",json.dumps(payload),2)    
        except Exception as e:
            print("Error",e)
            print(traceback.format_exc())


logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

print("Connecting to MQTT")
# Initialize MQTT client

mqtt_client.on_message = on_message
mqtt_client.on_connect = on_connect
mqtt_client.on_disconnect = on_disconnect
try:
    mqtt_client.connect(mqtt_broker_address, mqtt_port)
except Exception as e:
    print(f"Connection error: {e}")
    exit(1)
     
mqtt_client.enable_logger(logger)
publish_thread = threading.Thread(target=process_messages)
publish_thread.daemon = True  # Daemonize the thread so it will exit when the main thread exits
publish_thread.start()
print("Done")

# Start the MQTT client loop
mqtt_client.loop_forever()
print("App ended")
