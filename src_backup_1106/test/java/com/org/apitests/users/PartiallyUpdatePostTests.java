package com.org.apitests.users;

import com.org.apiautomation.models.response.PostResponse;
import com.org.apiautomation.services.PostService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * PATCH (partial update) examples against the sample CRUD resource
 * ({@link PostService}).
 */
public class PartiallyUpdatePostTests extends BaseTest {

    private final PostService postService = new PostService();

    @Test(groups = {"smoke", "sanity", "regression", "users", "patch", "positive"},
            description = "A partial update touches only the submitted field(s); untouched fields keep their prior value.")
    public void patchPost_withPartialPayload_returns200AndMergesFields() {
        Response original = postService.getPostById(1);
        String originalBody = original.as(PostResponse.class).body();

        Response response = postService.partiallyUpdatePost(1, Map.of("title", "Patched Title Only"));

        Assert.assertEquals(response.statusCode(), 200);
        PostResponse patched = response.as(PostResponse.class);
        Assert.assertEquals(patched.title(), "Patched Title Only");
        Assert.assertEquals(patched.body(), originalBody, "an untouched field should be unaffected by a partial update");
    }

    @Test(groups = {"regression", "users", "patch", "negative"},
            description = "PATCH on a nonexistent id returns 200 (fake success), unlike PUT on the same id, which returns 500 - "
                    + "verified live. Documenting this asymmetry explicitly: it's a real quirk of this fake API's handlers, not an "
                    + "inconsistency in this test suite.")
    public void patchPost_withNonExistentId_returns200DueToFakeApiBehavior() {
        Response response = postService.partiallyUpdatePost(9999, Map.of("title", "irrelevant"));

        Assert.assertEquals(response.statusCode(), 200);
    }
}
