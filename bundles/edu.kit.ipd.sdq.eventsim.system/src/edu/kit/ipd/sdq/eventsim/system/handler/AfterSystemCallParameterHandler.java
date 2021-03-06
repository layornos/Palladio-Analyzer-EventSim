package edu.kit.ipd.sdq.eventsim.system.handler;

import java.util.List;

import org.apache.log4j.Logger;
import org.palladiosimulator.pcm.parameter.VariableUsage;
import org.palladiosimulator.pcm.usagemodel.EntryLevelSystemCall;

import de.uka.ipd.sdq.simucomframework.variables.StackContext;
import de.uka.ipd.sdq.simucomframework.variables.stackframe.SimulatedStackframe;
import edu.kit.ipd.sdq.eventsim.api.events.IEventHandler;
import edu.kit.ipd.sdq.eventsim.api.events.SystemRequestFinishedEvent;
import edu.kit.ipd.sdq.eventsim.system.entities.Request;
import edu.kit.ipd.sdq.eventsim.util.ParameterHelper;

public class AfterSystemCallParameterHandler implements  IEventHandler<SystemRequestFinishedEvent> {

    private static final Logger logger = Logger.getLogger(AfterSystemCallParameterHandler.class);

	@Override
	public Registration handle(SystemRequestFinishedEvent simulationEvent) {
		if (logger.isDebugEnabled()) {
            logger.debug("Begin handling system call output parameters");
        }

		Request request = (Request) simulationEvent.getRequest();

        final EntryLevelSystemCall call = request.getSystemCall();
        final StackContext ctx = request.getRequestState().getStoExContext();

        // get a reference on the current stack frame which is being removed soon
        final SimulatedStackframe<Object> serviceBodyFrame = ctx.getStack().currentStackFrame();

        // remove the current stack frame. This restores the pre-call scope.
        ctx.getStack().removeStackFrame();

        // evaluate the return parameters of the call and add them to the current scope
        final List<VariableUsage> parameters = call.getOutputParameterUsages_EntryLevelSystemCall();
        final SimulatedStackframe<Object> currentFrame = ctx.getStack().currentStackFrame();
        ParameterHelper.evaluateParametersAndCopyToFrame(parameters, serviceBodyFrame, currentFrame);

        if (logger.isDebugEnabled()) {
            logger.debug("Finished handling system call output parameters");
        }
        
        return Registration.KEEP_REGISTERED;
	}

}
