// package coordinator;
package coordinator;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class CoordinatorServer {
    public static void main(String[] args) {
        try {
            CoordinatorImpl coordinator = new CoordinatorImpl();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("CoordinatorService", coordinator);
            System.out.println("Coordinator RMI server is running...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
