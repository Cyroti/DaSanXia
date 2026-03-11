// CalculatorServer.java
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.Naming;

public class CalculatorServer {
    public static void main(String[] args) {
        try {
            // 创建远程对象实例
            CalculatorImpl calculator = new CalculatorImpl();
            
            // 方式1：使用 Registry（推荐）
            // 创建本地注册表，端口 1099
            Registry registry = LocateRegistry.createRegistry(1099);
            
            // 将远程对象绑定到注册表，命名为 "CalculatorService"
            registry.rebind("CalculatorService", calculator);
            
            // 方式2：使用 Naming 类（底层也是 Registry）
            // Naming.rebind("rmi://localhost:1099/CalculatorService", calculator);
            
            System.out.println(">>> 计算器服务已启动，等待客户端连接...");
            System.out.println(">>> 服务地址: rmi://localhost:1099/CalculatorService");
            
        } catch (Exception e) {
            System.err.println("服务端异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
