package io.testfly.unit.junit5;

import io.testfly.junit5.EnableTestFly;
import io.testfly.junit5.TestFlyExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testng.annotations.Test;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link EnableTestFly} annotation.
 * Verifies annotation metadata and that it registers the TestFly extension.
 */
@Test(singleThreaded = true)
public class EnableTestFlyTest {

    // ----------------------------------------------------------
    // Annotation presence → extension is registered
    // ----------------------------------------------------------

    @Test
    public void annotation_isMetaAnnotatedWithExtendWith() {
        ExtendWith extendWith = EnableTestFly.class.getAnnotation(ExtendWith.class);

        assertNotNull(extendWith, "@EnableTestFly should be meta-annotated with @ExtendWith");
    }

    @Test
    public void annotation_registersTestFlyExtension() {
        ExtendWith extendWith = EnableTestFly.class.getAnnotation(ExtendWith.class);

        assertNotNull(extendWith);
        Class<?>[] extensions = extendWith.value();
        assertEquals(extensions.length, 1);
        assertEquals(extensions[0], TestFlyExtension.class);
    }

    @Test
    public void annotation_hasRuntimeRetention() {
        Retention retention = EnableTestFly.class.getAnnotation(Retention.class);

        assertNotNull(retention, "@EnableTestFly should have @Retention");
        assertEquals(retention.value(), RetentionPolicy.RUNTIME);
    }

    @Test
    public void annotation_isInherited() {
        Inherited inherited = EnableTestFly.class.getAnnotation(Inherited.class);

        assertNotNull(inherited, "@EnableTestFly should be @Inherited");
    }

    // ----------------------------------------------------------
    // Annotation on a test class → extension is discoverable
    // ----------------------------------------------------------

    @Test
    public void annotatedClass_hasEnableTestFlyAnnotation() {
        assertTrue(SampleAnnotatedTest.class.isAnnotationPresent(EnableTestFly.class));
    }

    @Test
    public void annotatedClass_inheritsExtendWithViaEnableTestFly() {
        EnableTestFly annotation = SampleAnnotatedTest.class.getAnnotation(EnableTestFly.class);
        assertNotNull(annotation);

        // Through the meta-annotation chain, the class should resolve TestFlyExtension
        ExtendWith extendWith = EnableTestFly.class.getAnnotation(ExtendWith.class);
        assertNotNull(extendWith);
        assertEquals(extendWith.value()[0], TestFlyExtension.class);
    }

    // ----------------------------------------------------------
    // Annotation absence → extension is NOT registered
    // ----------------------------------------------------------

    @Test
    public void unannotatedClass_doesNotHaveEnableTestFly() {
        assertFalse(SamplePlainTest.class.isAnnotationPresent(EnableTestFly.class));
    }

    @Test
    public void unannotatedClass_doesNotHaveExtendWith() {
        assertFalse(SamplePlainTest.class.isAnnotationPresent(ExtendWith.class));
    }

    // ----------------------------------------------------------
    // Test fixtures
    // ----------------------------------------------------------

    @EnableTestFly
    static class SampleAnnotatedTest {
    }

    static class SamplePlainTest {
    }
}
