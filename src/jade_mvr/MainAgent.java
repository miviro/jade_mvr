package src.jade_mvr;

public class MainAgent {
    private int count;
    private GUI view;

    public MainAgent() {
        this.count = 0; // Initialize the counter
        this.view = new GUI();

        // Show the GUI
        view.setVisible(true);
    }


    private void updateView() {
    }

    public static void main(String[] args) {
        new MainAgent(); // Start the application
    }
}
