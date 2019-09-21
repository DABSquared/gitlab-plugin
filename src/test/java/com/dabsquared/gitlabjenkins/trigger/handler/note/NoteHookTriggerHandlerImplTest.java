package com.dabsquared.gitlabjenkins.trigger.handler.note;

import com.dabsquared.gitlabjenkins.gitlab.hook.model.State;
import com.dabsquared.gitlabjenkins.gitlab.hook.model.User;
import com.dabsquared.gitlabjenkins.trigger.filter.BranchFilterFactory;
import com.dabsquared.gitlabjenkins.trigger.filter.BranchFilterType;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.util.OneShotEvent;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import static com.dabsquared.gitlabjenkins.gitlab.hook.model.builder.generated.CommitBuilder.commit;
import static com.dabsquared.gitlabjenkins.gitlab.hook.model.builder.generated.MergeRequestObjectAttributesBuilder.mergeRequestObjectAttributes;
import static com.dabsquared.gitlabjenkins.gitlab.hook.model.builder.generated.NoteHookBuilder.noteHook;
import static com.dabsquared.gitlabjenkins.gitlab.hook.model.builder.generated.NoteObjectAttributesBuilder.noteObjectAttributes;
import static com.dabsquared.gitlabjenkins.gitlab.hook.model.builder.generated.ProjectBuilder.project;
import static com.dabsquared.gitlabjenkins.gitlab.hook.model.builder.generated.UserBuilder.user;
import static com.dabsquared.gitlabjenkins.trigger.filter.BranchFilterConfig.BranchFilterConfigBuilder.branchFilterConfig;
import static com.dabsquared.gitlabjenkins.trigger.filter.MergeRequestLabelFilterFactory.newMergeRequestLabelFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Nikolay Ustinov
 */
public class NoteHookTriggerHandlerImplTest {

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private NoteHookTriggerHandler noteHookTriggerHandler;

    @Before
    public void setup() {
        noteHookTriggerHandler = new NoteHookTriggerHandlerImpl("ci-run", "test");
    }

    @Test
    public void note_ciSkip() throws IOException, InterruptedException {
        final OneShotEvent buildTriggered = new OneShotEvent();
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                buildTriggered.signal();
                return true;
            }
        });
        Date currentDate = new Date();
        project.setQuietPeriod(0);
        noteHookTriggerHandler.handle(project, noteHook()
                .withObjectAttributes(noteObjectAttributes()
                    .withId(1)
                    .withNote("ci-run")
                    .withAuthorId(1)
                    .withProjectId(1)
                    .withCreatedAt(currentDate)
                    .withUpdatedAt(currentDate)
                    .withUrl("https://gitlab.org/test/merge_requests/1#note_1")
                    .build())
                .withUser(user().withName("test").withUsername("test").withEmail("test@test.com").build())
                .withMergeRequest(mergeRequestObjectAttributes().withDescription("[ci-skip]").build())
                .build(), true, BranchFilterFactory.newBranchFilter(branchFilterConfig().build(BranchFilterType.All)),
                                      newMergeRequestLabelFilter(null));

        buildTriggered.block(10000);
        assertThat(buildTriggered.isSignaled(), is(false));
    }

    @Test
    public void note_build() throws IOException, InterruptedException, GitAPIException, ExecutionException {
        Git.init().setDirectory(tmp.getRoot()).call();
        tmp.newFile("test");
        Git git = Git.open(tmp.getRoot());
        git.add().addFilepattern("test");
        RevCommit commit = git.commit().setMessage("test").call();
        ObjectId head = git.getRepository().resolve(Constants.HEAD);
        String repositoryUrl = tmp.getRoot().toURI().toString();

        final OneShotEvent buildTriggered = new OneShotEvent();
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.setScm(new GitSCM(repositoryUrl));
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                buildTriggered.signal();
                return true;
            }
        });
        Date currentDate = new Date();
        project.setQuietPeriod(0);
        noteHookTriggerHandler.handle(project, noteHook()
                .withUser(user()
                    .withName("test")
                    .withUsername("test")
                    .withEmail("test@test.com")
                    .build())
                .withObjectAttributes(noteObjectAttributes()
                    .withId(1)
                    .withNote("ci-run")
                    .withAuthorId(1)
                    .withProjectId(1)
                    .withCreatedAt(currentDate)
                    .withUpdatedAt(currentDate)
                    .withUrl("https://gitlab.org/test/merge_requests/1#note_1")
                    .build())
                .withMergeRequest(mergeRequestObjectAttributes()
                    .withTargetBranch("refs/heads/" + git.nameRev().add(head).call().get(head))
                    .withState(State.opened)
                    .withIid(1)
                    .withTitle("test")
                    .withTargetProjectId(1)
                    .withSourceProjectId(1)
                    .withSourceBranch("feature")
                    .withTargetBranch("master")
                    .withLastCommit(commit().withAuthor(user().withName("test").build()).withId(commit.getName()).build())
                    .withSource(project()
                        .withName("test")
                        .withNamespace("test-namespace")
                        .withHomepage("https://gitlab.org/test")
                        .withUrl("git@gitlab.org:test.git")
                        .withSshUrl("git@gitlab.org:test.git")
                        .withHttpUrl("https://gitlab.org/test.git")
                        .build())
                    .withTarget(project()
                        .withName("test")
                        .withNamespace("test-namespace")
                        .withHomepage("https://gitlab.org/test")
                        .withUrl("git@gitlab.org:test.git")
                        .withSshUrl("git@gitlab.org:test.git")
                        .withHttpUrl("https://gitlab.org/test.git")
                        .withWebUrl("https://gitlab.org/test.git")
                        .build())
                    .build())
                .build(), true, BranchFilterFactory.newBranchFilter(branchFilterConfig().build(BranchFilterType.All)),
                                      newMergeRequestLabelFilter(null));

        buildTriggered.block(10000);
        assertThat(buildTriggered.isSignaled(), is(true));
    }

    @Test
    public void note_userNotAllowed() throws IOException, InterruptedException, GitAPIException, ExecutionException {
        Git.init().setDirectory(tmp.getRoot()).call();
        tmp.newFile("test");
        Git git = Git.open(tmp.getRoot());
        git.add().addFilepattern("test");
        RevCommit commit = git.commit().setMessage("test").call();
        ObjectId head = git.getRepository().resolve(Constants.HEAD);
        String repositoryUrl = tmp.getRoot().toURI().toString();

        final OneShotEvent buildTriggered = new OneShotEvent();
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.setScm(new GitSCM(repositoryUrl));
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                buildTriggered.signal();
                return true;
            }
        });
        Date currentDate = new Date();
        project.setQuietPeriod(0);
        noteHookTriggerHandler.handle(project, noteHook()
                .withUser(user()
                    .withName("user_not_allow")
                    .withUsername("user_not_allow")
                    .withEmail("user_not_allow@test.com")
                    .build())
                .withObjectAttributes(noteObjectAttributes()
                    .withId(1)
                    .withNote("ci-run")
                    .withAuthorId(1)
                    .withProjectId(1)
                    .withCreatedAt(currentDate)
                    .withUpdatedAt(currentDate)
                    .withUrl("https://gitlab.org/test/merge_requests/1#note_1")
                    .build())
                .withMergeRequest(mergeRequestObjectAttributes()
                    .withTargetBranch("refs/heads/" + git.nameRev().add(head).call().get(head))
                    .withState(State.opened)
                    .withIid(1)
                    .withTitle("test")
                    .withTargetProjectId(1)
                    .withSourceProjectId(1)
                    .withSourceBranch("feature")
                    .withTargetBranch("master")
                    .withLastCommit(commit().withAuthor(user().withName("test").build()).withId(commit.getName()).build())
                    .withSource(project()
                        .withName("test")
                        .withNamespace("test-namespace")
                        .withHomepage("https://gitlab.org/test")
                        .withUrl("git@gitlab.org:test.git")
                        .withSshUrl("git@gitlab.org:test.git")
                        .withHttpUrl("https://gitlab.org/test.git")
                        .build())
                    .withTarget(project()
                        .withName("test")
                        .withNamespace("test-namespace")
                        .withHomepage("https://gitlab.org/test")
                        .withUrl("git@gitlab.org:test.git")
                        .withSshUrl("git@gitlab.org:test.git")
                        .withHttpUrl("https://gitlab.org/test.git")
                        .withWebUrl("https://gitlab.org/test.git")
                        .build())
                    .build())
                .build(), true, BranchFilterFactory.newBranchFilter(branchFilterConfig().build(BranchFilterType.All)),
            newMergeRequestLabelFilter(null));

        buildTriggered.block(10000);
        assertThat(buildTriggered.isSignaled(), is(false));
    }
}
