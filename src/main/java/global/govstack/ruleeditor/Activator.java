package global.govstack.ruleeditor;

import global.govstack.ruleeditor.element.RuleEditorElement;
import global.govstack.ruleeditor.element.RuleEditorResources;
import global.govstack.ruleeditor.lib.RulesServiceProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for Joget Rule Editor Plugin.
 *
 * Registers:
 * - RulesServiceProvider: API endpoints for validation and compilation
 * - RuleEditorResources: Static file serving for CodeMirror
 * - RuleEditorElement: Form element for Rules Script editing
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        // Register the Rules Parser API plugin
        registrationList.add(context.registerService(
            RulesServiceProvider.class.getName(),
            new RulesServiceProvider(),
            null
        ));

        // Register the Rule Editor Resources plugin (serves static files)
        registrationList.add(context.registerService(
            RuleEditorResources.class.getName(),
            new RuleEditorResources(),
            null
        ));

        // Register the Rule Editor Form Element
        registrationList.add(context.registerService(
            RuleEditorElement.class.getName(),
            new RuleEditorElement(),
            null
        ));
    }

    @Override
    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
