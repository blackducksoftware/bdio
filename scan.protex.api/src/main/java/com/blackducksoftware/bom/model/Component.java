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

import java.util.UUID;

import javax.annotation.Nullable;

import com.blackducksoftware.bom.BlackDuckTerm;
import com.blackducksoftware.bom.BlackDuckType;
import com.blackducksoftware.bom.DoapTerm;

/**
 * A component in a Bill of Materials.
 *
 * @author jgustie
 */
public class Component extends AbstractModel<Component> {

    @Nullable
    private String name;

    private static final ModelField<Component> NAME = new ModelField<Component>(DoapTerm.NAME) {
        @Override
        protected Object get(Component component) {
            return component.getName();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setName(valueToString(value));
        }
    };

    @Nullable
    private String version;

    private static final ModelField<Component> VERSION = new ModelField<Component>(DoapTerm.REVISION) {
        @Override
        protected Object get(Component component) {
            return component.getVersion();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setVersion(valueToString(value));
        }
    };

    @Nullable
    private String homepage;

    private static final ModelField<Component> HOMEPAGE = new ModelField<Component>(DoapTerm.HOMEPAGE) {
        @Override
        protected Object get(Component component) {
            return component.getHomepage();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setHomepage(valueToString(value));
        }
    };

    @Nullable
    private String license;

    private static final ModelField<Component> LICENSE = new ModelField<Component>(DoapTerm.LICENSE) {
        @Override
        protected Object get(Component component) {
            return component.getLicense();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setLicense(valueToString(value));
        }
    };

    @Nullable
    private String legacyId;

    private static final ModelField<Component> LEGACY_ID = new ModelField<Component>(BlackDuckTerm.LEGACY_ID) {
        @Override
        protected Object get(Component component) {
            return component.getLegacyId();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setLegacyId(valueToString(value));
        }
    };

    @Nullable
    private String legacyVersionId;

    private static final ModelField<Component> LEGACY_VERSION_ID = new ModelField<Component>(BlackDuckTerm.LEGACY_VERSION_ID) {
        @Override
        protected Object get(Component component) {
            return component.getLegacyVersionId();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setLegacyVersionId(valueToString(value));
        }
    };

    @Nullable
    private UUID knowledgeBaseId;

    private static final ModelField<Component> KNOWLEDGE_BASE_ID = new ModelField<Component>(BlackDuckTerm.KNOWLEDGE_BASE_ID) {
        @Override
        protected Object get(Component component) {
            return component.getKnowledgeBaseId();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setKnowledgeBaseId(UUID.fromString(valueToString(value)));
        }
    };

    @Nullable
    private UUID knowledgeBaseVersionId;

    private static final ModelField<Component> KNOWLEDGE_BASE_VERSION_ID = new ModelField<Component>(BlackDuckTerm.KNOWLEDGE_BASE_VERSION_ID) {
        @Override
        protected Object get(Component component) {
            return component.getKnowledgeBaseVersionId();
        }

        @Override
        protected void set(Component component, Object value) {
            component.setKnowledgeBaseVersionId(UUID.fromString(valueToString(value)));
        }
    };

    public Component() {
        super(BlackDuckType.COMPONENT,
                NAME, VERSION, HOMEPAGE, LICENSE, LEGACY_ID, LEGACY_VERSION_ID, KNOWLEDGE_BASE_ID, KNOWLEDGE_BASE_VERSION_ID);
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @Nullable
    public String getVersion() {
        return version;
    }

    public void setVersion(@Nullable String version) {
        this.version = version;
    }

    @Nullable
    public String getHomepage() {
        return homepage;
    }

    public void setHomepage(@Nullable String homepage) {
        this.homepage = homepage;
    }

    @Nullable
    public String getLicense() {
        return license;
    }

    public void setLicense(@Nullable String license) {
        this.license = license;
    }

    @Nullable
    public String getLegacyId() {
        return legacyId;
    }

    public void setLegacyId(@Nullable String legacyId) {
        this.legacyId = legacyId;
    }

    @Nullable
    public String getLegacyVersionId() {
        return legacyVersionId;
    }

    public void setLegacyVersionId(@Nullable String legacyVersionId) {
        this.legacyVersionId = legacyVersionId;
    }

    @Nullable
    public UUID getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(@Nullable UUID knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    @Nullable
    public UUID getKnowledgeBaseVersionId() {
        return knowledgeBaseVersionId;
    }

    public void setKnowledgeBaseVersionId(@Nullable UUID knowledgeBaseVersionId) {
        this.knowledgeBaseVersionId = knowledgeBaseVersionId;
    }

}
