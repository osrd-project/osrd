package fr.sncf.osrd.railjson.schema.infra;

import com.squareup.moshi.Json;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.sncf.osrd.railjson.schema.common.Identified;
import fr.sncf.osrd.railjson.schema.geom.RJSLineString;
import fr.sncf.osrd.railjson.schema.infra.trackranges.RJSCurve;
import fr.sncf.osrd.railjson.schema.infra.trackranges.RJSLoadingGaugeLimit;
import fr.sncf.osrd.railjson.schema.infra.trackranges.RJSSlope;
import java.util.List;

@SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR", "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD"})
public class RJSTrackSection implements Identified {
    public String id;
    public double length;

    public List<RJSSlope> slopes;
    public List<RJSCurve> curves;

    @Json(name = "loading_gauge_limits")
    public List<RJSLoadingGaugeLimit> loadingGaugeLimits;

    public RJSLineString geo;
    public RJSLineString sch;

    public RJSTrackSection(String id, double length) {
        this.id = id;
        this.length = length;
    }

    @Override
    public String getID() {
        return id;
    }
}
