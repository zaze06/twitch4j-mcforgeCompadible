package twitch4j.helix;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.RetryableException;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ContextedRuntimeException;
import twitch4j.helix.domain.TwitchHelixError;
import twitch4j.helix.exception.NotFoundException;
import twitch4j.helix.exception.UnauthorizedException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TwitchHelixErrorDecoder implements ErrorDecoder {

    // Decoder
    final Decoder decoder;

    // Error Decoder
    final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    // ObjectMapper
    final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructor
     *
     * @param decoder Feign Decoder
     */
    public TwitchHelixErrorDecoder(Decoder decoder) {
        this.decoder = decoder;
    }

    /**
     * Overwrite the Decode Method to handle custom error cases
     *
     * @param methodKey
     * @param response
     * @return
     */
    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            String responseBody = IOUtils.toString(response.body().asInputStream(), StandardCharsets.UTF_8.name());

            if (response.status() == 401) {
                throw new UnauthorizedException()
                        .addContextValue("requestUrl", response.request().url())
                        .addContextValue("requestMethod", response.request().method())
                        .addContextValue("requestHeaders", response.request().headers().entrySet().toString())
                        .addContextValue("responseBody", responseBody);
            }
            else if (response.status() == 404) {
                throw new NotFoundException()
                    .addContextValue("requestUrl", response.request().url())
                    .addContextValue("requestMethod", response.request().method())
                    .addContextValue("requestHeaders", response.request().headers().entrySet().toString())
                    .addContextValue("responseBody", responseBody);
            }
            else if (response.status() == 503) {
                // If you get an HTTP 503 (Service Unavailable) error, retry once.
                // If that retry also results in an HTTP 503, there probably is something wrong with the downstream service.
                // Check the status page for relevant updates.
                return new RetryableException("getting service unavailable, retrying ...", null);
            }

            TwitchHelixError helixError = objectMapper.readValue(responseBody, TwitchHelixError.class);
            return new ContextedRuntimeException("Helix API Error")
                .addContextValue("requestUrl", response.request().url())
                .addContextValue("requestMethod", response.request().method())
                .addContextValue("requestHeaders", response.request().headers().entrySet().toString())
                .addContextValue("responseBody", responseBody)
                .addContextValue("errorType", helixError.getError())
                .addContextValue("errorStatus", helixError.getStatus())
                .addContextValue("errorType", helixError.getMessage());
        } catch (IOException fallbackToDefault) {
            return defaultDecoder.decode(methodKey, response);
        }
    }
}
