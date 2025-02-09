package com.dabsquared.gitlabjenkins.workflow;

import com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty;
import com.dabsquared.gitlabjenkins.gitlab.api.GitLabClient;
import com.dabsquared.gitlabjenkins.gitlab.api.model.BuildState;
import com.dabsquared.gitlabjenkins.gitlab.api.model.MergeRequest;
import com.dabsquared.gitlabjenkins.gitlab.hook.model.State;
import com.dabsquared.gitlabjenkins.util.CommitStatusUpdater;
import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:robin.mueller@1und1.de">Robin Müller</a>
 */
@ExportedBean
public class GitLabMergeRequestStatusStep extends Step {

    private final static Logger LOGGER = Logger.getLogger(GitLabMergeRequestStatusStep.class.getName());

    private String projectName;
    private String branchName;

    @DataBoundConstructor
    public GitLabMergeRequestStatusStep(String project_name, String branch_name) {
        this.projectName = project_name;
        this.branchName = branch_name;
    }

    public String getProjectName() {
        return projectName;
    }

    @DataBoundSetter
    public void setProjectName(String project_name) {
        this.projectName = project_name;
    }

    public String getBranchName() {
        return branchName;
    }

    @DataBoundSetter
    public void setBranchName(String branch_name) {
        this.branchName = branch_name;
    }

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new GitLabMergeRequestStatusStepExecution(context, this);
	}

    public static class GitLabMergeRequestStatusStepExecution extends SynchronousNonBlockingStepExecution<MergeRequest> {
        private static final long serialVersionUID = 1;

        private final transient Run<?, ?> run;

        private final transient GitLabMergeRequestStatusStep step;

        GitLabMergeRequestStatusStepExecution(StepContext context, GitLabMergeRequestStatusStep step) throws Exception {
            super(context);
            this.step = step;
            run = context.get(Run.class);
        }

        @Override
        protected MergeRequest run() throws Exception {
            GitLabClient client = GitLabConnectionProperty.getClient(run);
            if (client == null) {
                LOGGER.log(Level.WARNING, "Failed to find gitlab connection");
                return null;
            }

            Integer page = 1;
            do {
                List<MergeRequest> mergeRequests = client.getMergeRequests(step.branchName, State.opened, page, 100);
                for (MergeRequest mr : mergeRequests) {
                    if (mr.getSourceBranch().equals(step.branchName)) {
                        return mr;
                    }
                }
                page = mergeRequests.isEmpty() ? null : page + 1;
            } while (page != null);
            return null;
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public String getDisplayName() {
            return "Return the MergeRequest status for the branch, if it exists.";
        }

        @Override
        public String getFunctionName() {
            return "gitlabMergeRequestStatus";
        }

        public ListBoxModel doFillStateItems() {
            ListBoxModel options = new ListBoxModel();
            for (BuildState buildState : EnumSet.allOf(BuildState.class)) {
                options.add(buildState.name());
            }
            return options;
        }

		@Override
		public Set<Class<?>> getRequiredContext() {
			return ImmutableSet.of(TaskListener.class, Run.class);
		}
    }
}
