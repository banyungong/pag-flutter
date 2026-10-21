package com.example.flutter_pag_plugin;

/** Restores the consumer's external-texture binding after a SurfaceTexture operation. */
final class TextureBindingGuard {
    interface GlBindings {
        boolean hasCurrentContext();
        int activeTexture();
        int externalTextureBinding();
        void activeTexture(int unit);
        void bindExternalTexture(int texture);
    }

    private final GlBindings gl;

    TextureBindingGuard(GlBindings gl) {
        this.gl = gl;
    }

    void run(Runnable operation) {
        if (!gl.hasCurrentContext()) {
            // Preserve SurfaceTexture's original behavior/error when no context is current.
            operation.run();
            return;
        }

        final int activeTexture = gl.activeTexture();
        final int texture = gl.externalTextureBinding();
        try {
            operation.run();
        } finally {
            gl.activeTexture(activeTexture);
            gl.bindExternalTexture(texture);
        }
    }
}
