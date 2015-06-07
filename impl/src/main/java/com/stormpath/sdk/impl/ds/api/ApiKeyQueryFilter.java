/*
 * Copyright 2015 Stormpath, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stormpath.sdk.impl.ds.api;

import com.stormpath.sdk.api.ApiKey;
import com.stormpath.sdk.api.ApiKeyList;
import com.stormpath.sdk.impl.api.ApiKeyParameter;
import com.stormpath.sdk.impl.api.DefaultApiKeyCriteria;
import com.stormpath.sdk.impl.api.DefaultApiKeyList;
import com.stormpath.sdk.impl.ds.DefaultResourceDataRequest;
import com.stormpath.sdk.impl.ds.Filter;
import com.stormpath.sdk.impl.ds.FilterChain;
import com.stormpath.sdk.impl.ds.ResourceAction;
import com.stormpath.sdk.impl.ds.ResourceDataRequest;
import com.stormpath.sdk.impl.ds.ResourceDataResult;
import com.stormpath.sdk.impl.http.CanonicalUri;
import com.stormpath.sdk.impl.http.QueryString;
import com.stormpath.sdk.impl.http.QueryStringFactory;
import com.stormpath.sdk.impl.http.support.DefaultCanonicalUri;
import com.stormpath.sdk.impl.query.DefaultEqualsExpressionFactory;
import com.stormpath.sdk.impl.security.DefaultSaltGenerator;
import com.stormpath.sdk.impl.security.SaltGenerator;
import com.stormpath.sdk.lang.Collections;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.stormpath.sdk.impl.api.DefaultApiKeyCriteria.*;

/**
 * @since 1.0.RC
 */
public class ApiKeyQueryFilter implements  Filter {

    private static String ENCRYPT_SECRET = ApiKeyParameter.ENCRYPT_SECRET.getName();
    private static String ENCRYPTION_KEY_SALT = ApiKeyParameter.ENCRYPTION_KEY_SALT.getName();
    private static String ENCRYPTION_KEY_SIZE = ApiKeyParameter.ENCRYPTION_KEY_SIZE.getName();
    private static String ENCRYPTION_KEY_ITERATIONS = ApiKeyParameter.ENCRYPTION_KEY_ITERATIONS.getName();
    private static String ENCRYPTION_METADATA = ApiKeyParameter.ENCRYPTION_METADATA.getName();

    private final SaltGenerator saltGenerator;
    private final QueryStringFactory queryStringFactory;

    public ApiKeyQueryFilter(QueryStringFactory queryStringFactory) {
        saltGenerator = new DefaultSaltGenerator();
        this.queryStringFactory = queryStringFactory;
    }

    /**
     * <p>
     * This method verifies the provided {@code queryString} and, if the {@code clazz} argument
     * is of type {@link ApiKey}.class or {@link ApiKeyList}.class, returns new {@link QueryString} if no encryption
     * parameters are found.
     * </p>
     * <p>
     * The new {@link QueryString} will contain all the necessary parameters to encrypt the api key secret
     * plus the parameters that are already present in the {@code queryString} argument.
     * </p>
     * <p>
     * If the filter does not apply to api keys, or the {@code queryString} argument already has the encryption parameters,
     * the same {@code queryString} argument will be returned.
     * </p>
     *
     * @param clazz       the {@link Class} to verify if this filter should run.
     * @param queryString the query string to be filtered.
     * @return a new {@link QueryString} with all the necessary parameters to encrypt the api key secret
     * plus the parameters that are already present in the {@code queryString} argument, if the filter
     * applies to api keys and the {@code queryString} argument does not have the encryption parameters;
     * otherwise, the same {@code queryString} argument will be returned.
     */
    public QueryString filter(Class clazz, QueryString queryString) {

        if ((ApiKey.class.isAssignableFrom(clazz) || ApiKeyList.class.isAssignableFrom(clazz))
                && (queryString == null || !queryString.containsKey(ENCRYPT_SECRET))) {

            DefaultApiKeyCriteria criteria = new DefaultApiKeyCriteria();
            criteria.add(new DefaultEqualsExpressionFactory(ENCRYPT_SECRET).eq(Boolean.TRUE));
            criteria.add(new DefaultEqualsExpressionFactory(ENCRYPTION_KEY_SIZE).eq(128));
            criteria.add(new DefaultEqualsExpressionFactory(ENCRYPTION_KEY_ITERATIONS).eq(1024));
            criteria.add(new DefaultEqualsExpressionFactory(ENCRYPTION_KEY_SALT).eq(saltGenerator.generate()));

            QueryString qs = queryStringFactory.createQueryString(criteria);

            if (queryString != null && !queryString.isEmpty()) {

                for (Map.Entry<String, String> entry : queryString.entrySet()) {

                    if (!entry.getValue().equals(ENCRYPT_SECRET)
                            && !entry.getValue().equals(ENCRYPTION_KEY_SIZE)
                            && !entry.getValue().equals(ENCRYPTION_KEY_ITERATIONS)
                            && !entry.getValue().equals(ENCRYPTION_KEY_SALT)) {

                        qs.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            return qs;
        }

        return queryString;
    }

    @Override
    public ResourceDataResult filter(ResourceDataRequest request, FilterChain chain) {

        if (request.getAction() == ResourceAction.DELETE) {
            return chain.filter(request);
        }

        Class clazz = request.getResourceClass();

        boolean isCollection = false;

        if (!(ApiKey.class.isAssignableFrom(clazz) || (isCollection = ApiKeyList.class.isAssignableFrom(clazz)))) {
            return chain.filter(request);
        }

        QueryString query = request.getUri().getQuery();

        boolean isQueryEmpty = Collections.isEmpty(query);

        boolean addEncryptionCriteria = isQueryEmpty || !query.containsKey(ENCRYPT_SECRET);

        //If encryptSecret is present, we assume encryptionKeySalt is there as well. If not the request to the server
        //will error.
        boolean addEncryptionMetadata = addEncryptionCriteria || Boolean.parseBoolean(query.get(ENCRYPT_SECRET));

        if (addEncryptionCriteria) {

            DefaultApiKeyCriteria criteria = new DefaultApiKeyCriteria();
            criteria.add(new DefaultEqualsExpressionFactory(ENCRYPT_SECRET).eq(Boolean.TRUE));
            criteria.add(new DefaultEqualsExpressionFactory(ENCRYPTION_KEY_SIZE).eq(DEFAULT_ENCRYPTION_SIZE));
            criteria.add(new DefaultEqualsExpressionFactory(ENCRYPTION_KEY_ITERATIONS).eq(DEFAULT_ENCRYPTION_ITERATIONS));
            criteria.add(new DefaultEqualsExpressionFactory(ENCRYPTION_KEY_SALT).eq(saltGenerator.generate()));

            QueryString encryptionQueryParams = queryStringFactory.createQueryString(criteria);

            if (query == null) {
                CanonicalUri uri = new DefaultCanonicalUri(request.getUri().getAbsolutePath(), encryptionQueryParams);
                request = new DefaultResourceDataRequest(request.getAction(), uri, request.getResourceClass(), request.getData());
            } else {
                query.putAll(encryptionQueryParams);
            }
        }

        ResourceDataResult result = chain.filter(request);

        if (addEncryptionMetadata) {

            query = request.getUri().getQuery();

            Map<String, Object> encryptionMetadata = new LinkedHashMap<String, Object>();

            String salt = query.get(ENCRYPTION_KEY_SALT);

            Integer size = query.containsKey(ENCRYPTION_KEY_SIZE) ? Integer.valueOf(query.get(ENCRYPTION_KEY_SIZE)) : DEFAULT_ENCRYPTION_SIZE;

            Integer iterations = query.containsKey(ENCRYPTION_KEY_ITERATIONS) ? Integer.valueOf(query.get(ENCRYPTION_KEY_ITERATIONS)) : DEFAULT_ENCRYPTION_ITERATIONS;

            encryptionMetadata.put(ENCRYPTION_KEY_SALT, salt);
            encryptionMetadata.put(ENCRYPTION_KEY_SIZE, size);
            encryptionMetadata.put(ENCRYPTION_KEY_ITERATIONS, iterations);


            Map<String, Object> data = result.getData();

            if (DefaultApiKeyList.isCollectionResource(data)) {

                @SuppressWarnings("unchecked")
                Collection<Map<String, Object>> items = (Collection<Map<String, Object>>) data.get(DefaultApiKeyList.ITEMS_PROPERTY_NAME);

                for (Map<String, Object> item : items) {
                    item.put(ENCRYPTION_METADATA, encryptionMetadata);
                }
            } else {
                data.put(ENCRYPTION_METADATA, encryptionMetadata);
            }
        }
        return result;
    }
}
