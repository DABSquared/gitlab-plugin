package com.dabsquared.gitlabjenkins.publisher;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.dabsquared.gitlabjenkins.cause.CauseData;
import com.dabsquared.gitlabjenkins.cause.CauseDataBuilder;
import com.dabsquared.gitlabjenkins.cause.GitLabWebHookCause;
import com.dabsquared.gitlabjenkins.cause.CauseData.ActionType;
import com.dabsquared.gitlabjenkins.connection.GitLabConnection;
import com.dabsquared.gitlabjenkins.connection.GitLabConnectionConfig;
import com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty;
import com.dabsquared.gitlabjenkins.gitlab.api.model.BuildState;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.plugins.git.Revision;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.dabsquared.gitlabjenkins.cause.CauseDataBuilder.causeData;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author Robin Müller
 */
public class GitLabCommitStatusPublisherTest {

    private static final String GIT_LAB_CONNECTION = "GitLab";
    private static final String API_TOKEN = "secret";

    @ClassRule
    public static MockServerRule mockServer = new MockServerRule(new Object());

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    private MockServerClient mockServerClient;
    private BuildListener listener;

    @BeforeClass
    public static void setupConnection() throws IOException {
        GitLabConnectionConfig connectionConfig = jenkins.get(GitLabConnectionConfig.class);
        String apiTokenId = "apiTokenId";
        for (CredentialsStore credentialsStore : CredentialsProvider.lookupStores(Jenkins.getInstance())) {
            if (credentialsStore instanceof SystemCredentialsProvider.StoreImpl) {
                List<Domain> domains = credentialsStore.getDomains();
                credentialsStore.addCredentials(domains.get(0),
                                                new StringCredentialsImpl(CredentialsScope.SYSTEM, apiTokenId, "GitLab API Token", Secret.fromString(API_TOKEN)));
            }
        }
        connectionConfig.addConnection(new GitLabConnection(GIT_LAB_CONNECTION, "http://localhost:" + mockServer.getPort() + "/gitlab", apiTokenId, false, 10, 10));
    }

    @Before
    public void setup() {
        listener = new StreamBuildListener(jenkins.createTaskListener().getLogger(), Charset.defaultCharset());
        mockServerClient = new MockServerClient("localhost", mockServer.getPort());
    }

    @After
    public void cleanup() {
        mockServerClient.reset();
    }

    @Test
    public void running() throws UnsupportedEncodingException {
        HttpRequest[] requests = new HttpRequest[] {
            prepareExistsCommitWithSuccessResponse("test/project", "123abc"),
            prepareUpdateCommitStatusWithSuccessResponse("test/project", "123abc", "sourceBranch", jenkins.getInstance().getRootUrl() + "/build/123", BuildState.running)
        };
        AbstractBuild build = mockBuild("123abc", "/build/123", GIT_LAB_CONNECTION, null, "test/project");

        GitLabCommitStatusPublisher publisher = new GitLabCommitStatusPublisher("jenkins", false);
        publisher.prebuild(build, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void runningWithDotInProjectId() throws IOException {
        HttpRequest[] requests = new HttpRequest[] {
            prepareExistsCommitWithSuccessResponse("1", "123abc"),
            prepareUpdateCommitStatusWithSuccessResponse("1", "123abc", "sourceBranch", jenkins.getInstance().getRootUrl() + "/build/123", BuildState.running)
        };
        AbstractBuild build = mockBuild("123abc", "/build/123", GIT_LAB_CONNECTION, null, "test/project.test");

        GitLabCommitStatusPublisher publisher = new GitLabCommitStatusPublisher("jenkins", false);
        publisher.prebuild(build, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void canceled() throws IOException, InterruptedException {
        HttpRequest[] requests = new HttpRequest[] {
                prepareExistsCommitWithSuccessResponse("test/project", "123abc"),
                prepareUpdateCommitStatusWithSuccessResponse("test/project", "123abc", "sourceBranch", jenkins.getInstance().getRootUrl() + "/build/123", BuildState.canceled)
        };
        AbstractBuild build = mockBuild("123abc", "/build/123", GIT_LAB_CONNECTION, Result.ABORTED, "test/project");

        GitLabCommitStatusPublisher publisher = new GitLabCommitStatusPublisher("jenkins", false);
        publisher.perform(build, null, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void success() throws IOException, InterruptedException {
        HttpRequest[] requests = new HttpRequest[] {
                prepareExistsCommitWithSuccessResponse("test/project", "123abc"),
                prepareUpdateCommitStatusWithSuccessResponse("test/project", "123abc", "sourceBranch", jenkins.getInstance().getRootUrl() + "/build/123", BuildState.success)
        };
        AbstractBuild build = mockBuild("123abc", "/build/123", GIT_LAB_CONNECTION, Result.SUCCESS, "test/project");

        GitLabCommitStatusPublisher publisher = new GitLabCommitStatusPublisher("jenkins", false);
        publisher.perform(build, null, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void failed() throws IOException, InterruptedException {
        HttpRequest[] requests = new HttpRequest[] {
                prepareExistsCommitWithSuccessResponse("test/project", "123abc"),
                prepareUpdateCommitStatusWithSuccessResponse("test/project", "123abc", "sourceBranch", jenkins.getInstance().getRootUrl() + "/build/123", BuildState.failed)
        };
        AbstractBuild build = mockBuild("123abc", "/build/123", GIT_LAB_CONNECTION, Result.FAILURE, "test/project");

        GitLabCommitStatusPublisher publisher = new GitLabCommitStatusPublisher("jenkins", false);
        publisher.perform(build, null, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void unstable() throws IOException, InterruptedException {
        HttpRequest[] requests = new HttpRequest[] {
            prepareExistsCommitWithSuccessResponse("test/project", "123abc"),
            prepareUpdateCommitStatusWithSuccessResponse("test/project", "123abc", "sourceBranch", jenkins.getInstance().getRootUrl() + "/build/123", BuildState.failed)
        };
        AbstractBuild build = mockBuild("123abc", "/build/123", GIT_LAB_CONNECTION, Result.UNSTABLE, "test/project");

        GitLabCommitStatusPublisher publisher = new GitLabCommitStatusPublisher("jenkins", false);
        publisher.perform(build, null, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void unstableAsSuccess() throws IOException, InterruptedException {
        HttpRequest[] requests = new HttpRequest[] {
            prepareExistsCommitWithSuccessResponse("test/project", "123abc"),
            prepareUpdateCommitStatusWithSuccessResponse("test/project", "123abc", "sourceBranch", jenkins.getInstance().getRootUrl() + "/build/123", BuildState.success)
        };
        AbstractBuild build = mockBuild("123abc", "/build/123", GIT_LAB_CONNECTION, Result.UNSTABLE, "test/project");

        GitLabCommitStatusPublisher publisher = new GitLabCommitStatusPublisher("jenkins", true);
        publisher.perform(build, null, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void running_multipleRepos() throws UnsupportedEncodingException {
        HttpRequest[] requests = new HttpRequest[] {
                prepareExistsCommitWithSuccessResponse("test/project-1", "123abc"),
                prepareUpdateCommitStatusWithSuccessResponse("test/project-1", "123abc", "sourceBranch", jenkins.getInstance().getRootUrl() + "/build/123", BuildState.running),
                prepareExistsCommitWithSuccessResponse("test/project-2", "123abc"),
                prepareUpdateCommitStatusWithSuccessResponse("test/project-2", "123abc", "sourceBranch", jenkins.getInstance().getRootUrl() + "/build/123", BuildState.running)
        };
        AbstractBuild build = mockBuild("123abc", "/build/123", GIT_LAB_CONNECTION, null, "test/project-1", "test/project-2");

        GitLabCommitStatusPublisher publisher = new GitLabCommitStatusPublisher("jenkins", false);
        publisher.prebuild(build, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void running_commitNotExists() throws UnsupportedEncodingException {
        HttpRequest updateCommitStatus = prepareUpdateCommitStatusWithSuccessResponse("test/project", "123abc", "sourceBranch", jenkins.getInstance().getRootUrl() + "/build/123", BuildState.running);
        AbstractBuild build = mockBuild("123abc", "/build/123", GIT_LAB_CONNECTION, null, "test/project");

        GitLabCommitStatusPublisher publisher = new GitLabCommitStatusPublisher("jenkins", false);
        publisher.prebuild(build, listener);

        mockServerClient.verify(updateCommitStatus, VerificationTimes.exactly(0));
    }

    @Test
    public void running_failToUpdate() throws UnsupportedEncodingException {
        prepareExistsCommitWithSuccessResponse("test/project", "123abc");
        HttpRequest updateCommitStatus = prepareUpdateCommitStatus("test/project", "123abc", "sourceBranch", jenkins.getInstance().getRootUrl() + "/build/123", BuildState.running);
        mockServerClient.when(updateCommitStatus).respond(response().withStatusCode(403));
        AbstractBuild build = mockBuild("123abc", "/build/123", GIT_LAB_CONNECTION, null, "test/project");
        BuildListener buildListener = mock(BuildListener.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(buildListener.getLogger()).thenReturn(new PrintStream(outputStream));

        GitLabCommitStatusPublisher publisher = new GitLabCommitStatusPublisher("jenkins", false);
        publisher.prebuild(build, buildListener);

        assertThat(outputStream.toString(), CoreMatchers.containsString("Failed to update Gitlab commit status for project 'test/project': HTTP 403 Forbidden"));
        mockServerClient.verify(updateCommitStatus);
    }


    private HttpRequest prepareUpdateCommitStatusWithSuccessResponse(String projectId, String sha, String sourceBranch, String targetUrl, BuildState state) throws UnsupportedEncodingException {
        HttpRequest updateCommitStatus = prepareUpdateCommitStatus(projectId, sha, sourceBranch, targetUrl, state);
        mockServerClient.when(updateCommitStatus).respond(response().withStatusCode(200));
        return updateCommitStatus;
    }


    private HttpRequest prepareUpdateCommitStatus(String projectId, String sha, String sourceBranch, String targetUrl, BuildState state) throws UnsupportedEncodingException {
        String ref = (sourceBranch == null)?"":"&ref=" + URLEncoder.encode(sourceBranch, "UTF-8");
        return request()
                .withPath("/gitlab/api/v3/projects/" + URLEncoder.encode(projectId, "UTF-8") + "/statuses/" + sha)
                .withMethod("POST")
                .withHeader("PRIVATE-TOKEN", "secret")
        
                .withBody("state=" + URLEncoder.encode(state.name(), "UTF-8") + ref + "&context=jenkins" + "&target_url=" + URLEncoder.encode(targetUrl, "UTF-8"));
    }

    private HttpRequest prepareExistsCommitWithSuccessResponse(String projectId, String sha) throws UnsupportedEncodingException {
        HttpRequest existsCommit = prepareExistsCommit(projectId, sha);
        mockServerClient.when(existsCommit).respond(response().withStatusCode(200));
        return existsCommit;
    }

    private HttpRequest prepareExistsCommit(String projectId, String sha) throws UnsupportedEncodingException {
        return request()
                .withPath("/gitlab/api/v3/projects/" + URLEncoder.encode(projectId, "UTF-8") + "/repository/commits/" + sha)
                .withMethod("GET")
                .withHeader("PRIVATE-TOKEN", "secret");
    }

    private HttpRequest prepareGetProjectResponse(String projectName, int projectId) throws IOException {
        HttpRequest request= request()
                     .withPath("/gitlab/api/v3/projects/" + URLEncoder.encode(projectName, "UTF-8"))
                     .withMethod("GET")
                   .  withHeader("PRIVATE-TOKEN", "secret");

        HttpResponse response = response().withBody(getSingleProjectJson("GetSingleProject.json",projectName,projectId));

        response.withHeader("Content-Type", "application/json");
        mockServerClient.when(request).respond(response.withStatusCode(200));
        return request;
    }

    private AbstractBuild mockBuild(String sha, String buildUrl, String gitLabConnection, Result result, String... projectIds) {
        AbstractBuild build = mock(AbstractBuild.class);
        Revision revision = mock(Revision.class);
        when(revision.getSha1String()).thenReturn(sha);
        when(build.getResult()).thenReturn(result);
        when(build.getUrl()).thenReturn(buildUrl);
        List<Cause> causes = new ArrayList<Cause>();
        for (String projectId : projectIds) {
            GitLabWebHookCause cause = new GitLabWebHookCause(CauseDataBuilder.causeData()
                    .withActionType(CauseData.ActionType.MERGE)
                    .withSourceProjectId(1)
                    .withTargetProjectId(1)
                    .withBranch("sourceBranch")
                    .withSourceBranch("sourceBranch")
                    .withUserName("")
                    .withSourceRepoHomepage("https://gitlab.org/test")
                    .withSourceRepoName(projectId)
                    .withSourceNamespace("test-namespace")
                    .withSourceRepoUrl("git@gitlab.org:test.git")
                    .withSourceRepoSshUrl("git@gitlab.org:test.git")
                    .withSourceRepoHttpUrl("https://gitlab.org/test.git")
                    .withMergeRequestTitle("Test")
                    .withMergeRequestId(1)
                    .withMergeRequestIid(1)
                    .withTargetBranch("targetBranch")
                    .withTargetRepoName("test")
                    .withTargetNamespace("test-namespace")
                    .withTargetRepoSshUrl("git@gitlab.org:test.git")
                    .withTargetRepoHttpUrl("https://gitlab.org/test.git")
                    .withTriggeredByUser("test")
                    .withLastCommit(sha)
                    .withTargetProjectUrl("https://gitlab.org/test")
                    .build());
            causes.add(cause);
        }
        when(build.getCauses()).thenReturn(causes);
        AbstractProject<?, ?> project = mock(AbstractProject.class);
        when(project.getProperty(GitLabConnectionProperty.class)).thenReturn(new GitLabConnectionProperty(gitLabConnection));
        when(build.getProject()).thenReturn(project);
        EnvVars environment = mock(EnvVars.class);
        when(environment.expand(anyString())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return (String) invocation.getArguments()[0];
            }
        });
        try {
            when(build.getEnvironment(any(TaskListener.class))).thenReturn(environment);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return build;
    }
    private String getSingleProjectJson(String name,String projectNameWithNamespace, int porjectId) throws IOException {
        String nameSpace = projectNameWithNamespace.split("/")[0];
        String projectName = projectNameWithNamespace.split("/")[1];
        return IOUtils.toString(getClass().getResourceAsStream(name))
                 .replace("${projectId}", porjectId + "")
                 .replace("${nameSpace}", nameSpace)
                 .replace("${projectName}", projectName);
    }
}
