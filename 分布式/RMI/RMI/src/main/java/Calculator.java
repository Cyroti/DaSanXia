// CalculatorImpl.java
import java.rmi.Remote;
import java.rmi.RemoteException;

// Calculator.java
import java.rmi.Remote;
import java.rmi.RemoteException;

// 远程接口必须继承 Remote，所有方法必须抛出 RemoteException
public interface Calculator extends Remote {
    int add(int a, int b) throws RemoteException;
    int subtract(int a, int b) throws RemoteException;
    int multiply(int a, int b) throws RemoteException;
    double divide(int a, int b) throws RemoteException;
}
