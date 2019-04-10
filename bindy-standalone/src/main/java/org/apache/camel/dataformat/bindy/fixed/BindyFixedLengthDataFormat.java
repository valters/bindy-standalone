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
package org.apache.camel.dataformat.bindy.fixed;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.dataformat.bindy.BindyAbstractDataFormat;
import org.apache.camel.dataformat.bindy.BindyAbstractFactory;
import org.apache.camel.dataformat.bindy.BindyExchange;
import org.apache.camel.dataformat.bindy.BindyExchangeImpl;
import org.apache.camel.dataformat.bindy.BindyFixedLengthFactory;
import org.apache.camel.dataformat.bindy.FormatFactory;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> (
 * {@link DataFormat}) using Bindy to marshal to and from Fixed Length
 */
public class BindyFixedLengthDataFormat<TypeEntry, TypeHeader, TypeFooter> extends BindyAbstractDataFormat<TypeEntry, TypeHeader, TypeFooter> {

    private static final Logger LOG = LoggerFactory.getLogger(BindyFixedLengthDataFormat.class);

    private BindyFixedLengthFactory headerFactory;
    private BindyFixedLengthFactory footerFactory;

    public BindyFixedLengthDataFormat() {
    }

    public BindyFixedLengthDataFormat(final Class<TypeEntry> entryType, final Class<TypeHeader> headerType, final Class<TypeFooter> footerType) {
        super(entryType, headerType, footerType);
    }

    @Override
    public String getDataFormatName() {
        return "bindy-fixed";
    }

    public BindyExchange<TypeEntry, TypeHeader, TypeFooter> unmarshal(final InputStream inputStream) throws Exception {

        final BindyExchangeImpl<TypeEntry, TypeHeader, TypeFooter> exchange = exchange();

        final BindyFixedLengthFactory factory = (BindyFixedLengthFactory)getFactory();
        org.apache.camel.util.ObjectHelper.notNull(factory, "not instantiated");

        // List of Pojos
        final List<Map<String, Object>> models = new ArrayList<>();

        // Pojos of the model
        Map<String, Object> model;

        final InputStreamReader in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);

        // Scanner is used to read big file
        final Scanner scanner = new Scanner(in);
        boolean isEolSet = false;
        if( !"".equals(factory.getEndOfLine())) {
            scanner.useDelimiter(factory.getEndOfLine());
            isEolSet = true;
        }

        final AtomicInteger count = new AtomicInteger(0);

        try {

            // Parse the header if it exists
            if(((isEolSet && scanner.hasNext()) || ( !isEolSet && scanner.hasNextLine())) && factory.hasHeader()) {

                // Read the line (should not trim as its fixed length)
                final String line = getNextNonEmptyLine(scanner, count, isEolSet);

                if( !factory.skipHeader()) {
                    final Map<String, Object> headerObjMap = createModel(headerFactory, line, count.intValue());
                    exchange.header(headerObjMap);
                }
            }

            String thisLine = getNextNonEmptyLine(scanner, count, isEolSet);

            String nextLine = null;
            if(thisLine != null) {
                nextLine = getNextNonEmptyLine(scanner, count, isEolSet);
            }

            // Parse the main file content
            while(thisLine != null && nextLine != null) {

                model = createModel(factory, thisLine, count.intValue());

                // Add objects graph to the list
                models.add(model);

                thisLine = nextLine;
                nextLine = getNextNonEmptyLine(scanner, count, isEolSet);
            }

            // this line should be the last non-empty line from the file
            // optionally parse the line as a footer
            if(thisLine != null) {
                if(factory.hasFooter()) {
                    if( !factory.skipFooter()) {
                        final Map<String, Object> footerObjMap = createModel(footerFactory, thisLine, count.intValue());
                        exchange.footer(footerObjMap);
                    }
                }
                else {
                    model = createModel(factory, thisLine, count.intValue());
                    models.add(model);
                }
            }

            // BigIntegerFormatFactory if models list is empty or not
            // If this is the case (correspond to an empty stream, ...)
            if(models.size() == 0) {
                throw new java.lang.IllegalArgumentException("No records have been defined in the file");
            }
            else {
                return extractUnmarshalResult(models, exchange);
            }

        }
        finally {
            scanner.close();
            IOHelper.close(in, "in", LOG);
        }

    }

    private String getNextNonEmptyLine(final Scanner scanner, final AtomicInteger count, final boolean isEolSet) {
        String line = "";
        while(org.apache.camel.util.ObjectHelper.isEmpty(line) && ((isEolSet && scanner.hasNext()) || ( !isEolSet && scanner.hasNextLine()))) {
            count.incrementAndGet();
            if( !isEolSet) {
                line = scanner.nextLine();
            }
            else {
                line = scanner.next();
            }
        }

        if(org.apache.camel.util.ObjectHelper.isEmpty(line)) {
            return null;
        }
        else {
            return line;
        }
    }

    protected Map<String, Object> createModel(final BindyFixedLengthFactory factory, final String line, final int count) throws Exception {
        String myLine = line;

        // Check if the record length corresponds to the parameter
        // provided in the @FixedLengthRecord
        if(factory.recordLength() > 0) {
            if(isPaddingNeededAndEnable(factory, myLine)) {
                //myLine = rightPad(myLine, factory.recordLength());
            }
            if(isTrimmingNeededAndEnabled(factory, myLine)) {
                myLine = myLine.substring(0, factory.recordLength());
            }
            if((myLine.length() < factory.recordLength()
                    && !factory.isIgnoreMissingChars()) || (myLine.length() > factory.recordLength())) {
                throw new java.lang.IllegalArgumentException("Size of the record: " + myLine.length()
                        + " is not equal to the value provided in the model: " + factory.recordLength());
            }
        }

        // Create POJO where Fixed data will be stored
        final Map<String, Object> model = factory.factory();

        // Bind data from Fixed record with model classes
        factory.bind(myLine, model, count);

        // Link objects together
        factory.link(model);

        LOG.debug("Graph of objects created: {}", model);
        return model;
    }

    private boolean isTrimmingNeededAndEnabled(final BindyFixedLengthFactory factory, final String myLine) {
        return factory.isIgnoreTrailingChars() && myLine.length() > factory.recordLength();
    }

    @SuppressWarnings("unused")
    private String rightPad(final String myLine, final int length) {
        return String.format("%1$-" + length + "s", myLine);
    }

    private boolean isPaddingNeededAndEnable(final BindyFixedLengthFactory factory, final String myLine) {
        return myLine.length() < factory.recordLength() && factory.isIgnoreMissingChars();
    }

    @Override
    protected BindyAbstractFactory createModelFactory(final FormatFactory formatFactory) throws Exception {

        final BindyFixedLengthFactory factory = new BindyFixedLengthFactory(getClassType());
        factory.setFormatFactory(formatFactory);

        // Optionally initialize the header factory... using header model classes
        if(factory.hasHeader()) {
            this.headerFactory = new BindyFixedLengthFactory(factory.header());
            this.headerFactory.setFormatFactory(formatFactory);
        }

        // Optionally initialize the footer factory... using footer model classes
        if(factory.hasFooter()) {
            this.footerFactory = new BindyFixedLengthFactory(factory.footer());
            this.footerFactory.setFormatFactory(formatFactory);
        }

        return factory;
    }

}
