package fr.sncf.osrd.railjson.schema.infra;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.sncf.osrd.railjson.schema.common.Identified;
import fr.sncf.osrd.railjson.schema.infra.trackranges.RJSOperationalPointPart;
import java.util.List;

@SuppressFBWarnings({"UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD"})
public class RJSOperationalPoint implements Identified {
    public String id;
    public List<RJSOperationalPointPart> parts;

    public RJSOperationalPoint(String id, List<RJSOperationalPointPart> parts) {
        this.id = id;
        this.parts = parts;
    }

    @Override
    public String getID() {
        return id;
    }
}
