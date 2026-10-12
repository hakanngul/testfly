package io.testfly.unit.client;

import io.testfly.assertion.SoftAssertions;
import io.testfly.client.ApiRequest;
import io.testfly.client.ApiResponse;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.net.URI;

import static org.testng.Assert.*;

public class ApiResponseSoftAssertionsTest {

    @AfterMethod(alwaysRun = true)
    public void clear() {
        SoftAssertions.clear();
    }

    private static ApiResponse response(int status, String body) {
        ApiRequest request = ApiRequest.builder().uri(URI.create("http://localhost/x")).build();
        return ApiResponse.builder().request(request).status(status).body(body).header("X-Id", "7").build();
    }

    @Test
    public void softFailuresAreCollectedInsteadOfThrown() {
        response(200, "{\"name\":\"a\"}")
                .assertStatusSoft(201)
                .assertJsonSoft("$.name", "b")
                .assertHeaderSoft("X-Id", "8");

        assertEquals(SoftAssertions.get().getFailures().size(), 3);
    }

    @Test
    public void passingSoftAssertionsCollectNothing() {
        response(200, "{\"name\":\"a\"}").assertStatusSoft(200).assertJsonSoft("$.name", "a").assertHeaderSoft("X-Id", "7");
        assertTrue(SoftAssertions.get().getFailures().isEmpty());
    }
}
