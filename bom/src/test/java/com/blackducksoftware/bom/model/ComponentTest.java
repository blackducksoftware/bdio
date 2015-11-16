package com.blackducksoftware.bom.model;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class ComponentTest {

    @Test
    public void testIsVersionWhenNotVersion() {
        final Component component = new Component();
        component.setName("FooBar");

        assertThat(component.isVersion()).isFalse();
    }

    @Test
    public void testIsVersionWhenVersion() {
        final Component component = new Component();
        component.setName("FooBar");
        component.setVersion("1.0.0");

        assertThat(component.isVersion()).isTrue();
    }

}
