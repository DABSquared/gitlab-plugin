package com.dabsquared.gitlabjenkins.trigger.handler.push;

import com.dabsquared.gitlabjenkins.trigger.filter.BranchFilter;
import com.dabsquared.gitlabjenkins.trigger.filter.MergeRequestLabelFilter;
import hudson.model.Job;
import org.gitlab4j.api.webhook.PushEvent;

/**
 * @author Robin Müller
 */
class NopPushHookTriggerHandler implements PushHookTriggerHandler {
    @Override
    public void handle(
            Job<?, ?> job,
            PushEvent event,
            boolean ciSkip,
            BranchFilter branchFilter,
            MergeRequestLabelFilter mergeRequestLabelFilter) {
        // nothing to do
    }
}
