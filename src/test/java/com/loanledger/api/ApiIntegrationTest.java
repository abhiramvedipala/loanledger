package com.loanledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the whole app against real Postgres, including the migrations and the seed. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiIntegrationTest {

    private static final String PROBLEM = "application/problem+json";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void seedHas50LoansWithTheirAccountsAndUpTo12MonthsOfPayments() {
        // Seed rows are the only ones originated in December 2024; other tests create 2025 loans.
        String seeded = "(select id from loans where origination_date < '2025-01-01')";
        assertThat(jdbc.queryForObject("select count(*) from " + seeded + " s", Long.class)).isEqualTo(50);
        assertThat(jdbc.queryForObject("select count(*) from escrow_accounts where loan_id in " + seeded, Long.class)).isEqualTo(50);
        assertThat(jdbc.queryForObject("select max(c) from (select count(*) c from payments where loan_id in "
                + seeded + " group by loan_id) x", Long.class)).isEqualTo(12);
    }

    @Test
    void listIsPaginatedAndRejectsOversizedPages() throws Exception {
        mvc.perform(get("/api/v1/loans?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.size").value(5));
        mvc.perform(get("/api/v1/loans?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createThenGetAndSchedule() throws Exception {
        long id = createLoan();
        mvc.perform(get("/api/v1/loans/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CURRENT"))
                .andExpect(jsonPath("$.monthlyPayment").value(1896.20))
                .andExpect(jsonPath("$.loanNumber").isNotEmpty())
                .andExpect(jsonPath("$.escrow.monthlyAmount").value(300.00));
        mvc.perform(get("/api/v1/loans/" + id + "/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(360)))
                .andExpect(jsonPath("$.rows[0].interest").value(1625.00));
    }

    @Test
    void invalidCreateIsA400ProblemNamingTheField() throws Exception {
        send(post("/api/v1/loans"), "{\"principal\": -5}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.principal").exists());
    }

    @Test
    void unknownLoanIsA404Problem() throws Exception {
        mvc.perform(get("/api/v1/loans/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    void postedPaymentUpdatesLoanEscrowAndHistory() throws Exception {
        long id = createLoan();
        send(post("/api/v1/loans/" + id + "/payments"), payment("2196.20"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interest").value(1625.00))
                .andExpect(jsonPath("$.principal").value(271.20))
                .andExpect(jsonPath("$.escrow").value(300.00))
                .andExpect(jsonPath("$.balanceAfter").value(299728.80));
        mvc.perform(get("/api/v1/loans/" + id))
                .andExpect(jsonPath("$.currentBalance").value(299728.80))
                .andExpect(jsonPath("$.escrow.balance").value(300.00));
        mvc.perform(get("/api/v1/loans/" + id + "/payments"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(2196.20));
    }

    @Test
    void simulateSavesNothing() throws Exception {
        long id = createLoan();
        send(post("/api/v1/loans/" + id + "/payments/simulate"), payment("2196.20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAfter").value(299728.80))
                .andExpect(jsonPath("$.paymentId").isEmpty());
        mvc.perform(get("/api/v1/loans/" + id))
                .andExpect(jsonPath("$.currentBalance").value(300000.00));
        mvc.perform(get("/api/v1/loans/" + id + "/payments"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void paymentAbovePayoffIsA422Problem() throws Exception {
        long id = createLoan();
        send(post("/api/v1/loans/" + id + "/payments"), payment("999999.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void lossMitigationCaseWalksTheStateMachineAndMovesTheLoan() throws Exception {
        long id = createLoan();
        long caseId = idOf(send(post("/api/v1/loans/" + id + "/loss-mitigation-cases"), "{\"notes\":\"hardship\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CURRENT")));
        send(post("/api/v1/loans/" + id + "/loss-mitigation-cases"), "{}").andExpect(status().isConflict());

        String url = "/api/v1/loss-mitigation-cases/" + caseId;
        send(patch(url), "{\"status\":\"RESOLVED\"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));

        send(patch(url), "{\"status\":\"DELINQUENT\"}").andExpect(status().isOk());
        mvc.perform(get("/api/v1/loans/delinquent?size=100"))
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").exists());

        send(patch(url), "{\"status\":\"FORBEARANCE\"}").andExpect(status().isOk());
        send(patch(url), "{\"status\":\"RESOLVED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        mvc.perform(get("/api/v1/loans/" + id)).andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void swaggerUiAndOpenApiAreServed_andSpecIsExportedForCountScript() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        String spec = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(spec).at("/paths").has("/api/v1/loans")).isTrue();
        Files.createDirectories(Path.of("build"));
        Files.writeString(Path.of("build/openapi.json"), spec);
    }

    private long createLoan() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        return idOf(send(post("/api/v1/loans"), """
                {"borrower": {"firstName": "Test", "lastName": "Borrower", "email": "%s"},
                 "principal": 300000, "annualRate": 6.5, "termMonths": 360, "originationDate": "2025-01-01",
                 "annualTax": 2400, "annualInsurance": 1200}""".formatted(email))
                .andExpect(status().isCreated()));
    }

    private static String payment(String amount) {
        return "{\"amount\": %s, \"paymentDate\": \"2025-02-01\"}".formatted(amount);
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long idOf(ResultActions result) throws Exception {
        JsonNode node = json.readTree(result.andReturn().getResponse().getContentAsString());
        return node.get("id").asLong();
    }
}
