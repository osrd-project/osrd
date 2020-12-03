package fr.sncf.osrd.infra.graph;

import fr.sncf.osrd.util.Freezable;
import fr.sncf.osrd.util.Indexable;

public abstract class AbstractNode<EdgeT extends AbstractEdge<?>> implements Indexable, Freezable {
    private int index = -1;

    @Override
    public void setIndex(int index) {
        assert this.index == -1;
        this.index = index;
    }

    @Override
    public int getIndex() {
        assert index != -1;
        return index;
    }
}
