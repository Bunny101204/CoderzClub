package com.coderzclub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "contest")
public class ContestProperties {
    /**
     * When false, ALL_USERS contests cannot be published and are hidden from user APIs.
     * Drafts may still be saved. Enabling this later does not require a model change.
     */
    private boolean globalEnabled = false;

    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public void setGlobalEnabled(boolean globalEnabled) {
        this.globalEnabled = globalEnabled;
    }
}
