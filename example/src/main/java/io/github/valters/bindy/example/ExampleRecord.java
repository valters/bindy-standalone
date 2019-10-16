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

import java.time.LocalDate;
import java.util.Optional;

import org.apache.camel.dataformat.bindy.annotation.BindyConverter;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Example of parsing a fixed length format file.
 */
@FixedLengthRecord(length = 35, crlf = "UNIX", header = ExampleHeader.class, footer = ExampleFooter.class)
public class ExampleRecord {

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    @DataField(pos = 1, length = 7, paddingChar = '.')
    String field1;

    @DataField(pos = 8, length = 5, paddingChar = '.')
    String field2;

    @DataField(pos = 13, length = 9, paddingChar = '.')
    String field3;

    @DataField(pos = 22, length = 7, paddingChar = '.')
    String field4;

    @DataField(pos = 29, length = 8, paddingChar = '.')
    String field5;

}
