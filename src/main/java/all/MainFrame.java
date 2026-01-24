package all;

import java.awt.Color;

import javax.swing.JFrame;


public class MainFrame extends JFrame{

    final static int WIDTH = 1280;
    final static int HEIGHT = 720;
    SimPanel simPanel;
    ToolPanel toolPanel;
    OptionPanel optionPanel;

    public static void main(String[] args) {
        new MainFrame();
    }

    public MainFrame() {
        // add frame components
        addSimPanel();
        // addToolPanel();
        // addOptionPanel();

        // frame settings
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(WIDTH, HEIGHT);
        this.setResizable(true);
        this.setTitle("Gravity Simulation");
        this.getContentPane().setBackground(Color.darkGray);
        this.setLayout(null);
        this.setVisible(true);
    }    

    private void addSimPanel() {
        // initialize the simulation panel
        simPanel = new SimPanel();
        this.getLayeredPane().add(simPanel, Integer.valueOf(1));
        simPanel.requestFocus();
        simPanel.startSimulationThread();
    }

    private void addToolPanel() {
        // initialize the tool panel
        toolPanel = new ToolPanel();
        this.getLayeredPane().add(toolPanel, Integer.valueOf(2));
    }

    private void addOptionPanel() {
        // initialize settings panel
        optionPanel = new OptionPanel();
        this.getLayeredPane().add(optionPanel, Integer.valueOf(2));
    }
}
