package edu.kit.ipd.sdq.eventsim;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import edu.kit.ipd.sdq.eventsim.api.ISimulationMiddleware;
import edu.kit.ipd.sdq.eventsim.api.PCMModel;
import edu.kit.ipd.sdq.eventsim.command.ICommand;
import edu.kit.ipd.sdq.eventsim.command.PCMModelCommandExecutor;
import edu.kit.ipd.sdq.eventsim.entities.EventSimEntity;
import edu.kit.ipd.sdq.eventsim.measurement.MeasurementStorage;

/**
 * This class is the basis for a simulation component based on EventSim. It
 * contains the management of the active EventSim entities.
 * 
 * @author Christoph Föhrdes
 */
abstract public class AbstractEventSimModel {

	private PCMModelCommandExecutor executor;
	private List<EventSimEntity> activeEntitiesList;
	private ISimulationMiddleware middleware;
	private MeasurementStorage 	measurementStorage;
	
	public AbstractEventSimModel(ISimulationMiddleware middleware, MeasurementStorage measurementStorage) {
		this.middleware = middleware;
		this.measurementStorage = measurementStorage;
		init();
	}

	public ISimulationMiddleware getSimulationMiddleware() {
		return middleware;
	}
	
	public MeasurementStorage getMeasurementStorage() {
		return measurementStorage;
	}

	/**
	 * Initializes the model
	 */
	private void init() {
		this.executor = new PCMModelCommandExecutor(
				getSimulationMiddleware().getSimulationConfiguration().getPCMModel());
		this.activeEntitiesList = new CopyOnWriteArrayList<EventSimEntity>();
	}

	/**
	 * Executes the specified command and returns the result.
	 * 
	 * @param <T>
	 *            the return type
	 * @param command
	 *            the command that is to be executed
	 * @return the command's result
	 */
	public <T> T execute(final ICommand<T, PCMModel> command) {
		return this.executor.execute(command);
	}

	/**
	 * Informs this simulation model about the creation of a new entity.
	 * <p>
	 * Once the registered entity has left the simulation system,
	 * {@link #unregisterEntity(EventSimEntity)} should be called. Entities that
	 * were registered, but have not been unregistered are deemed as active
	 * entities, i.e. they have not yet finished simulating their behavior. When
	 * the simulation is about to stop, the {@code finalise} method notifies
	 * active entities of the imminent stop.
	 * 
	 * @param entity
	 *            the entity that has just been spawned
	 */
	public void registerEntity(EventSimEntity entity) {
		this.activeEntitiesList.add(entity);
	}

	/**
	 * Informs this simulation model that the specified entity has finished to
	 * simulate its behavior and is about to leave the simulated system.
	 * 
	 * @param entity
	 *            the entity that has just finished their simulated behavior
	 */
	public void unregisterEntity(EventSimEntity entity) {
		this.activeEntitiesList.remove(entity);
	}

	/**
	 * This method does some common EventSim related clean up.
	 * Should be called when a simulation has stopped.
	 */
	public void finalise() {	
		// notify active entities that the simulation is finished (and
		// therefore, also their existence in the simulated system)
		for (EventSimEntity entity : activeEntitiesList) {
			assert entity.getState().equals(EventSimEntity.EntityState.ENTERED_SYSTEM) : "Found an entity in the " + "list of active entities which is in the state " + entity.getState() + ", and therefore can not be an active entity.";
			entity.notifyLeftSystem();
		}
		assert activeEntitiesList.isEmpty() : "There are some entities left in the list of active entities, though " + "each of them was asked to leave the system.";

		EventSimEntity.resetIdGenerator();
	}

}
