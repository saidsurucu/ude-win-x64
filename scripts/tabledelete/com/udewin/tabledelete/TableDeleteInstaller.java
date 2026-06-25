package com.udewin.tabledelete;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import javax.swing.text.JTextComponent;

/**
 * TableDelete'i her metin alanina baglar. WPAppManager.main'e build-zamani
 * insertBefore ile cagrilir (Mac'teki -javaagent yerine). Listener yalniz
 * gelecekteki FOCUS_GAINED olaylarina tepki verir; kurulum aninda Swing'e
 * dokunmaz, bu yuzden EDT gerekmez.
 */
public final class TableDeleteInstaller {
    private static boolean installed = false;
    private TableDeleteInstaller() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                public void eventDispatched(AWTEvent e) {
                    if (e.getID() != FocusEvent.FOCUS_GAINED) return;
                    Object src = e.getSource();
                    if (src instanceof JTextComponent) {
                        try { TableDelete.bind((JTextComponent) src); }
                        catch (Throwable ignore) {}
                    }
                }
            }, AWTEvent.FOCUS_EVENT_MASK);
        } catch (Throwable t) {
            System.err.println("[tabledelete] kurulamadi: " + t);
        }
    }
}
