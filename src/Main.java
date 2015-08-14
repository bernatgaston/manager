import java.lang.String;

public class Main {
	static {
		System.loadLibrary("rina_java");
	}
	public static void main(String[ ] args)
	{
		Manager manager = new Manager("rina.apps.manager", "1", "NMS.DIF");
		manager.applicationRegister(false);
		
	}
}
