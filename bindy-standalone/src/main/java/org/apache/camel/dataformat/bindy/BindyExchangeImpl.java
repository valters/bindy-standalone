/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dataformat.bindy;

import java.util.List;
import java.util.Map;

public class BindyExchangeImpl<TypeEntry, TypeHeader, TypeFooter> implements BindyExchange<TypeEntry, TypeHeader, TypeFooter> {

    private List<TypeEntry> entries;

    private TypeHeader header;

    private TypeFooter footer;

    private final Class<TypeHeader> headerClass;
    private final Class<TypeFooter> footerClass;

    BindyExchangeImpl(final Class<TypeHeader> header, final Class<TypeFooter> footer) {
        super();
        this.headerClass = header;
        this.footerClass = footer;
    }

    @Override
    public List<TypeEntry> getEntries() {
        return entries;
    }

    @Override
    public TypeHeader getHeader() {
        return header;
    }

    @Override
    public TypeFooter getFooter() {
        return footer;
    }

    @SuppressWarnings("unchecked")
    public void header(final Map<String, Object> headerObjMap) {
        header = (TypeHeader)headerObjMap.get(headerClass.getName());
    }

    @SuppressWarnings("unchecked")
    public void footer(final Map<String, Object> footerObjMap) {
        footer = (TypeFooter)footerObjMap.get(footerClass.getName());
    }

    public void entries(final List<TypeEntry> entries) {
        this.entries = entries;
    }

}
