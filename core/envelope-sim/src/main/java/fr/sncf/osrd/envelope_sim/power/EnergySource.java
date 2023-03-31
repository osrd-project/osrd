package fr.sncf.osrd.envelope_sim.power;

public interface EnergySource {

    /** Return available power based on contextual speed and electrification availability */
    double getMaxOutputPower(double speed, boolean electrification);

    /** Return the maximum refill power the source is capable of capturing */
    double getMaxInputPower();

    /** Consume an algebric amount of energy from the source.
     *  If energyDelta > 0 that means some energy has been taken from the source.
     *  If energyDelta < 0 that means some energy has been sent to the source.*/
    void consumeEnergy(double energyDelta);
}
