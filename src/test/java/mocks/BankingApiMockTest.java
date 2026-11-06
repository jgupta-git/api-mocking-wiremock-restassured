package mocks;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@WireMockTest(httpPort = 8089)   // starts WireMock and loads src/test/resources/mappings/*.json
class BankingApiMockTest {

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost:8089";
    }

    @Test
    void getUsers_returnsUserList() {
        given()
        .when()
            .get("/api/users")
        .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("[0].id", equalTo(1),
                  "[0].firstName", equalTo("J"),
                  "[0].lastName", equalTo("Doe"),
                  "[0].email", equalTo("j.doe@example.com"));
    }

    @Test
    void createUser_returns201_andRequestIsVerified() {
        Map<String, Object> user = new HashMap<>();
        user.put("firstName", "S");
        user.put("lastName", "Scott");
        user.put("email", "s.scott@example.com");

        given()
            .contentType(ContentType.JSON)
            .body(user)
        .when()
            .post("/api/users")
        .then()
            .statusCode(201)
            .body("status", equalTo("Success"),
                  "userId", equalTo(3));

        // WireMock checks it really received this request, with this email
        verify(1, postRequestedFor(urlEqualTo("/api/users"))
                .withRequestBody(matchingJsonPath("$.email",
                        com.github.tomakehurst.wiremock.client.WireMock.equalTo("s.scott@example.com"))));
    }

    @Test
    void withdrawal_insufficientFunds_returns422() {
        Map<String, Object> withdrawal = new HashMap<>();
        withdrawal.put("accountId", "12345");
        withdrawal.put("amount", 5000.00);

        given()
            .contentType(ContentType.JSON)
            .body(withdrawal)
        .when()
            .post("/api/withdrawal")
        .then()
            .statusCode(422)
            .body("error.code", equalTo("INSUFFICIENT_FUNDS"),
                  "error.details.availableBalance", equalTo(150.0f),
                  "error.details.currency", equalTo("USD"));
    }
    
    @Test
    void withdrawal_withinBalance_returns200() {
        Map<String, Object> withdrawal = new HashMap<>();
        withdrawal.put("accountId", "12345");
        withdrawal.put("amount", 100.00);

        given()
            .contentType(ContentType.JSON)
            .body(withdrawal)
        .when()
            .post("/api/withdrawal")
        .then()
            .statusCode(200)
            .body("status", equalTo("COMPLETED"),
                  "remainingBalance", equalTo(50.0f));
    }

    @Test
    void serviceDown_returns503() {
        given()
        .when()
            .get("/api/servicedown")
        .then()
            .statusCode(503);
    }

    @Test
    void slowResponse_takesAtLeast3Seconds() {
        given()
            .header("speed", "slow")
        .when()
            .get("/api/slow")
        .then()
            .statusCode(200)
            .time(greaterThanOrEqualTo(3000L));
    }
}