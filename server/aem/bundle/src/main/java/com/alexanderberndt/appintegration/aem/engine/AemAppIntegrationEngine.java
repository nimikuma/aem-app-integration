package com.alexanderberndt.appintegration.aem.engine;

import com.alexanderberndt.appintegration.aem.engine.logging.AemLogAppender;
import com.alexanderberndt.appintegration.engine.AppIntegrationEngine;
import com.alexanderberndt.appintegration.engine.AppIntegrationFactory;
import com.alexanderberndt.appintegration.engine.Application;
import com.alexanderberndt.appintegration.engine.ResourceLoader;
import com.alexanderberndt.appintegration.engine.logging.LogAppender;
import com.alexanderberndt.appintegration.exceptions.AppIntegrationException;
import org.apache.sling.api.resource.*;
import org.eclipse.jetty.util.log.Log;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.GregorianCalendar;

import static org.apache.sling.api.resource.ResourceResolverFactory.SUBSERVICE;

@Component(service = AemAppIntegrationEngine.class)
public class AemAppIntegrationEngine extends AppIntegrationEngine<SlingApplicationInstance, AemGlobalContext> {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String SUB_SERVICE_ID = "engine";

    @Reference
    private AemAppIntegrationFactory factory;

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    protected AppIntegrationFactory<SlingApplicationInstance, AemGlobalContext> getFactory() {
        return factory;
    }

    @Override
    protected AemGlobalContext createGlobalContext(@Nonnull String applicationId, @Nonnull Application application) {
        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(Collections.singletonMap(SUBSERVICE, SUB_SERVICE_ID))) {
            // ToDo: Re-factor to handle resource-loader loading in super-class
            final ResourceLoader resourceLoader = factory.getResourceLoader(application.getResourceLoaderName());
            return new AemGlobalContext(resolver, resourceLoader, createLogAppender(resolver, applicationId));
        } catch (LoginException | PersistenceException e) {
            throw new AppIntegrationException("Cannot login to service user session!", e);
        }
    }

    public LogAppender createLogAppender(@Nonnull ResourceResolver resolver, @Nonnull String applicationId) throws PersistenceException {

        final GregorianCalendar now = new GregorianCalendar();
        final String rootPath = String.format("/var/aem-app-integration/logs/%1$s/%2$TY/%2$Tm/%2$Td/%2$TH/%2$TM", applicationId, now);

        LOG.debug("Create Log-Root {}", rootPath);
        final Resource rootLoggingRes = ResourceUtil.getOrCreateResource(
                resolver, rootPath, "alex/resourcetype", "alex/intermediate", true);

        final String logResName = ResourceUtil.createUniqueChildName(rootLoggingRes, "prefetch");
        final Resource logRes = resolver.create(rootLoggingRes, logResName, Collections.singletonMap("date", now));

        return new AemLogAppender(logRes);
    }
}
