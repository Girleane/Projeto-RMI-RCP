package halma;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.awt.Point;
import java.util.List;

// Interface que o Servidor expõe para o Cliente
interface IHalmaServer extends Remote {
    int registrarCliente(IHalmaClient cliente, String nome) throws RemoteException;
    void enviarMovimento(int playerId, List<Point> sequencia) throws RemoteException;
    void enviarChat(int playerId, String mensagem) throws RemoteException;
    void desconectar(int playerId) throws RemoteException;
    void solicitarReinicio(int playerId) throws RemoteException; // NOVO MÉTODO
}

// Interface que o Cliente expõe para o Servidor (Callbacks)
interface IHalmaClient extends Remote {
    void receberMensagem(String msg) throws RemoteException;

    void atualizarTabuleiro(HalmaBoard board, Point lastMove) throws RemoteException;

    void definirTurno(boolean meuTurno) throws RemoteException;

    void notificarFimDeJogo(int idVencedor) throws RemoteException;

    void notificarInicioJogo(String p1Name, String p2Name) throws RemoteException;

    void ping() throws RemoteException; // Para verificar se o cliente ainda está vivo

    void atualizarPlacar(int p1Moves, int p2Moves) throws RemoteException;
}