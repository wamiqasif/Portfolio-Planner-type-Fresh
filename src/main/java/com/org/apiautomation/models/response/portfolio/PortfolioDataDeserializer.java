package com.org.apiautomation.models.response.portfolio;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Map;

/**
 * The User Portfolio API's {@code data} field is an object ({@code
 * {"fund-data": {...}}}) for an investor who owns funds, but a bare empty
 * array ({@code []}) for an investor with no existing portfolio at all -
 * confirmed empirically (not documented anywhere) by running the
 * data-driven Portfolio Planner suite against investors with no holdings,
 * where the default record mapping threw {@code MismatchedInputException}
 * ("Cannot deserialize value of type PortfolioData from Array value").
 * Treated as "no holdings" rather than an error, since that's exactly what
 * the shape means.
 */
final class PortfolioDataDeserializer extends JsonDeserializer<PortfolioData> {

    @Override
    public PortfolioData deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isArray() || node.isNull()) {
            return new PortfolioData(Map.of());
        }
        return parser.getCodec().treeToValue(node, PortfolioData.class);
    }
}
