/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.camel.dataformat.bindy;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.camel.dataformat.bindy.annotation.FormatFactories;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.format.factories.DefaultFactoryRegistry;
import org.apache.camel.dataformat.bindy.format.factories.FactoryRegistry;
import org.apache.camel.dataformat.bindy.format.factories.FormatFactoryInterface;
import org.apache.camel.spi.DataFormatName;

public abstract class BindyAbstractDataFormat<TypeEntry, TypeHeader, TypeFooter> implements DataFormatName {

    private Class<TypeEntry> entryType;
    private Class<TypeHeader> headerType;
    private Class<TypeFooter> footerType;

    private String locale;
    private BindyAbstractFactory modelFactory;
    private boolean unwrapSingleInstance = true;

    public BindyAbstractDataFormat() {
    }

    protected BindyAbstractDataFormat(final Class<TypeEntry> entryType, final Class<TypeHeader> headerType, final Class<TypeFooter> footerType) {
        this.entryType = entryType;
        this.headerType = headerType;
        this.footerType = footerType;
    }

    protected BindyExchangeImpl<TypeEntry, TypeHeader, TypeFooter> exchange() {
        return new BindyExchangeImpl<>(headerType, footerType);
    }

    public Class<?> getClassType() {
        return entryType;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(final String locale) {
        this.locale = locale;
    }

    public boolean isUnwrapSingleInstance() {
        return unwrapSingleInstance;
    }

    public void setUnwrapSingleInstance(final boolean unwrapSingleInstance) {
        this.unwrapSingleInstance = unwrapSingleInstance;
    }

    public BindyAbstractFactory getFactory() throws Exception {
        if(modelFactory == null) {
            final FormatFactory formatFactory = createFormatFactory();
            registerAdditionalConverter(formatFactory);
            modelFactory = createModelFactory(formatFactory);
            modelFactory.setLocale(locale);
        }
        return modelFactory;
    }

    private void registerAdditionalConverter(final FormatFactory formatFactory) throws IllegalAccessException, InstantiationException {
        final Function<Class<?>, FormatFactories> g = aClass -> aClass.getAnnotation(FormatFactories.class);
        final Function<FormatFactories, List<Class<? extends FormatFactoryInterface>>> h = formatFactories -> Arrays.asList(formatFactories.value());
        final List<Class<? extends FormatFactoryInterface>> array = Optional
                .ofNullable(entryType)
                .map(g)
                .map(h)
                .orElse(Collections.emptyList());
        for(final Class<? extends FormatFactoryInterface> l : array) {
            formatFactory.getFactoryRegistry().register(l.newInstance());
        }
    }

    private FormatFactory createFormatFactory() {
        final FormatFactory formatFactory = new FormatFactory();
        final FactoryRegistry factoryRegistry = createFactoryRegistry();
        formatFactory.setFactoryRegistry(factoryRegistry);
        return formatFactory;
    }

    private FactoryRegistry createFactoryRegistry() {
        return tryToGetFactoryRegistry();
    }

    private FactoryRegistry tryToGetFactoryRegistry() {
        return new DefaultFactoryRegistry();
    }

    public void setModelFactory(final BindyAbstractFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    protected Map<String, Object> createLinkedFieldsModel(final Object model) throws IllegalAccessException {
        final Map<String, Object> row = new HashMap<>();
        createLinkedFieldsModel(model, row);
        return row;
    }

    protected void createLinkedFieldsModel(final Object model, final Map<String, Object> row) throws IllegalAccessException {
        for(final Field field : model.getClass().getDeclaredFields()) {
            final Link linkField = field.getAnnotation(Link.class);
            if(linkField != null) {
                final boolean accessible = field.isAccessible();
                field.setAccessible(true);
                if( !row.containsKey(field.getType().getName())) {
                    row.put(field.getType().getName(), field.get(model));
                }
                field.setAccessible(accessible);
            }
        }
    }

    protected abstract BindyAbstractFactory createModelFactory(FormatFactory formatFactory) throws Exception;

    protected BindyExchange<TypeEntry, TypeHeader, TypeFooter> extractUnmarshalResult(final List<Map<String, Object>> models,
            final BindyExchangeImpl<TypeEntry, TypeHeader, TypeFooter> exchange) {

        requireNonNull(entryType);

        // we expect to findForFormattingOptions this type in the models, and grab only that type
        final List<TypeEntry> answer = new ArrayList<>();
        for(final Map<String, Object> entry : models) {
            @SuppressWarnings("unchecked")
            final TypeEntry data = (TypeEntry)entry.get(entryType.getName());
            if(data != null) {
                answer.add(data);
            }
        }

        exchange.entries(answer);
        return exchange;
    }

}
