// CalculatorClient.java
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.Naming;

public class  CalculatorClient {
    public static void main(String[] args) {
        try {
            // 获取远程注册表（连接到服务端 IP 和端口）
            // 如果服务端在本地：localhost:1099
            // 如果服务端在远程：替换为实际 IP
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            
            // 从注册表查找远程对象
            Calculator calculator = (Calculator) registry.lookup("CalculatorService");
            
            // 方式2：使用 Naming 查找
            // Calculator calculator = (Calculator) Naming.lookup("rmi://localhost:1099/CalculatorService");
            
            System.out.println(">>> 成功连接到远程计算器服务");
            System.out.println("----------------------------------------");
            
            // 像调用本地方法一样调用远程方法！
            int a = 20, b = 10;
            
            System.out.println("调用远程方法 add(" + a + ", " + b + ") = " + calculator.add(a, b));
            System.out.println("调用远程方法 subtract(" + a + ", " + b + ") = " + calculator.subtract(a, b));
            System.out.println("调用远程方法 multiply(" + a + ", " + b + ") = " + calculator.multiply(a, b));
            System.out.println("调用远程方法 divide(" + a + ", " + b + ") = " + calculator.divide(a, b));
            
            System.out.println("----------------------------------------");
            System.out.println(">>> 所有远程调用完成！");
            
        } catch (Exception e) {
            System.err.println("客户端异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
