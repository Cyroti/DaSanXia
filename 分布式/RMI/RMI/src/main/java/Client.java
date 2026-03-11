import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public class Client {
    public static void main(String[] args) {
        try {
            HelloService helloService = (HelloService) Naming.lookup("rmi://172.20.10.3:1099/HelloService");
            String Response = helloService.sayHello("World");
            System.out.println(Response);
        } catch (MalformedURLException | NotBoundException | RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}
