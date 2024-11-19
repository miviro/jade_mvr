package src.jade_mvr;

import javax.swing.*;
import java.awt.event.ActionListener;

public class GUI extends JFrame {
    private JButton incrementButton;
    private JButton resetButton;
    private JLabel counterLabel;

    public GUI() {
        // Configuración de la ventana
        setTitle("Contador Simple");
        setSize(300, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Componentes
        incrementButton = new JButton("Incrementar");
        resetButton = new JButton("Reiniciar");
        counterLabel = new JLabel("Contador: 0", SwingConstants.CENTER);

        // Layout
        setLayout(new java.awt.BorderLayout());
        add(counterLabel, java.awt.BorderLayout.CENTER);
        add(incrementButton, java.awt.BorderLayout.NORTH);
        add(resetButton, java.awt.BorderLayout.SOUTH);
    }

    public void setCounterLabel(String text) {
        counterLabel.setText(text);
    }

    public void addIncrementListener(ActionListener listener) {
        incrementButton.addActionListener(listener);
    }

    public void addResetListener(ActionListener listener) {
        resetButton.addActionListener(listener);
    }

    public static void main(String[] args) {
        MainAgent logic = new MainAgent();
        GUI gui = new GUI();

        // Vincular la GUI con la lógica
        logic.setView(gui);

        // Hacer visible la ventana
        gui.setVisible(true);
    }
}
