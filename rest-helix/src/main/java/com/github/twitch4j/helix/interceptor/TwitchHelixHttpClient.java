package com.github.twitch4j.helix.interceptor;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import feign.Client;
import feign.Request;
import feign.Response;
import feign.okhttp.OkHttpClient;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static com.github.twitch4j.helix.interceptor.TwitchHelixClientIdInterceptor.AUTH_HEADER;
import static com.github.twitch4j.helix.interceptor.TwitchHelixClientIdInterceptor.BEARER_PREFIX;
import static com.github.twitch4j.helix.interceptor.TwitchHelixDecoder.singleFirst;

@Slf4j
public class TwitchHelixHttpClient implements Client {

    private final Client client;
    private final ScheduledExecutorService executor;
    private final TwitchHelixClientIdInterceptor interceptor;
    private final long timeout;

    public TwitchHelixHttpClient(OkHttpClient client, ScheduledThreadPoolExecutor executor, TwitchHelixClientIdInterceptor interceptor, Integer timeout) {
        this.client = client;
        this.executor = executor;
        this.interceptor = interceptor;
        this.timeout = timeout == null ? 60 * 1000 : timeout.longValue();
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        // Check whether this request should be delayed to conform to rate limits
        String token = singleFirst(request.headers().get(AUTH_HEADER));
        if (token != null && token.startsWith(BEARER_PREFIX)) {
            OAuth2Credential credential = interceptor.getAccessTokenCache().getIfPresent(token.substring(BEARER_PREFIX.length()));
            if (credential != null) {
                Bucket bucket = interceptor.getOrInitializeBucket(interceptor.getKey(credential));
                try {
                    return executeAgainstBucket(
                        bucket,
                        () -> delegatedExecute(request, options),
                        executor
                    ).get(timeout, TimeUnit.MILLISECONDS);
                } catch (ExecutionException | InterruptedException | TimeoutException e) {
                    if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
                    log.error("Throttled Helix API call timed-out before completion", e);
                    return null;
                }
            }
        }

        // Fallback: just run the http request
        return delegatedExecute(request, options);
    }

    /**
     * After the helix rate limit has been evaluated, check for any other endpoint-specific limits before actually executing the request.
     *
     * @param request feign request
     * @param options feign request options
     * @return feign response
     * @throws IOException on network errors
     */
    private Response delegatedExecute(Request request, Request.Options options) throws IOException {
        // Moderation API: banUser and unbanUser share a bucket per channel id
        if (request.requestTemplate().path().endsWith("/moderation/bans")) {
            // Obtain the channel id
            String channelId = request.requestTemplate().queries().getOrDefault("broadcaster_id", Collections.emptyList()).iterator().next();

            // Conform to endpoint-specific bucket
            Bucket modBucket = interceptor.getModerationBucket(channelId);
            try {
                return executeAgainstBucket(modBucket, () -> client.execute(request, options), executor).get(timeout, TimeUnit.MILLISECONDS);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
                log.error("Throttled Helix API call timed-out before completion", e);
                return null;
            }
        }

        // no endpoint-specific rate limiting was needed; perform network request now
        return client.execute(request, options);
    }

    /**
     * Performs the callable after a token has been consumed from the bucket using the executor.
     *
     * @param bucket   rate limit bucket
     * @param call     task that requires a bucket point
     * @param executor scheduled executor service for async calls
     * @return the future result of the call
     */
    private static <T> CompletableFuture<T> executeAgainstBucket(Bucket bucket, Callable<T> call, ScheduledExecutorService executor) {
        if (bucket.tryConsume(1L))
            return CompletableFuture.supplyAsync(new SneakySupplier<>(call));

        return bucket.asScheduler().consume(1L, executor).thenApplyAsync(v -> new SneakySupplier<>(call).get());
    }

    @RequiredArgsConstructor
    private static class SneakySupplier<T> implements Supplier<T> {
        private final Callable<T> callable;

        @Override
        @SneakyThrows
        public T get() {
            return callable.call();
        }
    }

}
