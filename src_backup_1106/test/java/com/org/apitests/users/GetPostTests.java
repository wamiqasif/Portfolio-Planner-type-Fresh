package com.org.apitests.users;

import com.org.apiautomation.models.response.PostResponse;
import com.org.apiautomation.services.PostService;
import com.org.apitests.base.BaseTest;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

/**
 * GET examples against the sample CRUD resource ({@link PostService}).
 *
 * <p>Group tags on each method are the axis suites filter on:
 * {@code smoke}/{@code sanity}/{@code regression} pick the tier, {@code
 * users} picks the business module, {@code get}/{@code positive}/{@code
 * negative} allow ad-hoc cross-cutting runs (e.g. "every negative test").
 * Broader tiers include narrower ones' critical tests - a method tagged
 * {@code smoke} is also tagged {@code sanity} and {@code regression}, since
 * "runs in regression" should always be true of "runs in smoke."
 */
public class GetPostTests extends BaseTest {

    private final PostService postService = new PostService();

    @Test(groups = {"smoke", "sanity", "regression", "users", "get", "positive"},
            description = "The one non-negotiable GET check: a known-valid id returns 200 with the expected fields.")
    public void getPostById_withValidId_returns200WithMatchingPost() {
        Response response = postService.getPostById(1);

        Assert.assertEquals(response.statusCode(), 200);
        Assert.assertEquals(response.contentType(), "application/json; charset=utf-8");

        PostResponse post = response.as(PostResponse.class);
        Assert.assertEquals(post.id(), 1);
        Assert.assertTrue(post.userId() > 0, "userId should be a positive owner id");
        Assert.assertFalse(post.title().isBlank(), "title should not be blank");
        Assert.assertFalse(post.body().isBlank(), "body should not be blank");
    }

    @Test(groups = {"sanity", "regression", "users", "get", "positive"},
            description = "Structural contract check via JSON Schema, not just field-by-field assertions.")
    public void getPostById_responseMatchesSchema() {
        Response response = postService.getPostById(1);

        response.then().assertThat()
                .body(JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/post-schema.json"));
    }

    @DataProvider(name = "validPostIds")
    public Object[][] validPostIds() {
        // Sparse sample across the known-valid range (this API always has
        // exactly 100 posts, ids 1-100) rather than all 100 - enough to
        // catch an id-specific bug without 100x-ing this test's cost.
        return new Object[][]{{1}, {2}, {50}, {100}};
    }

    @Test(dataProvider = "validPostIds", groups = {"regression", "users", "get", "positive"},
            description = "The single-resource GET path holds across the full valid id range, not just id=1.")
    public void getPostById_dataDriven_returnsRequestedId(int id) {
        Response response = postService.getPostById(id);

        Assert.assertEquals(response.statusCode(), 200);
        Assert.assertEquals(response.as(PostResponse.class).id(), id);
    }

    @Test(groups = {"sanity", "regression", "users", "get", "negative"},
            description = "A genuinely nonexistent id returns 404 with an empty JSON object - verified against the real API, not assumed.")
    public void getPostById_withNonExistentId_returns404WithEmptyBody() {
        Response response = postService.getPostById(9999);

        Assert.assertEquals(response.statusCode(), 404);
        Assert.assertEquals(response.asString().trim(), "{}");
    }

    @DataProvider(name = "invalidIdFormats")
    public Object[][] invalidIdFormats() {
        return new Object[][]{{"abc"}, {"0"}, {"-1"}, {"99999"}};
    }

    @Test(dataProvider = "invalidIdFormats", groups = {"regression", "users", "get", "negative"},
            description = "Non-numeric, zero, negative, and out-of-range ids all resolve to 404 - verified live, not guessed.")
    public void getPostById_withInvalidIdFormats_returns404(String invalidId) {
        Response response = postService.getPostById(invalidId);

        Assert.assertEquals(response.statusCode(), 404);
    }

    @Test(groups = {"regression", "users", "get", "positive"},
            description = "Query-param filtering (?userId=) narrows the collection - exercises RequestSpecification.queryParam via PostService.")
    public void getPostsByUserId_returnsOnlyMatchingPosts() {
        int totalCount = postService.getAllPosts().jsonPath().getList("$").size();
        Response filtered = postService.getPostsByUserId(1);

        Assert.assertEquals(filtered.statusCode(), 200);
        List<Integer> userIds = filtered.jsonPath().getList("userId", Integer.class);
        Assert.assertTrue(userIds.stream().allMatch(id -> id == 1), "every returned post should belong to userId=1");
        Assert.assertTrue(userIds.size() < totalCount, "the filtered collection should be smaller than the full one");
    }
}
