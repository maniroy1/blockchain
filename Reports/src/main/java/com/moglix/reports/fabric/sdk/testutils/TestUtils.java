/*
 *
 *  Copyright 2016,2017 DTCC, Fujitsu Australia Software Technology, IBM - All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.moglix.reports.fabric.sdk.testutils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.hyperledger.fabric.sdk.helper.Config;

public class TestUtils {

    private TestUtils() {
    }

    /**
     * Sets the value of a field on an object
     *
     * @param o         The object that contains the field
     * @param fieldName The name of the field
     * @param value     The new value
     * @return The previous value of the field
     */
    public static Object setField(Object o, String fieldName, Object value) {
        Object oldVal = null;
        try {
            final Field field = o.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            oldVal = field.get(o);
            field.set(o, value);
        } catch (Exception e) {
            throw new RuntimeException("Cannot get value of field " + fieldName, e);
        }
        return oldVal;
    }

    /**
     * Invokes method on object.
     * Used to access private methods.
     *
     * @param o          The object that contains the field
     * @param methodName The name of the field
     * @param args       The arguments.
     * @return Result of method.
     */
    public static Object invokeMethod(Object o, String methodName, Object... args) throws Throwable {
        Object oldVal = null;

        Method[] methods = o.getClass().getDeclaredMethods();
        List<Method> reduce = new ArrayList<>(Arrays.asList(methods));
        for (Iterator<Method> i = reduce.iterator(); i.hasNext();
                ) {
            Method m = i.next();
            if (!methodName.equals(m.getName())) {
                i.remove();
                continue;
            }
            Class<?>[] parameterTypes = m.getParameterTypes();
            if (parameterTypes.length != args.length) {
                i.remove();
                continue;
            }
        }
        if (reduce.isEmpty()) {
            throw new RuntimeException(String.format("TEST ISSUE Could not find method %s on %s with %d arguments.",
                    methodName, o.getClass().getName(), args.length));
        }
        if (reduce.size() > 1) {
            throw new RuntimeException(String.format("TEST ISSUE Could not find unique method %s on %s. Found with %d matches.",
                    methodName, o.getClass().getName(), reduce.size()));
        }

        Method method = reduce.iterator().next();
        method.setAccessible(true);
        try {
            return method.invoke(o, args);
        } catch (IllegalAccessException e) {
            throw e;
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }

    }

    /**
     * Gets the value of a field on an object
     *
     * @param o         The object that contains the field
     * @param fieldName The name of the field
     * @return The value of the field
     */
    public static Object getField(Object o, String fieldName) {

        try {
            final Field field = o.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(o);
        } catch (Exception e) {
            throw new RuntimeException("Cannot get value of field " + fieldName, e);
        }
    }

    /**
     * Reset config.
     */
    public static void resetConfig() {

        try {
            final Field field = Config.class.getDeclaredField("config");
            field.setAccessible(true);
            field.set(Config.class, null);
            Config.getConfig();
        } catch (Exception e) {
            throw new RuntimeException("Cannot reset config", e);
        }

    }

    /**
     * Sets a Config property value
     * <p>
     * The Config instance is initialized once on startup which means that
     * its properties don't change throughout its lifetime.
     * This method allows a Config property to be changed temporarily for testing purposes
     *
     * @param key   The key of the property (eg Config.LOGGERLEVEL)
     * @param value The new value
     * @return The previous value
     */
    public static String setConfigProperty(String key, String value) throws Exception {

        String oldVal = null;

        try {
            Config config = Config.getConfig();

            final Field sdkPropertiesInstance = config.getClass().getDeclaredField("sdkProperties");
            sdkPropertiesInstance.setAccessible(true);

            final Properties sdkProperties = (Properties) sdkPropertiesInstance.get(config);
            oldVal = sdkProperties.getProperty(key);
            sdkProperties.put(key, value);

        } catch (Exception e) {
            throw new RuntimeException("Failed to set Config property " + key, e);
        }

        return oldVal;
    }

}
