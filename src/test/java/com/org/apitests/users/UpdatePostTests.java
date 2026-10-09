package com.org.apitests.users;

import com.org.apiautomation.models.request.CreatePostRequest;
import com.org.apiautomation.models.response.PostResponse;
import com.org.apiautomation.services.PostService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * PUT (full update) examples against the sample CRUD resource ({@link PostService}).
 */
public class UpdatePostTests extends BaseTest {

    private final PostService postService = new PostService();

    @Test(groups = {"smoke", "sanity", "regression", "users", "put", "positive"},
            description = "A valid full update returns 200 and echoes every submitted field.")
    public void updatePost_withValidPayload_returns200AndEchoesUpdatedFields() {
        CreatePostRequest request = new CreatePostRequest("Updated Title", "Updated body content", 2);

        Response response = postService.updatePost(1, request);

        Assert.assertEquals(response.statusCode(), 200);
        PostResponse updated = response.as(PostResponse.class);
        Assert.assertEquals(updated.title(), request.title());
        Assert.assertEquals(updated.body(), request.body());
        Assert.assertEquals(updated.userId(), request.userId());
    }

    @Test(groups = {"sanity", "regression", "users", "put", "negative"},
            description = "PUT on a nonexistent id returns 500, not 404 - a genuinely surprising, verified quirk of this fake API's PUT "
                    + "handler (it dereferences the resource before checking it exists). Asserted explicitly so a real API swap that "
                    + "behaves differently (the correct 404) fails loudly here instead of silently.")
    public void updatePost_withNonExistentId_returns500() {
        CreatePostRequest request = new CreatePostRequest("irrelevant", "irrelevant", 1);

        Response response = postService.updatePost(9999, request);

        Assert.assertEquals(response.statusCode(), 500);
    }
}
