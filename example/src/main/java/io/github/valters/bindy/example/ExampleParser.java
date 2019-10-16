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
package io.github.valters.bindy.example;

import static java.util.Objects.requireNonNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.camel.dataformat.bindy.BindyExchange;
import org.apache.camel.dataformat.bindy.fixed.BindyFixedLengthDataFormat;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bindy accepts an input stream: this parser handles providing the stream from the input file.
 */
public class ExampleParser {

    private static final Logger log = LoggerFactory.getLogger(ExampleParser.class);

    public static final BindyFixedLengthDataFormat<ExampleRecord, ExampleHeader, ExampleFooter> PARSER = new BindyFixedLengthDataFormat<>(
            ExampleRecord.class,
            ExampleHeader.class,
            ExampleFooter.class);

    public ExampleParseContext parseFile(final File file) {

        Validate.notNull(file, "file argument may not be null");

        log.info("file [{}] has {} byte(s)", file, file.length());

        Validate.validState(file.length() > 0, "Error, validation failed: input file [%s] is zero length. " +
                "Please investigate the root cause. (For example, maybe error occurred during file transmission, or issue was caused by running out of disk space.)",
                file);

        try(InputStream in = new BufferedInputStream(new FileInputStream(file))) {

            return parseStream(new ExampleParseContext(file), in);
        }
        catch(final Exception e) {
            throw new RuntimeException("Error, failed while parsing file [" + file + "], please see cause exception.", e);
        }

    }

    protected ExampleParseContext parseStream(final ExampleParseContext ctx, final InputStream in) throws Exception {

        final BindyExchange<ExampleRecord, ExampleHeader, ExampleFooter> res = ExampleParser.PARSER.unmarshal(in);
        ctx.header = res.getHeader();
        ctx.footer = res.getFooter();

        return translateList(ctx, res.getEntries());
    }

    protected ExampleParseContext translateList(final ExampleParseContext context, final List<ExampleRecord> records) {

        requireNonNull(records, "records argument may not be null");

        log.info("start processing {} record(s)", records.size());

        for(final ExampleRecord rec : records) {

            requireNonNull(rec, "record may not be null");

            context.entries.add(rec);
        }

        log.info("{} record(s) parsed", context.entries.size());

        return context;
    }

}
