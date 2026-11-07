# API Mocking with WireMock + REST Assured

![Tests](https://github.com/jgupta-git/api-mocking-wiremock-restassured/actions/workflows/tests.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-17+-orange)
![WireMock](https://img.shields.io/badge/WireMock-3.9.1-blue)
![REST Assured](https://img.shields.io/badge/REST%20Assured-5.5.1-green)
![JUnit 5](https://img.shields.io/badge/JUnit-5.11-red)

A WireMock + REST Assured test suite that mocks a small banking API. It tests the client side against happy paths, business-rule failures and outages, with no real backend.

---

## Overview

Real downstream services are often unavailable, slow, or hard to push into error states such as "insufficient funds" or "service down". WireMock replaces them with a local HTTP server that returns whatever response a test needs.

This project shows how to:

- Define **stub mappings** as JSON files, separate from the test code
- Serve large response bodies from **body files** (`__files/`)
- Return different responses for the same endpoint depending on the **request body** (JsonPath filters)
- Inject **faults**: a 503 outage and a 3-second delay, for resilience and chaos testing
- **Verify** that the client actually sent the request it was supposed to send

---

## Tech Stack

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 17+ | Language |
| WireMock | 3.9.1 | HTTP mock server |
| REST Assured | 5.5.1 | API calls and assertions |
| JUnit 5 | 5.11.3 | Test runner |
| Maven | 3.x | Build and dependencies |
| Surefire Report Plugin | 3.5.6 | HTML test report |

---

## Test Scenarios

### Users API

| Test | Request | Stub | Expected |
|------|---------|------|----------|
| `getUsers_returnsUserList` | `GET /api/users` | `get_user.json` + `__files/users.json` | 200, 2 users, first user's fields checked |
| `createUser_returns201_andRequestIsVerified` | `POST /api/users` | `post_user.json` | 201, `userId` returned; WireMock `verify()` confirms the request body sent |

### Banking API (business rule: balance is $150)

| Test | Request | Stub | Expected |
|------|---------|------|----------|
| `withdrawal_withinBalance_returns200` | `POST /api/withdrawal` amount = 100 | `withdrawal_success.json` (`amount <= 150`) | 200, `COMPLETED`, remaining balance 50.00 |
| `withdrawal_insufficientFunds_returns422` | `POST /api/withdrawal` amount = 5000 | `insufficient_funds.json` (`amount > 150`) | 422, `INSUFFICIENT_FUNDS`, available balance 150.00 |

### Fault Injection (Resilience / Chaos)

| Test | Request | Stub | Expected |
|------|---------|------|----------|
| `serviceDown_returns503` | `GET /api/servicedown` | `service_down.json` | 503 Service Unavailable |
| `slowResponse_takesAtLeast3Seconds` | `GET /api/slow` with header `speed: slow` | `slow_response.json` (`fixedDelayMilliseconds: 3000`) | 200 after a delay of at least 3 s |

---

## Project Structure

```
api-mocking-wiremock-restassured/
├── pom.xml
└── src/test/
    ├── java/mocks/
    │   └── BankingApiMockTest.java      # All 6 tests
    └── resources/
        ├── mappings/                    # Stub mappings (request → response)
        │   ├── get_user.json
        │   ├── post_user.json
        │   ├── withdrawal_success.json
        │   ├── insufficient_funds.json
        │   ├── service_down.json
        │   └── slow_response.json
        └── __files/                     # Response body files
            └── users.json
```

---

## Key Concepts

### Stub mappings vs body files

- **`mappings/`**: each file says *"when a request looks like this, return that"*. `@WireMockTest` loads every file in this folder automatically when the test starts.
- **`__files/`**: response bodies referenced with `"bodyFileName"`. This keeps large JSON out of the mapping file.

```json
{
  "request":  { "method": "GET", "url": "/api/users" },
  "response": { "status": 200, "bodyFileName": "users.json" }
}
```

### Matching on the request body

The same endpoint, `POST /api/withdrawal`, returns different responses depending on the amount. JsonPath filter expressions make the two stubs mutually exclusive:

```json
{ "matchesJsonPath": "$[?(@.amount <= 150)]" }   // withdrawal_success.json → 200
{ "matchesJsonPath": "$[?(@.amount > 150)]" }    // insufficient_funds.json → 422
```

### Fault injection

```json
"response": { "status": 200, "fixedDelayMilliseconds": 3000 }
```

The test asserts the delay with REST Assured:

```java
.then().time(greaterThanOrEqualTo(3000L));
```

### Request verification

A mock should prove more than "the response was right". It should also prove the client sent the right request:

```java
verify(1, postRequestedFor(urlEqualTo("/api/users"))
        .withRequestBody(matchingJsonPath("$.email", WireMock.equalTo("..."))));
```

> **Note:** Hamcrest and WireMock both have an `equalTo()`. The WireMock one is called as `WireMock.equalTo(...)` to avoid the ambiguity.

---

## Quick Start

### Prerequisites

- Java 17 or later
- Maven 3.x

### Clone and run

```bash
git clone https://github.com/jgupta-git/api-mocking-wiremock-restassured.git
cd api-mocking-wiremock-restassured
mvn clean test
```

No external server is needed. WireMock starts on port **8089** for the test run and stops afterwards.

### Generate the HTML report

```bash
mvn clean test surefire-report:report-only
```

Open `target/reports/surefire.html` in a browser.

📊 **[Latest test report](https://jgupta-git.github.io/api-mocking-wiremock-restassured/)** (published by GitHub Actions on every push)

> **Windows / Git Bash:** if Maven fails with `invalid target release: 17`, JAVA_HOME points to an older JDK. Set it for the session:
> ```bash
> export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
> ```

### Run in Eclipse

`File → Import → Existing Maven Projects`, then right-click `BankingApiMockTest.java` → `Run As → JUnit Test`.

---

## References

- [WireMock documentation](https://wiremock.org/docs/)
- [WireMock request matching](https://wiremock.org/docs/request-matching/)
- [WireMock JUnit 5 extension](https://wiremock.org/docs/junit-jupiter/)
- [REST Assured usage guide](https://github.com/rest-assured/rest-assured/wiki/Usage)
