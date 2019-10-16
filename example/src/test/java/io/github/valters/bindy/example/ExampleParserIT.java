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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public class ExampleParserIT {

    ExampleParser service = new ExampleParser();

    @Test
    public void shouldParseExampleFixture() throws Exception {

        ExampleParseContext ctx = new ExampleParseContext(new File("unit test"));

        try (InputStream in = getResource("sample.cnab").openStream()) {
            service.parseStream(ctx, in);
        }

        assertThat(ctx.header.field1).isEqualTo("THIS.");
        assertThat(ctx.header.field2).isEqualTo("IS.");
        assertThat(ctx.footer.field1).isEqualTo("HERE...");
        assertThat(ctx.footer.field2).isEqualTo("BE...");

        ExampleRecord rec = ctx.entries.get(0);
        assertThat(rec.field1).isEqualTo("AND....");
        assertThat(rec.field2).isEqualTo("THIS.");
        assertThat(rec.field3).isEqualTo("IS.......");
        assertThat(rec.field4).isEqualTo("ENTRY..");
        assertThat(rec.field5).isEqualTo("ITSELF.");

    }


    public URL getResource(final String classpathResource) {

        return requireNonNull(Thread.currentThread().getContextClassLoader().getResource(classpathResource),
                "Failed to locate resource [" + classpathResource + "]");
    }
}
