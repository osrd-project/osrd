package fr.sncf.osrd.infra.railjson.schema.signaling;

import com.squareup.moshi.*;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.sncf.osrd.infra.railjson.schema.ID;
import fr.sncf.osrd.infra.railjson.schema.Identified;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

/** This class represents the function of the behaviour of a signal. */
@SuppressFBWarnings({"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
public class RJSSignalFunction implements Identified {
    /** Name of the function */
    @Json(name = "function_name")
    public final String functionName;

    /** The list of the names of the argument of the function. Types are deduced from the AST */
    public final String[] arguments;

    /** A collections of expressions to be evaluated */
    public final RJSSignalExpr[] rules;

    /**
     * A template for a signal. It contains references to parameters which are specified inside actual signals.
     * @param functionName The identifier for the template
     * @param arguments the function parameters
     * @param rules The list of rules which apply
     */
    public RJSSignalFunction(
            String functionName,
            String[] arguments,
            RJSSignalExpr[] rules
    ) {
        this.functionName = functionName;
        this.arguments = arguments;
        this.rules = rules;
    }

    @Override
    public String getID() {
        return functionName;
    }

    public static final class ArgumentRef<T> {
        public final String argumentName;

        public ArgumentRef(String argumentName) {
            this.argumentName = argumentName;
        }

        public static class Adapter<T> extends JsonAdapter<ArgumentRef<T>> {
            @SuppressWarnings({"rawtypes", "unchecked"})
            public static final JsonAdapter.Factory FACTORY = new ArgumentRef.Adapter()::factory;

            private JsonAdapter<?> factory(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
                // the raw type is the one without a type parameter
                Class<?> rawType = Types.getRawType(type);
                if (!annotations.isEmpty())
                    return null;

                // if the type of the objects to adapt isn't something the factory can produce adapters for,
                // return null to tell the frame
                if (rawType != ArgumentRef.class)
                    return null;

                return this;
            }

            @Override
            public ArgumentRef<T> fromJson(JsonReader reader) throws IOException {
                return new ArgumentRef<T>(reader.nextString());
            }

            @Override
            public void toJson(@NonNull JsonWriter writer, ArgumentRef<T> value) throws IOException {
                if (value != null)
                    writer.value(value.argumentName);
                else
                    writer.nullValue();
            }
        }
    }
}
