package com.blackducksoftware.bom.model;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ComponentTest {
    private static final String NAME = "ComponentName";

    private static final String VERSION = "1.0.0";

    @Test
    public void testIsVersionWhenNotVersion() {
        final Component component = new Component();
        component.setName(NAME);

        Assert.assertFalse(component.isVersion(), "Component should not be a component version.");
    }

    @Test
    public void testIsVersionWhenVersion() {
        final Component component = new Component();
        component.setName(NAME);
        component.setVersion(VERSION);

        Assert.assertTrue(component.isVersion(), "Component should be a component version.");
    }
}
