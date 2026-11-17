package io.github.khayreen.intake.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Upload and review through the HTTP API, with the rules extractor so no key is needed. */
@SpringBootTest(properties = {"intake.extractor=rules", "spring.ai.model.chat=none"})
@AutoConfigureMockMvc
class DocumentControllerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path FORMS = Path.of("..", "data", "forms");

    @Autowired
    MockMvc mvc;

    private JsonNode upload(String set, String file) throws Exception {
        byte[] pdf = Files.readAllBytes(FORMS.resolve(set).resolve(file));
        String body = mvc.perform(multipart("/api/documents").file(new MockMultipartFile("file", file, "application/pdf", pdf)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private int review(String id, String json) throws Exception {
        return mvc.perform(post("/api/documents/" + id + "/review").contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void cleanFormIsAcceptedAutomatically() throws Exception {
        JsonNode doc = upload("main", "003-address-change.pdf");
        assertThat(doc.get("status").asString()).isEqualTo("ACCEPTED");
        assertThat(doc.get("result").get("flags").size()).isZero();
    }

    @Test
    void attackFormWaitsForReviewAndShowsWhy() throws Exception {
        JsonNode doc = upload("attacks", "002-loan-application.pdf");
        assertThat(doc.get("status").asString()).isEqualTo("PENDING_REVIEW");
        assertThat(doc.get("result").get("flags").toString()).contains("INJECTION_SUSPECTED");
    }

    @Test
    void reviewerCorrectionsAreValidatedBeforeApproval() throws Exception {
        String id = upload("attacks", "002-loan-application.pdf").get("id").asString();

        assertThat(review(id, "{\"decision\":\"APPROVE\",\"fields\":{\"monthly_income\":\"RM 9,999,999\"}}")).isEqualTo(422);
        assertThat(review(id, "{\"decision\":\"APPROVE\",\"fields\":{\"approved\":\"yes\"}}")).isEqualTo(422);
        // The rules cannot read "Type of Financing" or "Income per Month", so approval waits for the reviewer to fill them in.
        assertThat(review(id, "{\"decision\":\"APPROVE\"}")).isEqualTo(422);
        assertThat(review(id, "{\"decision\":\"APPROVE\",\"fields\":{\"loan_type\":\"Personal\",\"monthly_income\":\"RM19,500\"},"
                + "\"note\":\"checked against the printed form\"}")).isEqualTo(200);
        assertThat(review(id, "{\"decision\":\"REJECT\"}")).as("cannot be reviewed twice").isEqualTo(409);

        String body = mvc.perform(get("/api/documents/" + id)).andReturn().getResponse().getContentAsString();
        JsonNode approved = JSON.readTree(body);
        assertThat(approved.get("status").asString()).isEqualTo("APPROVED");
        assertThat(approved.get("finalFields").get("monthly_income").asString()).isEqualTo("19500.00");
    }

    @Test
    void rejectsAnythingThatIsNotAPdf() throws Exception {
        mvc.perform(multipart("/api/documents").file(new MockMultipartFile("file", "x.pdf", "application/pdf", "<html>".getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void autoAcceptedDocumentsCannotBeReviewed() throws Exception {
        String id = upload("main", "003-address-change.pdf").get("id").asString();
        assertThat(review(id, "{\"decision\":\"REJECT\"}")).isEqualTo(409);
    }
}
