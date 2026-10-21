package com.example.flutter_pag_plugin;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import io.flutter.view.TextureRegistry;

/**
 * A PAG-owned SurfaceTexture that does not leak GL bindings into Flutter's raster context.
 *
 * SurfaceTexture.attachToGLContext/updateTexImage implicitly bind GL_TEXTURE_EXTERNAL_OES.
 * Skia caches texture bindings, so leaving PAG bound can make a subsequent WebView sample PAG
 * instead of its own image. These overrides run on the Flutter consumer thread, not the PAG
 * producer thread. Keep Flutter's wrapper, frame notifications and resource ownership unchanged.
 */
final class BindingGuardSurfaceTexture extends SurfaceTexture {
    private final TextureBindingGuard guard = new TextureBindingGuard(new GlBindings());
    // Allocated once; updateTexImage is called every frame.
    private final Runnable updateImage = super::updateTexImage;

    private BindingGuardSurfaceTexture() {
        super(0);
    }

    static TextureRegistry.SurfaceTextureEntry register(TextureRegistry registry) {
        final BindingGuardSurfaceTexture texture = new BindingGuardSurfaceTexture();
        try {
            return registry.registerSurfaceTexture(texture);
        } catch (RuntimeException | Error error) {
            // Flutter owns the texture only after registration succeeds.
            texture.release();
            throw error;
        }
    }

    @Override
    public void attachToGLContext(int textureName) {
        guard.run(() -> super.attachToGLContext(textureName));
    }

    @Override
    public void updateTexImage() {
        guard.run(updateImage);
    }

    private static final class GlBindings implements TextureBindingGuard.GlBindings {
        // Access is serialized by Flutter's SurfaceTextureWrapper, including context reattachment.
        private final int[] query = new int[1];

        @Override
        public boolean hasCurrentContext() {
            return !EGL14.EGL_NO_CONTEXT.equals(EGL14.eglGetCurrentContext());
        }

        @Override
        public int activeTexture() {
            GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, query, 0);
            return query[0];
        }

        @Override
        public int externalTextureBinding() {
            GLES20.glGetIntegerv(GLES11Ext.GL_TEXTURE_BINDING_EXTERNAL_OES, query, 0);
            return query[0];
        }

        @Override
        public void activeTexture(int unit) {
            GLES20.glActiveTexture(unit);
        }

        @Override
        public void bindExternalTexture(int texture) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
        }
    }
}
