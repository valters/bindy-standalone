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

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Keeps the state of parsing.
 */
public class ExampleParseContext {

    public final File file;

    public ExampleHeader header;
    public ExampleFooter footer;

    public final List<ExampleRecord> entries = new ArrayList<>();

    public ExampleParseContext(final File file) {
        this.file = requireNonNull(file, "file may not be null");
    }

    public String fileName() {
        return file.getName();
    }

}
