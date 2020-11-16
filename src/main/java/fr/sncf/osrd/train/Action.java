package fr.sncf.osrd.train;

public class Action implements Comparable<Action> {
    final ActionType type;
    final double force;

    public Action(ActionType type) {
        assert type != ActionType.ACCELERATE && type != ActionType.BRAKE;
        this.type = type;
        this.force = Double.NaN;
    }

    public Action(ActionType type, double force) {
        assert type == ActionType.ACCELERATE || type == ActionType.BRAKE;
        assert (force > 0. && type == ActionType.ACCELERATE) || (force < 0. && type == ActionType.BRAKE);
        this.type = type;
        this.force = force;
    }

    @Override
    public int compareTo(Action other) {
        int compare = Integer.compare(this.type.order, other.type.order);
        if (compare != 0)
            return compare;
        assert type == ActionType.ACCELERATE || type == ActionType.BRAKE;
        assert !Double.isNaN(this.force) && Double.isNaN(other.force);
        return Double.compare(this.force, other.force);
    }
}
