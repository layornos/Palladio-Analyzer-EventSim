package edu.kit.ipd.sdq.eventsim.launch;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.Platform;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;

import edu.kit.ipd.sdq.eventsim.api.ISimulationConfiguration;
import edu.kit.ipd.sdq.eventsim.api.PCMModel;
import edu.kit.ipd.sdq.eventsim.instrumentation.description.core.InstrumentationDescription;
import edu.kit.ipd.sdq.eventsim.launch.runconfig.EventSimConfigurationConstants;
import edu.kit.ipd.sdq.eventsim.middleware.MeasurementStorageModule;
import edu.kit.ipd.sdq.eventsim.middleware.SimulationMiddlewareModule;
import edu.kit.ipd.sdq.eventsim.modules.SimulationModule;
import edu.kit.ipd.sdq.eventsim.modules.SimulationModuleRegistry;

public class ExtendableSimulationModule extends AbstractModule {

    private static final Logger logger = Logger.getLogger(ExtendableSimulationModule.class);

    private ISimulationConfiguration config;

    private InstrumentationDescription instrumentationDescription;

    private SimulationModuleRegistry moduleRegistry;

    public ExtendableSimulationModule(ISimulationConfiguration config,
            InstrumentationDescription instrumentationDescription, SimulationModuleRegistry moduleRegistry) {
        this.config = config;
        this.instrumentationDescription = instrumentationDescription;
        this.moduleRegistry = moduleRegistry;
    }

    @Override
    protected void configure() {
        install(new SimulationMiddlewareModule(config));
        install(new MeasurementStorageModule(config));

        bind(SimulationModuleRegistry.class).toInstance(moduleRegistry);
        bind(ISimulationConfiguration.class).toInstance(config);
        bind(PCMModel.class).toInstance(config.getPCMModel());
        bind(InstrumentationDescription.class).toInstance(instrumentationDescription);
    }

    public static Module create(ISimulationConfiguration config,
            InstrumentationDescription instrumentationDescription) {
        SimulationModuleRegistry moduleRegistry = SimulationModuleRegistry.createFrom(Platform.getExtensionRegistry());

        Module module = new ExtendableSimulationModule(config, instrumentationDescription, moduleRegistry);

        // install enabled Guice modules of extensions, starting with extensions of lower priority
        Set<SimulationModule> enabledModules = loadEnabledModulesFromConfig(config);
        for (SimulationModule m : moduleRegistry.getModules()) {
            boolean enabled = enabledModules.contains(m.getId());
            m.setEnabled(enabled);
            if (enabled) {
                logger.info("Installing simulation module " + m.getName() + " (" + m.getId() + "), priority = "
                        + m.getPriority());
                if (m.getGuiceModule() != null) {
                    // workaround to allow redefinition of Guice bindings, so that simulation
                    // modules with higher priority may override existing bindings of modules with
                    // lower priority
                    module = Modules.override(module).with(m.getGuiceModule());
                } else {
                    logger.info("No Guice module defined for simulation module " + m.getName());
                }
            } else {
                logger.info("Skipping disabled simulation module " + m.getName() + " (" + m.getId() + "), priority = "
                        + m.getPriority());
            }
        }

        return module;
    }

    private static Set<SimulationModule> loadEnabledModulesFromConfig(ISimulationConfiguration config) {
        Set<SimulationModule> enabledModules = new HashSet<>();
        if (config.getConfigurationMap().get(EventSimConfigurationConstants.ENABLED_MODULES) != null) {
            enabledModules = (Set<SimulationModule>) config.getConfigurationMap()
                    .get(EventSimConfigurationConstants.ENABLED_MODULES);
        }
        return enabledModules;
    }

}
