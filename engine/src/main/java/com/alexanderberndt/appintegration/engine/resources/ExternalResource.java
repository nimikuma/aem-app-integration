package com.alexanderberndt.appintegration.engine.resources;

import com.alexanderberndt.appintegration.engine.resources.conversion.ConvertibleValue;
import com.alexanderberndt.appintegration.engine.resources.conversion.TextParserSupplier;
import com.alexanderberndt.appintegration.exceptions.AppIntegrationException;
import com.alexanderberndt.appintegration.utils.DataMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class ExternalResource {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public enum LoadStatus {OK, CACHED}

    @Nonnull
    private final URI uri;

    @Nonnull
    private final DataMap metadataMap = new DataMap();

    @Nonnull
    private ExternalResourceType type;

    @Nonnull
    private ConvertibleValue<?> content;

    @Nonnull
    private final List<ExternalResourceRef> referencedResources = new ArrayList<>();

    private LoadStatus loadStatus;

    private Map<String, Serializable> loadStatusDetails;

    public ExternalResource(
            @Nonnull URI uri,
            @Nullable ExternalResourceType type,
            @Nonnull InputStream content,
            @Nullable Map<String, Object> metadataMap,
            @Nullable TextParserSupplier textParserSupplier) {
        this.type = (type != null) ? type : ExternalResourceType.ANY;
        this.uri = uri;
        this.content = new ConvertibleValue<>(content, this.type.getDefaultCharset(), textParserSupplier);
        if (metadataMap != null) this.metadataMap.putAll(metadataMap);
    }

    public ExternalResource(
            @Nonnull InputStream content,
            @Nonnull ExternalResourceRef resourceRef,
            @Nullable TextParserSupplier textParserSupplier) {
        this(resourceRef.getUri(), resourceRef.getExpectedType(), content, null, textParserSupplier);
    }

    public void setMetadata(@Nonnull String name, @Nullable Object value) {
        LOG.debug("setMetadata({}, {})", name, value);
        metadataMap.setData(name, value);
    }

    public <T> T getMetadata(@Nonnull String name, @Nonnull Class<T> tClass) {
        return metadataMap.getData(name, tClass);
    }

    @Nonnull
    public DataMap getMetadataMap() {
        return metadataMap;
    }

    @Nonnull
    public InputStream getContentAsInputStream() {
        try {
            return Objects.requireNonNull(content.convertToInputStreamValue().get());
        } catch (IOException e) {
            throw new AppIntegrationException("Failed to convert content to input-stream", e);
        }
    }

    @Nonnull
    public Reader getContentAsReader() {
        try {
            return Objects.requireNonNull(content.convertToReaderValue().get());
        } catch (IOException e) {
            throw new AppIntegrationException("Failed to convert content to reader", e);
        }
    }

    public void setContent(InputStream inputStream) {
        LOG.debug("setContent(InputStream = {})", inputStream);
        this.content = this.content.recreateWithNewContent(inputStream);
    }

    public void setContent(Reader reader) {
        LOG.debug("setContent(Reader = {})", reader);
        this.content = this.content.recreateWithNewContent(reader);
    }

    public void setContent(String content) {
        LOG.debug("setContent(String = {})", content);
        this.content = this.content.recreateWithNewContent(new StringReader(content));
    }


    public <C> void setContentSupplier(@Nonnull Supplier<C> supplier, @Nonnull Class<C> type) {
        LOG.debug("setContent(Supplier<{}> = {})", type, supplier);
        this.content = this.content.recreateWithSupplier(supplier, type);
    }

    public void appendInputStreamFilter(UnaryOperator<InputStream> filterGenerator) {
        final InputStream inputStream = this.getContentAsInputStream();
        this.setContent(filterGenerator.apply(inputStream));
    }

    public <C> C getContentAsParsedObject(@Nonnull Class<C> expectedType) throws IOException {
        final C parsedObject = content.convertTo(expectedType).get();
        this.content = content.recreateWithNewContent(parsedObject);
        return parsedObject;
    }

    public void setContentAsParsedObject(Object parsedContent) {
        LOG.debug("setContent(parsedContent = {})", parsedContent);
        this.content = this.content.recreateWithNewContent(parsedContent);
    }

    public Charset getCharset() {
        return content.getCharset();
    }

    public void setCharset(Charset charset) {
        LOG.debug("setCharset({})", charset);
        this.content = this.content.recreateWithNewCharset(charset);
    }


    public void addReference(String relativeUrl) {
        this.addReference(relativeUrl, ExternalResourceType.ANY);
    }

    public void addReference(String relativeUrl, ExternalResourceType expectedType) {
        LOG.debug("addReference({},{})", relativeUrl, expectedType);
        final URI referenceUri = this.getUri().resolve(relativeUrl);
        referencedResources.add(new ExternalResourceRef(referenceUri, expectedType));
    }

    public List<ExternalResourceRef> getReferencedResources() {
        return Collections.unmodifiableList(referencedResources);
    }

    @Nonnull
    public URI getUri() {
        return uri;
    }

    @Nonnull
    public ExternalResourceType getType() {
        return type;
    }

    public void setType(@Nonnull ExternalResourceType type) {
        LOG.debug("setType({})", type);
        // don't overwrite a more qualified type
        if (!this.type.isMoreQualifiedThan(type)) {
            this.type = type;
        }
    }

    public void setLoadStatus(LoadStatus loadStatus, Map<String, Serializable> loadStatusDetails) {
        this.loadStatus = loadStatus;
        this.loadStatusDetails = loadStatusDetails;
    }

    public LoadStatus getLoadStatus() {
        return loadStatus;
    }

    public Map<String, Serializable> getLoadStatusDetails() {
        return loadStatusDetails;
    }

}
