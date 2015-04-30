/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.security.access.config;

import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;

/**
 * An access control v2 rule action.
 *
 * An action consists of an {@link Operation} on an {@link ObjectType} with certain properties, stored in a {@link java.util.Map}.
 * The operation and object should be an allowable combination, based on the {@link ObjectType#isAllowed(Operation)}
 * method of the object, which is exposed as the {@link #isAllowed()} method here. The internal #propertiesMatch(Map)
 * and #valueMatches(String, String) methods are used to determine wildcarded matching of properties, with
 * the empty string or "*" matching all values, and "*" at the end of a rule value indicating prefix matching.
 * <p>
 * The {@link #matches(Action)} method is intended to be used when determining precedence of rules, and
 * {@link #equals(Object)} and {@link #hashCode()} are intended for use in maps. This is due to the wildcard matching
 * described above.
 */
public class Action
{
    private final Operation _operation;
    private final ObjectType _object;
    private final ObjectProperties _properties;

    public Action(Operation operation)
    {
        this(operation, ObjectType.ALL);
    }

    public Action(Operation operation, ObjectType object, String name)
    {
        this(operation, object, new ObjectProperties(name));
    }

    public Action(Operation operation, ObjectType object)
    {
        this(operation, object, ObjectProperties.EMPTY);
    }

    public Action(Operation operation, ObjectType object, ObjectProperties properties)
    {
        _operation = operation;
        _object = object;
        _properties = properties;
    }

    public Operation getOperation()
    {
        return _operation;
    }

    public ObjectType getObjectType()
    {
        return _object;
    }

    public ObjectProperties getProperties()
    {
        return _properties;
    }

    public boolean isAllowed()
    {
        return _object.isAllowed(_operation);
    }

    public boolean matches(Action a)
    {
        if (!operationsMatch(a))
        {
            return false;
        }

        if (!objectTypesMatch(a))
        {
            return false;
        }

        if (!propertiesMatch(a))
        {
            return false;
        }

        return true;
    }

    private boolean operationsMatch(Action a)
    {
        return Operation.ALL == a.getOperation() || getOperation() == a.getOperation();
    }

    private boolean objectTypesMatch(Action a)
    {
        return ObjectType.ALL == a.getObjectType() || getObjectType() == a.getObjectType();
    }

    private boolean propertiesMatch(Action a)
    {
        boolean propertiesMatch = false;
        if (_properties != null)
        {
            propertiesMatch = _properties.matches(a.getProperties());
        }
        else if (a.getProperties() == null)
        {
            propertiesMatch = true;
        }
        return propertiesMatch;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final Action action = (Action) o;

        if (getOperation() != action.getOperation())
        {
            return false;
        }
        if (_object != action._object)
        {
            return false;
        }
        return !(getProperties() != null
                ? !getProperties().equals(action.getProperties())
                : action.getProperties() != null);

    }

    @Override
    public int hashCode()
    {
        int result = getOperation() != null ? getOperation().hashCode() : 0;
        result = 31 * result + (_object != null ? _object.hashCode() : 0);
        result = 31 * result + (getProperties() != null ? getProperties().hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return "Action[" +
               "operation=" + _operation +
               ", object=" + _object +
               ", properties=" + _properties +
               ']';
    }
}
