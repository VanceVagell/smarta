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

// This version of the program is for use ONLY with the Make Motor Shield v1.1.

#include <Make_DCMotor.h>
#include <SoftwareSerial.h>

// Adjust these delay values to run the motor longer/shorter depending on the characteristics of your
// SMARTA's conveyor belt, cups, and motor.
#define DISPENSE_DURATION 920
#define REWIND_DURATION 1100

// IMPORTANT! Replaced motors 2 and 3 (m2 and m3) with serial connection to tablet.
SoftwareSerial tabletSerial(9, 5); // RX, TX

Make_DCMotor motor1(M1);

#define DEBUG_LED 13

void setup(void) { 
  Serial.begin(115200);
  
  motor1.setBrake(BRAKE_OFF);
  
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
      motor1.setDirection(DIR_CW);
      motor1.setSpeed(100);
      delay(DISPENSE_DURATION);
      digitalWrite(DEBUG_LED, LOW);
      motor1.setSpeed(0);
    } else if (in == '2') {
      Serial.println(F("Received back signal, running motor briefly in reverse"));
      digitalWrite(DEBUG_LED, HIGH);
      motor1.setDirection(DIR_CCW);
      motor1.setSpeed(100);
      delay(REWIND_DURATION);
      digitalWrite(DEBUG_LED, LOW);
      motor1.setSpeed(0);
    } else if (in == '3') {
      Serial.println(F("Received far back signal, running motor for a while in reverse"));
      digitalWrite(DEBUG_LED, HIGH);
      motor1.setDirection(DIR_CCW);
      motor1.setSpeed(100);
      delay(REWIND_DURATION * 7);
      digitalWrite(DEBUG_LED, LOW);
      motor1.setSpeed(0);
    }
  }
}

