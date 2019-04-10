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
package org.apache.camel.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.Callable;

import org.apache.camel.api.RuntimeCamelException;
import org.apache.camel.api.TypeConverter;
import org.apache.camel.util.StringHelper;

/**
 * A number of useful helper methods for working with Objects
 */
public final class ObjectHelper {

    /**
     * Utility classes should not have a public constructor.
     */
    private ObjectHelper() {
    }


    /**
     * A helper method for comparing objects for equality in which it uses type coercion to coerce
     * types between the left and right values. This allows you test for equality for example with
     * a String and Integer type as Camel will be able to coerce the types.
     */
    public static boolean typeCoerceEquals(final TypeConverter converter, final Object leftValue, final Object rightValue) {
        return typeCoerceEquals(converter, leftValue, rightValue, false);
    }

    /**
     * A helper method for comparing objects for equality in which it uses type coercion to coerce
     * types between the left and right values. This allows you test for equality for example with
     * a String and Integer type as Camel will be able to coerce the types.
     */
    public static boolean typeCoerceEquals(final TypeConverter converter, final Object leftValue, final Object rightValue, final boolean ignoreCase) {
        // sanity check
        if (leftValue == null && rightValue == null) {
            // they are equal
            return true;
        } else if (leftValue == null || rightValue == null) {
            // only one of them is null so they are not equal
            return false;
        }

        // try without type coerce
        boolean answer = org.apache.camel.util.ObjectHelper.equal(leftValue, rightValue, ignoreCase);
        if (answer) {
            return true;
        }

        // are they same type, if so return false as the equals returned false
        if (leftValue.getClass().isInstance(rightValue)) {
            return false;
        }

        // convert left to right
        Object value = converter.tryConvertTo(rightValue.getClass(), leftValue);
        answer = org.apache.camel.util.ObjectHelper.equal(value, rightValue, ignoreCase);
        if (answer) {
            return true;
        }

        // convert right to left
        value = converter.tryConvertTo(leftValue.getClass(), rightValue);
        answer = org.apache.camel.util.ObjectHelper.equal(leftValue, value, ignoreCase);
        return answer;
    }

    /**
     * A helper method for comparing objects for inequality in which it uses type coercion to coerce
     * types between the left and right values.  This allows you test for inequality for example with
     * a String and Integer type as Camel will be able to coerce the types.
     */
    public static boolean typeCoerceNotEquals(final TypeConverter converter, final Object leftValue, final Object rightValue) {
        return !typeCoerceEquals(converter, leftValue, rightValue);
    }

    /**
     * A helper method for comparing objects ordering in which it uses type coercion to coerce
     * types between the left and right values.  This allows you test for ordering for example with
     * a String and Integer type as Camel will be able to coerce the types.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int typeCoerceCompare(final TypeConverter converter, final Object leftValue, final Object rightValue) {

        // if both values is numeric then compare using numeric
        final Long leftNum = converter.tryConvertTo(Long.class, leftValue);
        final Long rightNum = converter.tryConvertTo(Long.class, rightValue);
        if (leftNum != null && rightNum != null) {
            return leftNum.compareTo(rightNum);
        }

        // also try with floating point numbers
        final Double leftDouble = converter.tryConvertTo(Double.class, leftValue);
        final Double rightDouble = converter.tryConvertTo(Double.class, rightValue);
        if (leftDouble != null && rightDouble != null) {
            return leftDouble.compareTo(rightDouble);
        }

        // prefer to NOT coerce to String so use the type which is not String
        // for example if we are comparing String vs Integer then prefer to coerce to Integer
        // as all types can be converted to String which does not work well for comparison
        // as eg "10" < 6 would return true, where as 10 < 6 will return false.
        // if they are both String then it doesn't matter
        if (rightValue instanceof String && (!(leftValue instanceof String))) {
            // if right is String and left is not then flip order (remember to * -1 the result then)
            return typeCoerceCompare(converter, rightValue, leftValue) * -1;
        }

        // prefer to coerce to the right hand side at first
        if (rightValue instanceof Comparable) {
            final Object value = converter.tryConvertTo(rightValue.getClass(), leftValue);
            if (value != null) {
                return ((Comparable) rightValue).compareTo(value) * -1;
            }
        }

        // then fallback to the left hand side
        if (leftValue instanceof Comparable) {
            final Object value = converter.tryConvertTo(leftValue.getClass(), rightValue);
            if (value != null) {
                return ((Comparable) leftValue).compareTo(value);
            }
        }

        // use regular compare
        return compare(leftValue, rightValue);
    }

    /**
     * A helper method to invoke a method via reflection and wrap any exceptions
     * as {@link RuntimeCamelException} instances
     *
     * @param method the method to invoke
     * @param instance the object instance (or null for static methods)
     * @param parameters the parameters to the method
     * @return the result of the method invocation
     */
    public static Object invokeMethod(final Method method, final Object instance, final Object... parameters) {
        try {
            if (parameters != null) {
                return method.invoke(instance, parameters);
            } else {
                return method.invoke(instance);
            }
        } catch (final IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        } catch (final InvocationTargetException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e.getCause());
        }
    }

    /**
     * A helper method to invoke a method via reflection in a safe way by allowing to invoke
     * methods that are not accessible by default and wrap any exceptions
     * as {@link RuntimeCamelException} instances
     *
     * @param method the method to invoke
     * @param instance the object instance (or null for static methods)
     * @param parameters the parameters to the method
     * @return the result of the method invocation
     */
    public static Object invokeMethodSafe(final Method method, final Object instance, final Object... parameters) throws InvocationTargetException, IllegalAccessException {
        Object answer;
        method.setAccessible(true);
        if (parameters != null) {
            answer = method.invoke(instance, parameters);
        } else {
            answer = method.invoke(instance);
        }
        return answer;
    }

    /**
     * A helper method to create a new instance of a type using the default
     * constructor arguments.
     */
    public static <T> T newInstance(final Class<T> type) {
        try {
            return type.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * A helper method to create a new instance of a type using the default
     * constructor arguments.
     */
    public static <T> T newInstance(final Class<?> actualType, final Class<T> expectedType) {
        try {
            final Object value = actualType.newInstance();
            return org.apache.camel.util.ObjectHelper.cast(expectedType, value);
        } catch (final InstantiationException e) {
            throw new RuntimeCamelException(e);
        } catch (final IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * A helper method for performing an ordered comparison on the objects
     * handling nulls and objects which do not handle sorting gracefully
     */
    public static int compare(final Object a, final Object b) {
        return compare(a, b, false);
    }

    /**
     * A helper method for performing an ordered comparison on the objects
     * handling nulls and objects which do not handle sorting gracefully
     *
     * @param a  the first object
     * @param b  the second object
     * @param ignoreCase  ignore case for string comparison
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compare(final Object a, final Object b, final boolean ignoreCase) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        if (ignoreCase && a instanceof String && b instanceof String) {
            return ((String) a).compareToIgnoreCase((String) b);
        }
        if (a instanceof Comparable) {
            final Comparable comparable = (Comparable)a;
            return comparable.compareTo(b);
        }
        int answer = a.getClass().getName().compareTo(b.getClass().getName());
        if (answer == 0) {
            answer = a.hashCode() - b.hashCode();
        }
        return answer;
    }


    /**
     * Calling the Callable with the setting of TCCL with a given classloader.
     *
     * @param call        the Callable instance
     * @param classloader the class loader
     * @return the result of Callable return
     */
    public static Object callWithTCCL(final Callable<?> call, final ClassLoader classloader) throws Exception {
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            if (classloader != null) {
                Thread.currentThread().setContextClassLoader(classloader);
            }
            return call.call();
        } finally {
            if (tccl != null) {
                Thread.currentThread().setContextClassLoader(tccl);
            }
        }
    }


    /**
     * Returns true if the collection contains the specified value
     */
    public static boolean contains(Object collectionOrArray, Object value) {
        // favor String types
        if (collectionOrArray != null && (collectionOrArray instanceof StringBuffer || collectionOrArray instanceof StringBuilder)) {
            collectionOrArray = collectionOrArray.toString();
        }
        if (value != null && (value instanceof StringBuffer || value instanceof StringBuilder)) {
            value = value.toString();
        }

        if (collectionOrArray instanceof Collection) {
            final Collection<?> collection = (Collection<?>)collectionOrArray;
            return collection.contains(value);
        } else if (collectionOrArray instanceof String && value instanceof String) {
            final String str = (String)collectionOrArray;
            final String subStr = (String)value;
            return str.contains(subStr);
        }
        return false;
    }

    /**
     * Returns true if the collection contains the specified value by considering case insensitivity
     */
    public static boolean containsIgnoreCase(Object collectionOrArray, Object value) {
        // favor String types
        if (collectionOrArray != null && (collectionOrArray instanceof StringBuffer || collectionOrArray instanceof StringBuilder)) {
            collectionOrArray = collectionOrArray.toString();
        }
        if (value != null && (value instanceof StringBuffer || value instanceof StringBuilder)) {
            value = value.toString();
        }

        if (collectionOrArray instanceof Collection) {
            final Collection<?> collection = (Collection<?>)collectionOrArray;
            return collection.contains(value);
        } else if (collectionOrArray instanceof String && value instanceof String) {
            final String str = (String)collectionOrArray;
            final String subStr = (String)value;
            return StringHelper.containsIgnoreCase(str, subStr);
        }
        return false;
    }
}
