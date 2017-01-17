/*
 * Copyright (C) 2016 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.bdio2.datatype;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * Tests for {@link Products} and {@link Product}.
 *
 * @author jgustie
 */
public class ProductsTest {

    @Test
    public void singleNameNoVersionNoComment() {
        Products products = Products.valueOf("foo");
        assertThat(products).hasSize(1);
        assertThat(products.mostSignificant().name()).isEqualTo("foo");
        assertThat(products.mostSignificant().version()).isNull();
        assertThat(products.mostSignificant().comment()).isNull();
    }

    @Test
    public void singleNameVersionNoComment() {
        Products products = Products.valueOf("foo/1.0");
        assertThat(products).hasSize(1);
        assertThat(products.mostSignificant().name()).isEqualTo("foo");
        assertThat(products.mostSignificant().version()).isEqualTo("1.0");
        assertThat(products.mostSignificant().comment()).isNull();
    }

    @Test
    public void singleNameVersionComment() {
        Products products = Products.valueOf("foo/1.0 (bar)");
        assertThat(products).hasSize(1);
        assertThat(products.mostSignificant().name()).isEqualTo("foo");
        assertThat(products.mostSignificant().version()).isEqualTo("1.0");
        assertThat(products.mostSignificant().comment()).isEqualTo("(bar)");
    }

    @Test
    public void multipleComments() {
        assertThat(Products.valueOf("foo/1.0 (bar)(gus)").mostSignificant().comment()).isEqualTo("(bar)(gus)");
        assertThat(Products.valueOf("foo/1.0 (bar) (gus)").mostSignificant().comment()).isEqualTo("(bar) (gus)");
        assertThat(Products.valueOf("foo/1.0 (bar)  (gus)").mostSignificant().comment()).isEqualTo("(bar)  (gus)");

        assertThat(Products.valueOf("foo/1.0 (bar)(gus)(toad)").mostSignificant().comment()).isEqualTo("(bar)(gus)(toad)");
        assertThat(Products.valueOf("foo/1.0 (bar) (gus) (toad)").mostSignificant().comment()).isEqualTo("(bar) (gus) (toad)");
        assertThat(Products.valueOf("foo/1.0 (bar)  (gus)  (toad)").mostSignificant().comment()).isEqualTo("(bar)  (gus)  (toad)");
    }

    @Test
    public void commentsWithSpaces() {
        assertThat(Products.valueOf("foo/1.0 (bar gus)").mostSignificant().comment()).isEqualTo("(bar gus)");
        assertThat(Products.valueOf("foo/1.0 (bar  gus)").mostSignificant().comment()).isEqualTo("(bar  gus)");
    }

    @Test
    public void multipleNamesNoVersionsNoComments() {
        Products fooBar = Products.valueOf("foo bar");
        assertThat(fooBar).hasSize(2);
        assertThat(fooBar).containsExactly(Product.valueOf("foo"), Product.valueOf("bar")).inOrder();
    }

    @Test
    public void multipleNamesVersionsNoComments() {
        Products fooBar = Products.valueOf("foo/1.0 bar/1.0");
        assertThat(fooBar).hasSize(2);
        assertThat(fooBar).containsExactly(Product.valueOf("foo/1.0"), Product.valueOf("bar/1.0")).inOrder();
    }

    @Test
    public void builderKeepsFirstDistinct() {
        assertThat(new Products.Builder()
                .addProduct("foo")
                .addProduct("bar")
                .addProduct("foo")
                .addProduct("gus")
                .build().toString()).isEqualTo("foo bar gus");
    }

    @Test
    public void builderKeepsFirstIgnoresComments() {
        // TODO It would be nice if this produced "foo/1 (foo) (bar) bar gus"
        assertThat(new Products.Builder()
                .addProduct("foo", "1", "foo")
                .addProduct("bar")
                .addProduct("foo", "1", "bar")
                .addProduct("gus", "2")
                .build().toString()).isEqualTo("foo/1 (foo) bar gus/2");
    }

}
