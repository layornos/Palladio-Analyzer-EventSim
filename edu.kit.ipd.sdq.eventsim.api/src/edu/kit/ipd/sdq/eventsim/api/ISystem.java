package edu.kit.ipd.sdq.eventsim.api;

import org.palladiosimulator.pcm.usagemodel.EntryLevelSystemCall;

/**
 * Represents a system simulation component which handles service calls generated by an {@link IWorkload} simulation
 * component.
 * 
 * @author Christoph Föhrdes
 */
public interface ISystem {

	/**
	 * Processed a request from a given user to a specific system service.
	 * 
	 * @param user
	 *            The user issuing the request
	 * @param call
	 *            The specification of the system service which is called
	 */
	public void callService(IUser user, EntryLevelSystemCall call);

}
