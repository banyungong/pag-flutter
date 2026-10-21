package com.example.flutter_pag_plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

public class TextureBindingGuardTest {
    @Test
    public void restoresBindingAndActiveUnitAfterUpdate() {
        FakeBindings gl = new FakeBindings();
        TextureBindingGuard guard = new TextureBindingGuard(gl);

        guard.run(() -> { gl.unit = 3; gl.texture = 99; });

        assertEquals(1, gl.unit);
        assertEquals(31, gl.texture);
        assertEquals(2, gl.queries);
        assertEquals(2, gl.restores);
    }

    @Test
    public void restoresBindingWhenOperationThrowsAndPreservesException() {
        FakeBindings gl = new FakeBindings();
        RuntimeException expected = new RuntimeException("update failed");
        try {
            new TextureBindingGuard(gl).run(() -> {
                gl.texture = 99;
                throw expected;
            });
            fail("Expected the original SurfaceTexture exception");
        } catch (RuntimeException actual) {
            assertSame(expected, actual);
        }
        assertEquals(31, gl.texture);
        assertEquals(2, gl.restores);
    }

    @Test
    public void preservesZeroBinding() {
        FakeBindings gl = new FakeBindings();
        gl.texture = 0;
        new TextureBindingGuard(gl).run(() -> gl.texture = 99);
        assertEquals(0, gl.texture);
    }

    @Test
    public void readsCurrentStateForEveryFrame() {
        FakeBindings gl = new FakeBindings();
        TextureBindingGuard guard = new TextureBindingGuard(gl);
        guard.run(() -> gl.texture = 99);
        gl.texture = 42;
        guard.run(() -> gl.texture = 100);
        assertEquals(42, gl.texture);
        assertEquals(4, gl.queries);
    }

    @Test
    public void doesNotQueryOrRestoreGlWithoutCurrentContext() {
        FakeBindings gl = new FakeBindings();
        gl.current = false;
        new TextureBindingGuard(gl).run(() -> gl.texture = 99);
        assertEquals(99, gl.texture);
        assertEquals(0, gl.queries);
        assertEquals(0, gl.restores);
    }

    @Test
    public void preservesOriginalErrorWithoutCurrentContext() {
        FakeBindings gl = new FakeBindings();
        gl.current = false;
        RuntimeException expected = new RuntimeException("no context");
        try {
            new TextureBindingGuard(gl).run(() -> { throw expected; });
            fail("Expected the original SurfaceTexture exception");
        } catch (RuntimeException actual) {
            assertSame(expected, actual);
        }
        assertEquals(0, gl.queries);
        assertEquals(0, gl.restores);
    }

    private static final class FakeBindings implements TextureBindingGuard.GlBindings {
        boolean current = true;
        int unit = 1;
        int texture = 31;
        int queries;
        int restores;

        public boolean hasCurrentContext() { return current; }
        public int activeTexture() { queries++; return unit; }
        public int externalTextureBinding() { queries++; return texture; }
        public void activeTexture(int unit) { restores++; this.unit = unit; }
        public void bindExternalTexture(int texture) { restores++; this.texture = texture; }
    }
}
