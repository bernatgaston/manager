package pristine.manager;

import eu.irati.librina.FlowInformation;
import eu.irati.librina.con_handle_t;
import eu.irati.librina.flags_t;
import eu.irati.librina.res_info_t;
import eu.irati.librina.CDAPCallbackInterface;
import eu.irati.librina.CDAPProviderInterface;
import eu.irati.librina.rina;
import eu.irati.librina.ser_obj_t;
import eu.irati.librina.res_code_t;
import eu.irati.librina.obj_info_t;
import eu.irati.librina.filt_info_t;

import eu.irati.librinad.ipcp_config_t;
import eu.irati.librinad.StringList;
import eu.irati.librinad.IPCPConfigEncoder;
import eu.irati.librinad.StringEncoder;

import java.lang.Runnable;

public class ManagerWorker extends CDAPCallbackInterface implements Runnable{

	static int max_sdu_size_in_bytes = 10000;
	static String IPCP_1 = "/computingSystemID=1/processingSystemID=1/kernelApplicationProcess/osApplicationProcess/ipcProcesses/ipcProcessID=4";
	
	private FlowInformation flow_;
	private CDAPProviderInterface cdap_prov;

	public ManagerWorker(FlowInformation flow)
	{
		flow_ = flow;
	}

	public void open_connection(con_handle_t con, flags_t flags, int message_id)
	{
		res_info_t res = new res_info_t();
		res.setCode_(res_code_t.CDAP_SUCCESS);
		System.out.println("open conection request CDAP message received");
		cdap_prov.send_open_connection_result(con, res, message_id);
		System.out.println("open conection response CDAP message sent");
	}
	
	public void remote_create_result(con_handle_t con, obj_info_t obj, res_info_t res) {
		System.out.println("Result code is: " + res.getCode_());
	}

	public void remote_read_result(con_handle_t con, obj_info_t obj, res_info_t res) {
		// decode object value
		// print object value
		System.out.println("Query Rib operation returned result " + res.getCode_());
		String[] query_rib = new String[1];
		StringEncoder stringEncoder = new StringEncoder();
		stringEncoder.decode(obj.getValue_(), query_rib);
		System.out.println("QueryRIB:");
		System.out.println(query_rib);
	}
	
	public void run(){
        System.out.println("Manager worker started");
        rina.init(this, false);
        cdap_prov = rina.getProvider();
        // CACEP
        cacep(flow_.getPortId());
        createIPCP_1(flow_.getPortId());
	}
	
	private void cacep(int port_id)
	{
		byte[] buffer = new byte[Manager.max_sdu_size_in_bytes];
		int bytes_read = rina.getIpcManager().readSDU(port_id, buffer, Manager.max_sdu_size_in_bytes);
		ser_obj_t message = new ser_obj_t();
		message.setMessage_(buffer);
		message.setSize_(bytes_read);
		cdap_prov.process_message(message, port_id);
	}
	
	private boolean createIPCP_1(int port_id)
	{
		byte[] buffer = new byte[max_sdu_size_in_bytes];

		ipcp_config_t ipc_config = new ipcp_config_t();
		ipc_config.setProcess_instance("1");
		ipc_config.setProcess_name("test1.IRATI");
		ipc_config.setProcess_type("normal-ipc");
		ipc_config.setDif_to_assign("normal.DIF");
		StringList difList = new StringList();
		difList.addFirst("300");
		ipc_config.setDifs_to_register(difList);

		obj_info_t obj = new obj_info_t();
		obj.setName_(IPCP_1);
		obj.setClass_("IPCProcess");
		obj.setInst_(0);
		IPCPConfigEncoder ipcpConfigE = new IPCPConfigEncoder();
		ipcpConfigE.encode(ipc_config,obj.getValue_());

		flags_t flags = new flags_t();
		flags.setFlags_(flags_t.Flags.NONE_FLAGS);

		filt_info_t filt =  new filt_info_t();
		filt.setFilter_("");
		filt.setScope_(0);

		cdap_prov.remote_create(port_id, obj, flags, filt);
		System.out.println("create IPC request CDAP message sent to port " + port_id);

		try {
			int bytes_read = rina.getIpcManager().readSDU(port_id, buffer,
								max_sdu_size_in_bytes);
			ser_obj_t message = new ser_obj_t();
			message.setMessage_(buffer);
			message.setSize_(bytes_read);
			cdap_prov.process_message(message, port_id);
		} catch (Exception e) {
			System.out.println("ReadSDUException in createIPCP_1: " + e.getMessage());
			return false;
		}
		return true;
	}
}
