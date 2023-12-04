package fr.sncf.osrd.stdcm.graph

import fr.sncf.osrd.graph.PathfindingEdgeLocationId
import fr.sncf.osrd.sim_infra.api.Block
import fr.sncf.osrd.sim_infra.api.DirDetectorId

data class STDCMNode(
    val time: Double, // Time at the transition of the edge
    val speed: Double,  // Speed at the end of the previous edge
    val detector: DirDetectorId?,  // Detector that separates the blocks, this is the physical location of the node
    val totalPrevAddedDelay: Double,  // Sum of all the delays we have added by shifting the departure time
    val maximumAddedDelay: Double,  // Maximum delay we can add by delaying the start time without causing conflicts
    val previousEdge: STDCMEdge,  // Edge that lead to this node
    val waypointIndex: Int,  // Index of the last waypoint passed by the train
    val locationOnBlock: PathfindingEdgeLocationId<Block>?,  // Position on a block, if this node isn't on the transition between blocks (stop)
    val stopDuration: Double?  // When the node is a stop, how long the train remains here
)