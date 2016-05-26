import java.lang.String;

import pristine.manager.*;

public class Main {
	static {
		System.loadLibrary("rina_java");
		System.loadLibrary("rinad_java");
	}
	public static void main(String[ ] args)
	{	
		Manager manager = new Manager("rina.apps.manager", "1", "NMS.DIF");
		manager.run(false);
	}
}
