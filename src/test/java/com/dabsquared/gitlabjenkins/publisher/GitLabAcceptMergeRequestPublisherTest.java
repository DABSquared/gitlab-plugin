package com.dabsquared.gitlabjenkins.publisher;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.dabsquared.gitlabjenkins.connection.GitLabConnection;
import com.dabsquared.gitlabjenkins.connection.GitLabConnectionConfig;
import com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty;
import hudson.Launcher;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.plugins.git.util.BuildData;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author Nikolay Ustinov
 */
public class GitLabAcceptMergeRequestPublisherTest {

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
    public void matrixAggregatable() throws UnsupportedEncodingException, InterruptedException, IOException {
        AbstractBuild build = mock(AbstractBuild.class);
        AbstractProject project = mock(MatrixConfiguration.class);
        GitLabCommitStatusPublisher publisher = mock(GitLabCommitStatusPublisher.class);
        MatrixBuild parentBuild = mock(MatrixBuild.class);

        when(build.getParent()).thenReturn(project);
        when(publisher.createAggregator(any(MatrixBuild.class), any(Launcher.class), any(BuildListener.class))).thenCallRealMethod();
        when(publisher.perform(any(AbstractBuild.class), any(Launcher.class), any(BuildListener.class))).thenReturn(true);

        MatrixAggregator aggregator = publisher.createAggregator(parentBuild, null, listener);
        aggregator.startBuild();
        aggregator.endBuild();
        verify(publisher).perform(parentBuild, null, listener);
    }

    @Test
    public void success() throws IOException, InterruptedException {
        Integer buildNumber = 1;
        Integer projectId = 3;
        Integer mergeRequestId = 1;
        AbstractBuild build = mockBuild("/build/123", GIT_LAB_CONNECTION, Result.SUCCESS, buildNumber);

        HttpRequest[] requests = new HttpRequest[] {
            prepareAcceptMergeRequestWithSuccessResponse(projectId, mergeRequestId)
        };

        GitLabAcceptMergeRequestPublisher publisher = spy(new GitLabAcceptMergeRequestPublisher());
        doReturn(projectId).when(publisher).getProjectId(build);
        doReturn(mergeRequestId).when(publisher).getMergeRequestId(build);
        publisher.perform(build, null, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void failed() throws IOException, InterruptedException {
        Integer buildNumber = 1;
        Integer projectId = 3;
        Integer mergeRequestId = 1;
        AbstractBuild build = mockBuild("/build/123", GIT_LAB_CONNECTION, Result.FAILURE, buildNumber);

        GitLabAcceptMergeRequestPublisher publisher = spy(new GitLabAcceptMergeRequestPublisher());
        doReturn(projectId).when(publisher).getProjectId(build);
        doReturn(mergeRequestId).when(publisher).getMergeRequestId(build);
        publisher.perform(build, null, listener);

        mockServerClient.verifyZeroInteractions();
    }

    private HttpRequest prepareAcceptMergeRequestWithSuccessResponse(Integer projectId, Integer mergeRequestId) throws UnsupportedEncodingException {
        HttpRequest updateCommitStatus = prepareAcceptMergeRequest(projectId, mergeRequestId);
        mockServerClient.when(updateCommitStatus).respond(response().withStatusCode(200));
        return updateCommitStatus;
    }

    private HttpRequest prepareAcceptMergeRequest(Integer projectId, Integer mergeRequestId) throws UnsupportedEncodingException {
        return request()
                .withPath("/gitlab/api/v4/projects/" + projectId + "/merge_requests/" + mergeRequestId + "/merge")
                .withMethod("PUT")
                .withHeader("PRIVATE-TOKEN", "secret")
                .withBody("merge_commit_message=Merge+Request+accepted+by+jenkins+build+success&should_remove_source_branch=false");
    }

    private AbstractBuild mockBuild(String buildUrl, String gitLabConnection, Result result, Integer buildNumber, String... remoteUrls) {
        AbstractBuild build = mock(AbstractBuild.class);
        BuildData buildData = mock(BuildData.class);
        when(buildData.getRemoteUrls()).thenReturn(new HashSet<>(Arrays.asList(remoteUrls)));
        when(build.getAction(BuildData.class)).thenReturn(buildData);
        when(build.getResult()).thenReturn(result);
        when(build.getUrl()).thenReturn(buildUrl);
        when(build.getResult()).thenReturn(result);
        when(build.getUrl()).thenReturn(buildUrl);
        when(build.getNumber()).thenReturn(buildNumber);

        AbstractProject<?, ?> project = mock(AbstractProject.class);
        when(project.getProperty(GitLabConnectionProperty.class)).thenReturn(new GitLabConnectionProperty(gitLabConnection));
        when(build.getProject()).thenReturn(project);
        return build;
    }
}
