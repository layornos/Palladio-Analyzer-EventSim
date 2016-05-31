package edu.kit.ipd.sdq.eventsim.workload.interpreter.instructions;

import org.palladiosimulator.pcm.usagemodel.AbstractUserAction;
import org.palladiosimulator.pcm.usagemodel.ScenarioBehaviour;
import org.palladiosimulator.pcm.usagemodel.Start;

import edu.kit.ipd.sdq.eventsim.command.PCMModelCommandExecutor;
import edu.kit.ipd.sdq.eventsim.command.useraction.FindActionInUsageBehaviour;
import edu.kit.ipd.sdq.eventsim.interpreter.ITraversalInstruction;
import edu.kit.ipd.sdq.eventsim.workload.interpreter.state.UserState;

/**
 * TODO: adjust javadoc since the term scope is slightly outdated and should be better denoted by
 * the level of traversal hierarchy.
 * 
 * Use this instruction to continue the traversal with a specified {@link ScenarioBehaviour}. This
 * opens a new scope on the {@link TraversalStateStack}. As soon as the specified behaviour has been
 * traversed completely, the previous scope is restored.
 * 
 * @author Philipp Merkle
 * 
 */
public class TraverseUsageBehaviourInstruction implements ITraversalInstruction<AbstractUserAction, UserState> {

    private final ScenarioBehaviour behaviour;
    private final AbstractUserAction actionAfterCompletion;
    private final PCMModelCommandExecutor executor;

    /**
     * Constructs a new instruction.
     * 
     * @param model
     *            the simulation model
     * @param behaviour
     *            the behaviour that is to be traversed in a new scope
     * @param actionAfterCompletion
     *            the action that is to be traversed after leaving the scope
     */
    public TraverseUsageBehaviourInstruction(final PCMModelCommandExecutor executor, final ScenarioBehaviour behaviour,
            final AbstractUserAction actionAfterCompletion) {
        this.executor = executor;
        this.behaviour = behaviour;
        this.actionAfterCompletion = actionAfterCompletion;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbstractUserAction process(final UserState state) {
        state.setPreviousPosition(state.getCurrentPosition());
        state.setCurrentPosition(this.actionAfterCompletion);

        state.pushStackFrame();

        final Start start = executor.execute(new FindActionInUsageBehaviour<Start>(this.behaviour, Start.class));
        state.setCurrentPosition(start);
        return start;
    }

}
