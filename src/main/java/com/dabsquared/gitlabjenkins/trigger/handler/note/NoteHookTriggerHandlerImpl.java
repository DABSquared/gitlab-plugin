package com.dabsquared.gitlabjenkins.trigger.handler.note;

import com.dabsquared.gitlabjenkins.cause.CauseData;
import com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty;
import com.dabsquared.gitlabjenkins.gitlab.api.GitLabClient;
import com.dabsquared.gitlabjenkins.gitlab.hook.model.NoteHook;
import com.dabsquared.gitlabjenkins.publisher.GitLabMessagePublisher;
import com.dabsquared.gitlabjenkins.trigger.exception.NoRevisionToBuildException;
import com.dabsquared.gitlabjenkins.trigger.filter.BranchFilter;
import com.dabsquared.gitlabjenkins.trigger.filter.MergeRequestLabelFilter;
import com.dabsquared.gitlabjenkins.trigger.handler.AbstractWebHookTriggerHandler;
import hudson.model.Job;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.RevisionParameterAction;
import org.apache.commons.lang.StringUtils;

import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.dabsquared.gitlabjenkins.cause.CauseDataBuilder.causeData;
import static com.dabsquared.gitlabjenkins.trigger.handler.builder.generated.BuildStatusUpdateBuilder.buildStatusUpdate;

/**
 * @author Nikolay Ustinov
 */
class NoteHookTriggerHandlerImpl extends AbstractWebHookTriggerHandler<NoteHook> implements NoteHookTriggerHandler {

    private static final Logger LOGGER = Logger.getLogger(NoteHookTriggerHandlerImpl.class.getName());

    private final String noteRegex;

    private final String userRegex;

    NoteHookTriggerHandlerImpl(String noteRegex, String userRegex) {
        this.noteRegex = noteRegex;
        this.userRegex = userRegex;
    }

    @Override
    public void handle(Job<?, ?> job, NoteHook hook, boolean ciSkip, BranchFilter branchFilter, MergeRequestLabelFilter mergeRequestLabelFilter) {
        if (isValidTriggerPhrase(hook.getObjectAttributes().getNote()) && isAllowedUser(hook.getUser().getUsername())) {
            super.handle(job, hook, ciSkip, branchFilter, mergeRequestLabelFilter);
        }
    }

    @Override
    protected boolean isCiSkip(NoteHook hook) {
        return hook.getMergeRequest() != null
                && hook.getMergeRequest().getDescription() != null
                && hook.getMergeRequest().getDescription().contains("[ci-skip]");
    }

    @Override
    protected String getSourceBranch(NoteHook hook) {
        return hook.getMergeRequest() == null ? null : hook.getMergeRequest().getSourceBranch();
    }

    @Override
    protected String getTargetBranch(NoteHook hook) {
        return hook.getMergeRequest() == null ? null : hook.getMergeRequest().getTargetBranch();
    }

    @Override
    protected String getTriggerType() {
        return "note";
    }

    @Override
    protected CauseData retrieveCauseData(NoteHook hook) {
        return causeData()
                .withActionType(CauseData.ActionType.NOTE)
                .withSourceProjectId(hook.getMergeRequest().getSourceProjectId())
                .withTargetProjectId(hook.getMergeRequest().getTargetProjectId())
                .withBranch(hook.getMergeRequest().getSourceBranch())
                .withSourceBranch(hook.getMergeRequest().getSourceBranch())
                .withUserName(hook.getUser().getUsername())
                .withUserEmail(hook.getUser().getEmail())
                .withSourceRepoHomepage(hook.getMergeRequest().getSource().getHomepage())
                .withSourceRepoName(hook.getMergeRequest().getSource().getName())
                .withSourceNamespace(hook.getMergeRequest().getSource().getNamespace())
                .withSourceRepoUrl(hook.getMergeRequest().getSource().getUrl())
                .withSourceRepoSshUrl(hook.getMergeRequest().getSource().getSshUrl())
                .withSourceRepoHttpUrl(hook.getMergeRequest().getSource().getHttpUrl())
                .withMergeRequestTitle(hook.getMergeRequest().getTitle())
                .withMergeRequestDescription(hook.getMergeRequest().getDescription())
                .withMergeRequestId(hook.getMergeRequest().getId())
                .withMergeRequestIid(hook.getMergeRequest().getIid())
                .withMergeRequestTargetProjectId(hook.getMergeRequest().getTargetProjectId())
                .withTargetBranch(hook.getMergeRequest().getTargetBranch())
                .withTargetRepoName(hook.getMergeRequest().getTarget().getName())
                .withTargetNamespace(hook.getMergeRequest().getTarget().getNamespace())
                .withTargetRepoSshUrl(hook.getMergeRequest().getTarget().getSshUrl())
                .withTargetRepoHttpUrl(hook.getMergeRequest().getTarget().getHttpUrl())
                .withTriggeredByUser(hook.getUser().getName())
                .withLastCommit(hook.getMergeRequest().getLastCommit().getId())
                .withTargetProjectUrl(hook.getMergeRequest().getTarget().getWebUrl())
                .withTriggerPhrase(hook.getObjectAttributes().getNote())
                .build();
    }

    @Override
    protected RevisionParameterAction createRevisionParameter(NoteHook hook, GitSCM gitSCM) throws NoRevisionToBuildException {
        return new RevisionParameterAction(retrieveRevisionToBuild(hook), retrieveUrIish(hook));
    }

    @Override
    protected BuildStatusUpdate retrieveBuildStatusUpdate(NoteHook hook) {
        return buildStatusUpdate()
            .withProjectId(hook.getMergeRequest().getSourceProjectId())
            .withSha(hook.getMergeRequest().getLastCommit().getId())
            .withRef(hook.getMergeRequest().getSourceBranch())
            .build();
    }

    private String retrieveRevisionToBuild(NoteHook hook) throws NoRevisionToBuildException {
        if (hook.getMergeRequest() != null
                && hook.getMergeRequest().getLastCommit() != null
                && hook.getMergeRequest().getLastCommit().getId() != null) {

            return hook.getMergeRequest().getLastCommit().getId();
        } else {
            throw new NoRevisionToBuildException();
        }
    }

    private boolean isValidTriggerPhrase(String note) {
        if (StringUtils.isEmpty(this.noteRegex)) {
            return false;
        }
        final Pattern pattern = Pattern.compile(this.noteRegex);
        return pattern.matcher(note).matches();
    }

    private boolean isAllowedUser(String user) {
        if (StringUtils.isEmpty(this.userRegex)) {
            return true;
        }

        final Pattern pattern = Pattern.compile(this.userRegex);
        return pattern.matcher(user).matches();
    }
}
