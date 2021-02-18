package fr.sncf.osrd.infra.detectorgraph;

import fr.sncf.osrd.infra.graph.AbstractEdge;
import fr.sncf.osrd.infra.graph.AbstractNode;

import java.util.ArrayList;

public class DetectorNode extends AbstractNode {
    /**
     * List of neighbors seen when moving across the detector from the end of the track section to the beginning
     */
    public final ArrayList<TVDSectionPath> startToStopNeighbors;

    /**
     * List of neighbors seen when moving across the detector from the beginning of the track section to the end
     */
    public final ArrayList<TVDSectionPath> stopToStartNeighbors;

    public DetectorNode(ArrayList<TVDSectionPath> startToStopNeighbors,
                        ArrayList<TVDSectionPath> stopToStartNeighbors) {
        this.startToStopNeighbors = startToStopNeighbors;
        this.stopToStartNeighbors = stopToStartNeighbors;
    }
}
