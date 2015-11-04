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
package com.blackducksoftware.bom.model;

import static com.google.common.base.Objects.firstNonNull;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.Type;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Base class for top level models. Top level models appear directly in the JSON-LD graph.
 *
 * @author jgustie
 */
public class AbstractTopLevelModel<M extends AbstractTopLevelModel<M>> extends AbstractModel<M> {
    @Nullable
    private List<ExternalIdentifier> externalIdentifiers;

    protected AbstractTopLevelModel(Type type, ModelField<M, ?>... fields) {
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
        return (M) this;
    }

    public FluentIterable<ExternalIdentifier> externalIdentifiers() {
        return FluentIterable.from(firstNonNull(getExternalIdentifiers(), ImmutableList.<ExternalIdentifier> of()));
    }

    private static <M extends AbstractModel<M>> ModelField<M, ?>[] addTopLevelFields(ModelField<M, ?>[] fields) {
        ModelField<M, ?>[] newFields = Arrays.copyOf(fields, fields.length + 1);
        newFields[fields.length] = new ModelField<M, List<ExternalIdentifier>>(BlackDuckTerm.EXTERNAL_IDENTIFIER) {
            @Override
            protected List<ExternalIdentifier> get(M model) {
                return ((AbstractTopLevelModel<?>) model).getExternalIdentifiers();
            }

            @Override
            protected void set(M model, Object value) {
                ((AbstractTopLevelModel<?>) model).setExternalIdentifiers(emptyToNull(valueToNodes(value).transformAndConcat(toModel(ExternalIdentifier.class))
                        .toList()));
            }
        };
        return newFields;
    }
}
