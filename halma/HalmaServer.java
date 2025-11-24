package halma;

import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.awt.Point;
import java.net.InetAddress;

public class HalmaServer extends UnicastRemoteObject implements IHalmaServer {
    private IHalmaClient player1;
    private IHalmaClient player2;
    private String p1Name, p2Name;

    private HalmaBoard board;
    private int currentTurn = 1;
    private boolean gameActive = false;
    private int p1Moves = 0, p2Moves = 0;

    public HalmaServer(int port) throws RemoteException {
        super(port); // Exporta o objeto na mesma porta do registro para facilitar firewall
        board = new HalmaBoard();
    }

    public static void main(String[] args) {
        try {
            // --- 1. CONFIGURAÇÃO DE IP ---
            String ip = getLocalAddress();
            if (args.length > 0 && !args[0].startsWith("-") && !isNumeric(args[0])) {
                ip = args[0];
            }
            System.setProperty("java.rmi.server.hostname", ip);

            // --- 2. CONFIGURAÇÃO DE PORTA DINÂMICA ---
            int portToUse = 0; // 0 significa "escolha automática"

            // Tenta ler a porta do segundo argumento (args[1]) ou do primeiro se for numérico
            if (args.length > 1 && isNumeric(args[1])) {
                portToUse = Integer.parseInt(args[1]);
            } else if (args.length > 0 && isNumeric(args[0])) {
                portToUse = Integer.parseInt(args[0]);
            }

            // Se a porta for 0, encontramos uma livre automaticamente
            if (portToUse == 0) {
                ServerSocket s = new ServerSocket(0);
                portToUse = s.getLocalPort();
                s.close(); // Libera para o RMI usar
            }

            // --- 3. INICIALIZAÇÃO DO RMI ---
            LocateRegistry.createRegistry(portToUse);
            HalmaServer server = new HalmaServer(portToUse);
            Naming.rebind("rmi://localhost:" + portToUse + "/HalmaService", server);

            System.out.println("------------------------------------------------");
            System.out.println("SERVIDOR RMI INICIADO!");
            System.out.println("IP:   " + ip);
            System.out.println("PORTA: " + portToUse);
            System.out.println("------------------------------------------------");
            System.out.println("Use essas informacoes para conectar os clientes.");
        } catch (Exception e) {
            System.err.println("Erro ao iniciar servidor RMI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Método auxiliar simples para checar se string é numero
    private static boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getLocalAddress() {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {
                return "127.0.0.1";
            }
        }
    }

    // --- Implementação da Interface RMI ---

    @Override
    public int registrarCliente(IHalmaClient client, String name) throws RemoteException {
        int playerId = -1;
        boolean startGame = false;
        synchronized (this) {
            if (player1 == null) {
                player1 = client;
                p1Name = name;
                playerId = 1;
                System.out.println("Jogador 1 conectado: " + name);
                client.receberMensagem("Aguardando Jogador 2...");
            } else if (player2 == null) {
                player2 = client;
                p2Name = name;
                playerId = 2;
                System.out.println("Jogador 2 conectado: " + name);
                startGame = true; // Marca para iniciar o jogo fora do bloco sync
            }
        }

        if (startGame) {
            // Inicia o jogo em uma nova thread com um pequeno delay
            // para dar tempo ao cliente 2 de construir sua UI.
            new Thread(() -> {
                try { Thread.sleep(200); } catch (InterruptedException e) {}
                iniciarJogo();
            }).start();
        }
        return playerId; // Retorna o ID do jogador ou -1 se o servidor estiver cheio
    }

    @Override
    public synchronized void enviarMovimento(int playerId, List<Point> sequencia) throws RemoteException {
        if (!gameActive || playerId != currentTurn) return;

        // Converte List<Point> para array int[] (lógica existente)
        int[] coords = new int[sequencia.size() * 2];
        for (int i = 0; i < sequencia.size(); i++) {
            coords[i * 2] = sequencia.get(i).x;
            coords[i * 2 + 1] = sequencia.get(i).y;
        }

        boolean valido = board.moverPecaSequence(coords, playerId);
        if (valido) {
            // [NOVO] Incrementa o placar
            if (playerId == 1) p1Moves++;
            else p2Moves++;

            // Lógica de troca de turno existente
            currentTurn = (currentTurn == 1) ? 2 : 1;

            // Verifica vitória (lógica existente)
            if (board.verificarVencedor(playerId)) {
                gameActive = false;
                notificarVitoria(playerId, true);
            } else {
                notificarTurno();
            }

            // Atualiza tabuleiro para todos
            Point lastMove = sequencia.get(sequencia.size() - 1);
            broadcastUpdate(lastMove);

            // [NOVO] Envia atualização de placar
            broadcastScore();
        } else {
            // Se o movimento for inválido, avisa o jogador (opcional)
            if (playerId == 1 && player1 != null) player1.receberMensagem("[Erro] Movimento invalido pelo servidor.");
            if (playerId == 2 && player2 != null) player2.receberMensagem("[Erro] Movimento invalido pelo servidor.");
        }
    }

    @Override
    public synchronized void enviarChat(int playerId, String msg) throws RemoteException {
        String nome = (playerId == 1) ? p1Name : p2Name;
        if ("ABANDON_GAME".equals(msg)) {
            if (!gameActive) return; // Não faz nada se o jogo não estiver ativo

            int winnerId = (playerId == 1) ? 2 : 1;
            String winnerName = (winnerId == 1) ? p1Name : p2Name;

            broadcastMsg(nome + " desistiu. " + winnerName + " venceu a partida!");
            notificarVitoria(winnerId, true); // Notifica e reinicia o jogo
        } else {
            String formatted = "[CHAT] " + nome + ": " + msg;
            broadcastMsg(formatted);
        }
    }

    @Override
    public synchronized void solicitarReinicio(int playerId) throws RemoteException {
        // Apenas o jogador 1 pode solicitar o reinício para evitar chamadas duplicadas.
        // E só reinicia se o jogo estiver inativo (após uma vitória)
        // e ambos os jogadores ainda estiverem conectados.
        if (playerId == 1 && !gameActive && player1 != null && player2 != null) {
            System.out.println("Jogador 1 solicitou reinicio. Reiniciando o jogo...");
            iniciarJogo();
        }
    }

    private void broadcastScore() {
        try {
            if (player1 != null) player1.atualizarPlacar(p1Moves, p2Moves);
            if (player2 != null) player2.atualizarPlacar(p1Moves, p2Moves);
        } catch (RemoteException e) {
            System.err.println("Erro ao enviar placar: " + e.getMessage());
        }
    }

    @Override
    public synchronized void desconectar(int playerId) throws RemoteException {
        String nome = (playerId == 1) ? p1Name : p2Name;
        broadcastMsg("O jogador " + nome + " saiu do jogo.");

        if (playerId == 1) player1 = null;
        else player2 = null;

        gameActive = false;
        resetGame();
    }

    // --- Métodos Auxiliares ---

    private void iniciarJogo() {
        gameActive = true;
        currentTurn = 1;
        p1Moves = 0;
        p2Moves = 0;
        board = new HalmaBoard();

        // Notifica os clientes sobre o início e quem são os jogadores
        try {
            if (player1 != null) player1.notificarInicioJogo(p1Name, p2Name);
            if (player2 != null) player2.notificarInicioJogo(p1Name, p2Name);
        } catch (RemoteException e) {
            System.err.println("Erro ao notificar inicio de jogo: " + e.getMessage());
        }

        broadcastMsg("JOGO INICIADO!");
        broadcastUpdate(null);
        notificarTurno();
        broadcastScore(); // Garante que o placar seja zerado para os clientes
    }

    private void resetGame() {
        board = new HalmaBoard();
        currentTurn = 1;
        p1Moves = 0;
        p2Moves = 0;
        broadcastScore();
        broadcastUpdate(null);
        // Se ainda houver alguém conectado, avisa para esperar
        try {
            if (player1 != null) player1.receberMensagem("Esperando oponente...");
            if (player2 != null) player2.receberMensagem("Esperando oponente...");
        } catch (RemoteException e) {
        }
    }

    private void notificarTurno() {
        try {
            if (player1 != null) player1.definirTurno(currentTurn == 1);
            if (player2 != null) player2.definirTurno(currentTurn == 2);
        } catch (RemoteException e) {
            System.err.println("Erro ao notificar turno (cliente caiu?)");
        }
    }

    private void broadcastUpdate(Point lastMove) {
        try {
            // Envia o objeto board serializado
            if (player1 != null) player1.atualizarTabuleiro(board, lastMove);
            if (player2 != null) player2.atualizarTabuleiro(board, lastMove);
        } catch (RemoteException e) {
            // Lidar com desconexão silenciosa
        }
    }

    private void notificarVitoria(int winnerId, boolean reiniciar) {
        System.out.println("Jogador " + winnerId + " venceu o jogo!");
        broadcastMsg("O Jogador " + winnerId + " venceu!");

        try {
            // Notifica o Jogador 1
            if (player1 != null) {
                player1.notificarFimDeJogo(winnerId);
            }
            // Notifica o Jogador 2
            if (player2 != null) {
                player2.notificarFimDeJogo(winnerId);
            }
        } catch (RemoteException e) {
            System.err.println("Erro ao notificar fim de jogo: " + e.getMessage());
        }

        // Opcional: Resetar o jogo automaticamente ou encerrar
        gameActive = false;

    }

    private void broadcastMsg(String msg) {
        try {
            if (player1 != null) player1.receberMensagem(msg);
            if (player2 != null) player2.receberMensagem(msg);
        } catch (RemoteException e) {
        }
    }
}