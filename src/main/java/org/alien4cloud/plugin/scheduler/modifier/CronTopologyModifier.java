package org.alien4cloud.plugin.scheduler.modifier;

import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContextual;
import alien4cloud.utils.PropertyUtil;
import lombok.extern.java.Log;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;

import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.PolicyTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.normative.constants.NormativeRelationshipConstants;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.apache.commons.lang.mutable.MutableInt;
import org.springframework.stereotype.Component;

import static alien4cloud.utils.AlienUtils.safe;
import static org.alien4cloud.plugin.scheduler.policies.SchedulerPoliciesConstants.SCHEDULER_POLICIES_CRON;
import static org.alien4cloud.plugin.scheduler.csar.Version.SCHEDULER_CSAR_VERSION;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Log
@Component("cron-modifier")
public class CronTopologyModifier extends TopologyModifierSupport {

    /**
     * Name
     */
    private static final String A4C_CRON_MODIFIER_TAG = "a4c_cron-modifier";

    /**
     * Configurator to use
     */
    private static final String CRON_CONFIGURATOR = "org.alien4cloud.scheduling.types.CronConfigurator";

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        log.info("Processing topology " + topology.getId());

        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            doProcess(topology, context);
        } catch (Exception e) {
            context.getLog().error("Couldn't process " + A4C_CRON_MODIFIER_TAG);
            log.log(Level.WARNING, "Couldn't process " + A4C_CRON_MODIFIER_TAG, e);
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    private ScalarPropertyValue buildCommand(PolicyTemplate policy,FlowExecutionContext context) {
        Map<String,AbstractPropertyValue> props = policy.getProperties();

        String applicationId = context.getEnvironmentContext().get().getApplication().getId();
        String environmentId = context.getEnvironmentContext().get().getEnvironment().getId();

        StringBuilder builder = new StringBuilder();

        builder.append("TEMP=$(mktemp);");
        builder.append("URL=");
        builder.append(PropertyUtil.getScalarValue(props.get("endpoint")));
        builder.append("; curl -b $TEMP -c $TEMP -L -s -k -d \"username=");
        builder.append(PropertyUtil.getScalarValue(props.get("username")));
        builder.append("&password=");
        builder.append(PropertyUtil.getScalarValue(props.get("password")));
        builder.append("\" --output /dev/null $URL/login ;");
        builder.append("curl -d \"{}\" -b $TEMP -c $TEMP -L -k -H \"Content-Type: application/json\" $URL/rest/latest/applications/");
        builder.append(applicationId);
        builder.append("/environments/");
        builder.append(environmentId);
        builder.append("/workflows/");
        builder.append(PropertyUtil.getScalarValue(props.get("workflow_name")));
        builder.append(" ; curl -b $TEMP -c $TEMP -L -s --output /dev/null -k $URL/logout ; rm $TEMP");

        return new ScalarPropertyValue(builder.toString());
    }

    private void doProcess(Topology topology, FlowExecutionContext context) {
        int id = 1;

        Collection<NodeTemplate> nodes = safe(topology.getNodeTemplates()).values();

        Set<PolicyTemplate> policies = TopologyNavigationUtil.getPoliciesOfType(topology, SCHEDULER_POLICIES_CRON, true);

        Map<String,MutableInt> dependencyMap = nodes.stream()
                .collect(Collectors.toMap(NodeTemplate::getName,(e) -> new MutableInt(0)));

        for (NodeTemplate node : nodes) {
            for (RelationshipTemplate relation : safe(node.getRelationships()).values()) {
                    dependencyMap.get(relation.getTarget()).increment();
            }
        }

        List<String> targetToAdd = dependencyMap.entrySet().stream()
                .filter(e -> e.getValue().intValue() == 0)
                .map( Map.Entry<String,MutableInt>::getKey)
                .collect(Collectors.toList());

        for (PolicyTemplate policy : policies) {
            //String nodeName = String.format("%s_%s",policy.getName(), UUID.randomUUID().toString().replaceAll("-","_"));
            String nodeName = String.format("%s_%d",policy.getName(), id++);
            String cronExpression = PropertyUtil.getScalarValue(policy.getProperties().get("cron_expression"));
            String cronId = nodeName + "-" + context.getEnvironmentContext().get().getEnvironment().getId();

            NodeTemplate node = addNodeTemplate(null,topology,nodeName, CRON_CONFIGURATOR, SCHEDULER_CSAR_VERSION);
            setNodePropertyPathValue(null,topology,node,"command",buildCommand(policy,context));
            setNodePropertyPathValue(null,topology,node,"expression",new ScalarPropertyValue(cronExpression));
            setNodePropertyPathValue(null,topology,node,"cronid",new ScalarPropertyValue(cronId));

            for (String targetNodeName : targetToAdd) {
                //NodeTemplate sourceNode = topology.getNodeTemplates().get(sourceNodeName);
                addRelationshipTemplate(
                        null,
                        topology,
                        node,
                        targetNodeName,
                        NormativeRelationshipConstants.DEPENDS_ON,
                        "dependency",
                        "feature"
                );
            }
        }
    }
}
