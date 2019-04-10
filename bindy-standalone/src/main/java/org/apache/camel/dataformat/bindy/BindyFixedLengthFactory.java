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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.dataformat.bindy.annotation.BindyConverter;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.format.FormatException;
import org.apache.camel.dataformat.bindy.util.ConverterUtils;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The BindyCsvFactory is the class who allows to : Generate a model associated
 * to a fixed length record, bind data from a record to the POJOs, export data of POJOs
 * to a fixed length record and format data into String, Date, Double, ... according to
 * the format/pattern defined
 */
public class BindyFixedLengthFactory extends BindyAbstractFactory implements BindyFactory {

    private static final Logger LOG = LoggerFactory.getLogger(BindyFixedLengthFactory.class);

    boolean isOneToMany;

    private final Map<Integer, DataField> dataFields = new TreeMap<>();
    private final Map<Integer, Field> annotatedFields = new TreeMap<>();

    private int numberOptionalFields;
    private int numberMandatoryFields;
    private int totalFields;

    private boolean hasHeader;
    private boolean skipHeader;
    private boolean isHeader;
    private boolean hasFooter;
    private boolean skipFooter;
    private boolean isFooter;
    private char paddingChar;
    private int recordLength;
    private boolean ignoreTrailingChars;
    private boolean ignoreMissingChars;

    private Class<?> header;
    private Class<?> footer;

    public BindyFixedLengthFactory(final Class<?> type) throws Exception {
        super(type);

        header = void.class;
        footer = void.class;

        // initialize specific parameters of the fixed length model
        initFixedLengthModel();
    }

    /**
     * method uses to initialize the model representing the classes who will
     * bind the data. This process will scan for classes according to the
     * package name provided, check the annotated classes and fields
     */
    public void initFixedLengthModel() throws Exception {

        // Find annotated fields declared in the Model classes
        initAnnotatedFields();

        // initialize Fixed length parameter(s)
        // from @FixedLengthrecord annotation
        initFixedLengthRecordParameters();
    }

    @Override
    public void initAnnotatedFields() {

        for(final Class<?> cl : models) {

            final List<Field> linkFields = new ArrayList<>();

            if(LOG.isDebugEnabled()) {
                LOG.debug("Class retrieved: {}", cl.getName());
            }

            for(final Field field : cl.getDeclaredFields()) {
                final DataField dataField = field.getAnnotation(DataField.class);
                if(dataField != null) {

                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Position defined in the class: {}, position: {}, Field: {}", cl.getName(), dataField.pos(), dataField);
                    }

                    if(dataField.required()) {
                        ++numberMandatoryFields;
                    }
                    else {
                        ++numberOptionalFields;
                    }

                    dataFields.put(dataField.pos(), dataField);
                    annotatedFields.put(dataField.pos(), field);
                }

                final Link linkField = field.getAnnotation(Link.class);

                if(linkField != null) {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Class linked: {}, Field: {}", cl.getName(), field);
                    }
                    linkFields.add(field);
                }

            }

            if( !linkFields.isEmpty()) {
                annotatedLinkFields.put(cl.getName(), linkFields);
            }

            totalFields = numberMandatoryFields + numberOptionalFields;

            if(LOG.isDebugEnabled()) {
                LOG.debug("Number of optional fields: {}", numberOptionalFields);
                LOG.debug("Number of mandatory fields: {}", numberMandatoryFields);
                LOG.debug("Total: {}", totalFields);
            }

        }
    }

    public void bind(final String record, final Map<String, Object> model, final int line) throws Exception {

        int pos = 1;
        int counterMandatoryFields = 0;
        DataField dataField;
        String token;
        int offset = 1;
        int length;
        String delimiter;
        Field field;

        // Iterate through the list of positions
        // defined in the @DataField
        // and grab the data from the line
        final Collection<DataField> c = dataFields.values();
        final Iterator<DataField> itr = c.iterator();

        // this iterator is for a link list that was built using items in order
        while(itr.hasNext()) {
            dataField = itr.next();
            length = dataField.length();
            delimiter = dataField.delimiter();

            if(length == 0 && dataField.lengthPos() != 0) {
                final Field lengthField = annotatedFields.get(dataField.lengthPos());
                lengthField.setAccessible(true);
                final Object modelObj = model.get(lengthField.getDeclaringClass().getName());
                final Object lengthObj = lengthField.get(modelObj);
                length = ((Integer)lengthObj).intValue();
            }
            if(length < 1 && delimiter == null && dataField.lengthPos() == 0) {
                throw new IllegalArgumentException("Either length or delimiter must be specified for the field : " + dataField.toString());
            }
            if(offset - 1 <= -1) {
                throw new IllegalArgumentException("Offset/Position of the field " + dataField.toString()
                        + " cannot be negative");
            }

            // skip ahead if the expected position is greater than the offset
            if(dataField.pos() > offset) {
                LOG.debug("skipping ahead [{}] chars.", dataField.pos() - offset);
                offset = dataField.pos();
            }

            if(length > 0) {
                if(record.length() < offset) {
                    token = "";
                }
                else {
                    int endIndex = offset + length - 1;
                    if(endIndex > record.length()) {
                        endIndex = record.length();
                    }
                    token = record.substring(offset - 1, endIndex);
                }
                offset += length;
            }
            else if( !"".equals(delimiter)) {
                final String tempToken = record.substring(offset - 1, record.length());
                token = tempToken.substring(0, tempToken.indexOf(delimiter));
                // include the delimiter in the offset calculation
                offset += token.length() + 1;
            }
            else {
                // defined as a zero-length field
                token = "";
            }

            if(dataField.trim()) {
                token = trim(token, dataField, paddingChar);
                //token = token.trim();
            }

            // Check mandatory field
            if(dataField.required()) {

                // Increment counter of mandatory fields
                ++counterMandatoryFields;

                // Check if content of the field is empty
                // This is not possible for mandatory fields
                if(token.equals("")) {
                    throw new IllegalArgumentException("The mandatory field defined at the position " + pos
                            + " is empty for the line: " + (line - 1));
                }
            }

            // Get Field to be set
            field = annotatedFields.get(dataField.pos());
            field.setAccessible(true);

            if(LOG.isDebugEnabled()) {
                LOG.debug("Pos/Offset: {}, Data: {}, Field type: {}", offset, token, field.getType());
            }

            // Create format object to format the field
            final FormattingOptions formattingOptions = ConverterUtils.convert(dataField,
                    field.getType(),
                    field.getAnnotation(BindyConverter.class),
                    getLocale());
            final Format<?> format = formatFactory.getFormat(formattingOptions);

            // field object to be set
            final Object modelField = model.get(field.getDeclaringClass().getName());

            // format the data received
            Object value = null;

            if("".equals(token)) {
                token = dataField.defaultValue();
            }
            if( !"".equals(token)) {
                try {
                    value = format.parse(token);
                }
                catch(final FormatException ie) {
                    throw new IllegalArgumentException(ie.getMessage() +
                            ", field(" + annotatedFields.get(dataField.pos()) + ") position: " + dataField.pos() + ", line: " + (line - 1),
                            ie);
                }
                catch(final Exception e) {
                    throw new IllegalArgumentException(
                            "Parsing error detected for field (" + annotatedFields.get(dataField.pos()) + ") defined at the position/offset: " +
                                    dataField.pos() + ", line: " + (line - 1),
                            e);
                }
            }
            else {
                value = getDefaultValueForPrimitive(field.getType());
            }

            if(value != null && !dataField.method().isEmpty()) {
                final Class<?> clazz = field.getType();

                final String methodName = dataField.method().substring(dataField.method().lastIndexOf(".") + 1,
                        dataField.method().length());

                Method m = ReflectionHelper.findMethod(clazz, methodName, field.getType());
                if(m != null) {
                    // this method must be static and return type
                    // must be the same as the datafield and
                    // must receive only the datafield value
                    // as the method argument
                    value = ObjectHelper.invokeMethod(m, null, value);
                }
                else {
                    // fallback to method without parameter, that is on the value itself
                    m = ReflectionHelper.findMethod(clazz, methodName);
                    value = ObjectHelper.invokeMethod(m, value);
                }
            }

            field.set(modelField, value);

            ++pos;

        }

        // check for unmapped non-whitespace data at the end of the line
        if(offset <= record.length() && !(record.substring(offset - 1, record.length())).trim().equals("") && !isIgnoreTrailingChars()) {
            throw new IllegalArgumentException("Unexpected / unmapped characters found at the end of the fixed-length record at line : " + (line - 1));
        }

        LOG.debug("Counter mandatory fields: {}", counterMandatoryFields);

        if(pos < totalFields) {
            throw new IllegalArgumentException("Some fields are missing (optional or mandatory), line: " + (line - 1));
        }

        if(counterMandatoryFields < numberMandatoryFields) {
            throw new IllegalArgumentException("Some mandatory fields are missing, line: " + (line - 1));
        }

    }

    private String trim(String token, final DataField dataField, final char paddingChar) {
        char myPaddingChar = dataField.paddingChar();
        if(dataField.paddingChar() == 0) {
            myPaddingChar = paddingChar;
        }
        if("R".equals(dataField.align())) {
            return leftTrim(token, myPaddingChar);
        }
        else if("L".equals(dataField.align())) {
            return rightTrim(token, myPaddingChar);
        }
        else {
            token = leftTrim(token, myPaddingChar);
            return rightTrim(token, myPaddingChar);
        }
    }

    private String rightTrim(final String token, final char myPaddingChar) {
        final StringBuilder sb = new StringBuilder(token);

        while(sb.length() > 0 && myPaddingChar == sb.charAt(sb.length() - 1)) {
            sb.deleteCharAt(sb.length() - 1);
        }

        return sb.toString();
    }

    private String leftTrim(final String token, final char myPaddingChar) {
        final StringBuilder sb = new StringBuilder(token);

        while(sb.length() > 0 && myPaddingChar == (sb.charAt(0))) {
            sb.deleteCharAt(0);
        }

        return sb.toString();
    }

    /**
     * Get parameters defined in @FixedLengthRecord annotation
     */
    private void initFixedLengthRecordParameters() {

        for(final Class<?> cl : models) {

            // Get annotation @FixedLengthRecord from the class
            final FixedLengthRecord record = cl.getAnnotation(FixedLengthRecord.class);

            if(record != null) {
                LOG.debug("Fixed length record: {}", record);

                // Get carriage return parameter
                crlf = record.crlf();
                LOG.debug("Carriage return defined for the CSV: {}", crlf);

                eol = record.eol();
                LOG.debug("EOL(end-of-line) defined for the CSV: {}", eol);

                // Get header parameter
                header = record.header();
                LOG.debug("Header: {}", header);
                hasHeader = header != void.class;
                LOG.debug("Has Header: {}", hasHeader);

                // Get skipHeader parameter
                skipHeader = record.skipHeader();
                LOG.debug("Skip Header: {}", skipHeader);

                // Get footer parameter
                footer = record.footer();
                LOG.debug("Footer: {}", footer);
                hasFooter = record.footer() != void.class;
                LOG.debug("Has Footer: {}", hasFooter);

                // Get skipFooter parameter
                skipFooter = record.skipFooter();
                LOG.debug("Skip Footer: {}", skipFooter);

                // Get isHeader parameter
                isHeader = hasHeader ? cl.equals(header) : false;
                LOG.debug("Is Header: {}", isHeader);

                // Get isFooter parameter
                isFooter = hasFooter ? cl.equals(footer) : false;
                LOG.debug("Is Footer: {}", isFooter);

                // Get padding character
                paddingChar = record.paddingChar();
                LOG.debug("Padding char: {}", paddingChar);

                // Get length of the record
                recordLength = record.length();
                LOG.debug("Length of the record: {}", recordLength);

                // Get flag for ignore trailing characters
                ignoreTrailingChars = record.ignoreTrailingChars();
                LOG.debug("Ignore trailing chars: {}", ignoreTrailingChars);

                ignoreMissingChars = record.ignoreMissingChars();
                LOG.debug("Enable ignore missing chars: {}", ignoreMissingChars);
            }
        }

        if(hasHeader && isHeader) {
            throw new java.lang.IllegalArgumentException("Record can not be configured with both 'isHeader=true' and 'hasHeader=true'");
        }

        if(hasFooter && isFooter) {
            throw new java.lang.IllegalArgumentException("Record can not be configured with both 'isFooter=true' and 'hasFooter=true'");
        }

        if((isHeader || isFooter) && (skipHeader || skipFooter)) {
            throw new java.lang.IllegalArgumentException(
                    "skipHeader and/or skipFooter can not be configured on a record where 'isHeader=true' or 'isFooter=true'");
        }

    }

    /**
     * Gets the type of the header record.
     *
     * @return The type of the header record if any, otherwise
     *         <code>void.class</code>.
     */
    public Class<?> header() {
        return header;
    }

    /**
     * Flag indicating if we have a header
     */
    public boolean hasHeader() {
        return hasHeader;
    }

    /**
     * Gets the type of the footer record.
     *
     * @return The type of the footer record if any, otherwise
     *         <code>void.class</code>.
     */
    public Class<?> footer() {
        return footer;
    }

    /**
     * Flag indicating if we have a footer
     */
    public boolean hasFooter() {
        return hasFooter;
    }

    /**
     * Flag indicating whether to skip the header parsing
     */
    public boolean skipHeader() {
        return skipHeader;
    }

    /**
     * Flag indicating whether to skip the footer processing
     */
    public boolean skipFooter() {
        return skipFooter;
    }

    /**
     * Flag indicating whether this factory is for a header
     */
    public boolean isHeader() {
        return isHeader;
    }

    /**
     * Flag indicating whether this factory is for a footer
     */
    public boolean isFooter() {
        return isFooter;
    }

    /**
     * Padding char used to fill the field
     */
    public char paddingchar() {
        return paddingChar;
    }

    /**
     *  Expected fixed length of the record
     */
    public int recordLength() {
        return recordLength;
    }

    /**
     * Flag indicating whether trailing characters beyond the last declared field may be ignored
     */
    public boolean isIgnoreTrailingChars() {
        return this.ignoreTrailingChars;
    }

    /**
     * Flag indicating whether too short lines are ignored
     */
    public boolean isIgnoreMissingChars() {
        return ignoreMissingChars;
    }

}
