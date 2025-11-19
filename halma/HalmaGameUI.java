package halma;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import java.awt.RenderingHints;
import java.awt.BasicStroke;

public class HalmaGameUI extends JFrame {
    private JButton[][] buttons;
    private int size = 8;
    private int baseSize = 4; // tamanho da base triangular (escada) para 8x8
    private int selectedX = -1, selectedY = -1;
    private List<Point> moveSeq = new ArrayList<>();
    private JPanel chatPanel;
    private JTextArea chatAreaField;
    private JTextField chatInputField;
    private JLabel statusLabel;
    private boolean myTurn = false;
    private HalmaClient client;
    private String[] playerNames = new String[3];

    private JLabel p1MovesLabel;
    private JLabel p2MovesLabel;
    private Point lastMove = null;

    private Color lightSquare = new Color(170, 120, 70);
    private Color darkSquare = new Color(110, 70, 40);
    private Color player1Color = Color.BLACK;
    private Color player2Color = Color.WHITE;
    private Color lastMoveColor = new Color(255, 200, 0);
    private Color topBaseHighlight = new Color(85, 80, 82);
    private Color bottomBaseHighlight = new Color(204, 204, 204);

    private JPanel p1StatusPanel;
    private JPanel p2StatusPanel;
    private int myId = 0; // reserved for future use

    private Icon player1Icon;
    private Icon player2Icon;

    private Color scoreBgColor = new Color(30, 30, 30);
    private Color scoreFgColor = new Color(255, 255, 255);
    private Font scoreFont = new Font("Arial", Font.BOLD, 18);

    public HalmaGameUI(HalmaClient client, String playerName) {
        this.client = client;

        player1Icon = createPieceIconForStatus(player1Color);
        player2Icon = createPieceIconForStatus(player2Color);

        setTitle("Halma Online");
        setSize(980, 720);
        setMinimumSize(new Dimension(1200, 800));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        Color headerFooterBg = new Color(60, 63, 65);
        Color headerTextColor = Color.WHITE;

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(headerFooterBg);
        topBar.setPreferredSize(new Dimension(1200, 105));

        JLabel titleLabel = new JLabel("HALMA ONLINE", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 50));
        titleLabel.setForeground(headerTextColor);
        topBar.add(titleLabel, BorderLayout.CENTER);

        JPanel statusContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 5));
        statusContainer.setOpaque(false);
        statusContainer.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        p1StatusPanel = buildPlayerStatusPanel(1, player1Icon, scoreBgColor, scoreFgColor, scoreFont);
        p2StatusPanel = buildPlayerStatusPanel(2, player2Icon, scoreBgColor, scoreFgColor, scoreFont);

        statusContainer.add(p1StatusPanel);
        statusContainer.add(p2StatusPanel);
        topBar.add(statusContainer, BorderLayout.WEST);

        JButton abandonBtn = new JButton("DESISTIR");
        abandonBtn.setFont(new Font("Arial", Font.BOLD, 12));
        abandonBtn.setPreferredSize(new Dimension(100, 30));
        abandonBtn.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Tem certeza que deseja desistir? Isso dara a vitoria ao seu adversario.",
                    "Confirmacao de Desistencia",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (choice == JOptionPane.YES_OPTION) {
                client.sendChat("ABANDON_GAME");
            }
        });

        JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 25, 35));
        eastPanel.setOpaque(false);
        eastPanel.add(abandonBtn);
        topBar.add(eastPanel, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        JPanel boardPanel = new JPanel(new GridLayout(size, size));
        buttons = new JButton[size][size];

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                JButton b = new JButton();
                b.setMargin(new Insets(0, 0, 0, 0));
                b.setFocusPainted(false);
                b.setBorder(BorderFactory.createLineBorder(Color.black, 1));
                Color squareColor = getSquareColor(x, y);
                b.setBackground(squareColor);
                final int fx = x, fy = y;
                b.addActionListener(e -> handleClick(fx, fy));
                buttons[y][x] = b;
                boardPanel.add(b);
            }
        }
        boardPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(boardPanel, BorderLayout.CENTER);

        chatPanel = buildChatPanel(client);
        add(chatPanel, BorderLayout.EAST);

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 5));
        footerPanel.setBackground(headerFooterBg);
        footerPanel.setPreferredSize(new Dimension(980, 30));

        statusLabel = new JLabel("CONECTANDO...");
        statusLabel.setForeground(Color.YELLOW);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));

        JLabel instructionLabel = new JLabel("Clique para selecionar. Pressione ESC para cancelar.");
        instructionLabel.setForeground(new Color(180, 180, 180));
        instructionLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        footerPanel.add(statusLabel);
        footerPanel.add(instructionLabel);
        add(footerPanel, BorderLayout.SOUTH);

        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSelection");
        actionMap.put("clearSelection", new RunnableAction(this::clearSelection));

        highlightBases();
        setVisible(true);
    }

    private Icon createPieceIconForStatus(Color color) { return createPieceIconForStatusImpl(color); }
    private Icon createPieceIcon(Color color) { return createPieceIconImpl(color); }
    private Color getSquareColor(int x, int y) {
        if (isTopLeftBase(x, y)) return topBaseHighlight;
        else if (isBottomRightBase(x, y)) return bottomBaseHighlight;
        return ((x + y) % 2 == 0) ? lightSquare : darkSquare;
    }

    private boolean isTopLeftBase(int x, int y) { return (y >= 0 && y < baseSize) && (x >= 0 && x <= baseSize - 1 - y); }
    private boolean isBottomRightBase(int x, int y) { int rx = (size - 1) - x; int ry = (size - 1) - y; return (ry >= 0 && ry < baseSize) && (rx >= 0 && rx <= baseSize - 1 - ry); }

    public void setPlayerNameLabel(int id, String name) { if (id < 1 || id > 2) return; playerNames[id] = name; refreshPlayerLabels(); }

    private void refreshPlayerLabels() {
        setPanelName(p1StatusPanel, 1, (playerNames[1] != null) ? playerNames[1] : "Aguardando");
        setPanelName(p2StatusPanel, 2, (playerNames[2] != null) ? playerNames[2] : "Aguardando");
    }

    public void setPlayerId(int id) { this.myId = id; }

    public void updateScore(int p1Moves, int p2Moves) {
        if (p1MovesLabel != null) p1MovesLabel.setText(String.valueOf(p1Moves));
        if (p2MovesLabel != null) p2MovesLabel.setText(String.valueOf(p2Moves));
    }

    public void addChatMessage(String msg) {
        if (chatAreaField != null) { chatAreaField.append(msg + "\n"); chatAreaField.setCaretPosition(chatAreaField.getDocument().getLength()); }
    }

    public void clearChatArea() { if (chatAreaField != null) chatAreaField.setText(""); }

    public void enableTurn() { myTurn = true; statusLabel.setText("SUA VEZ!"); statusLabel.setForeground(new Color(120, 230, 120));}
    public void disableTurn() { myTurn = false; statusLabel.setText("AGUARDANDO ADVERSARIO..."); statusLabel.setForeground(Color.YELLOW);}

    public void showWin(int id) { JOptionPane.showMessageDialog(this, "Jogador " + id + " venceu!"); }


    private HalmaBoard currentBoard;

    public void setBoard(HalmaBoard board) {
        this.currentBoard = board;
        // Força uma atualização visual imediata sem movimento anterior destacado
        updateBoardFromModel(board, null);
    }

    /**
     * Atualiza os ícones de todos os botões com base no estado do HalmaBoard recebido.
     * @param board O tabuleiro atualizado.
     * @param lastMove O ponto de destino do último movimento (para realce), pode ser null.
     */
    public void updateBoardFromModel(HalmaBoard model, Point lastMove) {
        // Simplified: paint buttons based on model
        for (int y = 0; y < model.getSize(); y++) {
            for (int x = 0; x < model.getSize(); x++) {
                int v = model.getCell(x, y);
                JButton b = buttons[y][x];
                if (v == 1) { b.setIcon(createPieceIcon(player1Color)); }
                else if (v == 2) { b.setIcon(createPieceIcon(player2Color)); }
                else { b.setIcon(null); }

                // reset any highlight/background to default square color
                Color squareColor = getSquareColor(x, y);
                b.setBackground(squareColor);
                b.setBorder(BorderFactory.createLineBorder(Color.black, 1));
            }
        }
        this.lastMove = lastMove;

        // Highlight last move (if provided)
        if (this.lastMove != null) {
            int lx = this.lastMove.x;
            int ly = this.lastMove.y;
            if (ly >= 0 && ly < buttons.length && lx >= 0 && lx < buttons[0].length) {
                JButton lastBtn = buttons[ly][lx];
                lastBtn.setBackground(lastMoveColor);
            }
        }

        // no overlay highlighting active; done
    }

    // Auxiliares para identificar bases (caso não tenha na sua UI ainda)
    private boolean isPlayer1Base(int x, int y) {
        return (y < baseSize) && (x < baseSize - y);
    }


    private void showValidMovesForSelection() {
        // clear all highlights first
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            Color squareColor = getSquareColor(x, y);
            buttons[y][x].setBackground(squareColor);
            buttons[y][x].setBorder(BorderFactory.createLineBorder(Color.black, 1));
        }

        // reapply pieces/icons from current client board snapshot if available
        HalmaBoard model = client.getBoardCopy();
        if (model != null) {
            for (int y = 0; y < model.getSize(); y++) for (int x = 0; x < model.getSize(); x++) {
                int v = model.getCell(x, y);
                if (v == 1) buttons[y][x].setIcon(createPieceIcon(player1Color));
                else if (v == 2) buttons[y][x].setIcon(createPieceIcon(player2Color));
                else buttons[y][x].setIcon(null);
            }
        }

        // highlight last move if present
        if (this.lastMove != null) {
            int lx = this.lastMove.x;
            int ly = this.lastMove.y;
            if (ly >= 0 && ly < buttons.length && lx >= 0 && lx < buttons[0].length) {
                JButton lastBtn = buttons[ly][lx];
                lastBtn.setBackground(lastMoveColor);
            }
        }

        // if no selection, nothing else to do
        if (selectedX == -1) return;

        // compute valid moves using the model
        if (model == null) return; // can't compute
        java.util.List<Point> moves = model.getValidMoves(client.getMyId(), selectedX, selectedY);
        if (moves == null) return;
        for (Point p : moves) {
            if (p.y >= 0 && p.y < buttons.length && p.x >= 0 && p.x < buttons[0].length) {
                JButton target = buttons[p.y][p.x];
                target.setBackground(new Color(120, 230, 120)); // green
                target.setBorder(BorderFactory.createLineBorder(new Color(30, 120, 30), 2));
            }
        }
    }

    private void handleClick(int x, int y) {
        if (!myTurn) { addChatMessage("[Sistema] Ainda nao e sua vez."); return; }
        if (selectedX == -1) {
            // only start selection if the square contains a piece, and if we know our id, only our own pieces
            HalmaBoard model = client.getBoardCopy();
            if (model == null) { addChatMessage("[Sistema] Tabuleiro indisponivel."); return; }
            int cell = model.getCell(x, y);
            if (cell == 0) {return;}
            if (this.myId != 0 && cell != this.myId) {return;}

            // start selection
            selectedX = x; selectedY = y; moveSeq.clear(); moveSeq.add(new Point(x, y));
            showValidMovesForSelection();
        } else {
            // complete selection: add target and send
            moveSeq.add(new Point(x, y)); java.util.List<Point> seq = new ArrayList<>(moveSeq); client.sendMove(seq);
            selectedX = -1; selectedY = -1; moveSeq.clear();
            // refresh board to clear highlights
            HalmaBoard model = client.getBoardCopy(); if (model != null) updateBoardFromModel(model, this.lastMove);
        }
    }


    private void clearSelection() { selectedX = selectedY = -1; moveSeq.clear(); }

    private void highlightBases() { /* visual only */ }

    private JPanel buildChatPanel(HalmaClient client) {
        JPanel panel = new JPanel(new BorderLayout());
        chatAreaField = new JTextArea();
        chatAreaField.setEditable(false);
        chatAreaField.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(chatAreaField);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Arial", Font.BOLD, 12));
        tabbedPane.addTab("CHAT", scrollPane);

        chatInputField = new JTextField();
        JButton sendBtn = new JButton("ENVIAR");
        sendBtn.addActionListener(e -> {
            String msg = chatInputField.getText().trim();
            if (!msg.isEmpty()) { chatInputField.setText(""); client.sendChat(msg); }
        });

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));
        inputPanel.add(chatInputField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);

        panel.setPreferredSize(new Dimension(280, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 20));
        panel.add(tabbedPane, BorderLayout.CENTER);
        panel.add(inputPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPlayerStatusPanel(int playerId, Icon icon, Color bg, Color fg, Font font) {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBackground(bg);
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 15));

        JLabel iconLabel = new JLabel(icon);
        panel.add(iconLabel, BorderLayout.WEST);

        // Nome do jogador (pode ser pego do array playerNames se necessário)
        JLabel nameLabel = new JLabel("Jogador " + playerId);
        nameLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        nameLabel.setForeground(Color.WHITE);

        // Label de movimentos (Score)
        JLabel movesLabel = new JLabel("0", SwingConstants.CENTER);
        movesLabel.setForeground(fg);
        movesLabel.setFont(font);
        movesLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // [NOVO] Salva a referência para usar no updateScore
        if (playerId == 1) p1MovesLabel = movesLabel;
        else p2MovesLabel = movesLabel;

        JPanel scoreBox = new JPanel();
        scoreBox.setLayout(new BoxLayout(scoreBox, BoxLayout.Y_AXIS));
        scoreBox.setBackground(bg);
        scoreBox.add(nameLabel);
        scoreBox.add(movesLabel); // Adiciona o número de movimentos

        panel.add(scoreBox, BorderLayout.CENTER);

        return panel;
    }

    private void setPanelName(JPanel panel, int playerId, String name) {
        if (panel == null) return;
        Object o = panel.getClientProperty("nameLabel");
        if (o instanceof JLabel) {
            ((JLabel)o).setText(String.format("<html><b>JOGADOR %d</b><br>%s</html>", playerId, name));
        }
    }

    private void setPanelMoves(JPanel panel, int moves, Color fg) {
        if (panel == null) return;
        Object o = panel.getClientProperty("movesLabel");
        if (o instanceof JLabel) {
            ((JLabel)o).setText(String.valueOf(moves));
            ((JLabel)o).setForeground(fg);
        }
    }

    private static Icon createPieceIconForStatusImpl(Color color) {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.fillOval(2, 2, 20, 20);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1));
        g.drawOval(2, 2, 20, 20);
        g.dispose();
        return new ImageIcon(img);
    }

    private static Icon createPieceIconImpl(Color color) {
        BufferedImage img = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        final int x = 6, y = 6, size = 36;
        Color shadowColor = color.equals(Color.BLACK) ? Color.LIGHT_GRAY : Color.BLACK;
        Color lightColor = color.equals(Color.BLACK) ? Color.WHITE : Color.BLACK;
        g.setColor(color);
        g.fillOval(x, y, size, size);
        g.setStroke(new BasicStroke(4f));
        g.setColor(shadowColor);
        g.drawOval(x + 1, y + 1, size, size);
        g.setStroke(new BasicStroke(3f));
        g.setColor(lightColor);
        g.drawOval(x, y, size, size);
        g.dispose();
        return new ImageIcon(img);
    }

    // Small helper to avoid multiple anonymous AbstractAction classes
    private static class RunnableAction extends AbstractAction {
        private final Runnable r;
        public RunnableAction(Runnable r) { this.r = r; }
        @Override
        public void actionPerformed(ActionEvent e) { if (r != null) r.run(); }
    }
}