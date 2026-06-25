import java.awt.*; import java.awt.image.*; import javax.swing.*;

/** 7c: IconDarken.apply duz yesil ikonu ModeAwareImage'e sarar + koyu modda aydinlatir. */
public class DarkIconProbe {
    public static void main(String[] a) throws Exception {
        macosskin.DarkMode.setMode("dark");
        BufferedImage g = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        Graphics2D gg=g.createGraphics(); gg.setColor(new Color(16,124,65)); gg.fillRect(0,0,16,16); gg.dispose();
        ImageIcon icon = new ImageIcon(g);
        ImageIcon out = macosskin.IconDarken.apply(icon);
        System.out.println("wrapped=" + (out.getImage() instanceof macosskin.ModeAwareImage));
        BufferedImage canvas = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        Graphics2D cg=canvas.createGraphics(); out.paintIcon(null,cg,0,0); cg.dispose();
        Color c = new Color(canvas.getRGB(8,8));
        System.out.println("orig=(16,124,65) dark=(" + c.getRed()+","+c.getGreen()+","+c.getBlue()+")");
        macosskin.DarkMode.setMode("system");
    }
}
