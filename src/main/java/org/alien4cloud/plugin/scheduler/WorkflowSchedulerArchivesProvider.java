package org.alien4cloud.plugin.scheduler;

import alien4cloud.plugin.archives.AbstractArchiveProviderPlugin;
import org.springframework.stereotype.Component;

@Component("scheduler-archives-provider")
public class WorkflowSchedulerArchivesProvider extends AbstractArchiveProviderPlugin {
    @Override
    protected String[] getArchivesPaths() {
        return new String[] { "csar" };
    }
}
