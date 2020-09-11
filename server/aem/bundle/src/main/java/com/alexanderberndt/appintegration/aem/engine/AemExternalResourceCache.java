package com.alexanderberndt.appintegration.aem.engine;

import com.alexanderberndt.appintegration.engine.resources.ExternalResource;
import com.alexanderberndt.appintegration.engine.resources.ExternalResourceFactory;
import com.alexanderberndt.appintegration.engine.resources.ExternalResourceRef;
import com.alexanderberndt.appintegration.engine.resources.ExternalResourceType;
import com.alexanderberndt.appintegration.exceptions.AppIntegrationException;
import com.day.cq.commons.jcr.JcrUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.jackrabbit.JcrConstants.*;

public class AemExternalResourceCache {

    public static final String CACHE_ROOT = "/var/aem-app-integration/%s/files";

    public static final int MINUTES_UNTIL_LOCKS_EXPIRE = 5;

    public static final String LOCK_ATTR = "lock";
    public static final String LOCKED_SINCE_ATTR = "lockedSince";
    public static final String VERSION_ATTR = "version";
    public static final String URI_ATTR = "uri";
    public static final String TYPE_ATTR = "TYPE";
    public static final String JCR_PATH_SEPARATOR = "/";

    @Nonnull
    private final ResourceResolver resolver;

    // root-path, e.g. /var/app-integration/<my-app>/files
    @Nonnull
    private final String rootPath;

    private final Random random = new Random();

    private String lockId;


    public AemExternalResourceCache(@Nonnull ResourceResolver resolver, @Nonnull String applicationId) {
        this.resolver = resolver;
        this.rootPath = String.format(CACHE_ROOT, applicationId);
    }


    public void storeResource(@Nonnull ExternalResource resource, @Nullable String version) throws IOException {

        // find existing entry
        final Resource cachePathRes = getOrCreateResource(getCachePath(resource.getUri()));
        final String hashCode = Integer.toHexString(resource.getUri().toString().hashCode());

        // find new cache-entry name
        String entryName;
        int i = 0;
        do {
            if (i++ > 20) {
                throw new AppIntegrationException("Could NOT create a unique file entry for " + cachePathRes.getPath());
            }
            entryName = hashCode + "_" + Integer.toHexString(random.nextInt(0x1000));
        } while (cachePathRes.getChild(entryName) != null);


        final Resource targetCacheRes = resolver.create(cachePathRes, entryName, null);
        final ModifiableValueMap modifiableValueMap = Objects.requireNonNull(targetCacheRes.adaptTo(ModifiableValueMap.class));
        modifiableValueMap.put(URI_ATTR, resource.getUri().toString());
        if (StringUtils.isNotBlank(version)) {
            modifiableValueMap.put(VERSION_ATTR, version);
        }

        Resource res = resolver.create(targetCacheRes, "data", Collections.singletonMap(JCR_PRIMARYTYPE, NT_FILE));

        Map<String, Object> propertiesMap = new HashMap<>();
        propertiesMap.put(JCR_PRIMARYTYPE, NT_RESOURCE);
        propertiesMap.put(JCR_MIMETYPE, "text/plain");
        propertiesMap.put(JCR_DATA, resource.getContentAsInputStream());
        resolver.create(res, JCR_CONTENT, propertiesMap);
    }


    @Nullable
    public ExternalResource getCachedResource(ExternalResourceRef resourceRef, @Nonnull ExternalResourceFactory resourceFactory) {

        final Resource rootRes = resolver.getResource(rootPath);
        if (rootRes == null) {
            return null;
        }
        final String curVersion = StringUtils.defaultIfBlank(rootRes.getValueMap().get(VERSION_ATTR, String.class), null);

        // find existing entry
        final String cachePath = getCachePath(resourceRef.getUri());
        final Resource cachePathRes = resolver.getResource(cachePath);
        if (cachePathRes == null) {
            return null;
        }

        // iterate over all entry
        Iterator<Resource> cacheResIter = cachePathRes.listChildren();
        while (cacheResIter.hasNext()) {
            final Resource res = cacheResIter.next();
            final ValueMap valueMap = res.getValueMap();
            if (StringUtils.equals(resourceRef.getUri().toString(), valueMap.get(URI_ATTR, String.class))
                    && StringUtils.equals(curVersion, StringUtils.defaultIfBlank(valueMap.get(VERSION_ATTR, String.class), null))) {
                // found
                try {
                    final URI uri = new URI(Objects.requireNonNull(valueMap.get(URI_ATTR, String.class)));
                    final ExternalResourceType type = ExternalResourceType.parse(valueMap.get(TYPE_ATTR, String.class));

                    final Resource dataRes = Objects.requireNonNull(res.getChild("data"));

                    // ToDo: Implement futures for actual content
                    final InputStream content = Objects.requireNonNull(dataRes.adaptTo(InputStream.class));
                    final Map<String, Object> metadataMap = Optional.of(res)
                            .map(r -> r.getChild("metadata"))
                            .map(Resource::getValueMap)
                            .map(vm ->
                                vm.entrySet().stream()
                                        .filter(entry -> !StringUtils.startsWith(entry.getKey(), "jcr:"))
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                            ).orElse(null);

                    return resourceFactory.createExternalResource(uri, type, content, metadataMap);

                } catch (URISyntaxException | NullPointerException e) {
                    e.printStackTrace();
                }
            }
        }

        // nothing found in cache
        return null;
    }

    public void markResourceRefreshed(ExternalResource resource) {

    }


    public boolean lock() throws RepositoryException, PersistenceException {
        this.lockId = null;
        final Resource rootRes = getOrCreateResource(rootPath);
        if (canBeLocked(rootRes)) {
            final String tempLockId = Long.toHexString((long) Math.floor(Math.random() * 0x100000000L));
            final ModifiableValueMap valueMap = Objects.requireNonNull(rootRes.adaptTo(ModifiableValueMap.class));
            valueMap.put(LOCK_ATTR, tempLockId);
            valueMap.put(LOCKED_SINCE_ATTR, LocalDateTime.now());
            resolver.commit();
            this.lockId = tempLockId;
            return true;
        } else {
            return false;
        }
    }

    public boolean refreshLock() throws PersistenceException {
        if (lockId == null) {
            throw new AppIntegrationException("Cannot refresh lock, as cache is not locked yet!");
        }

        final Resource rootRes = resolver.getResource(rootPath);
        if (rootRes == null) {
            throw new AppIntegrationException("Cannot refresh lock on " + rootPath + ", because path not found!");
        }

        final ModifiableValueMap valueMap = Objects.requireNonNull(rootRes.adaptTo(ModifiableValueMap.class));
        if (lockId.equals(valueMap.get(LOCK_ATTR, String.class))) {
            valueMap.put(LOCKED_SINCE_ATTR, LocalDateTime.now());
            resolver.commit();
            return true;
        }

        return false;
    }


    public void releaseLock() throws PersistenceException {

        if (lockId == null) {
            throw new AppIntegrationException("Cannot refresh lock, as cache is not locked yet!");
        }

        final Resource rootRes = resolver.getResource(rootPath);
        if (rootRes != null) {
            final ModifiableValueMap valueMap = Objects.requireNonNull(rootRes.adaptTo(ModifiableValueMap.class));
            if (this.lockId.equals(valueMap.get(LOCK_ATTR, String.class))) {
                valueMap.remove(LOCK_ATTR);
                valueMap.remove(LOCKED_SINCE_ATTR);
                resolver.commit();
            }
        }
    }

    public void setActiveVersion(@Nullable String version) {
        final Resource rootRes = resolver.getResource(rootPath);
        if (rootRes == null) {
            throw new AppIntegrationException("cannot find root-path " + rootPath + " to set active version");
        }
        final String curVersion = rootRes.getValueMap().get(VERSION_ATTR, String.class);
        if (!StringUtils.equals(curVersion, version)) {
            final ModifiableValueMap modifiableValueMap = Objects.requireNonNull(rootRes.adaptTo(ModifiableValueMap.class));
            if (StringUtils.isNotBlank(version)) {
                modifiableValueMap.put(VERSION_ATTR, version);
            } else {
                modifiableValueMap.remove(VERSION_ATTR);
            }
        }
    }

    private boolean canBeLocked(Resource resource) {
        final ValueMap valueMap = resource.getValueMap();
        final String currentLockId = valueMap.get(LOCK_ATTR, String.class);

        // is resource not locked yet?
        if (StringUtils.isBlank(currentLockId)) {
            return true;
        }

        // is resource locked by ourselves
        if (this.lockId.equals(currentLockId)) {
            return true;
        }

        // is lock expired?
        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime lockedSince = valueMap.get(LOCKED_SINCE_ATTR, LocalDateTime.class);
        return (lockedSince == null) || Duration.between(lockedSince, now).toMinutes() >= MINUTES_UNTIL_LOCKS_EXPIRE;
    }

    @Nonnull
    protected Resource getOrCreateResource(String path) throws PersistenceException {
        final String[] splitPath = path.split(JCR_PATH_SEPARATOR);
        if ((splitPath.length < 3) || (!splitPath[0].equals(""))) {
            throw new AppIntegrationException("Cannot create path " + path + "! Must be an absolute path with at least two levels");
        }

        // handle top-level path-elem
        final String topLevelPath = JCR_PATH_SEPARATOR + splitPath[1];
        Resource curResource = resolver.getResource(topLevelPath);
        if (curResource == null) {
            throw new AppIntegrationException("Cannot create path " + path + ", as top-level paths should already exists: " + topLevelPath);
        }

        // handle 2nd-level path-elements and deeper
        for (int i = 2; i < splitPath.length; i++) {
            curResource = getOrCreateChild(curResource, splitPath[i]);
        }

        return curResource;
    }

    @Nonnull
    private Resource getOrCreateChild(@Nonnull Resource parentRes, @Nonnull String childName) throws PersistenceException {
        Resource childRes = parentRes.getChild(childName);
        if (childRes == null) {
            childRes = resolver.create(parentRes, childName, Collections.singletonMap(JCR_PRIMARYTYPE, NT_UNSTRUCTURED));
        }
        return childRes;
    }


    private String getCachePath(URI uri) {
        final List<String> splitPath = new ArrayList<>(Arrays.asList(StringUtils.splitByWholeSeparator(uri.getPath(), "/")));
        if (StringUtils.isNotBlank(uri.getQuery())) {
            splitPath.add(Integer.toHexString(uri.getQuery().hashCode()));
        }
        for (int i = 0; i < splitPath.size(); i++) {
            splitPath.set(i, JcrUtil.createValidName(splitPath.get(i)));
        }
        return rootPath + JCR_PATH_SEPARATOR + String.join(JCR_PATH_SEPARATOR, splitPath) + JCR_PATH_SEPARATOR + "jcr:content";
    }
}