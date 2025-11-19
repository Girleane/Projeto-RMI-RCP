package halma;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.io.Serializable; // Import necessário para RMI

// Adicionado 'implements Serializable' para permitir envio pela rede
public class HalmaBoard implements Serializable {
    // Adicionado serialVersionUID para evitar problemas de versão entre cliente/servidor
    private static final long serialVersionUID = 1L;

    private final int size = 8;
    private int[][] board;

    // Construtor de Cópia
    public HalmaBoard(HalmaBoard original) {
        int s = original.board.length;
        this.board = new int[s][s];
        for (int i = 0; i < s; i++)
            System.arraycopy(original.board[i], 0, this.board[i], 0, s);
    }

    // Construtor Padrão
    public HalmaBoard() {
        board = new int[size][size];
        inicializarPecas();
    }

    private void inicializarPecas() {
        // Jogador 1 (Canto superior esquerdo)
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4 - y; x++) {
                board[y][x] = 1;
            }
        }
        // Jogador 2 (Canto inferior direito)
        int startRow = size - 4;
        for (int y = startRow; y < size; y++) {
            int count = y - startRow + 1;
            int startX = size - count;
            for (int x = startX; x < size; x++) {
                board[y][x] = 2;
            }
        }
    }

    public int getSize() {
        return size;
    }

    public int getPieceAt(int x, int y) {
        if (!inBounds(x, y)) return 0;
        return board[y][x];
    }

    public synchronized List<Point> getValidMoves(int jogador, int x, int y) {
        List<Point> moves = new ArrayList<>();
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (!inBounds(nx, ny)) continue;
                if (board[ny][nx] != 0) continue;
                int max = Math.max(Math.abs(dx), Math.abs(dy));
                if (max == 1) moves.add(new Point(nx, ny));
                else if (max == 2) {
                    boolean isJump = (Math.abs(dx) == 2 && Math.abs(dy) == 0) ||
                            (Math.abs(dx) == 0 && Math.abs(dy) == 2) ||
                            (Math.abs(dx) == 2 && Math.abs(dy) == 2);
                    if (!isJump) continue;
                    int mx = x + dx / 2, my = y + dy / 2;
                    if (inBounds(mx, my) && board[my][mx] != 0) moves.add(new Point(nx, ny));
                }
            }
        return moves;
    }

    // Verifica se coordenadas estão dentro do tabuleiro
    private boolean inBounds(int x, int y) {
        return x >= 0 && x < size && y >= 0 && y < size;
    }

    public synchronized void setCell(int x, int y, int value) {
        if (inBounds(x, y)) board[y][x] = value;
    }

    public synchronized int getCell(int x, int y) {
        if (inBounds(x, y)) return board[y][x];
        return -1;
    }


    // Executa uma sequência de movimentos (verificação básica de validade)
    // coords = [x1, y1, x2, y2, x3, y3...]
    public synchronized boolean moverPecaSequence(int[] coords, int player) {
        if (coords.length < 4 || coords.length % 2 != 0) return false;

        int xStart = coords[0];
        int yStart = coords[1];

        // Verifica se a peça inicial pertence ao jogador
        if (!inBounds(xStart, yStart) || board[yStart][xStart] != player) return false;

        // Simula os movimentos passo a passo
        int currentX = xStart;
        int currentY = yStart;

        // Clonamos o tabuleiro temporariamente para validar sem corromper o estado atual se falhar no meio
        int[][] tempBoard = new int[size][size];
        for (int i = 0; i < size; i++) System.arraycopy(board[i], 0, tempBoard[i], 0, size);

        for (int i = 2; i < coords.length; i += 2) {
            int nextX = coords[i];
            int nextY = coords[i + 1];

            if (!validarPasso(tempBoard, currentX, currentY, nextX, nextY)) {
                return false; // Passo inválido
            }

            // Move a peça temporariamente
            tempBoard[nextY][nextX] = tempBoard[currentY][currentX];
            tempBoard[currentY][currentX] = 0;

            currentX = nextX;
            currentY = nextY;
        }

        // Se chegou aqui, a sequência é válida. Aplica no tabuleiro real.
        int xFinal = coords[coords.length - 2];
        int yFinal = coords[coords.length - 1];

        board[yFinal][xFinal] = board[yStart][xStart];
        board[yStart][xStart] = 0;

        return true;
    }

    // Valida um único passo (adjacente ou pulo)
    private boolean validarPasso(int[][] b, int x1, int y1, int x2, int y2) {
        if (!inBounds(x2, y2)) return false;
        if (b[y2][x2] != 0) return false; // Destino deve estar vazio

        int dx = x2 - x1;
        int dy = y2 - y1;
        int adx = Math.abs(dx);
        int ady = Math.abs(dy);

        // Movimento simples (adjacente)
        if (adx <= 1 && ady <= 1 && (adx + ady > 0)) {
            // Só permite movimento simples se for o primeiro e único passo da jogada?
            // Na regra simplificada, geralmente sim, mas aqui vamos permitir como parte da lógica básica.
            // Para ser estrito: movimentos simples não podem ser encadeados.
            // Mas este método é genérico. A UI controla a montagem da sequência.
            return true;
        }

        // Pulo (Jump)
        if ((adx == 0 && ady == 2) || (adx == 2 && ady == 0) || (adx == 2 && ady == 2)) {
            int midX = x1 + dx / 2;
            int midY = y1 + dy / 2;
            // Precisa ter uma peça no meio para pular
            if (b[midY][midX] != 0) return true;
        }

        return false;
    }

    public boolean verificarVencedor(int player) {
        // Jogador 1 vence se encher a base do Jogador 2 (inferior direita)
        // Jogador 2 vence se encher a base do Jogador 1 (superior esquerda)
        // Simplificação: verifica se todas as posições da base oposta estão ocupadas pelo jogador

        if (player == 1) {
            return isBaseFull(player, true); // Checa base alvo do P1 (que é a original do P2)
        } else {
            return isBaseFull(player, false); // Checa base alvo do P2 (que é a original do P1)
        }
    }

    private boolean isBaseFull(int player, boolean checkBottomRight) {
        int countTarget = 10; // 4+3+2+1 peças
        int count = 0;

        if (checkBottomRight) {
            // Base inferior direita
            int startRow = size - 4;
            for (int y = startRow; y < size; y++) {
                int c = y - startRow + 1;
                int startX = size - c;
                for (int x = startX; x < size; x++) {
                    if (board[y][x] == player) count++;
                }
            }
        } else {
            // Base superior esquerda
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4 - y; x++) {
                    if (board[y][x] == player) count++;
                }
            }
        }
        return count == countTarget;
    }
}