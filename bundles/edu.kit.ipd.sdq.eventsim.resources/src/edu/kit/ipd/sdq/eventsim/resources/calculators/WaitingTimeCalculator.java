package edu.kit.ipd.sdq.eventsim.resources.calculators;

import org.apache.log4j.Logger;
import org.palladiosimulator.pcm.core.composition.AssemblyContext;

import edu.kit.ipd.sdq.eventsim.measurement.Measurement;
import edu.kit.ipd.sdq.eventsim.measurement.MeasuringPoint;
import edu.kit.ipd.sdq.eventsim.measurement.MeasuringPointPair;
import edu.kit.ipd.sdq.eventsim.measurement.Pair;
import edu.kit.ipd.sdq.eventsim.measurement.annotation.Calculator;
import edu.kit.ipd.sdq.eventsim.measurement.annotation.ProbePair;
import edu.kit.ipd.sdq.eventsim.measurement.calculator.AbstractBinaryCalculator;
import edu.kit.ipd.sdq.eventsim.measurement.probe.IProbe;
import edu.kit.ipd.sdq.eventsim.resources.entities.SimPassiveResource;
import edu.kit.ipd.sdq.eventsim.resources.entities.SimulatedProcess;

@Calculator(metric = "waiting_time", type = SimPassiveResource.class, intendedProbes = {
		@ProbePair(from = "request_time", to = "acquire_time") })
public class WaitingTimeCalculator extends AbstractBinaryCalculator<SimPassiveResource, SimPassiveResource> {

	private static final Logger log = Logger.getLogger(WaitingTimeCalculator.class);

	@Override
	public void setup(IProbe<SimPassiveResource> fromProbe, IProbe<SimPassiveResource> toProbe) {
		fromProbe.enableCaching();
		toProbe.forEachMeasurement(m -> {
			// find "from"-measurement
			SimulatedProcess process = (SimulatedProcess) m.getWho();
			Measurement<SimPassiveResource> fromMeasurement = null;
			do {
				fromMeasurement = fromProbe.getLastMeasurementOf(process);
				process = process.getParent();
			} while (fromMeasurement == null && process != null);

			if (fromMeasurement != null) {
				notify(calculate(fromMeasurement, m));
			} else {
				// TODO improve warning, give hits on how to resolve this
				// problem
				log.warn(String.format("Could not find last measurement triggered by %s or a parent request. "
						+ "Skipping calculation.", m.getWho()));
			}
		});

	}

	@Override
	public Measurement<Pair<SimPassiveResource, SimPassiveResource>> calculate(Measurement<SimPassiveResource> from,
			Measurement<SimPassiveResource> to) {
		if (from == null) {
			return null;
		}

		double when = to.getWhen();
		double waitingTime = to.getValue() - from.getValue();

		AssemblyContext assemblyCtx = from.getWhere().getElement().getAssemblyContext();
		
		// TODO current position not yet accurate
		MeasuringPoint<Pair<SimPassiveResource, SimPassiveResource>> mp = new MeasuringPointPair<>(from.getWhere(), to.getWhere(), "waiting_time", assemblyCtx);
		return new Measurement<>("WAITING_TIME", mp, to.getWho(), waitingTime, when);

		// TODO add request as metadata?
		// from.getWho().getCurrentPosition().getId()
	}

}
