package halma;

import java.io.*;
import java.net.*;
import java.awt.*;
import java.awt.Point;
import java.util.List;
import javax.swing.*;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import javax.swing.*;

public class HalmaClient extends UnicastRemoteObject implements IHalmaClient {
    private IHalmaServer server;
    private HalmaGameUI ui;
    private int myId = 0;
    private String playerName;
    private HalmaBoard boardLocal = new HalmaBoard();

    @Override
    public void ping() throws RemoteException {
        // Apenas para manter conexão viva ou teste
    }

    // Construtor precisa lançar RemoteException por causa do UnicastRemoteObject
    public HalmaClient(String host, int port, String playerName) throws RemoteException {
        super(); // Exporta este objeto (cliente) para receber callbacks
        this.playerName = playerName;

        try {
            String rmiUrl = "rmi://" + host + ":" + port + "/HalmaService";
            server = (IHalmaServer) Naming.lookup(rmiUrl);

            // Registra este cliente no servidor e pega o ID
            this.myId = server.registrarCliente(this, playerName);

            if (this.myId == -1) {
                throw new Exception("Servidor cheio ou jogo em andamento.");
            }

        } catch (Exception e) {
            // Encapsula erro para ser tratado na UI/Launcher
            throw new RemoteException("Falha ao conectar via RMI: " + e.getMessage(), e);
        }
    }

    public void initGameUI() {
        SwingUtilities.invokeLater(() -> {
            ui = new HalmaGameUI(this, playerName);
            ui.setPlayerId(myId);
            ui.addChatMessage("[Sistema] Conectado via RMI. Voce eh o Jogador " + myId);
            // Atualiza o board inicial na UI
            ui.setBoard(boardLocal);
        });
    }


    public void sendMove(List<Point> seq) {
        if (seq == null || seq.size() < 2) return;
        try {
            server.enviarMovimento(myId, seq);
        } catch (RemoteException e) {
            ui.addChatMessage("[Erro] Falha ao enviar movimento: " + e.getMessage());
        }
    }

    public void sendChat(String msg) {
        try {
            server.enviarChat(myId, msg);
        } catch (RemoteException e) {
            ui.addChatMessage("[Erro] Falha ao enviar chat.");
        }
    }

    public int getMyId() {
        return myId;
    }

    public HalmaBoard getBoardCopy() {
        // Retorna uma cópia do estado local
        return new HalmaBoard(boardLocal);
    }

    // --- Métodos RMI (Callbacks vindos do Servidor) ---

    @Override
    public void receberMensagem(String msg) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            if (ui != null) ui.addChatMessage(msg);
        });
    }

    @Override
    public void atualizarTabuleiro(HalmaBoard newBoard, Point lastMove) throws RemoteException {
        this.boardLocal = newBoard; // Atualiza estado local
        SwingUtilities.invokeLater(() -> {
            if (ui != null) {
                ui.setBoard(newBoard); // Importante para a UI saber o estado atual
                ui.updateBoardFromModel(newBoard, lastMove);
            }
        });
    }

    @Override
    public void atualizarPlacar(int p1Moves, int p2Moves) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            if (ui != null) {
                ui.updateScore(p1Moves, p2Moves);
            }
        });
    }

    @Override
    public void definirTurno(boolean meuTurno) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            if (ui != null) {
                if (meuTurno) ui.enableTurn();
                else ui.disableTurn();
            }
        });
    }

    @Override
    public void notificarFimDeJogo(int idVencedor) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            if (ui != null) {
                ui.showWin(idVencedor);
                ui.disableTurn();
            }
        });
    }

    public void disconnect() {
        try {
            if (server != null && myId != 0) {
                server.desconectar(myId);
            }
        } catch (RemoteException e) {
            // Servidor já pode ter caído, apenas ignora
        }
        System.exit(0);
    }

    // --- Launcher Integrado (Main) ---
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            final String host = (args.length > 0) ? args[0] : "localhost";
            showInitialScreen(host);
        });
    }

    private static void showInitialScreen(String host) {
        final JFrame frame = new JFrame("Halma Online - Conexao");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(420, 450);
        frame.setLayout(new BorderLayout(10, 10));
        frame.setLocationRelativeTo(null);

        final Color bgColor = new Color(40, 44, 52);
        final Color fgColor = new Color(255, 255, 255);
        final Color accentColor = new Color(102, 217, 239);

        final JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(bgColor);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("HALMA ONLINE");
        title.setFont(new Font("Arial", Font.BOLD, 30));
        title.setForeground(accentColor);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Insira os dados para conectar-se.");
        subtitle.setFont(new Font("Arial", Font.PLAIN, 16));
        subtitle.setForeground(fgColor);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        final JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBackground(bgColor);
        inputPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 8, 0);

        JLabel nameLabel = new JLabel("Seu Nome:");
        nameLabel.setForeground(fgColor);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        inputPanel.add(nameLabel, gbc);

        final JTextField nameField = new JTextField(20);
        nameField.setBorder(BorderFactory.createLineBorder(accentColor, 2));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        inputPanel.add(nameField, gbc);

        JLabel hostLabel = new JLabel("Host/IP do Servidor:");
        hostLabel.setForeground(fgColor);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        inputPanel.add(hostLabel, gbc);

        final JTextField hostField = new JTextField(host);
        hostField.setBorder(BorderFactory.createLineBorder(accentColor, 2));
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.7;
        inputPanel.add(hostField, gbc);

        JLabel portLabel = new JLabel("Porta:");
        portLabel.setForeground(fgColor);
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        inputPanel.add(portLabel, gbc);

        final JTextField portField = new JTextField("9090");
        portField.setBorder(BorderFactory.createLineBorder(accentColor, 2));
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0.3;
        inputPanel.add(portField, gbc);

        final JButton connectButton = new JButton("CONECTAR");
        connectButton.setBackground(accentColor.darker());
        connectButton.setForeground(fgColor);
        connectButton.setFont(new Font("Arial", Font.BOLD, 16));
        connectButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JPanel buttonWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonWrapper.setBackground(bgColor);
        buttonWrapper.add(connectButton);

        final JLabel statusLabel = new JLabel("Status: Pronto.");
        statusLabel.setForeground(fgColor);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainPanel.add(title);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        mainPanel.add(subtitle);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(inputPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(buttonWrapper);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        mainPanel.add(statusLabel);

        frame.add(mainPanel, BorderLayout.CENTER);

        final Runnable attemptConnection = () -> {
            final String name = nameField.getText().trim();
            final String hostLocal = hostField.getText().trim();
            final String portStr = portField.getText().trim();

            if (name.isEmpty() || name.length() < 3) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("ERRO: O nome deve ter pelo menos 3 caracteres.");
                    statusLabel.setForeground(Color.RED);
                    nameField.requestFocusInWindow();
                });
                return;
            }

            int portLocal;
            try {
                portLocal = Integer.parseInt(portStr);
                if (portLocal <= 0 || portLocal > 65535) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("ERRO: Porta inválida.");
                    statusLabel.setForeground(Color.RED);
                    portField.requestFocusInWindow();
                });
                return;
            }

            SwingUtilities.invokeLater(() -> {
                connectButton.setEnabled(false);
                nameField.setEnabled(false);
                hostField.setEnabled(false);
                portField.setEnabled(false);
                statusLabel.setText("Status: Tentando conectar a " + hostLocal + ":" + portLocal + "...");
                statusLabel.setForeground(Color.YELLOW);
            });

            new Thread(() -> {
                try {
                    HalmaClient client = new HalmaClient(hostLocal, portLocal, name);
                    SwingUtilities.invokeLater(() -> {
                        frame.dispose();
                        client.initGameUI();
                    });
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("ERRO: Falha ao conectar ao servidor em " + hostLocal + ":" + portLocal);
                        statusLabel.setForeground(Color.RED);
                        connectButton.setEnabled(true);
                        nameField.setEnabled(true);
                        hostField.setEnabled(true);
                        portField.setEnabled(true);
                    });
                    System.err.println("Falha na conexão: " + e.getMessage());
                }
            }).start();
        };

        connectButton.addActionListener(e -> attemptConnection.run());
        nameField.addActionListener(e -> attemptConnection.run());
        hostField.addActionListener(e -> attemptConnection.run());
        portField.addActionListener(e -> attemptConnection.run());

        frame.setVisible(true);
    }

}