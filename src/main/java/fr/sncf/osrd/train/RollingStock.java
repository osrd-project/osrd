package fr.sncf.osrd.train;

import fr.sncf.osrd.infra.InvalidInfraException;


/**
 * The immutable characteristics of a specific train.
 * There must be a RollingStock instance per train on the network.
 */
public class RollingStock {
    /*
    These three coefficients are required for the train's physical simulation.

    https://www.sciencedirect.com/science/article/pii/S2210970616300415

    A = rollingResistance
    B = mechanicalResistance
    C = aerodynamicResistance

    R = (
        A
        + B * v
        + C * v^2
    )

    /!\ Be careful when importing data, conversions are needed /!\

    // configurationFile ?
    rollingResistance = cfg.getDouble("A") * mass / 100.0; // from dN/ton to N
    mechanicalResistance = cfg.getDouble("B") * mass / 100 * 3.6D; // from dN/ton/(km/h) to N
    aerodynamicResistance = cfg.getDouble("C") * mass / 100 * Math.pow(3.6D, 2); // from dN/ton/(km/h)2 to N

    // Json case
    rollingResistance = json.getDouble("coeffvoma") * 10; // from dN to N
    mechanicalResistance = json.getDouble("coeffvomb") * 10 * 3.6D; // from dN/(km/h) to N/(m/s)
    aerodynamicResistance = json.getDouble("coeffvomc") * 10 * Math.pow(3.6D, 2); // from dN/(km/h)2 to N/(m/s)2
    */

    private final double rollingResistance;      // in newtons
    private final double mechanicalResistance;   // in newtons / (m/s)
    private final double aerodynamicResistance;  // in newtons / (m/s^2)

    /**
     * Gets the rolling resistance at a given speed, which is a force that always goes
     * opposite to the train's movement
     * @param speed the speed to compute the rolling resistance for
     * @return the rolling resistance force, in newtons
     */
    @SuppressWarnings({"checkstyle:LocalVariableName", "UnnecessaryLocalVariable"})
    public double rollingResistance(double speed) {
        speed = Math.abs(speed);
        var A = rollingResistance;
        var B = mechanicalResistance;
        var C = aerodynamicResistance;
        // this formula is called the Davis equation.
        // it's completely empirical, and models the drag and friction forces
        return A + B * speed + C * speed * speed;
    }

    /** the length of the train, in meters. */
    public final double length;

    /** The max speed of the train, in meters per seconds. */
    public final double maxSpeed;

    /**
     * The time the train takes to start up, in seconds.
     * During this time, the train's maximum acceleration is limited.
     */
    public final double startUpTime;

    /** The acceleration to apply during the startup state. */
    public final double startUpAcceleration;

    /** The maximum acceleration when the train is in its regular operating mode. */
    public final double comfortAcceleration;

    /** The mass of the train, in kilograms. */
    public final double mass;

    /**
     * Inertia coefficient.
     * The mass alone isn't sufficient to compute accelerations, as the wheels and internals
     * also need force to get spinning. This coefficient can be used to account for the difference.
     * It's without unit.
     */
    public final double inertiaCoefficient;

    public final boolean isTVM300Equiped;
    public final boolean isTVM430Equiped;
    public final boolean isETCS1Equiped;
    public final boolean isETCS2Equiped;
    public final boolean isKVBEquiped;

    /**
     * Associates a speed to a force.
     * https://en.wikipedia.org/wiki/Tractive_force#Tractive_effort_curves
     */
    public final TractiveEffortPoint[] tractiveEffortCurve;

    public static final class TractiveEffortPoint {
        public final double speed;
        public final double maxEffort;

        public TractiveEffortPoint(double speed, double maxEffort) {
            this.speed = speed;
            this.maxEffort = maxEffort;
        }
    }

    /**
     * Returns the max tractive effort at a given speed.
     * @param speed the speed to compute the max tractive effort for
     * @return the max tractive effort
     */
    public double getMaxEffort(double speed) {
        double maxEffort = 0.0;
        for (var dataPoint : tractiveEffortCurve) {
            if (dataPoint.speed > speed)
                break;
            maxEffort = dataPoint.maxEffort;
        }
        return maxEffort;
    }

    /**
     * Creates a new train inventory item.
     * @param rollingResistance a rolling resistance coefficient, in newtons
     * @param mechanicalResistance a mechanical resistance coefficient, in newtons
     * @param aerodynamicResistance an aerodynamic resistance coefficient, in newtons
     * @param length the length of the train, in meters
     * @param maxSpeed the max speed, in m/s
     * @param startUpTime the train startup time, in seconds
     * @param startUpAcceleration the maximum acceleration during startup, in m/s^2
     * @param comfortAcceleration the cruise maximum acceleration, in m/s^2
     * @param mass the mass of the train, in kilograms
     * @param inertiaCoefficient a special inertia coefficient
     * @param isTVM300Equiped whether the train has TVM300 hardware
     * @param isTVM430Equiped whether the train has TVM430 hardware
     * @param isETCS1Equiped whether the train has ETCS1 hardware
     * @param isETCS2Equiped whether the train has ETCS2 hardware
     * @param isKVBEquiped whether the train has KVB hardware
     * @param tractiveEffortCurve the tractive effort curve for the train
     */
    public RollingStock(
            double rollingResistance,
            double mechanicalResistance,
            double aerodynamicResistance,
            double length,
            double maxSpeed,
            double startUpTime,
            double startUpAcceleration,
            double comfortAcceleration,
            double mass,
            double inertiaCoefficient,
            boolean isTVM300Equiped,
            boolean isTVM430Equiped,
            boolean isETCS1Equiped,
            boolean isETCS2Equiped,
            boolean isKVBEquiped,
            TractiveEffortPoint[] tractiveEffortCurve
    ) throws InvalidInfraException {
        this.rollingResistance = rollingResistance;
        this.mechanicalResistance = mechanicalResistance;
        this.aerodynamicResistance = aerodynamicResistance;
        this.length = length;
        this.maxSpeed = maxSpeed;
        this.startUpTime = startUpTime;
        this.startUpAcceleration = startUpAcceleration;
        this.comfortAcceleration = comfortAcceleration;
        this.mass = mass;
        this.inertiaCoefficient = inertiaCoefficient;
        this.isTVM300Equiped = isTVM300Equiped;
        this.isTVM430Equiped = isTVM430Equiped;
        this.isETCS1Equiped = isETCS1Equiped;
        this.isETCS2Equiped = isETCS2Equiped;
        this.isKVBEquiped = isKVBEquiped;
        this.tractiveEffortCurve = tractiveEffortCurve;
        validate();
    }

    /**
     * Validates the properties of the rolling stock
     * @throws InvalidInfraException if some property is invalid
     */
    public void validate() throws InvalidInfraException {
        if (rollingResistance < 0)
            throw new InvalidInfraException("Invalid rolling stock rollingResistance");

        if (mechanicalResistance < 0)
            throw new InvalidInfraException("Invalid rolling stock mechanicalResistance");

        if (aerodynamicResistance < 0)
            throw new InvalidInfraException("Invalid rolling stock aerodynamicResistance");

        if (length <= 0)
            throw new InvalidInfraException("invalid rolling stock length");

        if (mass <= 0)
            throw new InvalidInfraException("invalid rolling stock mass");

        if (inertiaCoefficient <= 0)
            throw new InvalidInfraException("Invalid rolling stock inertia coefficient");
    }
}
