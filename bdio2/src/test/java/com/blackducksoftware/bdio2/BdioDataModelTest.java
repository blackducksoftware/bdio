/*
 * Copyright 2017 Black Duck Software, Inc.
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
package com.blackducksoftware.bdio2;

import static com.blackducksoftware.common.base.ExtraOptionals.flatMapMany;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.blackducksoftware.common.base.ExtraStreams;
import com.google.common.base.Enums;
import com.google.common.collect.ImmutableSet;

/**
 * Tests to ensure consistency in the data model definition.
 *
 * @author jgustie
 */
@RunWith(Parameterized.class)
public class BdioDataModelTest {

    /**
     * This string is hard coded here independent of the {@link Bdio} class for testing purposes.
     */
    private static final String VOCAB = "https://blackducksoftware.github.io/bdio#";

    /**
     * These enumerations are not expected to start with {@value #VOCAB}.
     */
    private static final ImmutableSet<Enum<?>> NON_VOCAB = ImmutableSet.of(Bdio.Datatype.DateTime, Bdio.Datatype.Default, Bdio.Datatype.Long);

    /**
     * Helper that checks to the name and string representation.
     */
    private static void checkNameAndString(Enum<?> e, String regex) {
        assertThat(e.name()).matches(regex);
        if (e.toString().startsWith(VOCAB)) {
            assertThat(e.toString().substring(VOCAB.length())).matches(regex);
        }
    }

    /**
     * Generates a stream of all the BDIO enumerations.
     */
    private static Stream<Enum<?>> bdioEnums() {
        Stream.Builder<Enum<?>> result = Stream.builder();
        ExtraStreams.stream(Bdio.Class.class).forEach(result);
        ExtraStreams.stream(Bdio.ObjectProperty.class).forEach(result);
        ExtraStreams.stream(Bdio.DataProperty.class).forEach(result);
        ExtraStreams.stream(Bdio.Datatype.class).forEach(result);
        return result.build();
    }

    /**
     * Returns the name of the Java model type for a BDIO class.
     */
    private static Optional<Class<?>> modelType(Bdio.Class bdioClass) {
        try {
            String name = Bdio.class.getPackage().getName() + ".model." + bdioClass.name();
            return Optional.of(Class.forName(name));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Return the public methods of the model corresponding to the supplied {@code Bdio.Class}.
     */
    private static Stream<Method> modelTypeMethods(Bdio.Class bdioClass) {
        return flatMapMany(modelType(bdioClass), c -> Stream.of(c.getMethods()));
    }

    /**
     * Returns all of the BDIO enumerations as parameters for this test. This includes a useful name at index 0 so test
     * results are more readable; the actual enumeration will be a index 1.
     */
    @Parameters(name = "{0}")
    public static Iterable<Object[]> parameters() {
        return bdioEnums()
                .map(e -> new Object[] { "Bdio." + e.getDeclaringClass().getSimpleName() + "." + e.name(), e })
                .collect(Collectors.toList());
    }

    /**
     * The enumeration under test.
     */
    private final Enum<?> bdioEnum;

    public BdioDataModelTest(String name, Enum<?> bdioEnum) {
        this.bdioEnum = Objects.requireNonNull(bdioEnum);
    }

    /**
     * Predicate to check if the supplied Java method has the same name as the current enumeration.
     */
    private boolean methodName(Method method) {
        return method.getName().equals(bdioEnum.name());
    }

    /**
     * The {@code toString} method must return a valid URI with a non-null fragment.
     */
    @Test
    public void uriToString() throws URISyntaxException {
        // An empty string is allowed as a special case for Bdio.Datatype.Default
        assume().that(bdioEnum.toString()).isNotEmpty();
        assertThat(new URI(bdioEnum.toString()).getFragment()).isNotNull();
    }

    /**
     * Tests that the current BDIO enumeration has the correct vocabulary. In JSON-LD the "vocab" is the actually the
     * default string applied to unqualified IRIs; while this was leveraged in BDIO 1.x, BDIO 2+ <em>does not</em>
     * explicitly use a JSON-LD vocabulary to avoid accidental inclusion of terms that have not been defined in the
     * specification. This test ensures we remain consistent without the use of a JSON-LD "vocab".
     */
    @Test
    public void vocabulary() {
        assume().that(NON_VOCAB).doesNotContain(bdioEnum);
        assertThat(bdioEnum.toString()).startsWith(VOCAB);
    }

    /**
     * No two enumerations can have the same {@code name} or {@code toString}. If this test fails the test message will
     * be useless, but you will be able to see where the conflicts are based on which parameterized tests failed.
     */
    @Test
    public void uniqueness() {
        List<Enum<?>> duplicates = bdioEnums()
                .filter(e -> e.name().equals(bdioEnum.name()) || e.toString().equals(bdioEnum.toString()))
                .collect(Collectors.toList());

        // This list should only contain a single element every time
        assertThat(duplicates).containsExactly(bdioEnum);
    }

    /**
     * Restrict characters in our names to ASCII letters.
     */
    @Test
    public void allowedCharacters() {
        checkNameAndString(bdioEnum, "[a-zA-Z]+");
    }

    /**
     * By convention, only type enumerations will start with an upper case letter.
     */
    @Test
    public void nameConvention() {
        if (bdioEnum instanceof Bdio.Class || bdioEnum instanceof Bdio.Datatype) {
            checkNameAndString(bdioEnum, "^[A-Z].*");
        } else {
            checkNameAndString(bdioEnum, "^[a-z].*");
        }
    }

    /**
     * There is a stronger requirement for classes: the name should always be the fragment.
     */
    @Test
    public void classNameConvention() {
        assume().that(bdioEnum).isInstanceOf(Bdio.Class.class);
        assertThat(bdioEnum.toString()).isEqualTo(VOCAB + bdioEnum.name());
    }

    /**
     * Object properties must be annotated with {@link Bdio.AllowedOn} and {@link Bdio.ObjectPropertyRange}.
     */
    @Test
    public void objectPropertyMetadata() {
        assume().that(bdioEnum).isInstanceOf(Bdio.ObjectProperty.class);
        assertThat(Enums.getField(bdioEnum).getAnnotation(Bdio.AllowedOn.class)).named("@AllowedOn").isNotNull();
        assertThat(Enums.getField(bdioEnum).getAnnotation(Bdio.ObjectPropertyRange.class)).named("@ObjectPropertyRange").isNotNull();

        // BDIO object properties are not allowed in named graph metadata
        assertThat(Enums.getField(bdioEnum).getAnnotation(Bdio.AllowedOn.class).metadata()).named("@AllowedOn.metadata").isFalse();
    }

    /**
     * Object properties must be annotated with {@link Bdio.AllowedOn} and {@link Bdio.DataPropertyRange}.
     */
    @Test
    public void dataPropertyMetadata() {
        assume().that(bdioEnum).isInstanceOf(Bdio.DataProperty.class);
        assertThat(Enums.getField(bdioEnum).getAnnotation(Bdio.AllowedOn.class)).named("@AllowedOn").isNotNull();
        assertThat(Enums.getField(bdioEnum).getAnnotation(Bdio.DataPropertyRange.class)).named("@DataPropertyRange").isNotNull();
    }

    /**
     * Use reflection to validate the Java model.
     */
    @Test
    public void classHasModel() throws ReflectiveOperationException {
        assume().that(bdioEnum).isInstanceOf(Bdio.Class.class);
        Optional<Class<?>> modelType = modelType((Bdio.Class) bdioEnum);
        assertThat(modelType).isPresent();
        assertThat(modelType.get()).isAssignableTo(BdioObject.class);

        // Embedded types have a public no-argument constructor, everyone else has a String (identifier) constructor
        if (((Bdio.Class) bdioEnum).embedded()) {
            modelType.get().getConstructor();
        } else {
            modelType.get().getConstructor(String.class);
        }
    }

    /**
     * Use reflection to validate the Java model.
     */
    @Test
    public void propertyHasModel() throws ReflectiveOperationException {
        assume().that(bdioEnum instanceof Bdio.ObjectProperty || bdioEnum instanceof Bdio.DataProperty).isTrue();

        Bdio.AllowedOn allowedOn = Enums.getField(bdioEnum).getAnnotation(Bdio.AllowedOn.class);

        // Check the BdioMetadata class for metadata properties
        if (allowedOn.metadata()) {
            assertThat(Stream.of(BdioMetadata.class.getMethods())
                    .anyMatch(this::methodName))
                            .named("BdioMetadata").isTrue();
        }

        // Check the model type for other domain assignments
        assertThat(Stream.of(allowedOn.value())
                .filter(bdioClass -> modelTypeMethods(bdioClass).noneMatch(this::methodName))
                .map(Bdio.Class::name))
                        .isEmpty();
    }

    /**
     * Make sure we do not use any fields are being used for the detection of legacy formats.
     */
    @Test
    public void legacyFormatConflict() {
        // Technically we should make sure the BDIO 1.x vocabulary doesn't appear either
        if (bdioEnum instanceof Bdio.Class) {
            assertThat(bdioEnum.name()).isNotIn(EmitterFactory.BDIO_1X_TYPE_NAMES);
            assertThat(bdioEnum.toString()).isNotIn(EmitterFactory.BDIO_1X_TYPE_NAMES);
        } else if (bdioEnum instanceof Bdio.ObjectProperty || bdioEnum instanceof Bdio.DataProperty) {
            assertThat(bdioEnum.name()).isNotIn(EmitterFactory.SCAN_CONTAINER_FIELD_NAMES);
            assertThat(bdioEnum.name()).isNotIn(EmitterFactory.STREAMABLE_SCAN_CONTAINER_FIELD_NAMES);
            assertThat(bdioEnum.name()).isNotIn(EmitterFactory.BDIO_1X_FIELD_NAMES);
            assertThat(bdioEnum.toString()).isNotIn(EmitterFactory.BDIO_1X_FIELD_NAMES);
        }
    }

}
