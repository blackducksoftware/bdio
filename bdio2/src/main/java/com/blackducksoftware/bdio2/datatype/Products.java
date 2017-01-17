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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Representation of a sequence of product identifiers. This is basically what you would expect from an HTTP
 * {@code User-Agent} or {@code Server} header value.
 *
 * @author jgustie
 * @see Product
 */
public final class Products implements Iterable<Product> {

    private List<Product> products;

    private Products(Iterable<Product> products) {
        this.products = ImmutableList.copyOf(products);
        Preconditions.checkArgument(!this.products.isEmpty(), "must specify at least one product");
    }

    @JsonCreator
    public static Products valueOf(CharSequence input) {
        List<Product> result = new ArrayList<>();
        boolean inComment = false;
        boolean hasToken = false;
        int tokenStart = 0;
        int tokenEnd = 0;
        for (int i = 0; i < input.length(); ++i) {
            char c = input.charAt(i);
            if (inComment) {
                if (c == ')') {
                    inComment = false;
                    tokenEnd = i + 1;
                }
            } else if (c == '(') {
                inComment = true;
            } else if (c == ' ' || c == '\t') {
                hasToken = true;
                tokenEnd = i;
            } else if (hasToken) {
                result.add(Product.valueOf(input.subSequence(tokenStart, tokenEnd).toString()));
                tokenStart = tokenEnd = i;
                hasToken = false;
            }
        }
        result.add(Product.valueOf(input.subSequence(tokenStart, input.length()).toString()));
        return new Products(result);
    }

    public static Products of(Product product) {
        return new Products(ImmutableList.of(product));
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
            return new Products(products.stream().map(Supplier::get).distinct().collect(ExtraCollectors.toImmutableList()));
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
