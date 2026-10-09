package com.org.apitests.portfolio;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes the one Portfolio Planner {@code .xlsx} report per run: a Summary
 * sheet per row source plus a Detail sheet per row source, in a single
 * timestamped workbook under {@code reports/}.
 *
 * <p>Every suite-end hook ({@code @AfterSuite} in the data-driven classes)
 * calls {@link #writeReport()}; only the first call writes, so no matter
 * how many of those classes ran, exactly one file comes out. The two row
 * shapes ({@link BusinessValidationReportRow}, {@link PlannerFieldRulesReportRow})
 * stay separate sheets because their columns differ.
 */
final class PortfolioPlannerReportWriter {

    private static final Logger LOGGER = LogManager.getLogger(PortfolioPlannerReportWriter.class);
    private static final String REPORT_DIR = "reports";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static boolean written;

    private PortfolioPlannerReportWriter() {
    }

    static synchronized void writeReport() {
        if (written) {
            return;
        }
        boolean hasBusiness = BusinessValidationReportWriter.hasRows();
        boolean hasFieldRules = PlannerFieldRulesReportWriter.hasRows();
        if (!hasBusiness && !hasFieldRules) {
            LOGGER.info("Portfolio Planner report: no rows recorded, skipping report write.");
            return;
        }
        try {
            Path reportDir = Path.of(REPORT_DIR);
            Files.createDirectories(reportDir);
            Path path = reportDir.resolve("portfolio-planner-report-" + LocalDateTime.now().format(TIMESTAMP) + ".xlsx");

            Workbook workbook = PortfolioExcelReportSupport.newWorkbook();
            if (hasBusiness) {
                PortfolioExcelReportSupport.writeSummarySheet(workbook, "Business Summary", BusinessValidationReportWriter.summaryLines());
                PortfolioExcelReportSupport.writeDetailSheet(workbook, "Business Detail",
                        BusinessValidationReportWriter.header(), BusinessValidationReportWriter.detailRows());
            }
            if (hasFieldRules) {
                PortfolioExcelReportSupport.writeSummarySheet(workbook, "Field Rules Summary", PlannerFieldRulesReportWriter.summaryLines());
                PortfolioExcelReportSupport.writeDetailSheet(workbook, "Field Rules Detail",
                        PlannerFieldRulesReportWriter.header(), PlannerFieldRulesReportWriter.detailRows());
            }
            PortfolioExcelReportSupport.save(workbook, path);
            written = true;

            LOGGER.info("Portfolio Planner report written to {}", path.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Portfolio Planner report", e);
        }
    }
}
