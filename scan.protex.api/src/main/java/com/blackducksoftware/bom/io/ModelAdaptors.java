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

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.DoapTerm;
import com.blackducksoftware.bom.SpdxTerm;
import com.blackducksoftware.bom.Term;
import com.blackducksoftware.bom.Type;
import com.blackducksoftware.bom.model.BillOfMaterials;
import com.blackducksoftware.bom.model.Component;
import com.blackducksoftware.bom.model.File;
import com.blackducksoftware.bom.model.License;
import com.blackducksoftware.bom.model.Project;
import com.blackducksoftware.bom.model.Version;
import com.blackducksoftware.bom.model.Vulnerability;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import com.google.common.net.MediaType;

/**
 * The actual adaptor code. This thing seems overly engineered, but there is a method to madness. The primary goal of
 * this code is avoid object allocations, and therefore memory and garbage collection overhead. This is extremely
 * important when considering that there may be millions of models that need to generated and adapted for writing into
 * the linked data graph. Each adaptor class is effectively a singleton and uses a {@link Supplier} to isolate itself
 * from the model instance it is actually adapting: this minimizes the instance overhead.
 *
 * @author jgustie
 */
final class ModelAdaptors {

    /**
     * Interface of something that can adopt a model.
     */
    public static interface Adaptor {

        /**
         * Returns the types of the adopted model.
         */
        Set<Type> types();

        /**
         * Returns a map view of the terms obtained by the model produced by the supplied factory.
         */
        Map<Term, Object> asMap(Supplier<Object> model);

    }

    // =============================================================================================
    // BILL OF MATERIALS
    // =============================================================================================

    private static final class BillOfMaterialsAdaptor extends AbstractAdaptor<BillOfMaterials> {
        private BillOfMaterialsAdaptor() {
            super(BlackDuckType.BILL_OF_MATERIALS);
        }

        @Override
        protected Object lookup(BillOfMaterials model, Term term) {
            return null;
        }

        @Override
        protected Object store(BillOfMaterials model, Term term, Object value) {
            return null;
        }
    }

    // =============================================================================================
    // Component
    // =============================================================================================

    private static final class ComponentAdaptor extends AbstractAdaptor<Component> {
        private ComponentAdaptor() {
            super(BlackDuckType.COMPONENT,
                    DoapTerm.NAME,
                    DoapTerm.HOMEPAGE,
                    DoapTerm.LICENSE);
        }

        @Override
        protected Object lookup(Component model, Term term) {
            if (term.equals(DoapTerm.NAME)) {
                return model.getName();
            } else if (term.equals(DoapTerm.HOMEPAGE)) {
                return model.getHomepage();
            } else if (term.equals(DoapTerm.LICENSE)) {
                return model.getLicense();
            } else {
                return null;
            }
        }

        @Override
        protected Object store(Component model, Term term, Object value) {
            Object original = null;
            if (term.equals(DoapTerm.NAME)) {
                original = model.getName();
                model.setName(valueToString(value));
            } else if (term.equals(DoapTerm.HOMEPAGE)) {
                original = model.getHomepage();
                model.setHomepage(valueToString(value));
            } else if (term.equals(DoapTerm.LICENSE)) {
                original = model.getLicense();
                model.setLicense(valueToString(value));
            }
            return original;
        }
    }

    // =============================================================================================
    // File
    // =============================================================================================

    private static final class FileAdaptor extends AbstractAdaptor<File> {
        private FileAdaptor() {
            super(BlackDuckType.FILE,
                    SpdxTerm.FILE_NAME,
                    BlackDuckTerm.CONTENT_TYPE,
                    BlackDuckTerm.SIZE,
                    BlackDuckTerm.SHA1,
                    BlackDuckTerm.MD5);
        }

        @Override
        protected Object lookup(File model, Term term) {
            if (term.equals(SpdxTerm.FILE_NAME)) {
                return model.getPath();
            } else if (term.equals(BlackDuckTerm.CONTENT_TYPE)) {
                return model.getType();
            } else if (term.equals(BlackDuckTerm.SIZE)) {
                return model.getSize();
            } else if (term.equals(BlackDuckTerm.SHA1)) {
                return model.getSha1();
            } else if (term.equals(BlackDuckTerm.MD5)) {
                return model.getMd5();
            } else if (term.equals(SpdxTerm.ARTIFACT_OF)) {
                return model.getComponent();
            } else if (term.equals(SpdxTerm.LICENSE_CONCLUDED)) {
                return model.getLicense();
            } else {
                return null;
            }
        }

        @Override
        protected Object store(File model, Term term, Object value) {
            Object original = null;
            if (term.equals(SpdxTerm.FILE_NAME)) {
                original = model.getPath();
                model.setPath(valueToString(value));
            } else if (term.equals(BlackDuckTerm.CONTENT_TYPE)) {
                original = model.getType();
                model.setType(MediaType.parse(valueToString(value)));
            } else if (term.equals(BlackDuckTerm.SIZE)) {
                original = model.getSize();
                model.setSize(valueToLong(value));
            } else if (term.equals(BlackDuckTerm.SHA1)) {
                original = model.getSha1();
                model.setSha1(HashCode.fromString(valueToString(value)));
            } else if (term.equals(BlackDuckTerm.MD5)) {
                original = model.getMd5();
                model.setMd5(HashCode.fromString(valueToString(value)));
            } else if (term.equals(SpdxTerm.ARTIFACT_OF)) {
                original = model.getComponent();
                model.setComponent(valueToString(value));
            } else if (term.equals(SpdxTerm.LICENSE_CONCLUDED)) {
                original = model.getLicense();
                model.setLicense(valueToString(value));
            }
            return original;
        }
    }

    // =============================================================================================
    // License
    // =============================================================================================

    private static final class LicenseAdaptor extends AbstractAdaptor<License> {
        private LicenseAdaptor() {
            super(BlackDuckType.LICENSE,
                    SpdxTerm.NAME);
        }

        @Override
        protected Object lookup(License model, Term term) {
            if (term.equals(SpdxTerm.NAME)) {
                return model.getName();
            } else {
                return null;
            }
        }

        @Override
        protected Object store(License model, Term term, Object value) {
            Object original = null;
            if (term.equals(SpdxTerm.NAME)) {
                original = model.getName();
                model.setName(valueToString(value));
            }
            return original;
        }
    }

    // =============================================================================================
    // Project
    // =============================================================================================

    private static final class ProjectAdaptor extends AbstractAdaptor<Project> {
        private ProjectAdaptor() {
            super(BlackDuckType.PROJECT,
                    DoapTerm.NAME);
        }

        @Override
        protected Object lookup(Project model, Term term) {
            if (term.equals(DoapTerm.NAME)) {
                return model.getName();
            } else {
                return null;
            }
        }

        @Override
        protected Object store(Project model, Term term, Object value) {
            Object original = null;
            if (term.equals(DoapTerm.NAME)) {
                original = model.getName();
                model.setName(valueToString(value));
            }
            return original;
        }
    }

    // =============================================================================================
    // Version
    // =============================================================================================

    private static final class VersionAdaptor extends AbstractAdaptor<Version> {
        private VersionAdaptor() {
            super(BlackDuckType.VERSION,
                    DoapTerm.NAME,
                    DoapTerm.REVISION,
                    DoapTerm.HOMEPAGE,
                    DoapTerm.LICENSE);
        }

        @Override
        protected Object lookup(Version model, Term term) {
            if (term.equals(DoapTerm.NAME)) {
                return model.getName();
            } else if (term.equals(DoapTerm.REVISION)) {
                return model.getVersion();
            } else if (term.equals(DoapTerm.HOMEPAGE)) {
                return model.getHomepage();
            } else if (term.equals(DoapTerm.LICENSE)) {
                return model.getLicense();
            } else {
                return null;
            }
        }

        @Override
        protected Object store(Version model, Term term, Object value) {
            Object original = null;
            if (term.equals(DoapTerm.NAME)) {
                original = model.getName();
                model.setName(valueToString(value));
            } else if (term.equals(DoapTerm.REVISION)) {
                original = model.getVersion();
                model.setVersion(valueToString(value));
            } else if (term.equals(DoapTerm.HOMEPAGE)) {
                original = model.getHomepage();
                model.setHomepage(valueToString(value));
            } else if (term.equals(DoapTerm.LICENSE)) {
                original = model.getLicense();
                model.setLicense(valueToString(value));
            }
            return original;
        }
    }

    // =============================================================================================
    // Vulnerability
    // =============================================================================================

    private static final class VulnerabilityAdaptor extends AbstractAdaptor<Vulnerability> {
        private VulnerabilityAdaptor() {
            super(BlackDuckType.VULNERABILITY);
        }

        @Override
        protected Object lookup(Vulnerability model, Term term) {
            return null;
        }

        @Override
        protected Object store(Vulnerability model, Term term, Object value) {
            return null;
        }
    }

    // =============================================================================================

    /**
     * The base class for adaptors.
     */
    private static abstract class AbstractAdaptor<T> implements Adaptor {

        /**
         * A map backed by a model.
         */
        private final class ModelMap extends AbstractMap<Term, Object> {
            private final Supplier<Object> model;

            private ModelMap(Supplier<Object> model) {
                this.model = checkNotNull(model);
            }

            @Override
            public Set<Term> keySet() {
                return terms;
            }

            @Override
            public boolean containsKey(Object key) {
                return terms.contains(key);
            }

            @Override
            public int size() {
                return terms.size();
            }

            @Override
            public Set<Entry<Term, Object>> entrySet() {
                return Maps.asMap(terms, Functions.forMap(this)).entrySet();
            }

            @Override
            public Object get(Object key) {
                return key instanceof Term ? lookup((T) model.get(), (Term) key) : null;
            }

            @Override
            public Object put(Term key, Object value) {
                return store((T) model.get(), key, value);
            }
        }

        /**
         * The fully qualified name we are adapting.
         */
        private final Set<Type> types;

        /**
         * The set of terms supported by this adaptor.
         */
        private final Set<Term> terms;

        protected AbstractAdaptor(Type type, Term... terms) {
            this.types = ImmutableSet.of(type);
            this.terms = ImmutableSet.copyOf(terms);
        }

        /**
         * Returns the value of a term from a model.
         */
        @Nullable
        protected abstract Object lookup(T model, Term term);

        /**
         * Puts the value of of a term to to model, returning the previous value.
         */
        @Nullable
        protected abstract Object store(T model, Term term, Object value);

        @Override
        public final Set<Type> types() {
            return types;
        }

        @Override
        public final Map<Term, Object> asMap(Supplier<Object> model) {
            // Make sure the resulting map does not contain null values so it is compatible with ImmutableMap
            return Maps.filterValues(new ModelMap(model), Predicates.notNull());
        }

        /**
         * Helper to coerce a value into a string.
         */
        protected String valueToString(Object value) {
            return value.toString();
        }

        /**
         * Helper to coerce a value into a long.
         */
        protected Long valueToLong(Object value) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            } else {
                return Long.valueOf(value.toString());
            }
        }
    }

    /**
     * The mapping of model classes to adaptors.
     */
    private static Map<Class<?>, Adaptor> REGISTERED_ADAPTORS = ImmutableMap.<Class<?>, Adaptor> builder()
            .put(BillOfMaterials.class, new BillOfMaterialsAdaptor())
            .put(Component.class, new ComponentAdaptor())
            .put(File.class, new FileAdaptor())
            .put(License.class, new LicenseAdaptor())
            .put(Project.class, new ProjectAdaptor())
            .put(Version.class, new VersionAdaptor())
            .put(Vulnerability.class, new VulnerabilityAdaptor())
            .build();

    private ModelAdaptors() {
    }

    /**
     * Returns the collection of registered adaptors by model type.
     */
    public static Map<Class<?>, Adaptor> registeredAdaptors() {
        return REGISTERED_ADAPTORS;
    }
}
