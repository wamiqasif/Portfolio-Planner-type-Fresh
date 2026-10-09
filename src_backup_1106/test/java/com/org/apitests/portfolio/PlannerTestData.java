package com.org.apitests.portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.apiautomation.exceptions.ConfigurationException;
import com.org.apiautomation.models.request.planner.PlannerRequest;
import com.org.apiautomation.models.response.planner.PlannerData;
import com.org.apiautomation.models.response.planner.PlannerResponse;
import io.restassured.response.Response;
import org.testng.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Loads {@code testdata/planner-scenarios.json} and turns a {@link
 * PlannerScenario} into a ready-to-send {@link PlannerRequest} - the one
 * place scenario JSON gets parsed, so every test class shares it rather
 * than re-reading the file.
 */
final class PlannerTestData {

    private static final String SCENARIOS_RESOURCE = "testdata/planner-scenarios.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PlannerTestData() {
    }

    static List<PlannerScenario> loadScenarios() {
        try (InputStream inputStream = PlannerTestData.class.getClassLoader().getResourceAsStream(SCENARIOS_RESOURCE)) {
            if (inputStream == null) {
                throw new ConfigurationException("Test data file not found on classpath: " + SCENARIOS_RESOURCE);
            }
            return MAPPER.readValue(inputStream, MAPPER.getTypeFactory().constructCollectionType(List.class, PlannerScenario.class));
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read test data file: " + SCENARIOS_RESOURCE, e);
        }
    }

    static PlannerScenario scenario(String name) {
        return loadScenarios().stream()
                .filter(s -> s.scenario().equals(name))
                .findFirst()
                .orElseThrow(() -> new ConfigurationException("No scenario named '" + name + "' in " + SCENARIOS_RESOURCE));
    }

    /**
     * Builds the JSON body {@link PlannerRequest} for a scenario, matching a
     * real, currently-live curl exactly: {@code label_id}/{@code user_id}/
     * {@code investor_id} are sent as JSON numbers (not strings - the
     * scenario JSON keeps them as strings only because they're also used as
     * URL query values elsewhere, e.g. {@code PortfolioService}), {@code
     * aware_type} is always sent explicitly (including {@code "FRESH"} -
     * unlike the older, now-stale query-param contract, this JSON-body
     * contract does send it for both flows), and {@code investor_id_dev} is
     * deliberately never set here - the confirmed-current curl doesn't
     * include it at all.
     */
    static PlannerRequest toRequest(PlannerScenario s) {
        return PlannerRequest.builder()
                .goalType(s.goalType())
                .txnOptions(s.txnOptions())
                .durationType(s.durationType())
                .investmentDuration(s.investmentDuration())
                .monthlyAmount(s.monthlyAmount())
                .lumpsumAmount(s.lumpsumAmount())
                .labelId(Long.parseLong(s.labelId()))
                .userId(Long.parseLong(s.userId()))
                .riskProfileId(s.riskProfileId())
                .annualIncomeRange(s.annualIncomeRange())
                .investorId(Long.parseLong(s.investorId()))
                .awareType(s.awareType());
    }

    /**
     * Asserts a Planner Generate call actually succeeded before handing back
     * its {@code data} object, with a clear message and the raw body on
     * failure - deliberately not just {@code response.as(PlannerResponse.class)},
     * which throws an opaque Jackson "no content-type present" exception
     * (rather than a readable assertion failure) on a non-2xx/empty-body
     * response, exactly the kind of unhelpful failure section 12 of the
     * test brief warns against.
     */
    static PlannerData parseSuccessful(Response response) {
        Assert.assertEquals(response.statusCode(), 200,
                "Planner Generate did not return 200. Body: " + response.getBody().asString());
        PlannerResponse parsed = response.as(PlannerResponse.class);
        Assert.assertTrue(parsed.status(), "Planner Generate returned status:false. Body: " + response.getBody().asString());
        return parsed.data();
    }
}
