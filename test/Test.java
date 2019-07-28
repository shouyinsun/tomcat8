/**
 * author cash
 * create 2019-07-28-15:44
 **/
public class Test {
    public static void main(String[] args) {
        System.out.println(ClassLoader.getSystemClassLoader().getParent());
        System.out.println(System.getProperty("java.class.path"));
        System.out.println(System.getProperty("java.ext.dirs"));
        System.out.println(System.getProperty("sun.boot.class.path"));
    }
}
