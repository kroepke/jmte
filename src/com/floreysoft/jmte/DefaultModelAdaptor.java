package com.floreysoft.jmte;

import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import com.floreysoft.jmte.message.NoLogErrorHandler;
import com.floreysoft.jmte.token.InvalidToken;
import com.floreysoft.jmte.token.Token;
import com.floreysoft.jmte.util.Util;

/**
 * Default implementation of the model adapter.
 * <p>
 * Does the object traversal using the "." operator. Resolved value will be
 * checked if it is either a {@link Processor} or a {@link Callable} in which
 * case the final resolved value is computed by calling those executable
 * objects.
 * </p>
 * <p/>
 * <p>
 * Inherit from this adapter if you want a slight change of this behavior and
 * set your new adaptor on the engine
 * {@link Engine#setModelAdaptor(ModelAdaptor)} .
 * </p>
 */
public class DefaultModelAdaptor implements ModelAdaptor {

    private static final String DEFAULT_SPECIAL_ITERATOR_VARIABLE = "_it";
    private static final String ERROR_STRING = "";

    public enum LoopMode {
        DEFAULT,
        /**
         * Treat everything as a list when looping over it in for each.
         */
        LIST
    }

    protected final Map<Class<?>, Map<String, Member>> cache = new HashMap<Class<?>, Map<String, Member>>();
    private final LoopMode loopMode;
    private final String specialIteratorVariable;
    private final boolean enableSlowMapAccess;

    public DefaultModelAdaptor() {
        this(LoopMode.DEFAULT);
    }

    public DefaultModelAdaptor(LoopMode loopMode) {
        this(loopMode, DEFAULT_SPECIAL_ITERATOR_VARIABLE, true);
    }

    public DefaultModelAdaptor(LoopMode loopMode, String specialIteratorVariable, boolean enableSlowMapAccess) {
        this.loopMode = loopMode;
        this.specialIteratorVariable = specialIteratorVariable;
        this.enableSlowMapAccess = enableSlowMapAccess;
    }

    public Object getValue(Map<String, Object> model, String expression) {
        String[] split = expression.split("\\.");
        List<String> segments = Arrays.asList(split);
        ErrorHandler errorHandler = new NoLogErrorHandler();
        Token token = new InvalidToken();
        Object value = traverse(segments, model, errorHandler, token);
        return value;
    }


    @Override
    @SuppressWarnings("rawtypes")
    public Object getValue(TemplateContext context, Token token,
                           List<String> segments, String expression) {
        Object value = traverse(segments, context.model, context.errorHandler, token);
        // if value implements both, we use the more specialized implementation
        if (value instanceof Processor) {
            value = ((Processor) value).eval(context);
        } else if (value instanceof Callable) {
            try {
                value = ((Callable) value).call();
            } catch (Exception e) {
            }
        }
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterable<Object> getIterable(Object value) {
        final Iterable<Object> iterable;
        if (value == null) {
            iterable = Collections.emptyList();
        } else if (value instanceof Map) {
            iterable = this.loopMode == LoopMode.LIST ? Collections.singletonList(value) : ((Map) value).entrySet();
        } else if (value instanceof Iterable) {
            iterable = ((Iterable) value);
        } else {
            List<Object> arrayAsList = Util.arrayAsList(value);
            if (arrayAsList != null) {
                iterable = arrayAsList;
            } else {
                // we have a single value here and simply wrap it in a List
                iterable = Collections.singletonList(value);
            }
        }
        return iterable;
    }

    @Override
    public String getSpecialIteratorVariable() {
        return specialIteratorVariable;
    }

    protected Object traverse(List<String> segments, Map<String, Object> model,
                              ErrorHandler errorHandler, Token token) {
        return traverse(model, segments, 0, errorHandler, token);
    }

    protected Object traverse(Object o, List<String> attributeNames, int index,
                              ErrorHandler errorHandler, Token token) {
        Object result;
        if (index >= attributeNames.size()) {
            result = o;
        } else {
            if (o == null) {
                return null;
            }
            String attributeName = attributeNames.get(index);
            Object nextStep = nextStep(o, attributeName, errorHandler, token);
            result = traverse(nextStep, attributeNames, index + 1,
                    errorHandler, token);
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    protected Object nextStep(Object o, String attributeName,
                              ErrorHandler errorHandler, Token token) {
        Object result;
        if (o instanceof String) {
            if (o != ERROR_STRING) {
                errorHandler.error("no-call-on-string", token, new ModelBuilder(
                        "receiver", o.toString()).build());
            }
            return o;
        }

        final String rawAttributeName;
        final boolean arrayAccess = Util.isArrayAccess(attributeName);
        if (!arrayAccess) {
            rawAttributeName = attributeName;
        } else {
            final String arrayName = Util.extractArrayName(attributeName);
            rawAttributeName = arrayName;
        }

        if (o instanceof Map) {
            result = accessMap((Map) o, rawAttributeName);
        } else {
            List<Object> arrayAsList = Util.arrayAsList(o);
            if (arrayAsList != null && !arrayAccess && rawAttributeName.equalsIgnoreCase("length")) {
                return arrayAsList.size();
            }
            try {
                result = getPropertyValue(o, rawAttributeName);
            } catch (Exception e) {
                if (o != ERROR_STRING) {
                    errorHandler.error("property-access-error", token,
                            new ModelBuilder("property", rawAttributeName, "object",
                                    o, "exception", e).build());
                }
                return ERROR_STRING;
            }
        }
        if (arrayAccess) {
            final String arrayIndex = Util.extractArrayIndex(attributeName);
            result = getIndexFromArray(result, arrayIndex, errorHandler, token);
        }

        return result;
    }

    @SuppressWarnings("rawtypes")
    protected Object getIndexFromArray(Object array, String arrayIndex, ErrorHandler errorHandler, Token token) {
        if ( array == null ) {
            errorHandler.error("not-array-error", token,
                    new ModelBuilder("array", "[null]").build());
            return ERROR_STRING;
        }
        List<Object> arrayAsList = Util.arrayAsList(array);
        try {
            if (arrayAsList != null) {
                try {
                    final int index;
                    if (arrayIndex.equalsIgnoreCase("last")) {
                        if (arrayAsList.size() > 0) {
                            index = arrayAsList.size() - 1;
                            return arrayAsList.get(index);
                        } else {
                            if (array != ERROR_STRING) {
                                errorHandler.error("index-out-of-bounds-error", token,
                                        new ModelBuilder("arrayIndex", arrayIndex, "array", array.toString()).build());
                            }
                            return ERROR_STRING;
                        }
                    } else {
                        index = Integer.parseInt(arrayIndex);
                        return arrayAsList.get(index);
                    }
                } catch (NumberFormatException nfe) {
                    if (array != ERROR_STRING) {
                        errorHandler.error("invalid-index-error", token,
                                new ModelBuilder("arrayIndex", arrayIndex, "array", array.toString()).build());
                    }
                    return ERROR_STRING;
                }
            } else {
                if (array != ERROR_STRING) {
                    errorHandler.error("not-array-error", token,
                            new ModelBuilder("array", array.toString()).build());
                }
                return array;
            }
        } catch (IndexOutOfBoundsException e) {
            if (array != ERROR_STRING) {
                errorHandler.error("index-out-of-bounds-error", token,
                        new ModelBuilder("arrayIndex", arrayIndex, "array", array.toString()).build());
            }
            return ERROR_STRING;
        }
    }



    protected Object accessMap(Map map, String key) {
        Object result;
        // special cases to select which iterable we are interested in
        if ( key.equals("_entries") ) {
            result = map.entrySet();
        } else if ( key.equals("_keys") ) {
            result = map.keySet();
        } else if ( key.equals("_values") ) {
            result = map.values();
        } else {
            result = map.get(key);
        }
        if (result == null && enableSlowMapAccess && !(map instanceof ScopedMap)) {
            final Set<Map.Entry<?, ?>> entries = map.entrySet();
            for (Map.Entry entry: entries) {
                if (entry.getKey().toString().equals(key)) {
                    result = entry.getValue();
                    break;
                }
            }
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    protected Object getPropertyValue(Object o, String propertyName) {
        try {
            // XXX this is so strange, can not call invoke on key and value for
            // Map.Entry, so we have to get this done like this:
            if (o instanceof Map.Entry) {
                final Map.Entry entry = (Entry) o;
                if (propertyName.equals("key")) {
                    final Object result = entry.getKey();
                    return result;
                } else if (propertyName.equals("value")) {
                    final Object result = entry.getValue();
                    return result;
                }

            }
            boolean valueSet = false;
            Object value = null;
            Member member = null;
            final Class<?> clazz = o.getClass();
            Map<String, Member> members = cache.get(clazz);
            if (members == null) {
                members = new HashMap<String, Member>();
                cache.put(clazz, members);
            } else {
                member = members.get(propertyName);
                if (member != null) {
                    if (member.getClass() == Method.class)
                        return ((Method) member).invoke(o);
                    if (member.getClass() == Field.class)
                        return ((Field) member).get(o);
                }
            }

            final String suffix = Character.toUpperCase(propertyName.charAt(0))
                    + propertyName.substring(1);
            final Method[] declaredMethods = clazz.getMethods();
            for (Method method : declaredMethods) {
                if (Modifier.isPublic(method.getModifiers())
                        && (method.getName().equals("get" + suffix) || method
                        .getName().equals("is" + suffix))
                        && method.getParameterTypes().length == 0) {
                    value = method.invoke(o, (Object[]) null);
                    valueSet = true;
                    member = method;
                    break;

                }
            }
            if (!valueSet) {
                final Field field = clazz.getField(propertyName);
                if (Modifier.isPublic(field.getModifiers())) {
                    value = field.get(o);
                    member = field;
                    valueSet = true;
                }
            }
            if (valueSet) {
                members.put(propertyName, member);
            }
            return value;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
