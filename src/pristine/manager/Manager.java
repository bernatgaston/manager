/*
 * Echo Application
 *
 * Addy Bombeke <addy.bombeke@ugent.be>
 * Bernat Gaston <bernat.gaston@i2cat.net>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package pristine.manager;

import eu.irati.librina.ApplicationRegistrationInformation;
import eu.irati.librina.IPCEvent;
import eu.irati.librina.IPCEventType;
import eu.irati.librina.RegisterApplicationResponseEvent;
import eu.irati.librina.UnregisterApplicationResponseEvent;
import eu.irati.librina.rina;
import eu.irati.librina.ApplicationProcessNamingInformation;
import eu.irati.librina.ApplicationRegistrationType;
import eu.irati.librina.FlowInformation;
import eu.irati.librina.FlowRequestEvent;

import java.util.List;
import java.lang.String;
import java.util.ArrayList;

public class Manager {
	public static final int max_sdu_size_in_bytes = 10000;
	private String app_name_;
	private String app_instance_;
	private String dif_name_;
	
	public Manager(String app_name, String app_instance, String dif_name)
	{
		app_name_ = app_name;
		app_instance_ = app_instance;
		dif_name_ = dif_name;
	}

	public void applicationRegister()
	{
        long seqnum;
		ApplicationRegistrationInformation ari = new ApplicationRegistrationInformation();
		IPCEvent event = null;
		RegisterApplicationResponseEvent resp;
		
		ari.setIpcProcessId(0);
		ari.setAppName(new ApplicationProcessNamingInformation(app_name_, app_instance_));
		
		if(dif_name_.isEmpty())
			ari.setApplicationRegistrationType(ApplicationRegistrationType.APPLICATION_REGISTRATION_ANY_DIF);
		else
		{
			ari.setApplicationRegistrationType(ApplicationRegistrationType.APPLICATION_REGISTRATION_SINGLE_DIF);
			ari.setDifName(new ApplicationProcessNamingInformation(dif_name_, new String()));
		}
		
        // Request the registration
        seqnum = rina.getIpcManager().requestApplicationRegistration(ari);

        // Wait for the response to come
        for (;;) {
                event = rina.getIpcEventProducer().eventWait();
                if (event != null && event.getEventType().equals(IPCEventType.REGISTER_APPLICATION_RESPONSE_EVENT) &&
                    event.getSequenceNumber() == seqnum) {
                        break;
                }
        }

        resp = (RegisterApplicationResponseEvent)event;

        // Update librina state
        if (resp.getResult() == 0) {
        	rina.getIpcManager().commitPendingRegistration(seqnum, resp.getDIFName());
        } else {
        	rina.getIpcManager().withdrawPendingRegistration(seqnum);
                System.out.print("Failed to register application");
        }
	}
	
	public void run(boolean blocking)
	{
		List<Thread> active_workers= new ArrayList<Thread>();
		applicationRegister();
		while(true)
		{
			IPCEvent event = rina.getIpcEventProducer().eventWait();
			int port_id = 0;
			if(event != null)
			{
				switch(event.getEventType())
				{
				case REGISTER_APPLICATION_RESPONSE_EVENT:
					rina.getIpcManager().commitPendingRegistration(event.getSequenceNumber(), 
							((RegisterApplicationResponseEvent)event).getDIFName());
					break;
				case UNREGISTER_APPLICATION_RESPONSE_EVENT:
					rina.getIpcManager().appUnregistrationResult(event.getSequenceNumber(), 
							((UnregisterApplicationResponseEvent)event).getResult()==0);
					break;
				case FLOW_ALLOCATION_REQUESTED_EVENT:
					FlowInformation flow = rina.getIpcManager().allocateFlowResponse((FlowRequestEvent)event, 0, true);
					port_id = flow.getPortId();
					System.out.println("New flow allocated [port-id = "+ port_id + "]");
                    ManagerWorker worker = new ManagerWorker(flow);
                    Thread th = new Thread(worker);
                    th.start();
                    active_workers.add(th);
					break;
				case DEALLOCATE_FLOW_RESPONSE_EVENT:
					break;
				default:
					break;
				}
			}
		}
	}
}
