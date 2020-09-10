package com.alexanderberndt.appintegration.tasks.load;

import com.alexanderberndt.appintegration.engine.ResourceLoader;
import com.alexanderberndt.appintegration.engine.ResourceLoaderException;
import com.alexanderberndt.appintegration.engine.resources.ExternalResource;
import com.alexanderberndt.appintegration.engine.resources.ExternalResourceFactory;
import com.alexanderberndt.appintegration.engine.resources.ExternalResourceRef;
import com.alexanderberndt.appintegration.exceptions.AppIntegrationException;
import com.alexanderberndt.appintegration.pipeline.context.TaskContext;
import com.alexanderberndt.appintegration.pipeline.task.LoadingTask;
import org.osgi.service.component.annotations.Component;

import javax.annotation.Nonnull;
import java.io.IOException;

@Component
public class DownloadTask implements LoadingTask {

    @Override
    public ExternalResource load(@Nonnull TaskContext context, ExternalResourceRef resourceRef, ExternalResourceFactory factory) {
        ResourceLoader resourceLoader = context.getResourceLoader();
        try {
            return resourceLoader.load(resourceRef, factory);
        } catch (IOException | ResourceLoaderException e) {
            throw new AppIntegrationException("Failed to load resource " + resourceRef.getUri(), e);
        }
    }
}
