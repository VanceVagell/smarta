/*Copyright 2015 Vance Vagell

        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        See the License for the specific language governing permissions and
        limitations under the License.*/

// This version of the program is for use ONLY with the Adafruit Motor Shield v2.

#include <Wire.h>
#include <Adafruit_MotorShield.h>
#include <SoftwareSerial.h>

// Adjust these delay values to run the motor longer/shorter depending on the characteristics of your
// SMARTA's conveyor belt, cups, and motor.
#define DISPENSE_DURATION 620
#define REWIND_DURATION 664

SoftwareSerial tabletSerial(9, 5); // RX, TX

Adafruit_MotorShield AFMS = Adafruit_MotorShield(); 
Adafruit_DCMotor *motor1 = AFMS.getMotor(1);

#define DEBUG_LED 13

void setup(void) { 
  Serial.begin(115200);
  
  AFMS.begin();
  motor1->setSpeed(100);
  
  // initialize digital pin 13 as an output.
  pinMode(DEBUG_LED, OUTPUT);
  
  tabletSerial.begin(9600);
}

void loop(void) {
  // Print out whatever we receive from the tablet to our main serial connection for debugging.
  if (tabletSerial.available()) {
    char in = tabletSerial.read();
    if (in == '1') {
      Serial.println(F("Received start signal, running motor briefly"));
      digitalWrite(DEBUG_LED, HIGH);
      motor1->run(FORWARD);
      delay(DISPENSE_DURATION);
      digitalWrite(DEBUG_LED, LOW);
      motor1->run(RELEASE);
    } else if (in == '2') {
      Serial.println(F("Received back signal, running motor briefly in reverse"));
      digitalWrite(DEBUG_LED, HIGH);
      motor1->run(BACKWARD);
      delay(REWIND_DURATION);
      digitalWrite(DEBUG_LED, LOW);
      motor1->run(RELEASE);
    } else if (in == '3') {
      Serial.println(F("Received far back signal, running motor for a while in reverse"));
      digitalWrite(DEBUG_LED, HIGH);
      motor1->run(BACKWARD);
      delay(REWIND_DURATION * 7); // NOT CURRENTLY USED. Instead, phone app tracks how many reverses are needed and sends multiple "2" signals.
      digitalWrite(DEBUG_LED, LOW);
      motor1->run(RELEASE);
    }
  }
}

