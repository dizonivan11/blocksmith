package net.adeptfrog.blocksmith.client.render;

import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.data.VoxelShape;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Draws the forging screen's 3D view on the GPU with OpenGL: {@link GpuVoxelShaders} ray-cast the model straight into
 * a texture Minecraft owns (the editor's view texture), which the screen then draws like any other texture.
 * <p>
 * It talks to OpenGL directly, the same way on every supported Minecraft version, and puts back every piece of GL
 * state it touches so Minecraft's own state tracking stays correct. Anything going wrong (no OpenGL backend, a shader
 * that does not compile, a GL error) switches it off for the rest of the session; the screen then uses the software
 * {@link VoxelRaycaster} as before. Must only be used on the render thread.
 */
public final class GpuVoxelView implements AutoCloseable {
    private static boolean failed = Boolean.getBoolean("blocksmith.cpuEditor");

    /** Whether the GPU renderer may be used (false after a failure, or with -Dblocksmith.cpuEditor=true). */
    public static boolean usable() {
        return !failed;
    }

    /** Switches the GPU renderer off for the rest of the session. */
    public static void disable(String why) {
        fail(why, null);
    }

    private static void fail(String what, Throwable error) {
        failed = true;
        if (error != null) Blocksmith.LOGGER.warn("Blocksmith: GPU editor renderer disabled ({}); using the software renderer", what, error);
        else Blocksmith.LOGGER.warn("Blocksmith: GPU editor renderer disabled ({}); using the software renderer", what);
    }

    private int traceProgram, outlineProgram, vao;
    private int locP, locHl, locCells, locRaw, locOutline;
    private int cellsTex, cellsW, cellsH;
    private int rawTex, rawFbo, rawSize;
    private int outFbo, outTex;
    private ByteBuffer cellsBuf;
    private boolean ready;

    /**
     * Renders one frame into {@code targetTex} (an RGBA8 GL texture of view[0] x view[1] pixels).
     *
     * @param view         {@link VoxelRaycaster#exportView} of the screen's ray-caster (camera, light, lines)
     * @param cells        cell colours as for {@link VoxelRaycaster#syncCells} (0 = empty)
     * @param shapes       {@link VoxelShape} of each cell
     * @param cellsChanged whether {@code cells}/{@code shapes} changed since the last call
     * @param highlight    hovered cell {x, y, zi, kind (0 none, 1 voxel, 2 floor), nx, ny, nz, 0}
     * @return false if the GPU renderer failed and is now disabled
     */
    public boolean render(int targetTex, float[] view, int[] cells, byte[] shapes, boolean cellsChanged, int[] highlight) {
        if (failed || targetTex <= 0) return false;
        int size = (int) view[0];
        int g = (int) view[2], layers = (int) view[3];
        SavedState saved = null;
        try {
            saved = SavedState.capture();
            while (GL33C.glGetError() != GL33C.GL_NO_ERROR) { /* drop errors left by others */ }
            if (!ready) init();
            if (!ready) return false;

            boolean cellsResized = ensureCells(g, g * layers);
            if (cellsChanged || cellsResized) uploadCells(cells, shapes);
            ensureRaw(size);
            if (!ensureOut(targetTex)) return false;

            GL33C.glDisable(GL33C.GL_BLEND);
            GL33C.glDisable(GL33C.GL_DEPTH_TEST);
            GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
            GL33C.glDisable(GL33C.GL_CULL_FACE);
            GL33C.glDisable(GL33C.GL_STENCIL_TEST);
            GL33C.glColorMask(true, true, true, true);
            GL33C.glViewport(0, 0, size, size);
            GL33C.glBindVertexArray(this.vao);
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
            GL33C.glBindSampler(0, 0);

            // Pass 1: model, floor grid and supersampling into the raw image
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, this.rawFbo);
            GL33C.glUseProgram(this.traceProgram);
            GL33C.glUniform4fv(this.locP, view);
            GL33C.glUniform4iv(this.locHl, highlight);
            GL33C.glUniform1i(this.locCells, 0);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, this.cellsTex);
            GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 3);

            // Pass 2: silhouette outline into the target
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, this.outFbo);
            GL33C.glUseProgram(this.outlineProgram);
            GL33C.glUniform1i(this.locRaw, 0);
            GL33C.glUniform4i(this.locOutline, (int) view[27], view[25] > 0.5f ? 1 : 0, size, size);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, this.rawTex);
            GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 3);

            int err = GL33C.glGetError();
            if (err != GL33C.GL_NO_ERROR) {
                fail("OpenGL error 0x" + Integer.toHexString(err), null);
                return false;
            }
            return true;
        } catch (Throwable t) {
            fail("exception while rendering", t);
            return false;
        } finally {
            if (saved != null) {
                try { saved.restore(); } catch (Throwable ignored) { }
            }
        }
    }

    /** Reads an RGBA8 texture back as ARGB pixels (rows from the top). Used once for the self-check. */
    public static int[] readBack(int tex, int width, int height) {
        SavedState saved = SavedState.capture();
        ByteBuffer buf = MemoryUtil.memAlloc(width * height * 4);
        try {
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, tex);
            GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, buf);
            int[] out = new int[width * height];
            for (int i = 0; i < out.length; i++) {
                int r = buf.get(i * 4) & 0xFF, gg = buf.get(i * 4 + 1) & 0xFF, b = buf.get(i * 4 + 2) & 0xFF, a = buf.get(i * 4 + 3) & 0xFF;
                out[i] = (a << 24) | (r << 16) | (gg << 8) | b;
            }
            return out;
        } finally {
            MemoryUtil.memFree(buf);
            saved.restore();
        }
    }

    private void init() {
        int vs1 = compile(GL33C.GL_VERTEX_SHADER, GpuVoxelShaders.desktop(GpuVoxelShaders.VERTEX), "vertex");
        int fs1 = compile(GL33C.GL_FRAGMENT_SHADER, GpuVoxelShaders.desktop(GpuVoxelShaders.TRACE_FRAGMENT), "trace");
        int fs2 = compile(GL33C.GL_FRAGMENT_SHADER, GpuVoxelShaders.desktop(GpuVoxelShaders.OUTLINE_FRAGMENT), "outline");
        if (vs1 == 0 || fs1 == 0 || fs2 == 0) {
            if (vs1 != 0) GL33C.glDeleteShader(vs1);
            if (fs1 != 0) GL33C.glDeleteShader(fs1);
            if (fs2 != 0) GL33C.glDeleteShader(fs2);
            return;
        }
        this.traceProgram = link(vs1, fs1, "trace");
        this.outlineProgram = link(vs1, fs2, "outline");
        GL33C.glDeleteShader(vs1);
        GL33C.glDeleteShader(fs1);
        GL33C.glDeleteShader(fs2);
        if (this.traceProgram == 0 || this.outlineProgram == 0) return;
        this.locP = GL33C.glGetUniformLocation(this.traceProgram, "uP");
        this.locHl = GL33C.glGetUniformLocation(this.traceProgram, "uHl");
        this.locCells = GL33C.glGetUniformLocation(this.traceProgram, "uCells");
        this.locRaw = GL33C.glGetUniformLocation(this.outlineProgram, "uRaw");
        this.locOutline = GL33C.glGetUniformLocation(this.outlineProgram, "uOutline");
        this.vao = GL33C.glGenVertexArrays();
        this.ready = true;
        Blocksmith.LOGGER.info("Blocksmith: GPU editor renderer ready ({})", GL33C.glGetString(GL33C.GL_RENDERER));
    }

    private static int compile(int type, String src, String name) {
        int s = GL33C.glCreateShader(type);
        safeShaderSource(s, src);
        GL33C.glCompileShader(s);
        if (GL33C.glGetShaderi(s, GL33C.GL_COMPILE_STATUS) == GL33C.GL_FALSE) {
            String log = GL33C.glGetShaderInfoLog(s);
            GL33C.glDeleteShader(s);
            fail("shader '" + name + "' did not compile: " + log, null);
            return 0;
        }
        return s;
    }

    /**
     * glShaderSource with a null length pointer, so the driver reads up to the terminator. Some AMD drivers misread the
     * lengths and crash otherwise (same workaround as Sodium and Iris).
     */
    private static void safeShaderSource(int shader, CharSequence source) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer text = MemoryUtil.memUTF8(source, true);
            PointerBuffer pointers = stack.mallocPointer(1);
            pointers.put(text);
            GL33C.nglShaderSource(shader, 1, pointers.address0(), 0);
            org.lwjgl.system.APIUtil.apiArrayFree(pointers.address0(), 1);
        }
    }

    private static int link(int vs, int fs, String name) {
        int p = GL33C.glCreateProgram();
        GL33C.glAttachShader(p, vs);
        GL33C.glAttachShader(p, fs);
        GL33C.glLinkProgram(p);
        GL33C.glDetachShader(p, vs);
        GL33C.glDetachShader(p, fs);
        if (GL33C.glGetProgrami(p, GL33C.GL_LINK_STATUS) == GL33C.GL_FALSE) {
            String log = GL33C.glGetProgramInfoLog(p);
            GL33C.glDeleteProgram(p);
            fail("program '" + name + "' did not link: " + log, null);
            return 0;
        }
        return p;
    }

    private static int newTexture(int w, int h) {
        int t = GL33C.glGenTextures();
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, t);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAX_LEVEL, 0);
        GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, w, h, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return t;
    }

    private boolean ensureCells(int w, int h) {
        if (this.cellsTex != 0 && this.cellsW == w && this.cellsH == h) return false;
        if (this.cellsTex != 0) GL33C.glDeleteTextures(this.cellsTex);
        if (this.cellsBuf != null) MemoryUtil.memFree(this.cellsBuf);
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
        this.cellsTex = newTexture(w, h);
        this.cellsW = w;
        this.cellsH = h;
        this.cellsBuf = MemoryUtil.memAlloc(w * h * 4);
        return true;
    }

    private void uploadCells(int[] cells, byte[] shapes) {
        ByteBuffer b = this.cellsBuf;
        int count = Math.min(cells.length, this.cellsW * this.cellsH);
        b.clear();
        for (int i = 0; i < this.cellsW * this.cellsH; i++) {
            int c = i < count ? cells[i] : 0;
            int mask = c == 0 ? 0 : VoxelShape.octants(shapes == null ? VoxelShape.FULL : shapes[i]);
            b.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) mask);
        }
        b.flip();
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_ROW_LENGTH, 0);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_ROWS, 0);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_PIXELS, 0);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_ALIGNMENT, 4);
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, this.cellsTex);
        GL33C.glTexSubImage2D(GL33C.GL_TEXTURE_2D, 0, 0, 0, this.cellsW, this.cellsH, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, b);
    }

    private void ensureRaw(int size) {
        if (this.rawTex != 0 && this.rawSize == size) return;
        if (this.rawTex != 0) GL33C.glDeleteTextures(this.rawTex);
        if (this.rawFbo != 0) GL33C.glDeleteFramebuffers(this.rawFbo);
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
        this.rawTex = newTexture(size, size);
        this.rawSize = size;
        this.rawFbo = GL33C.glGenFramebuffers();
        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, this.rawFbo);
        GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, this.rawTex, 0);
    }

    private boolean ensureOut(int tex) {
        if (this.outFbo != 0 && this.outTex == tex) return true;
        if (this.outFbo == 0) this.outFbo = GL33C.glGenFramebuffers();
        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, this.outFbo);
        GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, tex, 0);
        int status = GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER);
        if (status != GL33C.GL_FRAMEBUFFER_COMPLETE) {
            fail("view texture cannot be drawn into (framebuffer status 0x" + Integer.toHexString(status) + ")", null);
            return false;
        }
        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, this.rawFbo);
        status = GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER);
        if (status != GL33C.GL_FRAMEBUFFER_COMPLETE) {
            fail("work texture cannot be drawn into (framebuffer status 0x" + Integer.toHexString(status) + ")", null);
            return false;
        }
        this.outTex = tex;
        return true;
    }

    /** Frees every GL object. Render thread only. */
    @Override
    public void close() {
        try {
            if (this.traceProgram != 0) GL33C.glDeleteProgram(this.traceProgram);
            if (this.outlineProgram != 0) GL33C.glDeleteProgram(this.outlineProgram);
            if (this.vao != 0) GL33C.glDeleteVertexArrays(this.vao);
            if (this.cellsTex != 0) GL33C.glDeleteTextures(this.cellsTex);
            if (this.rawTex != 0) GL33C.glDeleteTextures(this.rawTex);
            if (this.rawFbo != 0) GL33C.glDeleteFramebuffers(this.rawFbo);
            if (this.outFbo != 0) GL33C.glDeleteFramebuffers(this.outFbo);
        } catch (Throwable ignored) {
            // no GL context any more: the driver frees everything with it
        }
        if (this.cellsBuf != null) MemoryUtil.memFree(this.cellsBuf);
        this.cellsBuf = null;
        this.traceProgram = this.outlineProgram = this.vao = this.cellsTex = this.rawTex = this.rawFbo = this.outFbo = 0;
        this.outTex = 0;
        this.ready = false;
    }

    /** The GL state this renderer changes, captured before and put back after, so Minecraft's state cache stays right. */
    private static final class SavedState {
        int drawFbo, readFbo, program, vao, activeTexture, texture0, sampler0, unpackBuffer, packBuffer;
        int unpackRow, unpackSkipRows, unpackSkipPixels, unpackAlign, packRow, packSkipRows, packSkipPixels, packAlign;
        final int[] viewport = new int[4];
        boolean blend, depth, scissor, cull, stencil;
        boolean maskR, maskG, maskB, maskA;

        static SavedState capture() {
            SavedState s = new SavedState();
            s.drawFbo = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
            s.readFbo = GL33C.glGetInteger(GL33C.GL_READ_FRAMEBUFFER_BINDING);
            s.program = GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM);
            s.vao = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING);
            s.activeTexture = GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE);
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
            s.texture0 = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
            s.sampler0 = GL33C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
            s.unpackBuffer = GL33C.glGetInteger(GL33C.GL_PIXEL_UNPACK_BUFFER_BINDING);
            s.packBuffer = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
            s.unpackRow = GL33C.glGetInteger(GL33C.GL_UNPACK_ROW_LENGTH);
            s.unpackSkipRows = GL33C.glGetInteger(GL33C.GL_UNPACK_SKIP_ROWS);
            s.unpackSkipPixels = GL33C.glGetInteger(GL33C.GL_UNPACK_SKIP_PIXELS);
            s.unpackAlign = GL33C.glGetInteger(GL33C.GL_UNPACK_ALIGNMENT);
            s.packRow = GL33C.glGetInteger(GL33C.GL_PACK_ROW_LENGTH);
            s.packSkipRows = GL33C.glGetInteger(GL33C.GL_PACK_SKIP_ROWS);
            s.packSkipPixels = GL33C.glGetInteger(GL33C.GL_PACK_SKIP_PIXELS);
            s.packAlign = GL33C.glGetInteger(GL33C.GL_PACK_ALIGNMENT);
            GL33C.glGetIntegerv(GL33C.GL_VIEWPORT, s.viewport);
            s.blend = GL33C.glIsEnabled(GL33C.GL_BLEND);
            s.depth = GL33C.glIsEnabled(GL33C.GL_DEPTH_TEST);
            s.scissor = GL33C.glIsEnabled(GL33C.GL_SCISSOR_TEST);
            s.cull = GL33C.glIsEnabled(GL33C.GL_CULL_FACE);
            s.stencil = GL33C.glIsEnabled(GL33C.GL_STENCIL_TEST);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer m = stack.malloc(4);
                GL33C.glGetBooleanv(GL33C.GL_COLOR_WRITEMASK, m);
                s.maskR = m.get(0) != 0; s.maskG = m.get(1) != 0; s.maskB = m.get(2) != 0; s.maskA = m.get(3) != 0;
            }
            // For downloads: start from tightly packed rows
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
            GL33C.glPixelStorei(GL33C.GL_PACK_ROW_LENGTH, 0);
            GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_ROWS, 0);
            GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_PIXELS, 0);
            GL33C.glPixelStorei(GL33C.GL_PACK_ALIGNMENT, 4);
            return s;
        }

        void restore() {
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, this.drawFbo);
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, this.readFbo);
            GL33C.glViewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
            GL33C.glUseProgram(this.program);
            GL33C.glBindVertexArray(this.vao);
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, this.texture0);
            GL33C.glBindSampler(0, this.sampler0);
            GL33C.glActiveTexture(this.activeTexture);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, this.unpackBuffer);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, this.packBuffer);
            GL33C.glPixelStorei(GL33C.GL_UNPACK_ROW_LENGTH, this.unpackRow);
            GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_ROWS, this.unpackSkipRows);
            GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_PIXELS, this.unpackSkipPixels);
            GL33C.glPixelStorei(GL33C.GL_UNPACK_ALIGNMENT, this.unpackAlign);
            GL33C.glPixelStorei(GL33C.GL_PACK_ROW_LENGTH, this.packRow);
            GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_ROWS, this.packSkipRows);
            GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_PIXELS, this.packSkipPixels);
            GL33C.glPixelStorei(GL33C.GL_PACK_ALIGNMENT, this.packAlign);
            set(GL33C.GL_BLEND, this.blend);
            set(GL33C.GL_DEPTH_TEST, this.depth);
            set(GL33C.GL_SCISSOR_TEST, this.scissor);
            set(GL33C.GL_CULL_FACE, this.cull);
            set(GL33C.GL_STENCIL_TEST, this.stencil);
            GL33C.glColorMask(this.maskR, this.maskG, this.maskB, this.maskA);
        }

        private static void set(int cap, boolean on) {
            if (on) GL33C.glEnable(cap); else GL33C.glDisable(cap);
        }
    }
}
