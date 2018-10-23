package twitch4j.helix.endpoints;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ContextedRuntimeException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import twitch4j.helix.domain.BitsLeaderboard;
import twitch4j.helix.domain.ExtensionAnalyticsList;
import twitch4j.helix.domain.GameAnalyticsList;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@Tag("integration")
public class AnalyticsEndpointTest extends AbtractEndpointTest {

    /**
     * Get Extension Analytics
     */
    @Test
    @DisplayName("Fetch extension analytics")
    public void getExtensionAnalytics() {
        // TestCase
        try {
            ExtensionAnalyticsList resultList = testUtils.getTwitchHelixClient().getExtensionAnalyticUrl(testUtils.getCredential().getAuthToken(),null,10, null, null, null, null);
        } catch (ContextedRuntimeException ex) {
            String responseBody = (String) ex.getFirstContextValue("responseBody");
            assertTrue(responseBody.contains("User Does Not Have Extensions"), "Test Account does not have extensions!");
        }
    }

    /**
     * Get Game Analytics
     */
    @Test
    @DisplayName("Fetch game analytics")
    @Disabled
    public void getGameAnalytics() {
        // TestCase
        try {
            GameAnalyticsList resultList = testUtils.getTwitchHelixClient().getGameAnalyticUrl(testUtils.getCredential().getAuthToken(),null,10, null, null, null, null);
        } catch (ContextedRuntimeException ex) {
            String responseBody = (String) ex.getFirstContextValue("responseBody");
            System.out.println(responseBody);
            //assertTrue(responseBody.contains("User Does Not Have Extensions"), "Test Account does not have extensions!");
        }
    }

}

