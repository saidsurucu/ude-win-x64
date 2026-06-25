package macosskin;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Path2D;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

/**
 * Combo box'ları Word paletine çeker (SKIN=1). Substance'ın
 * SubstanceComboBoxUI'ı yerine Basic-tabanlı delegate kurulur (WordTooltip
 * deseninin aynısı): yuvarlak köşeli düz dolgu + ince kontur + tek küçük
 * chevron; Substance'ın ayrık ok bölmesi ve bevel görünümü gider.
 * Renkler Word ekran ölçümünden: koyu dolgu #333333, kontur #5A5A5A
 * (tooltip çizgisiyle aynı); açık dolgu beyaz, kontur #C6C6C6.
 * Skin değişimi UIDefaults'u tazelediğinden hem aF.run() hem
 * setSkin(String) sarmasından çağrılmalı.
 */
public final class WordCombo {
    private WordCombo() {}

    static Color fill() {
        return DarkMode.isDark() ? new Color(51, 51, 51) : Color.WHITE;
    }

    static Color fillDisabled() {
        return DarkMode.isDark() ? new Color(44, 44, 44) : new Color(242, 242, 242);
    }

    static Color line() {
        return DarkMode.isDark() ? new Color(90, 90, 90) : new Color(198, 198, 198);
    }

    static Color text() {
        return DarkMode.isDark() ? new Color(228, 228, 228) : new Color(38, 38, 38);
    }

    static Color textDisabled() {
        return DarkMode.isDark() ? new Color(110, 110, 110) : new Color(168, 168, 168);
    }

    static Color chevron() {
        return DarkMode.isDark() ? new Color(200, 200, 200) : new Color(96, 96, 96);
    }

    public static void install() {
        try {
            boolean dark = DarkMode.isDark();
            UIManager.put("ComboBoxUI", "macosskin.WordCombo$UI");
            UIManager.put("ComboBox.background", new ColorUIResource(fill()));
            UIManager.put("ComboBox.foreground", new ColorUIResource(text()));
            UIManager.put("ComboBox.disabledBackground", new ColorUIResource(fillDisabled()));
            UIManager.put("ComboBox.disabledForeground", new ColorUIResource(textDisabled()));
            UIManager.put("ComboBox.selectionBackground",
                new ColorUIResource(dark ? new Color(71, 71, 71) : new Color(208, 208, 208)));
            UIManager.put("ComboBox.selectionForeground", new ColorUIResource(text()));
            UIManager.put("ComboBox.border", new BorderUIResource(new EmptyBorder(1, 8, 1, 2)));
            DarkMode.trace("WordCombo kuruldu dark=" + dark);
        } catch (Throwable t) {
            DarkMode.trace("WordCombo HATA: " + t);
        }
    }

    public static final class UI extends BasicComboBoxUI {
        public static ComponentUI createUI(JComponent c) {
            return new UI();
        }

        protected void installDefaults() {
            super.installDefaults();
            LookAndFeel.installProperty(comboBox, "opaque", Boolean.FALSE);
        }

        protected JButton createArrowButton() {
            JButton b = new JButton() {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(comboBox.isEnabled() ? chevron() : textDisabled());
                    g2.setStroke(new BasicStroke(1.4f,
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    float cw = 7f, ch = 4f;
                    float x = (getWidth() - cw) / 2f;
                    float y = (getHeight() - ch) / 2f;
                    Path2D.Float p = new Path2D.Float();
                    p.moveTo(x, y);
                    p.lineTo(x + cw / 2f, y + ch);
                    p.lineTo(x + cw, y);
                    g2.draw(p);
                    g2.dispose();
                }
            };
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setContentAreaFilled(false);
            b.setFocusable(false);
            b.setOpaque(false);
            return b;
        }

        public void update(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            int w = c.getWidth(), h = c.getHeight();
            g2.setColor(c.isEnabled() ? c.getBackground() : fillDisabled());
            g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
            g2.setColor(line());
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
            g2.dispose();
            paint(g, c);
        }

        public void paintCurrentValueBackground(Graphics g, Rectangle bounds,
                boolean hasFocus) {
        }

        public void paintCurrentValue(Graphics g, Rectangle bounds,
                boolean hasFocus) {
            super.paintCurrentValue(g, bounds, false);
        }

        protected ComboPopup createPopup() {
            BasicComboPopup p = (BasicComboPopup) super.createPopup();
            p.setBorder(new LineBorder(line()));
            return p;
        }

        protected void configureEditor() {
            super.configureEditor();
            if (editor instanceof JComponent) {
                ((JComponent) editor).setOpaque(false);
                editor.setForeground(comboBox.getForeground());
                if (editor instanceof JTextField) {
                    JTextField tf = (JTextField) editor;
                    tf.setBorder(BorderFactory.createEmptyBorder());
                    tf.setCaretColor(text());
                }
            }
        }

        private String typeBuf = "";
        private long typeLast;

        /* Popup açıkken harf yazmak seçimi COMMIT EDEMEZ: UDE'nin combo
         * listener'ları (örn. font ailesi: gui.iv) seçim değişir değişmez
         * niteliği uygular ve requestFocusInWindow ile odağı editöre taşır,
         * odak kaybı da popup'ı kapatır — "c yazınca C059 seçilip liste
         * kapanıyor" hatasının kökü. Bu yüzden Basic'in selectWithKeyChar
         * yolu popup açıkken atlanır; biriken önek yalnız listedeki vurguyu
         * taşır, seçim Enter ya da tıkla kesinleşir. */
        protected KeyListener createKeyListener() {
            final KeyListener base = super.createKeyListener();
            return new KeyListener() {
                public void keyTyped(KeyEvent e) { base.keyTyped(e); }
                public void keyReleased(KeyEvent e) { base.keyReleased(e); }
                public void keyPressed(KeyEvent e) {
                    if (handleTypeAhead(e)) { e.consume(); return; }
                    base.keyPressed(e);
                }
            };
        }

        boolean handleTypeAhead(KeyEvent e) {
            if (comboBox == null || comboBox.isEditable()
                    || !comboBox.isEnabled() || !comboBox.isPopupVisible()) {
                return false;
            }
            if (e.isMetaDown() || e.isControlDown() || e.isAltDown()) {
                return false;
            }
            char ch = e.getKeyChar();
            if (ch == KeyEvent.CHAR_UNDEFINED || ch < ' ' || ch == 127) {
                return false;
            }
            long factor = 1000L;
            Object tf = UIManager.get("ComboBox.timeFactor");
            if (tf instanceof Number) {
                factor = ((Number) tf).longValue();
            }
            typeBuf = nextBuffer(typeBuf, ch, e.getWhen() - typeLast, factor);
            typeLast = e.getWhen();
            int i = findPrefixMatch(comboBox.getModel(), typeBuf);
            if (i >= 0 && listBox != null) {
                listBox.setSelectedIndex(i);
                listBox.ensureIndexIsVisible(i);
            }
            return true;
        }

        static String nextBuffer(String buf, char ch, long elapsed, long factor) {
            return (elapsed > factor ? "" : buf) + ch;
        }

        static int findPrefixMatch(ListModel<?> m, String prefix) {
            if (prefix.isEmpty()) {
                return -1;
            }
            for (int i = 0; i < m.getSize(); i++) {
                Object o = m.getElementAt(i);
                String s = o == null ? null : o.toString();
                if (s != null && s.regionMatches(true, 0, prefix, 0, prefix.length())) {
                    return i;
                }
            }
            return -1;
        }
    }
}
