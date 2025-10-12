package com.brandedhustlers.electionbackend.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.util.Map;

@Converter
public class UserPreferencesConverter implements AttributeConverter<Map<String, String>, Object> {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Object convertToDatabaseColumn(Map<String, String> attribute) {
        if (attribute == null) {
            return null;
        }

        try{
            String json = mapper.writeValueAsString(attribute);

            PGobject pGobject = new PGobject();
            pGobject.setType("jsonb");
            pGobject.setValue(json);

            return pGobject;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e.getMessage());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return null;
        }
        try{
            String json = dbData.toString();
            return mapper.readValue(json, new TypeReference<Map<String, String>>(){});
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}