package com.org.apiautomation.services;

import com.org.apiautomation.core.ApiClient;
import com.org.apiautomation.core.RequestSpecFactory;
import com.org.apiautomation.models.request.CreatePostRequest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * Domain-facing facade over the sample CRUD resource used to demonstrate
 * the framework end to end (GET/POST/PUT/PATCH/DELETE). Tests call this,
 * never {@code ApiClient}/{@code RequestSpecFactory} directly - keeping
 * every HTTP detail (base URI, path shape) in one place instead of
 * scattered across test methods.
 *
 * <p><b>Why {@code https://jsonplaceholder.typicode.com} is a hardcoded
 * constant, not read from {@code ConfigManager}:</b> it's a public demo API
 * used to give this framework's layers something real to exercise, not a
 * per-environment Value Research service - there's no dev/qa/stage/prod
 * variant of it to configure. A genuine business module's service would
 * call {@code RequestSpecFactory.defaultRequestSpec()} with no base URI
 * argument, exactly like this class's methods call it with one.
 *
 * <p><b>Why {@code RequestSpecFactory.unauthenticatedRequestSpec(...)}, not
 * {@code defaultRequestSpec(...)}:</b> {@code defaultRequestSpec} attaches
 * whichever auth mechanism the active environment has configured for the
 * real target API - sending those credentials to a public demo API that
 * needs none of them would be wrong, not just unnecessary.
 *
 * <p><b>Why there's no {@code BaseService}:</b> with exactly one concrete
 * service, there is no shared logic across services yet to extract - same
 * reasoning as why {@code BaseTest} has no {@code BaseApiTest} on top of it.
 *
 * <p><b>Why some methods have a {@code String id} overload alongside
 * {@code int id}:</b> negative tests deliberately need to send malformed
 * path values ({@code "abc"}, {@code "-1"}) that don't fit in an {@code
 * int} parameter at all. The {@code String} overload exists specifically so
 * those tests can still go through this facade instead of reaching around
 * it to build a request by hand.
 *
 * <p><b>Why every method returns the raw {@code Response}, not a parsed
 * model:</b> consistency with {@code ApiClient}'s own contract, and
 * negative tests need the same access to raw status/headers/body that
 * positive tests do. Callers deserialize via {@code
 * response.as(PostResponse.class)} when they want the typed object.
 */
public final class PostService {

    private static final String BASE_URI = "https://jsonplaceholder.typicode.com";

    public Response getAllPosts() {
        return new ApiClient().get("/posts", requestSpec());
    }

    public Response getPostsByUserId(int userId) {
        return new ApiClient().get("/posts", requestSpec().queryParam("userId", userId));
    }

    public Response getPostById(int id) {
        return getPostById(String.valueOf(id));
    }

    public Response getPostById(String id) {
        return new ApiClient().get("/posts/" + id, requestSpec());
    }

    public Response createPost(CreatePostRequest request) {
        return new ApiClient().post("/posts", requestSpec(), request);
    }

    /**
     * Demonstrates a routing-level negative case: POSTing to a specific
     * item's path (rather than the collection path every other create call
     * uses) is not a supported operation on this API.
     */
    public Response createPostAtItemPath(int id, CreatePostRequest request) {
        return new ApiClient().post("/posts/" + id, requestSpec(), request);
    }

    public Response updatePost(int id, CreatePostRequest request) {
        return updatePost(String.valueOf(id), request);
    }

    public Response updatePost(String id, CreatePostRequest request) {
        return new ApiClient().put("/posts/" + id, requestSpec(), request);
    }

    /**
     * PATCH takes an arbitrary subset of fields, not a {@code
     * CreatePostRequest} - a strongly-typed model would force every field to
     * be nullable/optional to represent "not part of this particular patch",
     * which is exactly the ceremony a plain map avoids for something that's
     * inherently partial. Rest Assured/Jackson serialize a {@code Map} to
     * JSON with no extra configuration.
     */
    public Response partiallyUpdatePost(int id, Map<String, Object> fieldsToUpdate) {
        return partiallyUpdatePost(String.valueOf(id), fieldsToUpdate);
    }

    public Response partiallyUpdatePost(String id, Map<String, Object> fieldsToUpdate) {
        return new ApiClient().patch("/posts/" + id, requestSpec(), fieldsToUpdate);
    }

    public Response deletePost(int id) {
        return deletePost(String.valueOf(id));
    }

    public Response deletePost(String id) {
        return new ApiClient().delete("/posts/" + id, requestSpec());
    }

    private RequestSpecification requestSpec() {
        return RequestSpecFactory.unauthenticatedRequestSpec(BASE_URI);
    }
}
