package com.brandedhustlers.electionbackend.common.config.cache;

import com.brandedhustlers.electionbackend.auth.domain.Role;
import com.brandedhustlers.electionbackend.auth.dto.AppUserDTO;
import com.brandedhustlers.electionbackend.auth.dto.OrganizationDTO;
import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;
import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import com.brandedhustlers.electionbackend.election.dto.ElectionTypeDTO;
import com.brandedhustlers.electionbackend.ops.dto.GetLaunch;
import com.brandedhustlers.electionbackend.ops.dto.LaunchDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * Marker mixin: forces Jackson to embed "@class" in Redis JSON for types that NON_FINAL
     * typing would otherwise skip (Java records are implicitly final).
     * Registered only on the Redis-specific mapper — HTTP responses are never affected.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
    abstract static class RedisTypeMixin {}

    /**
     * Role entity mixin: suppresses the lazy-loaded rolePermissions collection.
     *
     * AppUserDTO carries Set<Role> (raw JPA entity) because MapStruct auto-maps it.
     * Role.rolePermissions is @ManyToMany(fetch=LAZY).  Spring's AOP ordering means
     * the @Cacheable write fires AFTER @Transactional commits (cache interceptor is
     * outer, transaction interceptor is inner), so the Hibernate session is already
     * closed when Jackson tries to iterate rolePermissions → LazyInitializationException.
     *
     * Ignoring rolePermissions here is correct: permission checks use the JPA path
     * (CustomUserDetailsService), never the Redis-cached user list.
     *
     * Long-term fix: change AppUserDTO.roles from Set<Role> to Set<String> (role names),
     * keeping JPA entities out of the cache entirely.
     */
    @JsonIgnoreProperties("rolePermissions")
    abstract static class RoleMixin {}

    /**
     * Dedicated Redis ObjectMapper built from scratch — NOT via ObjectMapper.copy().
     *
     * WHY NOT copy()?
     * ObjectMapper.copy() shares _serializerProvider (and its internal serializer cache)
     * with the original.  Spring's shared mapper has already cached an ArrayList serializer
     * that was built WITHOUT type info (before activateDefaultTyping was ever called on it).
     * That stale cached serializer survives into the copy, so collections serialize as bare
     * JSON arrays ([{...},{...}]) instead of the required WRAPPER_ARRAY format
     * (["java.util.ArrayList",[...]]).  The read side then expects a class-name string at
     * position [0] but finds START_OBJECT → MismatchedInputException, even after a Redis flush.
     *
     * A fresh ObjectMapper has an empty cache.  The first serialization of ArrayList builds
     * a new serializer that correctly includes the WRAPPER_ARRAY prefix.
     */
    @Bean
    public GenericJackson2JsonRedisSerializer redisJsonSerializer() {
        ObjectMapper redisMapper = new ObjectMapper();

        // java.time.Instant, LocalDate, ZonedDateTime, etc.
        redisMapper.registerModule(new JavaTimeModule());
        // Store dates as ISO-8601 strings ("2024-01-15T10:30:00Z"), not epoch longs
        redisMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Schema-evolution safety: ignore new fields added to DTOs so old cache
        // entries don't explode when the app is updated without a flush
        redisMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Embed "@class" for all non-final types (ArrayList, EligibilityPolicy, Role …)
        // so Jackson can reconstruct the correct concrete class on deserialization.
        redisMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // Java records are implicitly final — NON_FINAL typing skips them.
        // MixIns force "@class" for each cached record DTO.
        redisMapper.addMixIn(ElectionDTO.class,     RedisTypeMixin.class);
        redisMapper.addMixIn(ElectionTypeDTO.class, RedisTypeMixin.class);
        redisMapper.addMixIn(CandidateDTO.class,    RedisTypeMixin.class);
        redisMapper.addMixIn(OrganizationDTO.class, RedisTypeMixin.class);
        redisMapper.addMixIn(LaunchDto.class,       RedisTypeMixin.class);
        redisMapper.addMixIn(GetLaunch.class,       RedisTypeMixin.class);
        redisMapper.addMixIn(AppUserDTO.class,      RedisTypeMixin.class);
        // Suppress Role.rolePermissions (LAZY) — see RoleMixin javadoc above
        redisMapper.addMixIn(Role.class,            RoleMixin.class);

        return new GenericJackson2JsonRedisSerializer(redisMapper);
    }

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(GenericJackson2JsonRedisSerializer redisJsonSerializer) {
        return defaultConfig(Duration.ofMinutes(5), redisJsonSerializer);
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          GenericJackson2JsonRedisSerializer redisJsonSerializer) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig(Duration.ofMinutes(5), redisJsonSerializer))
                .withInitialCacheConfigurations(Map.of(
                        "featureFlags",          defaultConfig(Duration.ofMinutes(5),  redisJsonSerializer),
                        // Election caches — org-scoped keys; evicted on every write
                        CacheNames.ELECTIONS,    defaultConfig(Duration.ofMinutes(5),  redisJsonSerializer),
                        CacheNames.ELECTION,     defaultConfig(Duration.ofMinutes(5),  redisJsonSerializer),
                        // Launch caches
                        CacheNames.LAUNCHES,     defaultConfig(Duration.ofMinutes(5),  redisJsonSerializer),
                        CacheNames.LAUNCH,       defaultConfig(Duration.ofMinutes(5),  redisJsonSerializer),
                        // User list per org — low write frequency
                        CacheNames.ORG_USERS,    defaultConfig(Duration.ofMinutes(10), redisJsonSerializer)
                ))
                .build();
    }

    private RedisCacheConfiguration defaultConfig(Duration ttl,
                                                  GenericJackson2JsonRedisSerializer serializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                // Bump "v1" → "v2" whenever the serializer format changes.
                // Old keys under the previous prefix simply miss and get re-populated;
                // no manual flush required.
                .prefixCacheNameWith("v1::")
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer));
    }
}
