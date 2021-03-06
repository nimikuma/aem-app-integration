package com.alexanderberndt.appintegration.engine.testsupport;

import com.alexanderberndt.appintegration.engine.Application;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class TestApplication implements Application {

    @Nonnull
    private final String applicationId;

    @Nonnull
    private final String applicationInfoUrl;

    @Nonnull
    private final String resourceLoaderName;

    @Nonnull
    private final String processingPipelineName;

    @Nonnull
    private final List<String> contextProviderNames;

    @Nullable
    private final Map<String, Object> globalProperties;

    public TestApplication(@Nonnull String applicationId,
                           @Nonnull String applicationInfoUrl,
                           @Nonnull String resourceLoaderName,
                           @Nonnull String processingPipelineName,
                           @Nonnull List<String> contextProviderNames,
                           @Nullable Map<String, Object> globalProperties) {
        this.applicationId = applicationId;
        this.applicationInfoUrl = applicationInfoUrl;
        this.resourceLoaderName = resourceLoaderName;
        this.processingPipelineName = processingPipelineName;
        this.contextProviderNames = contextProviderNames;
        this.globalProperties = globalProperties;
    }

    /**
     * Globally unique id for the application.
     *
     * @return application-id
     */
    @Nonnull
    @Override
    public String getApplicationId() {
        return applicationId;
    }

    @Override
    @Nonnull
    public String getApplicationInfoUrl() {
        return applicationInfoUrl;
    }

    @Override
    @Nonnull
    public String getResourceLoaderName() {
        return resourceLoaderName;
    }

    @Override
    @Nonnull
    public String getProcessingPipelineName() {
        return processingPipelineName;
    }

    @Nonnull
    @Override
    public List<String> getContextProviderNames() {
        return contextProviderNames;
    }

    @Override
    @Nullable
    public Map<String, Object> getGlobalProperties() {
        return globalProperties;
    }
}
