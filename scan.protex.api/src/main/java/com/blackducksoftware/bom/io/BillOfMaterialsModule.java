/*
 * Copyright (C) 2015 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bom.io;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;

import java.io.IOException;
import java.util.Map.Entry;

import com.blackducksoftware.bom.ImmutableNode;
import com.blackducksoftware.bom.Node;
import com.blackducksoftware.bom.SimpleTerm;
import com.blackducksoftware.bom.SimpleType;
import com.blackducksoftware.bom.Term;
import com.blackducksoftware.bom.Type;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.net.MediaType;

/**
 * Jackson module for controlling custom serialization. Serialization requires a {@link LinkedDataContext} to behave
 * correctly.
 *
 * @author jgustie
 */
class BillOfMaterialsModule extends SimpleModule {

    /**
     * Serializer for types.
     */
    public static final class TypeSerializer extends JsonSerializer<Type> {
        private final LinkedDataContext context;

        private TypeSerializer(LinkedDataContext context) {
            this.context = checkNotNull(context);
        }

        @Override
        public void serialize(Type value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
            jgen.writeString((String) context.compactValue(JsonLdTerm.TYPE, value));
        }
    }

    /**
     * Deserializer for types.
     */
    public static final class TypeDeserializer extends JsonDeserializer<Type> {
        private final LinkedDataContext context;

        private TypeDeserializer(LinkedDataContext context) {
            this.context = checkNotNull(context);
        }

        @Override
        public Type deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            return SimpleType.create((String) context.expandValue(JsonLdTerm.TYPE, jp.getText()));
        }
    }

    /**
     * Serializer for nodes.
     */
    public static final class NodeSerializer extends JsonSerializer<Node> {
        private final LinkedDataContext context;

        private NodeSerializer(LinkedDataContext context) {
            this.context = checkNotNull(context);
        }

        @Override
        public void serialize(Node node, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
            // Write out all the values for the current node
            jgen.writeStartObject();
            writeTerm(jgen, JsonLdTerm.ID, node.id());
            if (!node.types().isEmpty()) {
                // Conditionally write a list of types (i.e. do not serialize a single element as a list)
                writeTerm(jgen, JsonLdTerm.TYPE, node.types().size() > 1 ? node.types() : getOnlyElement(node.types()));
            }
            for (Entry<Term, Object> datum : node.data().entrySet()) {
                writeTerm(jgen, datum.getKey(), datum.getValue());
            }
            jgen.writeEndObject();
        }

        protected void writeTerm(JsonGenerator jgen, Term term, Object value) throws IOException {
            Object effectiveValue = context.compactValue(term, value);
            if (effectiveValue != null) {
                jgen.writeFieldName(context.compactTerm(term));
                jgen.writeObject(effectiveValue);
            }
        }
    }

    /**
     * Deserializer for nodes.
     */
    public static final class NodeDeserializer extends JsonDeserializer<Node> {
        private final LinkedDataContext context;

        private NodeDeserializer(LinkedDataContext context) {
            this.context = checkNotNull(context);
        }

        @Override
        public Node deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
                throw new JsonParseException("expected start object", jp.getCurrentLocation());
            }

            ImmutableNode.Builder node = ImmutableNode.builder();
            while (jp.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jp.getCurrentName();
                if (fieldName.equals(JsonLdTerm.ID.toString())) {
                    node.id(jp.nextTextValue());
                } else if (fieldName.equals(JsonLdTerm.TYPE.toString())) {
                    // Handle one or more types
                    if (jp.nextToken() == JsonToken.START_ARRAY) {
                        while (jp.nextToken() != JsonToken.END_ARRAY) {
                            node.addType(jp.readValueAs(Type.class));
                        }
                    } else {
                        node.addType(jp.readValueAs(Type.class));
                    }
                } else {
                    readTerm(jp, node);
                }
            }
            return node.build();
        }

        protected void readTerm(JsonParser jp, ImmutableNode.Builder nodeBuilder) throws IOException {
            Term term = SimpleTerm.create(context.expandTerm(jp.getCurrentName()));
            Object value;

            JsonToken nextValue = jp.nextValue();
            if (nextValue.isScalarValue()) {
                // Read all scalar values out as a string
                value = jp.getValueAsString();
            } else if (nextValue == JsonToken.START_ARRAY) {
                // Read a list of values as strings (returns a lazy iterable that must be read now)
                jp.nextToken();
                value = ImmutableList.copyOf(jp.readValuesAs(String.class));
            } else {
                // This must be a structured object and we have no idea what it is
                throw new JsonParseException("cannot handle structured data", jp.getCurrentLocation());
            }

            nodeBuilder.put(term, context.expandValue(term, value));
        }
    }

    BillOfMaterialsModule(LinkedDataContext context) {
        super("BillOfMaterialsWriterModule", Version.unknownVersion());

        // Add serialization helpers for the types we use in the models
        addSerializer(Type.class, new TypeSerializer(context));
        addDeserializer(Type.class, new TypeDeserializer(context));
        addSerializer(Node.class, new NodeSerializer(context));
        addDeserializer(Node.class, new NodeDeserializer(context));
        addSerializer(HashCode.class, ToStringSerializer.instance);
        addSerializer(MediaType.class, ToStringSerializer.instance);
    }

}
