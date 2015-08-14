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

import eu.irati.librina.ApplicationRegistrationInformation;
import eu.irati.librina.IPCEvent;
import eu.irati.librina.IPCEventProducerSingleton;
import eu.irati.librina.IPCEventType;
import eu.irati.librina.RegisterApplicationResponseEvent;
import eu.irati.librina.ApplicationProcessNamingInformation;
import eu.irati.librina.ApplicationRegistrationType;
import eu.irati.librina.IPCManagerSingleton;

import java.lang.String;

public class Manager {
	public Manager(String app_name, String app_instance, String dif_name)
	{
		app_name_ = app_name;
		app_instance_ = app_instance;
		dif_name_ = dif_name;
	}

	public void applicationRegister(boolean blocking)
	{
        long seqnum;
		ApplicationRegistrationInformation ari = new ApplicationRegistrationInformation();
		IPCEvent event = null;
		RegisterApplicationResponseEvent resp;
		
		ari.setIpcProcessId(0);
		ari.setAppName(new ApplicationProcessNamingInformation(app_name_, app_instance_));
		ari.setBlocking(blocking);
		
		if(dif_name_.isEmpty())
			ari.setApplicationRegistrationType(ApplicationRegistrationType.APPLICATION_REGISTRATION_ANY_DIF);
		else
		{
			ari.setApplicationRegistrationType(ApplicationRegistrationType.APPLICATION_REGISTRATION_SINGLE_DIF);
			ari.setDifName(new ApplicationProcessNamingInformation(dif_name_, new String()));
		}
		
        // Request the registration
        seqnum = new IPCManagerSingleton().requestApplicationRegistration(ari);

        // Wait for the response to come
        for (;;) {
                event = new IPCEventProducerSingleton().eventWait();
                if (event != null && event.getEventType().equals(IPCEventType.REGISTER_APPLICATION_RESPONSE_EVENT) &&
                    event.getSequenceNumber() == seqnum) {
                        break;
                }
        }

        resp = (RegisterApplicationResponseEvent)event;

        // Update librina state
        if (resp.getResult() == 0) {
        	new IPCManagerSingleton().commitPendingRegistration(seqnum, resp.getDIFName());
        } else {
        	new IPCManagerSingleton().withdrawPendingRegistration(seqnum);
                System.out.print("Failed to register application");
        }
		
	}
	
	private String app_name_;
	private String app_instance_;
	private String dif_name_;
}
