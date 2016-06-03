package pristine.manager;

import eu.irati.librina.FlowInformation;
import eu.irati.librina.NamespaceManagerConfiguration;
import eu.irati.librina.PDUFTGConfiguration;
import eu.irati.librina.PFTConfiguration;
import eu.irati.librina.PolicyConfig;
import eu.irati.librina.PolicyParameter;
import eu.irati.librina.QoSCube;
import eu.irati.librina.RIBObjectData;
import eu.irati.librina.RMTConfiguration;
import eu.irati.librina.con_handle_t;
import eu.irati.librina.flags_t;
import eu.irati.librina.flags_t.Flags;
import eu.irati.librina.res_info_t;
import eu.irati.librina.AddressPrefixConfiguration;
import eu.irati.librina.AddressingConfiguration;
import eu.irati.librina.ApplicationProcessNamingInformation;
import eu.irati.librina.CDAPCallbackInterface;
import eu.irati.librina.CDAPProviderInterface;
import eu.irati.librina.DIFConfiguration;
import eu.irati.librina.rina;
import eu.irati.librina.ser_obj_t;
import eu.irati.librina.res_code_t;
import eu.irati.librina.obj_info_t;
import eu.irati.librina.filt_info_t;
import eu.irati.librina.Exception;
import eu.irati.librina.FlowAllocatorConfiguration;
import eu.irati.librina.ReadSDUException;
import eu.irati.librina.ResourceAllocatorConfiguration;
import eu.irati.librina.RoutingConfiguration;
import eu.irati.librina.SecurityManagerConfiguration;
import eu.irati.librina.StaticIPCProcessAddress;
import eu.irati.librina.CDAPException;
import eu.irati.librina.CDAPMessage;
import eu.irati.librinad.RIBObjectDataList;
import eu.irati.librinad.ipcp_config_t;
import eu.irati.librinad.StringList;
import eu.irati.librinad.IPCPConfigEncoder;
import eu.irati.librinad.RIBObjectDataListEncoder;
import eu.irati.librina.StringEncoder;
import eu.irati.librina.concrete_syntax_t;
import eu.irati.librina.DIFInformation;
import eu.irati.librina.DTCPConfig;
import eu.irati.librina.DTCPFlowControlConfig;
import eu.irati.librina.DTCPRateBasedFlowControlConfig;
import eu.irati.librina.DTCPRtxControlConfig;
import eu.irati.librina.DTCPWindowBasedFlowControlConfig;
import eu.irati.librina.DTPConfig;
import eu.irati.librina.DataTransferConstants;
import eu.irati.librina.EFCPConfiguration;
import eu.irati.librina.EnrollmentTaskConfiguration;

import java.lang.Runnable;

public class ManagerWorker extends CDAPCallbackInterface implements Runnable{

	static int max_sdu_size_in_bytes = 10000;
	static String IPCP_1 = "/computingSystemID=1/processingSystemID=1/kernelApplicationProcess/osApplicationProcess/ipcProcesses/ipcProcessID=4";
	
	private FlowInformation flow_;
	private CDAPProviderInterface cdap_prov;
	private boolean cacep_finished = false;

	public ManagerWorker(FlowInformation flow)
	{
		flow_ = flow;
	}

	public void open_connection(con_handle_t con, CDAPMessage message)
	{
		res_info_t res = new res_info_t();
		res.setCode_(res_code_t.CDAP_SUCCESS);
		System.out.println("open conection request CDAP message received");
		cdap_prov.send_open_connection_result(con, res, message.getInvoke_id_());
		System.out.println("open conection response CDAP message sent");
	}
	
	public void remote_create_result(con_handle_t con, obj_info_t obj, res_info_t res, int invoke_id) {
		System.out.println("Create IPCP result code is: " + res.getCode_());
	}

	public void remote_read_result(con_handle_t con, obj_info_t obj, res_info_t res, flags_t flags, int invoke_id) {
		System.out.println("Remote read result: " + res.getCode_());
		System.out.println("Object: " + obj.getName_());
		System.out.println("Flags: " + flags.getFlags_());
		
		if (flags.getFlags_() == flags_t.Flags.NONE_FLAGS)
		{
			cacep_finished = true;
		}
		/*
		RIBObjectDataList query_rib = new RIBObjectDataList();
		RIBObjectDataListEncoder encoder = new RIBObjectDataListEncoder();
		
		encoder.decode(obj.getValue_(), query_rib);
		int i = 1;
		System.out.println("RIBDataObject List size "+ query_rib.size());
		while (query_rib.size() > 0)
		{
			RIBObjectData data_obj = query_rib.getFirst();
			System.out.println("RIBDataObject " + i + ": " + data_obj.getDisplayable_value_());
			i++;
			query_rib.clearFirst();
		}

		//System.out.println(query_rib[0]);
		 */
	}
	
	public void run(){
        System.out.println("Manager worker started");
        concrete_syntax_t syntax = new concrete_syntax_t();
        rina.cdap_init(this, syntax, false);
        cdap_prov = rina.getProvider();
        // CACEP
        cacep(flow_.getPortId());
       /* if(createIPCP_1(flow_.getPortId()))
        {
        	//queryRIB(flow_.getPortId(), IPCP_1 + "/ribDaemon");
        }*/
        
	}
	
	private void cacep(int port_id)
	{
		byte[] buffer = new byte[Manager.max_sdu_size_in_bytes];
		ser_obj_t message = new ser_obj_t();
		try
		{
			int bytes_read = rina.getIpcManager().readSDU(port_id, buffer, Manager.max_sdu_size_in_bytes);
			message.setMessage_(buffer);
			message.setSize_(bytes_read);
		}catch(ReadSDUException e)
		{
			System.out.println("ReadSDUException in cacep: " + e.getMessage());
		}
		try 
		{
			cdap_prov.process_message(message, port_id);
			
			// Send a Readover all the objects of the RIB
			obj_info_t obj = new obj_info_t();
			obj.setName_("/computingSystemID=1");
			obj.setClass_("ComputingSystem");
			obj.setInst_(0);

			flags_t flags = new flags_t();
			flags.setFlags_(flags_t.Flags.NONE_FLAGS);

			filt_info_t filt =  new filt_info_t();;
			filt.setFilter_("");
			filt.setScope_(10);
			
			con_handle_t con = new con_handle_t();
			con.setPort_id(port_id);
			
	        cdap_prov.remote_read(con, obj, flags, filt, 28);
	        System.out.println("Read all the managed objects");
	        
	        while(!cacep_finished)
	        {
		        int bytes_read = rina.getIpcManager().readSDU(port_id,
	        				     buffer,
	        				     max_sdu_size_in_bytes);
		        message.setMessage_(buffer);
		        message.setSize_(bytes_read);
		        cdap_prov.process_message(message, port_id);
	        }
		} catch (CDAPException e) {
			System.out.println("CDAPException in cacep: " + e.getMessage());
		}
	}
	
	private boolean createIPCP_1(int port_id)
	{
		byte[] buffer = new byte[max_sdu_size_in_bytes];

		ipcp_config_t ipc_config = new ipcp_config_t();
		ApplicationProcessNamingInformation proc_name = new ApplicationProcessNamingInformation();
		proc_name.setProcessInstance("1");;
		proc_name.setProcessName("test1.IRATI");

		DIFInformation dif_info = new DIFInformation();
		ApplicationProcessNamingInformation dif_name = new ApplicationProcessNamingInformation();
		dif_info.setDif_type_("normal-ipc");
		dif_name.setProcessName("normal.DIF");

		DIFConfiguration dif_conf = new DIFConfiguration();
		dif_conf.setAddress_(16);
		
		// Data Transfer Constants
		EFCPConfiguration efcp_conf = new EFCPConfiguration();
		DataTransferConstants dt_const = new DataTransferConstants();
		dt_const.setAddress_length_(2);
		dt_const.setCep_id_length_(2);
		dt_const.setLength_length_(2);
		dt_const.setPort_id_length_(2);
		dt_const.setQos_id_length_(2);
		dt_const.setSequence_number_length_(4);
		dt_const.setMax_pdu_size_(10000);
		dt_const.setMax_pdu_lifetime_(60000);
		dt_const.setCtrl_sequence_number_length_(4);
		dt_const.setRate_length_(4);
		dt_const.setFrame_length_(4);

		// Qos Cube unreliable with flow control
		QoSCube unreliable = new QoSCube();
		unreliable.setName_("unreliablewithflowcontrol");
		unreliable.setId_(1);
		unreliable.setPartial_delivery_(false);
		unreliable.setOrdered_delivery_(true);
		DTPConfig dtp_config = new DTPConfig();
		PolicyConfig dtp_policy = new PolicyConfig();
		dtp_policy.setName_("default");
		dtp_policy.setVersion_("0");
		dtp_config.setInitial_a_timer_(300);
		dtp_config.setDtcp_present_(true);
		DTCPConfig dtcp_config = new DTCPConfig();
		PolicyConfig dtcp_policy = new PolicyConfig();
		dtcp_policy.setName_("default");
		dtcp_policy.setVersion_("0");
		dtcp_config.setRtx_control_(false);
		dtcp_config.setFlow_control_(true);
		DTCPFlowControlConfig flow_control_config = new DTCPFlowControlConfig();
		flow_control_config.setRate_based_(false);
		flow_control_config.setWindow_based_(true);
		DTCPWindowBasedFlowControlConfig window_based_config = new DTCPWindowBasedFlowControlConfig();
		window_based_config.setMax_closed_window_queue_length_(50);
		window_based_config.setInitial_credit_(50);

		// Qos Cube reliable with flow control
		QoSCube reliable = new QoSCube();
		reliable.setName_("reliablewithflowcontrol");
		reliable.setId_(2);
		reliable.setPartial_delivery_(false);
		reliable.setOrdered_delivery_(true);
		reliable.setMax_allowable_gap_(0);
		DTPConfig dtp_config2 = new DTPConfig();
		PolicyConfig dtp_policy2 = new PolicyConfig();
		dtp_policy2.setName_("default");
		dtp_policy2.setVersion_("0");
		dtp_config2.setInitial_a_timer_(300);
		dtp_config2.setDtcp_present_(true);
		DTCPConfig dtcp_config2 = new DTCPConfig();
		PolicyConfig dtcp_policy2 = new PolicyConfig();
		dtcp_policy2.setName_("default");
		dtcp_policy2.setVersion_("0");
		dtcp_config2.setRtx_control_(true);
		DTCPRtxControlConfig rtx_control_config2 = new DTCPRtxControlConfig();
		rtx_control_config2.setData_rxms_nmax_(5);
		rtx_control_config2.setInitial_rtx_time_(1000);
		dtcp_config2.setFlow_control_(true);
		DTCPFlowControlConfig flow_control_config2 = new DTCPFlowControlConfig();
		flow_control_config2.setRate_based_(false);
		flow_control_config2.setWindow_based_(true);
		DTCPWindowBasedFlowControlConfig window_based_config2 = new DTCPWindowBasedFlowControlConfig();
		window_based_config2.setMax_closed_window_queue_length_(50);
		window_based_config2.setInitial_credit_(50);

		// Namespace configuration
		NamespaceManagerConfiguration nsm_config = new NamespaceManagerConfiguration();
		AddressingConfiguration addressing_config = new AddressingConfiguration();
		StaticIPCProcessAddress addr1 = new StaticIPCProcessAddress();
		addr1.setAp_name_("test1.IRATI");
		addr1.setAp_instance_("1");
		addr1.setAddress_(16);
		
		StaticIPCProcessAddress addr2 = new StaticIPCProcessAddress();
		addr2.setAp_name_("test2.IRATI");
		addr2.setAp_instance_("1");
		addr2.setAddress_(17);
		
		AddressPrefixConfiguration pref1 = new AddressPrefixConfiguration();
		pref1.setAddress_prefix_(0);
		pref1.setOrganization_("N.Bourbaki");
		AddressPrefixConfiguration pref2 = new AddressPrefixConfiguration();
		pref2.setAddress_prefix_(16);
		pref2.setOrganization_("IRATI");

		PolicyConfig nsm_policy = new PolicyConfig();
		nsm_policy.setName_("default");
		nsm_policy.setVersion_("1");
		
		// RMT configuration
		RMTConfiguration rmt_conf = new RMTConfiguration();
		PFTConfiguration pft_conf = new PFTConfiguration();
		PolicyConfig pft_policy = new PolicyConfig();
		pft_policy.setName_("default");
		pft_policy.setVersion_("0");
		PolicyConfig rmt_policy = new PolicyConfig();
		rmt_policy.setName_("default");
		rmt_policy.setVersion_("1");

		// Enrollment Task configuration
		EnrollmentTaskConfiguration et_config = new EnrollmentTaskConfiguration();
		PolicyConfig et_policy = new PolicyConfig();
		et_policy.setName_("default");
		et_policy.setVersion_("1");
		
		PolicyParameter par1 = new PolicyParameter();
		par1.setName_("enrollTimeoutInMs");
		par1.setValue_("10000");
		PolicyParameter par2 = new PolicyParameter();
		par2.setName_("watchdogPeriodInMs");
		par2.setValue_("30000");
		PolicyParameter par3 = new PolicyParameter();
		par3.setName_("declaredDeadIntervalInMs");
		par3.setValue_("120000");
		PolicyParameter par4 = new PolicyParameter();
		par4.setName_("neighborsEnrollerPeriodInMs");
		par4.setValue_("30000");
		PolicyParameter par5 = new PolicyParameter();
		par5.setName_("maxEnrollmentRetries");
		par5.setValue_("3");
		
		// Flow allocation configuration
		FlowAllocatorConfiguration fa_config = new FlowAllocatorConfiguration();
		PolicyConfig fa_policy = new PolicyConfig();
		fa_policy.setName_("default");
		fa_policy.setVersion_("1");
		
		// Security manager configuration
		SecurityManagerConfiguration sm_config = new SecurityManagerConfiguration();
		PolicyConfig sm_policy = new PolicyConfig();
		sm_policy.setName_("default");
		sm_policy.setVersion_("1");
		
		// Resource allocation configuration
		ResourceAllocatorConfiguration ra_config = new ResourceAllocatorConfiguration();
		PDUFTGConfiguration pdu_config = new PDUFTGConfiguration();
		PolicyConfig ra_policy = new PolicyConfig();
		ra_policy.setName_("default");
		ra_policy.setVersion_("0");
		
		// Routing configuration
		RoutingConfiguration routing_conf = new RoutingConfiguration();
		PolicyConfig ro_policy = new PolicyConfig();
		ro_policy.setName_("link-state");
		ro_policy.setVersion_("1");
		PolicyParameter par6 = new PolicyParameter();
		par6.setName_("objectMaximumAge");
		par6.setValue_("10000");
		PolicyParameter par7 = new PolicyParameter();
		par7.setName_("waitUntilReadCDAP");
		par7.setValue_("5001");
		PolicyParameter par8 = new PolicyParameter();
		par8.setName_("waitUntilError");
		par8.setValue_("5001");
		PolicyParameter par9 = new PolicyParameter();
		par9.setName_("waitUntilPDUFTComputation");
		par9.setName_("103");
		PolicyParameter par10 = new PolicyParameter();
		par10.setName_("waitUntilFSODBPropagation");
		par10.setValue_("101");
		PolicyParameter par11 = new PolicyParameter();
		par11.setName_("waitUntilAgeIncrement");
		par11.setValue_("997");
		PolicyParameter par12 = new PolicyParameter();
		par12.setName_("routingAlgorithm");
		par12.setValue_("Dijkstra");
		
		// Assignments after initializations
		ipc_config.setName(proc_name);
		dif_info.setDif_name_(dif_name);
		efcp_conf.setData_transfer_constants_(dt_const);
		
		dtp_config.setDtp_policy_set_(dtp_policy);
		flow_control_config.setWindow_based_config_(window_based_config);
		dtcp_config.setFlow_control_config_(flow_control_config);
		dtcp_config.setDtcp_policy_set_(dtcp_policy);
		unreliable.setDtcp_config_(dtcp_config);
		unreliable.setDtp_config_(dtp_config);		
		efcp_conf.add_qos_cube(unreliable);
		
		dtp_config2.setDtp_policy_set_(dtp_policy2);
		flow_control_config2.setWindow_based_config_(window_based_config2);
		dtcp_config2.setFlow_control_config_(flow_control_config2);
		dtcp_config2.setDtcp_policy_set_(dtcp_policy2);
		dtcp_config2.setRtx_control_config_(rtx_control_config2);
		reliable.setDtcp_config_(dtcp_config2);
		reliable.setDtp_config_(dtp_config2);	
		efcp_conf.add_qos_cube(reliable);
		
		addressing_config.addAddress(addr1);
		addressing_config.addAddress(addr2);
		addressing_config.addPrefix(pref1);
		addressing_config.addPrefix(pref2);
		nsm_config.setPolicy_set_(nsm_policy);
		nsm_config.setAddressing_configuration_(addressing_config);
		dif_conf.setNsm_configuration_(nsm_config);
		
		rmt_conf.setPolicy_set_(rmt_policy);
		pft_conf.setPolicy_set_(pft_policy);
		rmt_conf.setPft_conf_(pft_conf);
		dif_conf.setRmt_configuration_(rmt_conf);
		
		et_policy.add_parameter(par1);
		et_policy.add_parameter(par2);
		et_policy.add_parameter(par3);
		et_policy.add_parameter(par4);
		et_policy.add_parameter(par5);
		et_config.setPolicy_set_(et_policy);
		dif_conf.setEt_configuration_(et_config);
		
		fa_config.setPolicy_set_(fa_policy);
		dif_conf.setFa_configuration_(fa_config);
		
		sm_config.setPolicy_set_(sm_policy);
		dif_conf.setSm_configuration_(sm_config);
		
		pdu_config.setPolicy_set_(ra_policy);
		ra_config.setPduftg_conf_(pdu_config);
		dif_conf.setRa_configuration_(ra_config);
		
		ro_policy.add_parameter(par6);
		ro_policy.add_parameter(par7);
		ro_policy.add_parameter(par8);
		ro_policy.add_parameter(par9);
		ro_policy.add_parameter(par10);
		ro_policy.add_parameter(par11);
		ro_policy.add_parameter(par12);
		routing_conf.setPolicy_set_(ro_policy);
		dif_conf.setRouting_configuration_(routing_conf);
		
		dif_conf.setEfcp_configuration_(efcp_conf);
		dif_info.setDif_configuration_(dif_conf);
		ipc_config.setDif_to_assign(dif_info);
		
		// DIFs to register
		ApplicationProcessNamingInformation dif1 = new ApplicationProcessNamingInformation();
		dif1.setProcessName("300");
		ipc_config.addRegister(dif1);

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

		con_handle_t con = new con_handle_t();
		con.setPort_id(port_id);

		cdap_prov.remote_create(con, obj, flags, filt, 28);
		System.out.println("create IPC request CDAP message sent to port " + port_id);

		ser_obj_t message = new ser_obj_t();
		try {
			int bytes_read = rina.getIpcManager().readSDU(port_id, buffer,
								max_sdu_size_in_bytes);
			message.setMessage_(buffer);
			message.setSize_(bytes_read);
		} catch (ReadSDUException e) {
			System.out.println("ReadSDUException in createIPCP_1: " + e.getMessage());
			return false;
		}
		try
		{
			cdap_prov.process_message(message, port_id);
		}
		catch (CDAPException e) {
			System.out.println("CDAPEXception in createIPCP_1: " + e.getMessage());
			return false;
		}
		return true;
	}
	
	private void queryRIB(int port_id, String name)
	{
		byte[] buffer = new byte[max_sdu_size_in_bytes];
        ser_obj_t message = new ser_obj_t();

		obj_info_t obj = new obj_info_t();
		obj.setName_(name);
		obj.setClass_("RIBDaemon");
		obj.setInst_(0);

		flags_t flags = new flags_t();
		flags.setFlags_(flags_t.Flags.NONE_FLAGS);

		filt_info_t filt =  new filt_info_t();;
		filt.setFilter_("");
		filt.setScope_(0);

		con_handle_t con = new con_handle_t();
		con.setPort_id(port_id);
		
        cdap_prov.remote_read(con, obj, flags, filt, 28);
        System.out.println("Read RIBDaemon request CDAP message sent");
		try {
	        int bytes_read = rina.getIpcManager().readSDU(port_id,
	        				     buffer,
	        				     max_sdu_size_in_bytes);
	        message.setMessage_(buffer);
	        message.setSize_(bytes_read);
		} catch (ReadSDUException e) {
			System.out.println("ReadSDUException in createIPCP_1: " + e.getMessage());
		}
		try{
	        cdap_prov.process_message(message, port_id);
		} catch (CDAPException e) {
			System.out.println("ReadSDUException in queryRIB: " + e.getMessage() + " " + e.toString());
		}
	}

}
