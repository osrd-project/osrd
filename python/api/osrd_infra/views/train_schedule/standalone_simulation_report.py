from typing import Optional

from osrd_schemas.path import PathPayload
from rest_framework.exceptions import APIException

from osrd_infra.models import PathModel, SimulationOutput, TrainSchedule
from osrd_infra.utils import call_backend, make_exception_from_error
from osrd_infra.views.projection import Projection


class SignalingSimulationError(APIException):
    status_code = 500
    default_detail = "An internal signaling simulation error occurred"
    default_code = "internal_signaling_simulation_error"


def create_simulation_report(
    infra,
    train_schedule: TrainSchedule,
    projection_path: PathModel,
    *,
    simulation_output: Optional[SimulationOutput] = None,
):
    """Create simulation report for a given input.

    Args:
        train_schedule: Train schedule associated to the simulation on which report is created.
        projection_path: Projection path.
        simulation_output: Simulation output corresponding to the given ``train_schedule``.
            Can be ``None`` since non STDCM's simulations are expected to come from database.
            Currently STDCM's simulations are not stored in database.
    """
    if simulation_output is None:
        simulation_output = train_schedule.simulation_output
    train_path = train_schedule.path
    train_path_payload = PathPayload.parse_obj(train_schedule.path.payload)

    # Compute projection object
    projection_path_payload = PathPayload.parse_obj(projection_path.payload)
    projection = Projection(projection_path_payload)
    train_length = train_schedule.rolling_stock.length

    base = project_simulation_results(
        infra,
        train_schedule,
        simulation_output.base_simulation,
        train_path_payload,
        projection,
        projection_path_payload,
        train_schedule.departure_time,
        train_length,
    )
    res = {
        "id": train_schedule.pk,
        "labels": train_schedule.labels,
        "path": train_schedule.path_id,
        "name": train_schedule.train_name,
        "vmax": simulation_output.mrsp,
        "slopes": train_path.slopes,
        "curves": train_path.curves,
        "base": base,
        "speed_limit_tags": train_schedule.speed_limit_tags,
        "electrification_ranges": simulation_output.electrification_ranges,
        "power_restriction_ranges": simulation_output.power_restriction_ranges,
    }

    # Check if train schedule has margins
    if simulation_output.eco_simulation is None:
        return res

    # Add margins and eco results if available
    res["eco"] = project_simulation_results(
        infra,
        train_schedule,
        simulation_output.eco_simulation,
        train_path_payload,
        projection,
        projection_path_payload,
        train_schedule.departure_time,
        train_length,
    )
    return res


def evaluate_signals(infra, version, projection_path_payload, signal_sightings, zone_updates):
    request_payload = {
        "infra": infra.pk,
        "expected_version": version,
        "train_path": projection_path_payload.dict(),
        "signal_sightings": signal_sightings,
        "zone_updates": zone_updates,
    }
    response = call_backend("/project_signals", json=request_payload)
    if not response:
        raise make_exception_from_error(response, SignalingSimulationError, SignalingSimulationError)
    return response.json()


def project_simulation_results(
    infra,
    train_schedule,
    simulation_result,
    train_path_payload: PathPayload,
    projection,
    projection_path_payload: PathPayload,
    departure_time,
    train_length,
):
    # Format data for charts
    sim_head_positions_results = simulation_result["head_positions"]
    head_positions = project_head_positions(sim_head_positions_results, projection, train_path_payload, departure_time)
    tail_positions = compute_tail_positions(head_positions, train_length)
    train_end_time = simulation_result["head_positions"][-1]["time"]

    signal_sightings = simulation_result["signal_sightings"]
    zone_updates = simulation_result["zone_updates"]
    signaling_sim_res = evaluate_signals(infra, None, projection_path_payload, signal_sightings, zone_updates)
    signal_updates = signaling_sim_res["signal_updates"]
    route_aspects = [
        {
            **update,
            "time_start": update["time_start"] + departure_time,
            "time_end": update.get("time_end", train_end_time) + departure_time,
        }
        for update in signal_updates
    ]

    speeds = [{**speed, "time": speed["time"] + departure_time} for speed in simulation_result["speeds"]]
    stops = [{**stop, "time": stop["time"] + departure_time} for stop in simulation_result["stops"]]

    return {
        "head_positions": head_positions,
        "tail_positions": tail_positions,
        "speeds": speeds,
        "stops": stops,
        "route_aspects": route_aspects,
        "mechanical_energy_consumed": simulation_result["mechanical_energy_consumed"],
    }


def interpolate_locations(loc_a, loc_b, path_position):
    diff_time = loc_b["time"] - loc_a["time"]
    diff_space = loc_b["path_offset"] - loc_a["path_offset"]
    if diff_space == 0:
        return loc_a["time"]
    coef = diff_time / diff_space
    return loc_a["time"] + (path_position - loc_a["path_offset"]) * coef


def project_head_positions(train_locations, projection, train_path_payload: PathPayload, departure_time: float):
    results = []
    loc_index = 0
    intersections = projection.intersections(train_path_payload)
    for path_range in intersections:
        current_curve = []
        begin_loc = path_range.begin
        # Skip points that doesn't intersect the range
        while train_locations[loc_index + 1]["path_offset"] < begin_loc.path_offset:
            loc_index += 1

        # Add begin point
        begin_time = interpolate_locations(
            train_locations[loc_index],
            train_locations[loc_index + 1],
            begin_loc.path_offset,
        )
        begin_position = projection.track_position(begin_loc.track, begin_loc.offset)
        assert begin_position is not None
        current_curve.append({"time": begin_time + departure_time, "position": begin_position})

        # Add intermediate points
        end_loc = path_range.end
        while (
            loc_index + 1 < len(train_locations) and train_locations[loc_index + 1]["path_offset"] < end_loc.path_offset
        ):
            loc_index += 1
            loc = train_locations[loc_index]
            position = projection.track_position(loc["track_section"], loc["offset"])
            assert position is not None
            current_curve.append({"time": loc["time"] + departure_time, "position": position})

        if loc_index + 1 < len(train_locations):
            # Add end points
            end_time = interpolate_locations(
                train_locations[loc_index],
                train_locations[loc_index + 1],
                end_loc.path_offset,
            )
            end_position = projection.track_position(end_loc.track, end_loc.offset)
            assert end_position is not None
            current_curve.append({"time": end_time + departure_time, "position": end_position})

        results.append(current_curve)
    return results


def compute_tail_positions(head_positions, train_length: float):
    results = []
    for curve in head_positions:
        ascending = curve[0]["position"] < curve[-1]["position"]
        first_pos = curve[0]["position"]
        current_curve = []
        if ascending:
            for point in curve:
                current_curve.append({**point, "position": max(first_pos, point["position"] - train_length)})
        else:
            for point in curve:
                current_curve.append({**point, "position": min(first_pos, point["position"] + train_length)})
        results.append(current_curve)
    return results


def build_signal_updates(signal_updates, departure_time):
    results = []

    for update in signal_updates:
        results.append(
            {
                "signal_id": update["signal_id"],
                "time_start": update["time_start"] + departure_time,
                "time_end": update["time_end"] + departure_time,
                "color": update["color"],
                "blinking": update["blinking"],
                "aspect_label": update["aspect_label"],
            }
        )
    return results