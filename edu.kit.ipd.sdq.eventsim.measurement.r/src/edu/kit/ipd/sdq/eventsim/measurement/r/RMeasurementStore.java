package edu.kit.ipd.sdq.eventsim.measurement.r;

import java.util.Map;
import java.util.function.Function;

import org.apache.log4j.Logger;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import edu.kit.ipd.sdq.eventsim.measurement.Measurement;
import edu.kit.ipd.sdq.eventsim.measurement.MeasurementStorage;
import edu.kit.ipd.sdq.eventsim.measurement.Pair;
import edu.kit.ipd.sdq.eventsim.measurement.PropertyExtractor;
import edu.kit.ipd.sdq.eventsim.measurement.r.jobs.FinalizeRProcessingJob;
import edu.kit.ipd.sdq.eventsim.measurement.r.jobs.MergeBufferedDataFramesJob;
import edu.kit.ipd.sdq.eventsim.measurement.r.jobs.PushBufferToRJob;
import edu.kit.ipd.sdq.eventsim.measurement.r.jobs.StoreRDSFileJob;

/**
 * Stores {@link Measurement}s into R using Rserve (for details on Rserve see https://rforge.net/Rserve).
 * <p>
 * Measurements are buffered and sent to R as a batch once the buffer size reaches its capacity. Increasing the buffer
 * capacity improves performance at the cost of higher memory consumption (this needs to be further evaluated, however).
 * 
 * @author Philipp Merkle
 *
 */
public class RMeasurementStore implements MeasurementStorage {

	static final Logger log = Logger.getLogger(RMeasurementStore.class);

	private static final int BUFFER_CAPACITY = 10_000;

	private static final int CONNECTION_RETRIES_MAX = 60;

	private static final int MILLISECONDS_BETWEEN_CONNECTION_RETRIES = 1000;

	private Buffer buffer;

	private PropertyExtractor idExtractor;

	private PropertyExtractor nameExtractor;

	private PropertyExtractor typeExtractor;

	private RConnection connection;

	private RJobProcessor rJobProcessor;

	private int bufferNumber;

	private boolean storeRds;
	private String rdsFilePath;

	/**
	 * Use this constructor when no RDS file is to be created upon finish.
	 */
	public RMeasurementStore() {
		this("");
		this.storeRds = false;
	}

	/**
	 * Use this constructor when an RDS file is to be created upon finish.
	 * 
	 * @param rdsFilePath
	 *            the location of the file to be created.
	 */
	public RMeasurementStore(String rdsFilePath) {
		this.storeRds = true;
		this.rdsFilePath = rdsFilePath;
		idExtractor = new PropertyExtractor();
		nameExtractor = new PropertyExtractor();
		typeExtractor = new PropertyExtractor();
		connection = connectToR();
		rJobProcessor = new RJobProcessor(connection);
		rJobProcessor.start();

		// add simple type extractor as a default
		typeExtractor.add(Object.class, new Function<Object, String>() {
			@Override
			public String apply(Object o) {
				return stripNamespace(o.getClass().getName());
			}

			private String stripNamespace(String fqn) {
				int startOfClassName = fqn.lastIndexOf(".");
				return fqn.substring(startOfClassName + 1, fqn.length());
			}
		});
		
		buffer = new Buffer(BUFFER_CAPACITY, idExtractor, nameExtractor, typeExtractor);
	}

	/**
	 * Constructs a {@link RMeasurementStore} by extracting configuration options from the provided launch
	 * configuration.
	 * 
	 * @param configuration
	 *            the launch configuration
	 * @return the constructed {@link RMeasurementStore}, or {@code null} if expected configuration options could not be
	 *         found in the provided launch configuration.
	 */
	public static RMeasurementStore fromLaunchConfiguration(Map<String, Object> configuration) {
		Boolean createRds = (Boolean) configuration.get(RConfigurationConstants.CREATE_RDS_FILE_KEY);
		if (!createRds) {
			return new RMeasurementStore();
		}
		String rdsFilePath = (String) configuration.get(RConfigurationConstants.RDS_FILE_PATH_KEY);
		if (rdsFilePath != null) {
			return new RMeasurementStore(rdsFilePath);
		}
		return null;
	}

	@Override
	public void addIdExtractor(Class<? extends Object> elementClass, Function<Object, String> extractionFunction) {
		idExtractor.add(elementClass, extractionFunction);
	}

	@Override
	public void addNameExtractor(Class<? extends Object> elementClass, Function<Object, String> extractionFunction) {
		nameExtractor.add(elementClass, extractionFunction);
	}

	@Override
	public void addTypeExtractor(Class<? extends Object> elementClass, Function<Object, String> extractionFunction) {
		typeExtractor.add(elementClass, extractionFunction);
	}

	@Override
	public <E> void put(Measurement<E, ?> m) {
		buffer.put(m);
		if (buffer.isFull()) {
			rJobProcessor.enqueue(new PushBufferToRJob(buffer, bufferNumber++));
			buffer = new Buffer(BUFFER_CAPACITY, idExtractor, nameExtractor, typeExtractor);
		}
	}

	@Override
	public <F, S> void putPair(Measurement<Pair<F, S>, ?> m) {
		buffer.putPair(m);
		if (buffer.isFull()) {
			rJobProcessor.enqueue(new PushBufferToRJob(buffer, bufferNumber++));
			buffer = new Buffer(BUFFER_CAPACITY, idExtractor, nameExtractor, typeExtractor);
		}
	}

	@Override
	public void finish() {
		buffer.shrinkToSize();

		rJobProcessor.enqueue(new PushBufferToRJob(buffer, bufferNumber++));
		rJobProcessor.enqueue(new MergeBufferedDataFramesJob());
		if (storeRds) {
			rJobProcessor.enqueue(new StoreRDSFileJob(rdsFilePath));
		} else {
			log.info("Skipping creation of RDS file.");
		}
		rJobProcessor.enqueue(new FinalizeRProcessingJob());

		// wait until R processing is finished
		rJobProcessor.waitUntilFinished();

		// clean up
		// TODO really needed?
		connection.close();
		buffer = new Buffer(BUFFER_CAPACITY, idExtractor, nameExtractor, typeExtractor);
		bufferNumber = 0;
	}

	private RConnection connectToR() {
		// try connecting to R
		RConnection connection = null;
		for (int retries = 0; retries < CONNECTION_RETRIES_MAX; retries++) {
			try {
				connection = new RConnection();
			} catch (RserveException e) {
				// handled in the following
			}

			if (connection != null && connection.isConnected()) {
				// successfully connected => leave for
				break;
			} else {
				if (retries == 0) {
					log.error("Could not establish Rserve connection to R. "
							+ "Make sure to run Rserve, e.g. by calling \"library(Rserve); Rserve()\" in R. ");
				}
				if (retries % 20 == 0) {
					log.error("Waiting for Rserve connection...");
				}
				// wait some time before retrying again
				try {
					Thread.sleep(MILLISECONDS_BETWEEN_CONNECTION_RETRIES);
				} catch (InterruptedException e) {
					log.error(e);
				}
			}
		}

		// still not yet connected? give up.
		if (connection == null || !connection.isConnected()) {
			throw new RuntimeException("Could not establish Rserve connection to R within " + CONNECTION_RETRIES_MAX
					+ " attempts. Giving up now.");
		}

		// try loading "data.table" library
		try {
			connection.voidEval("library(data.table)");
		} catch (RserveException e) {
			throw new RuntimeException("R could not load library \"data.table\". "
					+ "Please run \"install.packages('data.table')\" in R.");
		}
		return connection;
	}

}
