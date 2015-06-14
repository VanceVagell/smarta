#include <Make_Stepper.h>

const int stepsPerRevolution = 200;  // change this to fit the number of steps per revolution
                                     // for your motor

Make_Stepper stepper(stepsPerRevolution, M1, M2);

int stepCount = 0;         // number of steps the motor has taken

void setup(){
  Serial.begin(115200);
  Serial.print(F("Make Motor Shield Library version "));
  Serial.print(MakeMotorShield::version());
  Serial.println(F("- Stepper Motors")); 
}

void loop(void){
  stepper.step(1);
  Serial.print("steps:" );
  Serial.println(stepCount);
  stepCount++;
  delay(500);
}
