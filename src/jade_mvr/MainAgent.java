package src.jade_mvr;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MainAgent {
    private int count;
    private GUI view;

    public MainAgent() {
        this.count = 0; // Inicializar el contador
    }

    public void setView(GUI view) {
        this.view = view;

        // Conectar los botones de la GUI con la lógica
        view.addIncrementListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                incrementCount();
            }
        });

        view.addResetListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetCount();
            }
        });
    }

    private void incrementCount() {
        count++;
        updateView();
    }

    private void resetCount() {
        count = 0;
        updateView();
    }

    private void updateView() {
        view.setCounterLabel("Contador: " + count);
    }
}
