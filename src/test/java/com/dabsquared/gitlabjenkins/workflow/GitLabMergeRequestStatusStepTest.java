package com.dabsquared.gitlabjenkins.workflow;


import hudson.model.Run;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GitLabMergeRequestStatusStepTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void gitlabCommitStatus() throws Exception {
        WorkflowJob project = j.createProject(WorkflowJob.class);
        String pipelineText =  IOUtils.toString(getClass().getResourceAsStream(
            "pipeline/gitlabMergeRequestStatus-pipeline.groovy"));
        project.setDefinition(new CpsFlowDefinition(pipelineText, false));
        Run build = j.buildAndAssertSuccess(project);
        j.assertLogContains("MR1 target = master", build);
        j.assertLogContains("No MR for feature 2", build);
    }
}
