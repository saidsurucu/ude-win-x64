import javax.swing.UIManager;

/** 7b: skin kurulup install() cagrildiktan sonra UIManager delegate'lerimizin tuttugunu dogrular. */
public class SkinProbe {
    public static void main(String[] a) throws Exception {
        org.jvnet.substance.SubstanceLookAndFeel.setSkin(new macosskin.FlatUdeSkin());
        macosskin.WordTooltip.install();
        macosskin.WordCombo.install();
        macosskin.WordCheck.install();
        macosskin.WordButton.install();
        macosskin.WordTabs.install();
        macosskin.WordField.install();
        String[] keys = {"ButtonUI","ComboBoxUI","CheckBoxUI","RadioButtonUI","TabbedPaneUI","TextFieldUI","ToolTipUI"};
        for (String k : keys) System.out.println(k + " -> " + UIManager.get(k));
    }
}
