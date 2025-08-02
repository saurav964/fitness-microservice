package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityAiService {

    private final GeminiService geminiService;

    public Recommendation generateRecommendation(Activity activity) {
        String prompt = createPromptForActivity(activity);
        String aiResponse = geminiService.getAnswer(prompt);
        log.info("AI Response: {}", aiResponse);
        return processAiResponse(activity, aiResponse);
    }

    private Recommendation processAiResponse(Activity activity, String aiResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(aiResponse);

            JsonNode candidatesNode = rootNode.path("candidates");
            if (!candidatesNode.isArray() || candidatesNode.size() == 0) {
                log.error("Invalid AI response: missing 'candidates' array: {}", rootNode.toPrettyString());
                return createDefaultRecommendation(activity);
            }

            JsonNode firstCandidate = candidatesNode.get(0);
            if (firstCandidate == null) {
                log.error("First candidate missing: {}", rootNode.toPrettyString());
                return createDefaultRecommendation(activity);
            }

            JsonNode textNode = firstCandidate.path("content")
                    .path("parts")
                    .get(0)
                    .path("text");

            String rawText = textNode.asText("");
            if (rawText.isEmpty()) {
                log.error("Text content is empty in AI response");
                return createDefaultRecommendation(activity);
            }

            String cleanedJson = rawText
                    .replaceAll("```json\\n?", "")
                    .replaceAll("\\n```", "")
                    .trim();

            log.info("Extracted JSON string: {}", cleanedJson);

            JsonNode analysisJson = mapper.readTree(cleanedJson);
            JsonNode analysisNode = analysisJson.path("analysis");

            StringBuilder fullAnalysis = new StringBuilder();
            addAnalysisSection(fullAnalysis, analysisNode, "overall", "Overall: ");
            addAnalysisSection(fullAnalysis, analysisNode, "pace", "Pace: ");
            addAnalysisSection(fullAnalysis, analysisNode, "heartRate", "Heart Rate: ");
            addAnalysisSection(fullAnalysis, analysisNode, "caloriesBurned", "Calories Burned: ");

            List<String> improvements = extractImprovements(analysisJson.path("improvements"));
            List<String> suggestions = extractSuggestions(analysisJson.path("suggestions"));
            List<String> safety = extractSafety(analysisJson.path("safety"));

            return Recommendation.builder()
                    .activityId(activity.getId())
                    .userId(activity.getUserId())
                    .activityType(activity.getType())
                    .recommendation(fullAnalysis.toString().trim())
                    .improvements(improvements)
                    .suggestions(suggestions)
                    .safety(safety)
                    .createdAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to process AI response", e);
            return createDefaultRecommendation(activity);
        }
    }

    private Recommendation createDefaultRecommendation(Activity activity) {
        return Recommendation.builder()
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .activityType(activity.getType())
                .recommendation("No analysis available due to AI response failure.")
                .improvements(Collections.singletonList("No improvements provided."))
                .suggestions(Collections.singletonList("No workout suggestions provided."))
                .safety(Arrays.asList(
                        "Always warm up before exercise.",
                        "Stay hydrated.",
                        "Listen to your body."
                ))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private List<String> extractSafety(JsonNode safety) {
        List<String> safetyList = new ArrayList<>();
        if (safety.isArray()) {
            safety.forEach(item -> safetyList.add(item.asText()));
        }
        return safetyList.isEmpty()
                ? Collections.singletonList("No safety guidelines provided.")
                : safetyList;
    }

    private List<String> extractSuggestions(JsonNode suggestions) {
        List<String> suggestionsList = new ArrayList<>();
        if (suggestions.isArray()) {
            suggestions.forEach(suggestion -> {
                String workout = suggestion.path("workout").asText();
                String description = suggestion.has("description")
                        ? suggestion.get("description").asText()
                        : "";
                suggestionsList.add(String.format("%s: %s", workout, description));
            });
        }
        return suggestionsList.isEmpty()
                ? Collections.singletonList("No workout suggestions provided.")
                : suggestionsList;
    }

    private List<String> extractImprovements(JsonNode improvements) {
        List<String> improvementsList = new ArrayList<>();
        if (improvements.isArray()) {
            improvements.forEach(improvement -> {
                String area = improvement.path("area").asText();
                String detail = improvement.has("recommendation")
                        ? improvement.get("recommendation").asText()
                        : "";
                improvementsList.add(String.format("%s: %s", area, detail));
            });
        }
        return improvementsList.isEmpty()
                ? Collections.singletonList("No improvements provided.")
                : improvementsList;
    }

    private void addAnalysisSection(StringBuilder fullAnalysis, JsonNode analysisNode, String key, String prefix) {
        if (!analysisNode.path(key).isMissingNode()) {
            fullAnalysis.append(prefix)
                    .append(analysisNode.path(key).asText())
                    .append("\n\n");
        }
    }

    private String createPromptForActivity(Activity activity) {
        return String.format("""
            Analyze this fitness activity and provide detailed recommendation in the following EXACT JSON format:
            {
              "analysis": {
                 "overall": "Overall analysis here",
                 "pace": "Pace analysis here",
                 "heartRate": "Heart rate analysis here",
                 "caloriesBurned": "Calories analysis here"
              },
              "improvements": [
                 {
                    "area": "Area Name",
                    "recommendation": "Detailed recommendation"
                 }
              ],
              "suggestions": [
                 {
                   "workout": "Workout name",
                   "description": "Detailed workout description"
                 }
              ],
              "safety": [
                "Safety point 1",
                "Safety point 2"
              ]
            }
            Analyze this activity:
            Activity Type: %s
            Duration: %d minutes
            Calories Burned: %d
            Additional Metrics: %s

            Provide detailed analysis focusing on performance, improvements, next workout suggestions, and safety guidelines.
            Ensure the response follows the EXACT JSON format shown above.
            """,
                activity.getType(),
                activity.getDuration(),
                activity.getCaloriesBurned(),
                activity.getAdditionalMetrics() == null ? "{}" : activity.getAdditionalMetrics().toString()
        );
    }
}
