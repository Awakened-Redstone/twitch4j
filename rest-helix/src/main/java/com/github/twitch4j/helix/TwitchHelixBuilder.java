package com.github.twitch4j.helix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.common.config.ProxyConfig;
import com.github.twitch4j.common.config.Twitch4JGlobal;
import com.github.twitch4j.common.util.ThreadUtils;
import com.github.twitch4j.common.util.TypeConvert;
import com.github.twitch4j.helix.domain.CustomReward;
import com.github.twitch4j.helix.interceptor.CustomRewardEncodeMixIn;
import com.github.twitch4j.helix.interceptor.TwitchHelixTokenManager;
import com.github.twitch4j.helix.interceptor.TwitchHelixClientIdInterceptor;
import com.github.twitch4j.helix.interceptor.TwitchHelixDecoder;
import com.github.twitch4j.helix.interceptor.TwitchHelixHttpClient;
import com.github.twitch4j.helix.interceptor.TwitchHelixRateLimitTracker;
import com.netflix.config.ConfigurationManager;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.hystrix.HystrixFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.okhttp.OkHttpClient;
import feign.slf4j.Slf4jLogger;
import io.github.bucket4j.Bandwidth;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Twitch API - Helix
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class TwitchHelixBuilder {

    /**
     * The official base URL used by production Twitch Helix API servers.
     */
    public static final String OFFICIAL_BASE_URL = "https://api.twitch.tv/helix";

    /**
     * The default URL used by the Mock API server generated by the Twitch CLI.
     *
     * @see <a href="https://github.com/twitchdev/twitch-cli/blob/main/docs/mock-api.md">Mock API Docs</a>
     */
    public static final String MOCK_BASE_URL = "http://localhost:8080/mock";

    /**
     * @see <a href="https://dev.twitch.tv/docs/api/guide#rate-limits">Helix Rate Limit Reference</a>
     */
    public static final Bandwidth DEFAULT_BANDWIDTH = Bandwidth.simple(800, Duration.ofMinutes(1));

    /**
     * Client Id
     */
    @With
    private String clientId = Twitch4JGlobal.clientId;

    /**
     * Client Secret
     */
    @With
    private String clientSecret = Twitch4JGlobal.clientSecret;

    /**
     * User Agent
     */
    @With
    private String userAgent = Twitch4JGlobal.userAgent;

    /**
     * Default Auth Token for API Requests
     */
    @With
    private OAuth2Credential defaultAuthToken = null;

    /**
     * HTTP Request Queue Size
     */
    @With
    private Integer requestQueueSize = -1;

    /**
     * BaseUrl
     */
    @With
    private String baseUrl = OFFICIAL_BASE_URL;

    /**
     * Default Timeout
     */
    @With
    private Integer timeout = 5000;

    /**
     * you can overwrite the feign loglevel to print the full requests + responses if needed
     */
    @With
    private Logger.Level logLevel = Logger.Level.NONE;

    /**
     * Proxy Configuration
     */
    @With
    private ProxyConfig proxyConfig = null;

    /**
     * Scheduler Thread Pool Executor
     */
    @With
    private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = null;

    /**
     * Custom Rate Limit to use for Helix calls
     */
    @With
    private Bandwidth apiRateLimit = DEFAULT_BANDWIDTH;

    /**
     * Initialize the builder
     *
     * @return Twitch Helix Builder
     */
    public static TwitchHelixBuilder builder() {
        return new TwitchHelixBuilder();
    }

    /**
     * Twitch API Client (Helix)
     *
     * @return TwitchHelix
     */
    public TwitchHelix build() {
        log.debug("Helix: Initializing Module ...");

        // Hystrix
        ConfigurationManager.getConfigInstance().setProperty("hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds", timeout);
        ConfigurationManager.getConfigInstance().setProperty("hystrix.command.default.requestCache.enabled", false);
        ConfigurationManager.getConfigInstance().setProperty("hystrix.threadpool.default.maxQueueSize", getRequestQueueSize());
        ConfigurationManager.getConfigInstance().setProperty("hystrix.threadpool.default.queueSizeRejectionThreshold", getRequestQueueSize());

        // Hystrix: Ban/Unban API already has special 429 logic such that circuit breaking is not needed (and just trips on trivial errors like 'user is already banned')
        ConfigurationManager.getConfigInstance().setProperty("hystrix.command.TwitchHelix#banUser(String,String,String,BanUserInput).circuitBreaker.enabled", false);
        ConfigurationManager.getConfigInstance().setProperty("hystrix.command.TwitchHelix#unbanUser(String,String,String,String).circuitBreaker.enabled", false);

        // Warning
        if (logLevel == Logger.Level.HEADERS || logLevel == Logger.Level.FULL) {
            log.warn("Helix: The current feign loglevel will print sensitive information including your access token, please don't share this log!");
        }

        // Jackson ObjectMapper
        ObjectMapper mapper = TypeConvert.getObjectMapper();
        ObjectMapper serializer = mapper.copy().addMixIn(CustomReward.class, CustomRewardEncodeMixIn.class);

        // Create HttpClient with proxy
        okhttp3.OkHttpClient.Builder clientBuilder = new okhttp3.OkHttpClient.Builder();
        if (proxyConfig != null)
            proxyConfig.apply(clientBuilder);

        // Executor for rate limiting
        if (scheduledThreadPoolExecutor == null)
            scheduledThreadPoolExecutor = ThreadUtils.getDefaultScheduledThreadPoolExecutor("twitch4j-" + RandomStringUtils.random(4, true, true), 1);

        // Enforce non-null rate limit bandwidth
        if (apiRateLimit == null)
            apiRateLimit = DEFAULT_BANDWIDTH;

        // Feign
        TwitchHelixTokenManager tokenManager = new TwitchHelixTokenManager(clientId, clientSecret, defaultAuthToken);
        TwitchHelixRateLimitTracker rateLimitTracker = new TwitchHelixRateLimitTracker(apiRateLimit, tokenManager);
        return HystrixFeign.builder()
            .client(new TwitchHelixHttpClient(new OkHttpClient(clientBuilder.build()), scheduledThreadPoolExecutor, tokenManager, rateLimitTracker, timeout))
            .encoder(new JacksonEncoder(serializer))
            .decoder(new TwitchHelixDecoder(mapper, rateLimitTracker))
            .logger(new Slf4jLogger())
            .logLevel(logLevel)
            .errorDecoder(new TwitchHelixErrorDecoder(new JacksonDecoder(), rateLimitTracker))
            .requestInterceptor(new TwitchHelixClientIdInterceptor(userAgent, tokenManager))
            .options(new Request.Options(timeout / 3, TimeUnit.MILLISECONDS, timeout, TimeUnit.MILLISECONDS, true))
            .retryer(new Retryer.Default(500, timeout, 2))
            .target(TwitchHelix.class, baseUrl);
    }
}
