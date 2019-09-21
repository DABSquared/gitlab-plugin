package com.dabsquared.gitlabjenkins;


import com.dabsquared.gitlabjenkins.connection.GitLabConnection;
import com.dabsquared.gitlabjenkins.connection.GitLabConnectionConfig;
import com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty;
import com.dabsquared.gitlabjenkins.gitlab.hook.model.MergeRequestHook;
import com.dabsquared.gitlabjenkins.gitlab.hook.model.NoteHook;
import com.dabsquared.gitlabjenkins.gitlab.hook.model.PipelineHook;
import com.dabsquared.gitlabjenkins.gitlab.hook.model.PushHook;
import com.dabsquared.gitlabjenkins.publisher.GitLabAcceptMergeRequestPublisher;
import com.dabsquared.gitlabjenkins.publisher.GitLabCommitStatusPublisher;
import com.dabsquared.gitlabjenkins.publisher.GitLabMessagePublisher;
import com.dabsquared.gitlabjenkins.publisher.GitLabVotePublisher;
import com.dabsquared.gitlabjenkins.trigger.TriggerOpenMergeRequest;
import com.dabsquared.gitlabjenkins.trigger.branch.ProjectBranchesProvider;
import com.dabsquared.gitlabjenkins.trigger.filter.BranchFilter;
import com.dabsquared.gitlabjenkins.trigger.filter.BranchFilterFactory;
import com.dabsquared.gitlabjenkins.trigger.filter.BranchFilterType;
import com.dabsquared.gitlabjenkins.trigger.filter.MergeRequestLabelFilter;
import com.dabsquared.gitlabjenkins.trigger.filter.MergeRequestLabelFilterConfig;
import com.dabsquared.gitlabjenkins.trigger.filter.MergeRequestLabelFilterFactory;
import com.dabsquared.gitlabjenkins.trigger.handler.merge.MergeRequestHookTriggerHandler;
import com.dabsquared.gitlabjenkins.trigger.handler.note.NoteHookTriggerHandler;
import com.dabsquared.gitlabjenkins.trigger.handler.pipeline.PipelineHookTriggerHandler;
import com.dabsquared.gitlabjenkins.trigger.handler.push.PushHookTriggerHandler;
import com.dabsquared.gitlabjenkins.trigger.label.ProjectLabelsProvider;
import com.dabsquared.gitlabjenkins.webhook.GitLabWebHook;
import hudson.Extension;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Item;
import hudson.model.Job;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import hudson.util.Secret;
import hudson.util.SequentialExecutionQueue;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.triggers.SCMTriggerItem.SCMTriggerItems;
import net.karneim.pojobuilder.GeneratePojoBuilder;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.security.SecureRandom;

import static com.dabsquared.gitlabjenkins.trigger.filter.BranchFilterConfig.BranchFilterConfigBuilder.branchFilterConfig;
import static com.dabsquared.gitlabjenkins.trigger.handler.merge.MergeRequestHookTriggerHandlerFactory.newMergeRequestHookTriggerHandler;
import static com.dabsquared.gitlabjenkins.trigger.handler.note.NoteHookTriggerHandlerFactory.newNoteHookTriggerHandler;
import static com.dabsquared.gitlabjenkins.trigger.handler.pipeline.PipelineHookTriggerHandlerFactory.newPipelineHookTriggerHandler;
import static com.dabsquared.gitlabjenkins.trigger.handler.push.PushHookTriggerHandlerFactory.newPushHookTriggerHandler;


/**
 * Triggers a build when we receive a GitLab WebHook.
 *
 * @author Daniel Brooks
 */
public class GitLabPushTrigger extends Trigger<Job<?, ?>> implements MergeRequestTriggerConfig {

    private static final SecureRandom RANDOM = new SecureRandom();

    private boolean triggerOnPush = true;
    private boolean triggerOnMergeRequest = true;
    private boolean triggerOnPipelineEvent = false;
    private boolean triggerOnAcceptedMergeRequest = false;
    private boolean triggerOnClosedMergeRequest = false;
    private boolean triggerOnApprovedMergeRequest = false;
    private TriggerOpenMergeRequest triggerOpenMergeRequestOnPush;
    private boolean triggerOnNoteRequest = true;
    private String noteRegex = "";
    private String userRegex = "";
    private boolean ciSkip = true;
    private boolean skipWorkInProgressMergeRequest;
    private boolean setBuildDescription = true;
    private transient boolean addNoteOnMergeRequest;
    private transient boolean addCiMessage;
    private transient boolean addVoteOnMergeRequest;
    private transient boolean allowAllBranches = false;
    private transient String branchFilterName;
    private BranchFilterType branchFilterType;
    private String includeBranchesSpec;
    private String excludeBranchesSpec;
    private String sourceBranchRegex;
    private String targetBranchRegex;
    private MergeRequestLabelFilterConfig mergeRequestLabelFilterConfig;
    private volatile Secret secretToken;
    private String pendingBuildName;
    private boolean cancelPendingBuildsOnUpdate;

    private transient BranchFilter branchFilter;
    private transient PushHookTriggerHandler pushHookTriggerHandler;
    private transient MergeRequestHookTriggerHandler mergeRequestHookTriggerHandler;
    private transient NoteHookTriggerHandler noteHookTriggerHandler;
    private transient PipelineHookTriggerHandler pipelineTriggerHandler;
    private transient boolean acceptMergeRequestOnSuccess;
    private transient MergeRequestLabelFilter mergeRequestLabelFilter;

    /**
     * @deprecated use {@link #GitLabPushTrigger()} with setters to configure an instance of this class.
     */
    @Deprecated
    @GeneratePojoBuilder(intoPackage = "*.builder.generated", withFactoryMethod = "*")
    public GitLabPushTrigger(boolean triggerOnPush, boolean triggerOnMergeRequest, boolean triggerOnAcceptedMergeRequest, boolean triggerOnClosedMergeRequest,
    						 TriggerOpenMergeRequest triggerOpenMergeRequestOnPush, boolean triggerOnNoteRequest, String noteRegex, String userRegex,
    						 boolean skipWorkInProgressMergeRequest, boolean ciSkip,
                             boolean setBuildDescription, boolean addNoteOnMergeRequest, boolean addCiMessage, boolean addVoteOnMergeRequest,
                             boolean acceptMergeRequestOnSuccess, BranchFilterType branchFilterType,
                             String includeBranchesSpec, String excludeBranchesSpec, String sourceBranchRegex, String targetBranchRegex,
                             MergeRequestLabelFilterConfig mergeRequestLabelFilterConfig, String secretToken, boolean triggerOnPipelineEvent,
                             boolean triggerOnApprovedMergeRequest, String pendingBuildName, boolean cancelPendingBuildsOnUpdate) {
        this.triggerOnPush = triggerOnPush;
        this.triggerOnMergeRequest = triggerOnMergeRequest;
        this.triggerOnAcceptedMergeRequest = triggerOnAcceptedMergeRequest;
        this.triggerOnClosedMergeRequest = triggerOnClosedMergeRequest;
        this.triggerOnNoteRequest = triggerOnNoteRequest;
        this.noteRegex = noteRegex;
        this.userRegex = userRegex;
        this.triggerOpenMergeRequestOnPush = triggerOpenMergeRequestOnPush;
        this.triggerOnPipelineEvent = triggerOnPipelineEvent;
        this.ciSkip = ciSkip;
        this.skipWorkInProgressMergeRequest = skipWorkInProgressMergeRequest;
        this.setBuildDescription = setBuildDescription;
        this.addNoteOnMergeRequest = addNoteOnMergeRequest;
        this.addCiMessage = addCiMessage;
        this.addVoteOnMergeRequest = addVoteOnMergeRequest;
        this.branchFilterType = branchFilterType;
        this.includeBranchesSpec = includeBranchesSpec;
        this.excludeBranchesSpec = excludeBranchesSpec;
        this.sourceBranchRegex = sourceBranchRegex;
        this.targetBranchRegex = targetBranchRegex;
        this.acceptMergeRequestOnSuccess = acceptMergeRequestOnSuccess;
        this.mergeRequestLabelFilterConfig = mergeRequestLabelFilterConfig;
        this.secretToken = Secret.fromString(secretToken);
        this.triggerOnApprovedMergeRequest = triggerOnApprovedMergeRequest;
        this.pendingBuildName = pendingBuildName;
        this.cancelPendingBuildsOnUpdate = cancelPendingBuildsOnUpdate;

        initializeTriggerHandler();
        initializeBranchFilter();
        initializeMergeRequestLabelFilter();
    }

    @DataBoundConstructor
    public GitLabPushTrigger() { }

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void migrateJobs() throws IOException {
        GitLabPushTrigger.DescriptorImpl oldConfig = Trigger.all().get(GitLabPushTrigger.DescriptorImpl.class);
        if (!oldConfig.jobsMigrated) {
            GitLabConnectionConfig gitLabConfig = (GitLabConnectionConfig) Jenkins.getInstance().getDescriptor(GitLabConnectionConfig.class);
            gitLabConfig.getConnections().add(new GitLabConnection(
                oldConfig.gitlabHostUrl,
                    oldConfig.gitlabHostUrl,
                    oldConfig.gitlabApiToken,
                "autodetect",
                    oldConfig.ignoreCertificateErrors,
                    10,
                    10));

            String defaultConnectionName = gitLabConfig.getConnections().get(0).getName();
            for (AbstractProject<?, ?> project : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                GitLabPushTrigger trigger = project.getTrigger(GitLabPushTrigger.class);
                if (trigger != null) {
                    if (trigger.addCiMessage) {
                        project.getPublishersList().add(new GitLabCommitStatusPublisher("jenkins", false));
                    }
                    project.addProperty(new GitLabConnectionProperty(defaultConnectionName));
                    project.save();
                }
            }
            gitLabConfig.save();
            oldConfig.jobsMigrated = true;
            oldConfig.save();
        }
        if (!oldConfig.jobsMigrated2) {
            for (AbstractProject<?, ?> project : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                GitLabPushTrigger trigger = project.getTrigger(GitLabPushTrigger.class);
                if (trigger != null) {
                    if (trigger.addNoteOnMergeRequest) {
                        project.getPublishersList().add(new GitLabMessagePublisher());
                    }
                    if (trigger.addVoteOnMergeRequest) {
                        project.getPublishersList().add(new GitLabVotePublisher());
                    }
                    if (trigger.acceptMergeRequestOnSuccess) {
                        project.getPublishersList().add(new GitLabAcceptMergeRequestPublisher());
                    }
                    project.save();
                }
            }
            oldConfig.jobsMigrated2 = true;
            oldConfig.save();
        }
    }

    public boolean getTriggerOnPush() {
        return triggerOnPush;
    }

    @Override
    public boolean getTriggerOnMergeRequest() {
        return triggerOnMergeRequest;
    }

    @Override
    public boolean isTriggerOnAcceptedMergeRequest() {
        return triggerOnAcceptedMergeRequest;
    }

    @Override
    public boolean isTriggerOnApprovedMergeRequest() {
		return triggerOnApprovedMergeRequest;
	}    
    
    @Override
    public boolean isTriggerOnClosedMergeRequest() {
        return triggerOnClosedMergeRequest;
    }

    public boolean getTriggerOnNoteRequest() {
        return triggerOnNoteRequest;
    }

    public boolean getTriggerOnPipelineEvent() { return triggerOnPipelineEvent; }

    public String getNoteRegex() {
        return this.noteRegex == null ? "" : this.noteRegex;
    }

    public String getUserRegex() {
        return this.userRegex == null ? "" : this.userRegex;
    }

    @Override
    public TriggerOpenMergeRequest getTriggerOpenMergeRequestOnPush() {
        return triggerOpenMergeRequestOnPush;
    }

    public boolean getSetBuildDescription() {
        return setBuildDescription;
    }

    public boolean getCiSkip() {
        return ciSkip;
    }

    @Override
    public boolean isSkipWorkInProgressMergeRequest() {
        return skipWorkInProgressMergeRequest;
    }

    public BranchFilterType getBranchFilterType() {
        return branchFilterType;
    }

    public String getIncludeBranchesSpec() {
        return includeBranchesSpec;
    }

    public String getExcludeBranchesSpec() {
        return excludeBranchesSpec;
    }

    public String getSourceBranchRegex() {
        return sourceBranchRegex;
    }

    public String getTargetBranchRegex() {
        return targetBranchRegex;
    }

    public MergeRequestLabelFilterConfig getMergeRequestLabelFilterConfig() {
        return mergeRequestLabelFilterConfig;
    }

    public String getSecretToken() {
        return secretToken == null ? null : secretToken.getPlainText();
    }

    public String getPendingBuildName() {
        return pendingBuildName;
    }

    @Override
    public boolean getCancelPendingBuildsOnUpdate() {
        return this.cancelPendingBuildsOnUpdate;
    }

    @DataBoundSetter
    public void setTriggerOnPush(boolean triggerOnPush) {
        this.triggerOnPush = triggerOnPush;
    }
    
    @DataBoundSetter
    public void setTriggerOnApprovedMergeRequest(boolean triggerOnApprovedMergeRequest) {
        this.triggerOnApprovedMergeRequest = triggerOnApprovedMergeRequest;
    }

    @DataBoundSetter
    public void setTriggerOnMergeRequest(boolean triggerOnMergeRequest) {
        this.triggerOnMergeRequest = triggerOnMergeRequest;
    }

    @DataBoundSetter
    public void setTriggerOnAcceptedMergeRequest(boolean triggerOnAcceptedMergeRequest) {
        this.triggerOnAcceptedMergeRequest = triggerOnAcceptedMergeRequest;
    }

    @DataBoundSetter
    public void setTriggerOnClosedMergeRequest(boolean triggerOnClosedMergeRequest) {
        this.triggerOnClosedMergeRequest = triggerOnClosedMergeRequest;
    }

    @DataBoundSetter
    public void setTriggerOpenMergeRequestOnPush(TriggerOpenMergeRequest triggerOpenMergeRequestOnPush) {
        this.triggerOpenMergeRequestOnPush = triggerOpenMergeRequestOnPush;
    }

    @DataBoundSetter
    public void setTriggerOnNoteRequest(boolean triggerOnNoteRequest) {
        this.triggerOnNoteRequest = triggerOnNoteRequest;
    }

    @DataBoundSetter
    public void setNoteRegex(String noteRegex) {
        this.noteRegex = noteRegex;
    }

    @DataBoundSetter
    public void setUserRegex(String userRegex) {
        this.userRegex = userRegex;
    }

    @DataBoundSetter
    public void setCiSkip(boolean ciSkip) {
        this.ciSkip = ciSkip;
    }

    @DataBoundSetter
    public void setSkipWorkInProgressMergeRequest(boolean skipWorkInProgressMergeRequest) {
        this.skipWorkInProgressMergeRequest = skipWorkInProgressMergeRequest;
    }

    @DataBoundSetter
    public void setSetBuildDescription(boolean setBuildDescription) {
        this.setBuildDescription = setBuildDescription;
    }

    @DataBoundSetter
    public void setAddNoteOnMergeRequest(boolean addNoteOnMergeRequest) {
        this.addNoteOnMergeRequest = addNoteOnMergeRequest;
    }

    @DataBoundSetter
    public void setAddCiMessage(boolean addCiMessage) {
        this.addCiMessage = addCiMessage;
    }

    @DataBoundSetter
    public void setAddVoteOnMergeRequest(boolean addVoteOnMergeRequest) {
        this.addVoteOnMergeRequest = addVoteOnMergeRequest;
    }

    @DataBoundSetter
    public void setBranchFilterName(String branchFilterName) {
        this.branchFilterName = branchFilterName;
    }

    @DataBoundSetter
    public void setBranchFilterType(BranchFilterType branchFilterType) {
        this.branchFilterType = branchFilterType;
    }

    @DataBoundSetter
    public void setIncludeBranchesSpec(String includeBranchesSpec) {
        this.includeBranchesSpec = includeBranchesSpec;
    }

    @DataBoundSetter
    public void setExcludeBranchesSpec(String excludeBranchesSpec) {
        this.excludeBranchesSpec = excludeBranchesSpec;
    }

    @DataBoundSetter
    public void setSourceBranchRegex(String sourceBranchRegex) {
        this.sourceBranchRegex = sourceBranchRegex;
    }

    @DataBoundSetter
    public void setTargetBranchRegex(String targetBranchRegex) {
        this.targetBranchRegex = targetBranchRegex;
    }

    @DataBoundSetter
    public void setMergeRequestLabelFilterConfig(MergeRequestLabelFilterConfig mergeRequestLabelFilterConfig) {
        this.mergeRequestLabelFilterConfig = mergeRequestLabelFilterConfig;
    }

    @DataBoundSetter
    public void setSecretToken(String secretToken) {
        this.secretToken = Secret.fromString(secretToken);
    }

    @DataBoundSetter
    public void setAcceptMergeRequestOnSuccess(boolean acceptMergeRequestOnSuccess) {
        this.acceptMergeRequestOnSuccess = acceptMergeRequestOnSuccess;
    }

    @DataBoundSetter
    public void setTriggerOnPipelineEvent(boolean triggerOnPipelineEvent) {
        this.triggerOnPipelineEvent = triggerOnPipelineEvent;
    }

    @DataBoundSetter
    public void setPendingBuildName(String pendingBuildName) {
        this.pendingBuildName = pendingBuildName;
    }

    @DataBoundSetter
    public void setCancelPendingBuildsOnUpdate(boolean cancelPendingBuildsOnUpdate) {
        this.cancelPendingBuildsOnUpdate = cancelPendingBuildsOnUpdate;
    }

    // executes when the Trigger receives a push request
    public void onPost(final PushHook hook) {
        if (branchFilter == null) {
            initializeBranchFilter();
        }
        if (mergeRequestLabelFilter == null) {
            initializeMergeRequestLabelFilter();
        }
        if (pushHookTriggerHandler == null) {
            initializeTriggerHandler();
        }
        pushHookTriggerHandler.handle(job, hook, ciSkip, branchFilter, mergeRequestLabelFilter);
    }

    // executes when the Trigger receives a merge request
    public void onPost(final MergeRequestHook hook) {
        if (branchFilter == null) {
            initializeBranchFilter();
        }
        if (mergeRequestLabelFilter == null) {
            initializeMergeRequestLabelFilter();
        }
        if (mergeRequestHookTriggerHandler == null) {
            initializeTriggerHandler();
        }
        mergeRequestHookTriggerHandler.handle(job, hook, ciSkip, branchFilter, mergeRequestLabelFilter);
    }

    // executes when the Trigger receives a note request
    public void onPost(final NoteHook hook) {
        if (branchFilter == null) {
            initializeBranchFilter();
        }
        if (mergeRequestLabelFilter == null) {
            initializeMergeRequestLabelFilter();
        }
        if (noteHookTriggerHandler == null) {
            initializeTriggerHandler();
        }
        noteHookTriggerHandler.handle(job, hook, ciSkip, branchFilter, mergeRequestLabelFilter);
    }

    // executes when the Trigger receives a pipeline event
    public void onPost(final PipelineHook hook) {
        if (branchFilter == null) {
            initializeBranchFilter();
        }
        if (pipelineTriggerHandler == null) {
            initializeTriggerHandler();
        }
        pipelineTriggerHandler.handle(job, hook, ciSkip, branchFilter, mergeRequestLabelFilter);
    }

    private void initializeTriggerHandler() {
		mergeRequestHookTriggerHandler = newMergeRequestHookTriggerHandler(this);
        noteHookTriggerHandler = newNoteHookTriggerHandler(triggerOnNoteRequest, noteRegex, userRegex);
        pushHookTriggerHandler = newPushHookTriggerHandler(triggerOnPush, triggerOpenMergeRequestOnPush, skipWorkInProgressMergeRequest);
        pipelineTriggerHandler = newPipelineHookTriggerHandler(triggerOnPipelineEvent);
    }

    private void initializeBranchFilter() {
        branchFilter = BranchFilterFactory.newBranchFilter(branchFilterConfig()
                .withIncludeBranchesSpec(includeBranchesSpec)
                .withExcludeBranchesSpec(excludeBranchesSpec)
                .withSourceBranchRegex(sourceBranchRegex)
                .withTargetBranchRegex(targetBranchRegex)
                .build(branchFilterType));
    }

    private void initializeMergeRequestLabelFilter() {
        mergeRequestLabelFilter = MergeRequestLabelFilterFactory.newMergeRequestLabelFilter(mergeRequestLabelFilterConfig);
    }

    @Override
    protected Object readResolve() throws ObjectStreamException {
        if (branchFilterType == null) {
            branchFilterType = StringUtils.isNotBlank(branchFilterName) ? BranchFilterType.valueOf(branchFilterName) : BranchFilterType.All;
        }
        initializeTriggerHandler();
        initializeBranchFilter();
        initializeMergeRequestLabelFilter();
        return super.readResolve();
    }

    public static GitLabPushTrigger getFromJob(Job<?, ?> job) {
        GitLabPushTrigger trigger = null;
        if (job instanceof ParameterizedJobMixIn.ParameterizedJob) {
            ParameterizedJobMixIn.ParameterizedJob p = (ParameterizedJobMixIn.ParameterizedJob) job;
            for (Trigger t : p.getTriggers().values()) {
                if (t instanceof GitLabPushTrigger) {
                    trigger = (GitLabPushTrigger) t;
                }
            }
        }
        return trigger;
    }

    @Extension
    @Symbol("gitlab")
    public static class DescriptorImpl extends TriggerDescriptor {

        private transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(Jenkins.MasterComputer.threadPoolForRemoting);
        private boolean jobsMigrated = false;
        private boolean jobsMigrated2 = false;
        private String gitlabApiToken;
        private String gitlabHostUrl = "";
        private boolean ignoreCertificateErrors = false;

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof Job
                    && SCMTriggerItems.asSCMTriggerItem(item) != null
                    && item instanceof ParameterizedJobMixIn.ParameterizedJob;
        }

        @Override
        public String getDisplayName() {
            Job<?, ?> project = retrieveCurrentJob();
            if (project != null) {
                try {
                    return "Build when a change is pushed to GitLab. GitLab webhook URL: " + retrieveProjectUrl(project);
                } catch (IllegalStateException e) {
                    // nothing to do
                }
            }
            return "Build when a change is pushed to GitLab, unknown URL";
        }

        private StringBuilder retrieveProjectUrl(Job<?, ?> project) {
            return new StringBuilder()
                    .append(Jenkins.getInstance().getRootUrl())
                    .append(GitLabWebHook.WEBHOOK_URL)
                    .append(retrieveParentUrl(project))
                    .append('/').append(Util.rawEncode(project.getName()));
        }

        private StringBuilder retrieveParentUrl(Item item) {
            if (item.getParent() instanceof Item) {
                Item parent = (Item) item.getParent();
                return retrieveParentUrl(parent).append('/').append(Util.rawEncode(parent.getName()));
            } else {
                return new StringBuilder();
            }
        }

        private Job<?, ?> retrieveCurrentJob() {
            StaplerRequest request = Stapler.getCurrentRequest();
            if (request != null) {
                Ancestor ancestor = request.findAncestor(Job.class);
                return ancestor == null ? null : (Job<?, ?>) ancestor.getObject();
            }
            return null;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }

        public ListBoxModel doFillTriggerOpenMergeRequestOnPushItems(@QueryParameter String triggerOpenMergeRequestOnPush) {
            return new ListBoxModel(new Option("Never", "never", triggerOpenMergeRequestOnPush.matches("never")),
                    new Option("On push to source branch", "source", triggerOpenMergeRequestOnPush.matches("source")),
                    new Option("On push to source or target branch", "both", triggerOpenMergeRequestOnPush.matches("both")));
        }

        public AutoCompletionCandidates doAutoCompleteIncludeBranchesSpec(@AncestorInPath final Job<?, ?> job, @QueryParameter final String value) {
            return ProjectBranchesProvider.instance().doAutoCompleteBranchesSpec(job, value);
        }

        public AutoCompletionCandidates doAutoCompleteExcludeBranchesSpec(@AncestorInPath final Job<?, ?> job, @QueryParameter final String value) {
            return ProjectBranchesProvider.instance().doAutoCompleteBranchesSpec(job, value);
        }

        public FormValidation doCheckIncludeBranchesSpec(@AncestorInPath final Job<?, ?> project, @QueryParameter final String value) {
            return ProjectBranchesProvider.instance().doCheckBranchesSpec(project, value);
        }

        public FormValidation doCheckExcludeBranchesSpec(@AncestorInPath final Job<?, ?> project, @QueryParameter final String value) {
            return ProjectBranchesProvider.instance().doCheckBranchesSpec(project, value);
        }

        public AutoCompletionCandidates doAutoCompleteIncludeMergeRequestLabels(@AncestorInPath final Job<?, ?> job, @QueryParameter final String value) {
            return ProjectLabelsProvider.instance().doAutoCompleteLabels(job, value);
        }

        public AutoCompletionCandidates doAutoCompleteExcludeMergeRequestLabels(@AncestorInPath final Job<?, ?> job, @QueryParameter final String value) {
            return ProjectLabelsProvider.instance().doAutoCompleteLabels(job, value);
        }

        public FormValidation doCheckIncludeMergeRequestLabels(@AncestorInPath final Job<?, ?> project, @QueryParameter final String value) {
            return ProjectLabelsProvider.instance().doCheckLabels(project, value);
        }

        public FormValidation doCheckExcludeMergeRequestLabels(@AncestorInPath final Job<?, ?> project, @QueryParameter final String value) {
            return ProjectLabelsProvider.instance().doCheckLabels(project, value);
        }

        public void doGenerateSecretToken(@AncestorInPath final Job<?, ?> project, StaplerResponse response) {
            byte[] random = new byte[16];   // 16x8=128bit worth of randomness, since we use md5 digest as the API token
            RANDOM.nextBytes(random);
            String secretToken = Util.toHexString(random);
            response.setHeader("script", "document.getElementById('secretToken').value='" + secretToken + "'");
        }

        public void doClearSecretToken(@AncestorInPath final Job<?, ?> project, StaplerResponse response) {;
            response.setHeader("script", "document.getElementById('secretToken').value=''");
        }
    }
}
