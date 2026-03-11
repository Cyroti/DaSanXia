// CalculatorImpl.java
import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;

// 继承 UnicastRemoteObject 使其具备远程服务能力
public class CalculatorImpl extends UnicastRemoteObject implements Calculator {
    
    // 必须提供显式构造器（因为父类构造器抛出 RemoteException）
    public CalculatorImpl() throws RemoteException {
        super(); // 导出远程对象，默认端口 1099
    }

    @Override
    public int add(int a, int b) throws RemoteException {
        System.out.println("[服务端] 收到加法请求: " + a + " + " + b);
        return a + b;
    }

    @Override
    public int subtract(int a, int b) throws RemoteException {
        System.out.println("[服务端] 收到减法请求: " + a + " - " + b);
        return a - b;
    }

    @Override
    public int multiply(int a, int b) throws RemoteException {
        System.out.println("[服务端] 收到乘法请求: " + a + " * " + b);
        return a * b;
    }

    @Override
    public double divide(int a, int b) throws RemoteException {
        System.out.println("[服务端] 收到除法请求: " + a + " / " + b);
        if (b == 0) throw new RemoteException("除数不能为零");
        return (double) a / b;
    }
}
