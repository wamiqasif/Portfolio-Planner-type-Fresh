package com.org.apitests.users;

import com.org.apiautomation.services.PostService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * DELETE examples against the sample CRUD resource ({@link PostService}).
 *
 * <p>This fake API's DELETE handler always reports success regardless of
 * whether the id exists or is even numeric - verified live below rather
 * than assumed, and documented as a property of the demo API, not a gap in
 * these tests.
 */
public class DeletePostTests extends BaseTest {

    private final PostService postService = new PostService();

    @Test(groups = {"smoke", "sanity", "regression", "users", "delete", "positive"},
            description = "A valid delete returns 200 with an empty JSON object.")
    public void deletePost_withValidId_returns200WithEmptyBody() {
        Response response = postService.deletePost(1);

        Assert.assertEquals(response.statusCode(), 200);
        Assert.assertEquals(response.asString().trim(), "{}");
    }

    @Test(groups = {"regression", "users", "delete", "negative"},
            description = "Deleting a nonexistent id still returns 200 - this fake API's delete never checks existence first.")
    public void deletePost_withNonExistentId_stillReturns200() {
        Response response = postService.deletePost(9999);

        Assert.assertEquals(response.statusCode(), 200);
    }

    @Test(groups = {"regression", "users", "delete", "negative"},
            description = "Even a non-numeric id doesn't produce a routing error on delete - the loosest of this API's handlers.")
    public void deletePost_withNonNumericId_stillReturns200() {
        Response response = postService.deletePost("abc");

        Assert.assertEquals(response.statusCode(), 200);
    }
}
