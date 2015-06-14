#include <Make_DCMotor.h>
#include <SoftwareSerial.h>

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
      motor1.setSpeed(100); // You can tweak all the timing values in here for your particular setup.
      delay(1050);
      digitalWrite(DEBUG_LED, LOW);
      motor1.setSpeed(0);
    } else if (in == '2') {
      Serial.println(F("Received back signal, running motor briefly in reverse"));
      digitalWrite(DEBUG_LED, HIGH);
      motor1.setDirection(DIR_CCW);
      motor1.setSpeed(100);
      delay(950);
      digitalWrite(DEBUG_LED, LOW);
      motor1.setSpeed(0);
    } else if (in == '3') {
      Serial.println(F("Received far back signal, running motor for a while in reverse"));
      digitalWrite(DEBUG_LED, HIGH);
      motor1.setDirection(DIR_CCW);
      motor1.setSpeed(100);
      delay(6700);
      digitalWrite(DEBUG_LED, LOW);
      motor1.setSpeed(0);
    }
  }
}

