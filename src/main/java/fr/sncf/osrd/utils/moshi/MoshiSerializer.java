package fr.sncf.osrd.utils.moshi;

import com.squareup.moshi.JsonAdapter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import okio.Okio;
import okio.Sink;

import java.io.IOException;
import java.nio.file.Path;

public class MoshiSerializer {
    /** Serialize the given object to some file */
    @SuppressFBWarnings(
            value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE",
            justification = "that's a spotbugs bug :)"
    )
    public static <T> void serialize(JsonAdapter<T> adapter, T obj, Path outputPath) throws IOException {
        try (
                Sink fileSink = Okio.sink(outputPath);
                var bufferedSink = Okio.buffer(fileSink)
        ) {
            adapter.toJson(bufferedSink, obj);
        }
    }
}
