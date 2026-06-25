package macosskin;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 * Sekmeli panelleri (Bul/Değiştir diyaloğu vb.) Word tarzına çeker (SKIN=1).
 * Substance'ın kutulu, 3B kenarlıklı sekmeleri yerine düz görünüm:
 * sekme kutusu/içerik çerçevesi çizilmez, seçili sekme vurgu mavisi alt
 * çubukla işaretlenir, sekme satırının altına tek ince ayraç çizgisi gelir.
 * Skin değişimi UIDefaults'u tazelediğinden hem aF.run() hem
 * setSkin(String) sarmasından çağrılmalı.
 */
public final class WordTabs {
    private WordTabs() {}

    static Color accent() { return new Color(59, 105, 218); }

    static Color text(boolean dark) {
        return dark ? new Color(228, 228, 228) : new Color(38, 38, 38);
    }

    static Color textUnselected(boolean dark) {
        return dark ? new Color(170, 170, 170) : new Color(96, 96, 96);
    }

    static Color separator(boolean dark) {
        return dark ? new Color(90, 90, 90) : new Color(222, 222, 222);
    }

    static Color hoverFill(boolean dark) {
        return dark ? new Color(61, 61, 61) : new Color(232, 232, 232);
    }

    public static void install() {
        try {
            boolean dark = DarkMode.isDark();
            UIManager.put("TabbedPaneUI", "macosskin.WordTabs$UI");
            UIManager.put("TabbedPane.foreground", new ColorUIResource(text(dark)));
            UIManager.put("TabbedPane.contentBorderInsets", new Insets(8, 2, 2, 2));
            UIManager.put("TabbedPane.tabInsets", new Insets(6, 14, 6, 14));
            UIManager.put("TabbedPane.selectedTabPadInsets", new Insets(0, 0, 0, 0));
            UIManager.put("TabbedPane.tabAreaInsets", new Insets(2, 6, 0, 6));
            DarkMode.trace("WordTabs kuruldu dark=" + dark);
        } catch (Throwable t) {
            DarkMode.trace("WordTabs HATA: " + t);
        }
    }

    public static final class UI extends BasicTabbedPaneUI {
        public static ComponentUI createUI(JComponent c) {
            return new UI();
        }

        protected void installDefaults() {
            super.installDefaults();
            tabPane.setOpaque(false);
        }

        protected void paintTabBackground(Graphics g, int tabPlacement,
                int tabIndex, int x, int y, int w, int h, boolean isSelected) {
            if (!isSelected && getRolloverTab() == tabIndex) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hoverFill(DarkMode.isDark()));
                g2.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 8, 8);
                g2.dispose();
            }
        }

        protected void paintTabBorder(Graphics g, int tabPlacement,
                int tabIndex, int x, int y, int w, int h, boolean isSelected) {
            if (isSelected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accent());
                g2.fillRoundRect(x + 6, y + h - 3, w - 12, 3, 3, 3);
                g2.dispose();
            }
        }

        protected void paintContentBorder(Graphics g, int tabPlacement,
                int selectedIndex) {
            int width = tabPane.getWidth();
            Insets insets = tabPane.getInsets();
            int tabAreaHeight = calculateTabAreaHeight(tabPlacement,
                runCount, maxTabHeight);
            int y = insets.top + tabAreaHeight - 1;
            g.setColor(separator(DarkMode.isDark()));
            g.fillRect(insets.left, y, width - insets.left - insets.right, 1);
        }

        protected void paintFocusIndicator(Graphics g, int tabPlacement,
                Rectangle[] rects, int tabIndex, Rectangle iconRect,
                Rectangle textRect, boolean isSelected) {
        }

        protected void paintText(Graphics g, int tabPlacement,
                java.awt.Font font, java.awt.FontMetrics metrics, int tabIndex,
                String title, Rectangle textRect, boolean isSelected) {
            boolean dark = DarkMode.isDark();
            g.setFont(font);
            g.setColor(tabPane.isEnabledAt(tabIndex)
                ? (isSelected ? text(dark) : textUnselected(dark))
                : textUnselected(dark));
            int mnemIndex = tabPane.getDisplayedMnemonicIndexAt(tabIndex);
            javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(
                g, title, mnemIndex, textRect.x,
                textRect.y + metrics.getAscent());
        }
    }
}
