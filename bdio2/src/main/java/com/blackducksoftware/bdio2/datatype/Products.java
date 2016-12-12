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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.blackducksoftware.common.base.ExtraCollectors;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * Representation of a sequence of product identifiers. This is basically what you would expect from an HTTP
 * {@code User-Agent} or {@code Server} header value.
 *
 * @author jgustie
 * @see Product
 */
public final class Products implements Iterable<Product> {

    private static final CharMatcher WS = CharMatcher.is(' ').or(CharMatcher.is('\t'));

    private List<Product> products;

    private Products(Iterable<Product> products) {
        this.products = ImmutableList.copyOf(products);
        Preconditions.checkArgument(!this.products.isEmpty(), "must specify at least one product");
    }

    @JsonCreator
    public static Products valueOf(CharSequence input) {
        List<String> parts = Splitter.on(WS).splitToList(input);
        List<Product> result = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); ++i) {
            String value = parts.get(i);
            while (i < parts.size() - 1 && parts.get(i + 1).charAt(0) == '(') {
                value += ' ' + parts.get(++i);
            }
            result.add(Product.valueOf(value));
        }
        return new Products(result);
    }

    public Product mostSignificant() {
        return products.get(0);
    }

    public Products withoutComments() {
        return new Products(products.stream().map(Product::withoutComment).collect(ExtraCollectors.toImmutableList()));
    }

    @Override
    public Iterator<Product> iterator() {
        return products.iterator();
    }

    @Override
    public int hashCode() {
        return products.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof Products) {
            Products other = (Products) obj;
            return products.equals(other.products);
        } else {
            return false;
        }
    }

    @JsonValue
    @Override
    public String toString() {
        return products.stream().map(Product::toString).collect(Collectors.joining(" "));
    }

    public static final class Builder {

        // Defer parsing errors until build is called, or should we have failed fast?
        private List<Supplier<Product>> products = new LinkedList<>();

        public Products build() {
            return new Products(products.stream().map(Supplier::get).collect(ExtraCollectors.toImmutableList()));
        }

        public Builder addProduct(Product product) {
            products.add(() -> product);
            return this;
        }

        public Builder addProduct(String name) {
            products.add(() -> Product.create(name));
            return this;
        }

        public Builder addProduct(String name, String version) {
            products.add(() -> Product.create(name, version));
            return this;
        }

        public Builder addProduct(String name, String version, String comment) {
            products.add(() -> Product.create(name, version).withComment(comment));
            return this;
        }

    }

}
