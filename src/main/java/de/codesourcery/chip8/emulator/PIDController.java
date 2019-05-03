package de.codesourcery.chip8.emulator;

import java.util.concurrent.TimeUnit;

public final class PIDController {

    private float target;
    private float pGain;
    private float iGain;
    private float dGain;

    private long lastTime;
    private float lastError = 0;
    private float integral = 0;

    public PIDController(float set, float p, float i, float d) {

        reset(set);

        pGain = p;
        iGain = i;
        dGain = d;
    }

    public float update(long currTime, float currValue) {

        if (lastTime == 0) {

            lastTime = currTime;
            lastError = target - currValue;

            return 0;
        }

        float dt = (float)(currTime - lastTime) / TimeUnit.SECONDS.toNanos(1);

        if (dt == 0) {
            return 0;
        }

        float error = target - currValue;
        float deriv = (error - lastError) / dt;

        integral += error * dt;
        lastTime = currTime;
        lastError = error;

        return (pGain * error) + (iGain * integral) + (dGain * deriv);
    }

    public void setTarget(float value) {
        reset(value);
    }

    private void reset(float target) {

        this.target = target;

        lastTime = 0;
        lastError = 0;
        integral = 0;
    }
}