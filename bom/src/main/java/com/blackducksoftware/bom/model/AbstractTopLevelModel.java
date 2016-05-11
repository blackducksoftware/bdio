/*
 * Copyright 2015 Black Duck Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blackducksoftware.bom.model;

import static com.google.common.base.Objects.firstNonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.Type;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Base class for top level models. Top level models appear directly in the JSON-LD graph.
 *
 * @author jgustie
 */
public class AbstractTopLevelModel<M extends AbstractTopLevelModel<M>> extends AbstractModel<M> {

    @Nullable
    private List<ExternalIdentifier> externalIdentifiers;

    @Nullable
    private List<Relationship> relationships;

    protected AbstractTopLevelModel(Type type, Iterable<ModelField<M, ?>> fields) {
        super(type, addTopLevelFields(fields));
    }

    @Nullable
    public List<ExternalIdentifier> getExternalIdentifiers() {
        return externalIdentifiers;
    }

    public void setExternalIdentifiers(@Nullable List<ExternalIdentifier> externalIdentifiers) {
        this.externalIdentifiers = externalIdentifiers;
    }

    public M addExternalIdentifier(ExternalIdentifier externalIdentifier) {
        if (externalIdentifier != null) {
            List<ExternalIdentifier> externalIdentifiers = getExternalIdentifiers();
            if (externalIdentifiers != null) {
                externalIdentifiers.add(externalIdentifier);
            } else {
                setExternalIdentifiers(Lists.newArrayList(externalIdentifier));
            }
        }
        return self();
    }

    public FluentIterable<ExternalIdentifier> externalIdentifiers() {
        return FluentIterable.from(firstNonNull(getExternalIdentifiers(), ImmutableList.<ExternalIdentifier> of()));
    }

    @Nullable
    public List<Relationship> getRelationships() {
        return relationships;
    }

    public void setRelationships(@Nullable List<Relationship> relationships) {
        this.relationships = relationships;
    }

    public M addRelationship(Relationship relationship) {
        if (relationship != null) {
            List<Relationship> relationships = getRelationships();
            if (relationships != null) {
                relationships.add(relationship);
            } else {
                setRelationships(Lists.newArrayList(relationship));
            }
        }
        return self();
    }

    public FluentIterable<Relationship> relationships() {
        return FluentIterable.from(firstNonNull(getRelationships(), ImmutableList.<Relationship> of()));
    }

    private static <M extends AbstractModel<M>> Iterable<ModelField<M, ?>> addTopLevelFields(Iterable<ModelField<M, ?>> fields) {
        Collection<ModelField<M, ?>> newFields = new ArrayList<>(2);

        // External identifiers
        newFields.add(new ModelField<M, List<ExternalIdentifier>>(BlackDuckTerm.EXTERNAL_IDENTIFIER) {
            @Override
            protected List<ExternalIdentifier> get(M model) {
                return ((AbstractTopLevelModel<?>) model).getExternalIdentifiers();
            }

            @Override
            protected void set(M model, Object value) {
                ((AbstractTopLevelModel<?>) model).setExternalIdentifiers(emptyToNull(valueToNodes(value).transformAndConcat(toModel(ExternalIdentifier.class))
                        .toList()));
            }
        });

        // Relationships
        newFields.add(new ModelField<M, List<Relationship>>(SpdxTerm.RELATIONSHIP) {
            @Override
            protected List<Relationship> get(M model) {
                return ((AbstractTopLevelModel<?>) model).getRelationships();
            }

            @Override
            protected void set(M model, Object value) {
                ((AbstractTopLevelModel<?>) model).setRelationships(emptyToNull(valueToNodes(value).transformAndConcat(toModel(Relationship.class)).toList()));
            }
        });

        return Iterables.concat(fields, newFields);
    }
}
