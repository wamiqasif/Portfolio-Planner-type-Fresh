package com.org.apitests.users;

import com.org.apiautomation.models.request.CreatePostRequest;
import com.org.apiautomation.models.response.PostResponse;
import com.org.apiautomation.services.PostService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * POST (create) examples against the sample CRUD resource ({@link PostService}).
 *
 * <p>This API is a public fake/mock service: it never performs real
 * server-side validation, so any payload - missing fields, blank strings,
 * whatever - is accepted and echoed back with a generated id. Fabricating
 * a "missing required field returns 400" negative test here would assert
 * something the API doesn't actually do. The negative test below instead
 * targets real, verified behavior: a routing-level rejection.
 */
public class CreatePostTests extends BaseTest {

    private final PostService postService = new PostService();

    @Test(groups = {"smoke", "sanity", "regression", "users", "post", "positive"},
            description = "A valid create returns 201, echoes every submitted field, and includes a generated id.")
    public void createPost_withValidPayload_returns201AndEchoesPayloadWithGeneratedId() {
        CreatePostRequest request = new CreatePostRequest("Sample Title", "Sample body content", 1);

        Response response = postService.createPost(request);

        Assert.assertEquals(response.statusCode(), 201);

        PostResponse created = response.as(PostResponse.class);
        Assert.assertEquals(created.title(), request.title());
        Assert.assertEquals(created.body(), request.body());
        Assert.assertEquals(created.userId(), request.userId());
        Assert.assertTrue(created.id() > 0, "the API should assign a generated id");
    }

    @DataProvider(name = "validCreatePayloads")
    public Object[][] validCreatePayloads() {
        return new Object[][]{
                {new CreatePostRequest("Short", "x", 1)},
                {new CreatePostRequest("A title with punctuation: yes! & why not?", "A longer body. ".repeat(20), 5)},
                {new CreatePostRequest("Unicode éèê title", "body 😀", 10)},
        };
    }

    @Test(dataProvider = "validCreatePayloads", groups = {"regression", "users", "post", "positive"},
            description = "Create holds across varied payload shapes: short/long bodies, punctuation, unicode.")
    public void createPost_dataDriven_withVariousValidPayloads(CreatePostRequest request) {
        Response response = postService.createPost(request);

        Assert.assertEquals(response.statusCode(), 201);
        PostResponse created = response.as(PostResponse.class);
        Assert.assertEquals(created.title(), request.title());
        Assert.assertEquals(created.body(), request.body());
    }

    @Test(groups = {"sanity", "regression", "users", "post", "negative"},
            description = "POSTing to a specific item path (rather than the collection) is not a supported route - verified live, not assumed.")
    public void createPost_toItemPath_returns404() {
        CreatePostRequest request = new CreatePostRequest("irrelevant", "irrelevant", 1);

        Response response = postService.createPostAtItemPath(1, request);

        Assert.assertEquals(response.statusCode(), 404);
    }
}
