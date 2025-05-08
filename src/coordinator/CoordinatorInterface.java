package coordinator;
import java.rmi.Remote;
import java.rmi.RemoteException;
import common.User;

public interface CoordinatorInterface extends Remote {
    String login(String username, String password) throws RemoteException;
    boolean registerUser(User user, String adminToken) throws RemoteException;
    boolean uploadFile(String token, String filename, byte[] data) throws RemoteException;
    byte[] requestFile(String token, String filename) throws RemoteException;
    boolean deleteFile(String token, String filename) throws RemoteException;
    boolean editFile(String token, String filename, byte[] newData) throws RemoteException;

}
