package fr.sncf.osrd.envelope_utils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import javax.swing.*;

@ExcludeFromGeneratedCodeCoverage
@SuppressFBWarnings({"WA_AWAIT_NOT_IN_LOOP"})
public class SwingUtils {
    /** Creates a new frame, adds a pane and synchronously wait for the frame to be closed */
    public static void debugPanel(String windowName, Supplier<JPanel> panelSupplier) {
        final var lock = new ReentrantLock();
        final var windowClosed = lock.newCondition();

        SwingUtilities.invokeLater(() -> {
            var frame = new JFrame(windowName);
            frame.setSize(600, 400); // The values are arbitrary, it just prevents a single pixel
            // window
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(panelSupplier.get());
            frame.setVisible(true);
            frame.addWindowListener(new WindowAdapter() {
                public void windowClosed(WindowEvent e) {
                    lock.lock();
                    try {
                        windowClosed.signal();
                    } finally {
                        lock.unlock();
                    }
                }
            });
        });

        try {
            lock.lock();
            try {
                windowClosed.await();
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException ignored) {
        }
    }
}
