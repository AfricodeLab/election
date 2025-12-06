package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts eligibility attributes from voter data.
 * Supports auto-deriving age from national ID, region from ID prefix, etc.
 */
@Slf4j
@Service
public class AttributeExtractor {

    /**
     * Extract attributes from voter data.
     * This is Ghana-specific but can be adapted for other countries.
     *
     * @param nationalId National ID (Ghana: GHA-XXXXXXXXX-X format)
     * @param phone Phone number
     * @param email Email
     * @param providedAttributes Any manually provided attributes
     * @return Merged attribute map
     */
    public Map<String, Object> extractAttributes(
            String nationalId,
            String phone,
            String email,
            Map<String, Object> providedAttributes
    ) {
        Map<String, Object> attributes = new HashMap<>();

        // Start with provided attributes (if any)
        if (providedAttributes != null) {
            attributes.putAll(providedAttributes);
        }

        // Auto-derive from national ID (if not already provided)
        if (nationalId != null && !attributes.containsKey("age")) {
            Integer age = extractAgeFromNationalId(nationalId);
            if (age != null) {
                attributes.put("age", age);
                log.debug("Extracted age {} from national ID", age);
            }
        }

        // Auto-derive region from phone (Ghana: 024X = Accra, 054X = Kumasi, etc.)
        if (phone != null && !attributes.containsKey("region")) {
            String region = extractRegionFromPhone(phone);
            if (region != null) {
                attributes.put("region", region);
                log.debug("Extracted region {} from phone", region);
            }
        }

        // Default citizen to true (unless explicitly set)
        if (!attributes.containsKey("citizen")) {
            attributes.put("citizen", true);
        }

        // Add metadata
        attributes.put("extracted_at", System.currentTimeMillis());

        return attributes;
    }

    /**
     * Extract age from Ghana national ID.
     * Format: GHA-XXXXXXXXX-X where X's include birth year/date.
     *
     * ADAPT THIS for your country's ID format!
     */
    private Integer extractAgeFromNationalId(String nationalId) {
        try {
            // Example: GHA-199012345-1 → born 1990
            // This is illustrative - adapt to actual format!

            Pattern pattern = Pattern.compile("GHA-(\\d{4})\\d{5}-\\d");
            Matcher matcher = pattern.matcher(nationalId);

            if (matcher.matches()) {
                int birthYear = Integer.parseInt(matcher.group(1));
                int currentYear = LocalDate.now().getYear();
                int age = currentYear - birthYear;

                // Sanity check
                if (age >= 0 && age <= 120) {
                    return age;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract age from national ID: {}", nationalId, e);
        }

        return null;
    }

    /**
     * Extract region from phone number.
     * Ghana example: 024X = Greater Accra, 054X = Ashanti, etc.
     *
     * ADAPT THIS for your country's phone system!
     */
    private String extractRegionFromPhone(String phone) {
        // Normalize phone (remove spaces, +, etc.)
        String normalized = phone.replaceAll("[\\s+-]", "");

        // Ghana mobile prefixes
        if (normalized.startsWith("233")) {
            normalized = "0" + normalized.substring(3);
        }

        if (normalized.length() >= 3) {
            String prefix = normalized.substring(0, 3);

            switch (prefix) {
                case "024":
                case "054":
                case "055":
                    return "Greater Accra";
                case "050":
                case "020":
                    return "Ashanti";
                case "031":
                case "032":
                    return "Western";
                // Add more mappings
                default:
                    return "Unknown";
            }
        }

        return null;
    }

    /**
     * Validate that required attributes are present.
     * Returns list of missing attributes.
     */
    public List<String> validateRequiredAttributes(
            Map<String, Object> attributes,
            EligibilityPolicy policy
    ) {
        if (policy == null) {
            return List.of();
        }

        Set<String> requiredFields = new HashSet<>();

        // Extract fields from policy
        if (policy.getAll() != null) {
            policy.getAll().forEach(rule -> requiredFields.add(rule.getField()));
        }
        if (policy.getAny() != null) {
            policy.getAny().forEach(rule -> requiredFields.add(rule.getField()));
        }

        // Check which are missing
        List<String> missing = new ArrayList<>();
        for (String field : requiredFields) {
            if (attributes == null || !attributes.containsKey(field)) {
                missing.add(field);
            }
        }

        return missing;
    }
}