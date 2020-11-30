package fr.sncf.osrd;

import fr.sncf.osrd.infra.parsing.railml.RailMLParser;


public class App {
    /**
     * The main entry point for OSRD.
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: osrd infra.railml");
            System.exit(1);
        }

        var infraRailMLPath = args[0];
        new RailMLParser(infraRailMLPath).parse();
        // var infra =
        // Config config = new Config(1.0f, infra);
        // Simulation simulation = new Simulation(config);
        // simulation.run();
    }
}
