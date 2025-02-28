package searchengine.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class IndexingConfig {

    private final String userAgent;
    private final String referrer;

    public IndexingConfig(
            @Value("${indexing-settings.user-agent}") String userAgent,
            @Value("${indexing-settings.referrer}") String referrer
    ) {
        this.userAgent = userAgent;
        this.referrer = referrer;
    }

}