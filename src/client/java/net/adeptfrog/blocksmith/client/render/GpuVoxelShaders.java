package net.adeptfrog.blocksmith.client.render;

/**
 * GLSL sources of the GPU editor renderer: a line-by-line port of {@link VoxelRaycaster}, so the GPU picture matches
 * the software one. The bodies carry no {@code #version} line; {@link #desktop} adds the OpenGL 3.3 header.
 * <p>
 * Inputs shared by both passes:
 * <ul>
 *   <li>{@code uP[8]}: the 32 floats of {@link VoxelRaycaster#exportView}.</li>
 *   <li>{@code uCells}: one texel per cell, {@code gridSize} wide and {@code gridSize * layers} tall (row
 *       {@code zi * gridSize + y}); rgb = colour, alpha = filled octants (bit x + 2y + 4z, 0 = empty).</li>
 *   <li>{@code uHl[2]}: hovered cell (x, y, zi, kind: 0 none, 1 voxel face, 2 floor cell) and its face normal.</li>
 * </ul>
 * Pixel rows count from the top, like the software image: render target row {@code y} is image row {@code y}.
 */
public final class GpuVoxelShaders {
    private GpuVoxelShaders() {}

    public static String desktop(String body) {
        return "#version 330 core\n" + body;
    }

    /** Full-screen triangle from gl_VertexID; needs no vertex data. */
    public static final String VERTEX = """
            void main() {
                vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    /** Pass 1: ray-casts every pixel (model, floor grid, supersampling) into the raw image. */
    public static final String TRACE_FRAGMENT = """
            uniform vec4 uP[8];
            uniform ivec4 uHl[2];
            uniform sampler2D uCells;
            out vec4 fragColor;

            const float BG_DISTANCE = 400.0;
            const float BIG = 3.0e38;
            const int MAJOR_LINE_EVERY = 8;

            float W, H, G, L, LT, ACTIVE, SCALE, PAN_R, PAN_U, SS, PIXEL_SCALE, LINE_HALF, FOOT_X, FOOT_Y;
            vec3 R, U, F, LIGHT_DIR, LIGHT_OFF;
            bool CAMERA_LIGHT, DECORATIONS;
            int GI, LI;

            // current ray (cell space: z in layer cells)
            vec3 O, D;
            // last hit
            ivec3 hCell, hN;
            float hitT;

            void loadParams() {
                W = uP[0].x; H = uP[0].y; G = uP[0].z; L = uP[0].w;
                LT = uP[1].x; ACTIVE = uP[1].y; SCALE = uP[1].z; PAN_R = uP[1].w;
                PAN_U = uP[2].x; R = uP[2].yzw;
                U = uP[3].xyz; F = vec3(uP[3].w, uP[4].x, uP[4].y);
                LIGHT_DIR = vec3(uP[4].z, uP[4].w, uP[5].x); LIGHT_OFF = uP[5].yzw;
                CAMERA_LIGHT = uP[6].x > 0.5; DECORATIONS = uP[6].y > 0.5; SS = uP[6].z;
                FOOT_X = uP[7].x; FOOT_Y = uP[7].y; PIXEL_SCALE = uP[7].z; LINE_HALF = uP[7].w;
                GI = int(G + 0.5); LI = int(L + 0.5);
            }

            float clamp01(float v) { return v < 0.0 ? 0.0 : min(1.0, v); }
            float smoothK(float x) { x = clamp01(x); return x * x * (3.0 - 2.0 * x); }
            float clamp255(float v) { return v < 0.0 ? 0.0 : min(255.0, floor(v)); }
            float frac(float v) { return v - floor(v); }
            int jround(float v) { return int(floor(v + 0.5)); } // Java Math.round

            int cellMask(int cx, int cy, int cz) {
                return int(texelFetch(uCells, ivec2(cx, cz * GI + cy), 0).a * 255.0 + 0.5);
            }

            /** Whether the half-size block at (sx, sy, sz) (cell coordinates doubled) is solid. */
            bool occupiedHalf(ivec3 s) {
                ivec3 c = s >> 1;
                if (s.x < 0 || s.y < 0 || s.z < 0 || c.x >= GI || c.y >= GI || c.z >= LI) return false;
                int m = cellMask(c.x, c.y, c.z);
                int bit = (s.x & 1) + 2 * (s.y & 1) + 4 * (s.z & 1);
                return ((m >> bit) & 1) != 0;
            }

            void setRay(float pixelX, float pixelY) {
                float sx = (pixelX - W / 2.0) / SCALE - PAN_R;
                float sy = (H / 2.0 - pixelY) / SCALE - PAN_U;
                vec3 centre = vec3(G / 2.0, G / 2.0, L / 2.0 * LT);
                vec3 w = centre + R * sx + U * sy - F * BG_DISTANCE;
                O = vec3(w.x, w.y, w.z / LT);
                D = vec3(F.x, F.y, F.z / LT);
            }

            /**
             * Walks the ray through a grid of half-size blocks. Every voxel shape is a union of octants, so the first
             * solid half-block crossed is exactly the surface the software tracer finds (cube or shaped voxel).
             */
            bool trace() {
                vec3 o = O * 2.0, d = D * 2.0;
                vec3 size = vec3(2.0 * G, 2.0 * G, 2.0 * L);
                float tMin = -BIG, tMaxBox = BIG;
                int entryAxis = -1;
                for (int a = 0; a < 3; a++) {
                    if (abs(d[a]) < 1e-12) {
                        if (o[a] < 0.0 || o[a] > size[a]) return false;
                        continue;
                    }
                    float t1 = (0.0 - o[a]) / d[a];
                    float t2 = (size[a] - o[a]) / d[a];
                    float tNear = min(t1, t2), tFar = max(t1, t2);
                    if (tNear > tMin) { tMin = tNear; entryAxis = a; }
                    tMaxBox = min(tMaxBox, tFar);
                }
                if (tMin > tMaxBox || tMaxBox < 0.0) return false;
                if (tMin < 0.0) { tMin = 0.0; entryAxis = -1; }

                ivec3 isize = ivec3(2 * GI, 2 * GI, 2 * LI);
                float tIn = tMin + 1e-4;
                ivec3 c = clamp(ivec3(floor(o + d * tIn)), ivec3(0), isize - 1);
                ivec3 n = ivec3(0);
                if (entryAxis >= 0) n[entryAxis] = d[entryAxis] > 0.0 ? -1 : 1;

                ivec3 stp = ivec3(d.x > 0.0 ? 1 : -1, d.y > 0.0 ? 1 : -1, d.z > 0.0 ? 1 : -1);
                vec3 tDelta, tMax;
                for (int a = 0; a < 3; a++) {
                    if (abs(d[a]) < 1e-12) { tDelta[a] = BIG; tMax[a] = BIG; }
                    else {
                        tDelta[a] = abs(1.0 / d[a]);
                        tMax[a] = (float(stp[a] > 0 ? c[a] + 1 : c[a]) - o[a]) / d[a];
                    }
                }
                float tCur = tMin;
                int limit = isize.x + isize.y + isize.z + 4;
                for (int i = 0; i < limit; i++) {
                    if (occupiedHalf(c)) {
                        hCell = c >> 1; hN = n; hitT = tCur;
                        return true;
                    }
                    if (tMax.x <= tMax.y && tMax.x <= tMax.z) {
                        c.x += stp.x; tCur = tMax.x; tMax.x += tDelta.x; n = ivec3(-stp.x, 0, 0);
                        if (c.x < 0 || c.x >= isize.x) return false;
                    } else if (tMax.y <= tMax.z) {
                        c.y += stp.y; tCur = tMax.y; tMax.y += tDelta.y; n = ivec3(0, -stp.y, 0);
                        if (c.y < 0 || c.y >= isize.y) return false;
                    } else {
                        c.z += stp.z; tCur = tMax.z; tMax.z += tDelta.z; n = ivec3(0, 0, -stp.z);
                        if (c.z < 0 || c.z >= isize.z) return false;
                    }
                }
                return false;
            }

            /** Distance (cells) from the hit point to the nearest real edge of its surface, or BIG beyond limit. */
            float rimDistance(vec3 pt, ivec3 n, float limit) {
                int a = n.x != 0 ? 0 : (n.y != 0 ? 1 : 2);
                int s = n[a];
                ivec3 sub = ivec3(floor(pt * 2.0));
                sub[a] = s > 0 ? jround(pt[a] * 2.0) - 1 : jround(pt[a] * 2.0);
                if (!occupiedHalf(sub)) return BIG;
                float best = BIG;
                for (int i = 1; i <= 2; i++) {
                    int b = (a + i) % 3;
                    float t = pt[b] * 2.0 - float(sub[b]);
                    float dist = min(t, 1.0 - t) / 2.0;
                    if (dist >= limit || dist >= best) continue;
                    int side = t < 0.5 ? -1 : 1;
                    ivec3 nb = sub;
                    nb[b] += side;
                    bool real;
                    if (!occupiedHalf(nb)) real = true;
                    else {
                        nb[a] += s;
                        real = occupiedHalf(nb);
                    }
                    if (real) best = dist;
                }
                return best;
            }

            vec4 shadeHit() {
                vec3 base = floor(texelFetch(uCells, ivec2(hCell.x, hCell.z * GI + hCell.y), 0).rgb * 255.0 + 0.5);
                vec3 hf = vec3(hN);
                float light;
                if (CAMERA_LIGHT) {
                    float facing = max(0.0, dot(hf, LIGHT_DIR));
                    light = 0.55 + 0.45 * sqrt(facing);
                } else if (hN.y > 0) light = 1.0;
                else if (hN.y < 0) light = 0.5;
                else if (hN.z > 0) light = 0.85;
                else if (hN.z < 0) light = 0.6;
                else light = hN.x > 0 ? 0.75 : 0.65;

                vec3 p = O + D * hitT;
                float edge = max(0.08, 0.6 * PIXEL_SCALE / SCALE);
                float rim = rimDistance(p, hN, edge);
                if (rim < edge) light *= 1.0 - 0.5 * smoothK(1.0 - rim / edge);

                vec3 w = vec3(p.x, p.y, p.z * LT);
                vec3 l;
                if (CAMERA_LIGHT) l = vec3(G * 0.5 + LIGHT_OFF.x * G - w.x, G * 0.5 + LIGHT_OFF.y * G - w.y, L / 2.0 * LT + LIGHT_OFF.z * G - w.z);
                else l = vec3(G * 0.5 - 0.7 * G - w.x, G * 0.5 + 0.9 * G - w.y, L / 2.0 * LT + 1.4 * G - w.z);
                float ld = length(l);
                l /= ld;
                float ndl = max(0.0, dot(hf, l));
                float k = ld / (G * 2.2);
                float atten = 1.0 / (1.0 + k * k);
                float shine = 0.80 + 0.75 * ndl * (0.55 + 0.45 * atten) - 0.05;
                light *= shine;
                float dt = dot(hf, l);
                vec3 rv = 2.0 * dt * hf - l;
                float sp = max(0.0, -dot(rv, F));
                float spec = (sp > 0.0 ? pow(sp, 24.0) : 0.0) * 0.35 * atten * (ndl > 0.0 ? 1.0 : 0.0);

                vec3 c = base * light;
                if (light > 1.0) {
                    float wash = min(0.5, (light - 1.0) * 0.8);
                    c += (255.0 - c) * wash;
                }
                c += 255.0 * spec;

                if (uHl[0].w == 1 && hCell == uHl[0].xyz && (uHl[1].xyz == ivec3(0) || uHl[1].xyz == hN)) {
                    c = c * 0.55 + 255.0 * 0.45;
                }
                return vec4(c, 1.0);
            }

            float lineCoverage(float coord, float footprint, float spacing) {
                float c = coord / spacing;
                float d = min(frac(c), 1.0 - frac(c)) * spacing;
                float dPx = d / max(footprint, 1e-6);
                return clamp01(0.5 + LINE_HALF - dPx);
            }

            vec4 shadeFloor() {
                if (abs(D.z) < 1e-12) return vec4(0.0);
                float planeZ = ACTIVE + 0.5;
                float t = (planeZ - O.z) / D.z;
                if (t < 0.0) return vec4(0.0);
                float fxc = O.x + D.x * t, fyc = O.y + D.y * t;
                int cellX = int(floor(fxc)), cellY = int(floor(fyc));
                if (cellX < 0 || cellX >= GI || cellY < 0 || cellY >= GI) return vec4(0.0);

                float rad = sqrt((fxc - G * 0.5) * (fxc - G * 0.5) + (fyc - G * 0.5) * (fyc - G * 0.5)) / G;
                float edgeFade = 1.0 - 0.85 * smoothK(clamp01((rad - 0.28) / 0.44));

                float a = 0.07 * edgeFade;
                float r = 255.0, g = 255.0, b = 255.0;

                float foot = max(FOOT_X, FOOT_Y) * PIXEL_SCALE;
                float fade = clamp01(1.0 - (foot - 0.35) / 0.5);

                float reach = LINE_HALF + 0.5;
                bool nearLine = abs(fxc - roundEven(fxc)) < reach * max(FOOT_X, 1e-6)
                        || abs(fyc - roundEven(fyc)) < reach * max(FOOT_Y, 1e-6);
                float minor = 0.0, major = 0.0, border = 0.0;
                if (nearLine) {
                    minor = max(lineCoverage(fxc, FOOT_X, 1.0), lineCoverage(fyc, FOOT_Y, 1.0)) * fade;
                    major = max(lineCoverage(fxc, FOOT_X, float(MAJOR_LINE_EVERY)), lineCoverage(fyc, FOOT_Y, float(MAJOR_LINE_EVERY)));
                    border = max(lineCoverage(fxc, FOOT_X, G), lineCoverage(fyc, FOOT_Y, G));
                }

                float la = minor * 0.20 * edgeFade;
                if (la > 0.0) { float na = a + la * (1.0 - a); r = (r * a * (1.0 - la) + 159.0 * la) / na; g = (g * a * (1.0 - la) + 177.0 * la) / na; b = (b * a * (1.0 - la) + 210.0 * la) / na; a = na; }
                la = major * 0.32 * edgeFade;
                if (la > 0.0) { float na = a + la * (1.0 - a); r = (r * a * (1.0 - la) + 190.0 * la) / na; g = (g * a * (1.0 - la) + 203.0 * la) / na; b = (b * a * (1.0 - la) + 230.0 * la) / na; a = na; }
                la = border * 0.55 * max(edgeFade, 0.45);
                if (la > 0.0) { float na = a + la * (1.0 - a); r = (r * a * (1.0 - la) + 226.0 * la) / na; g = (g * a * (1.0 - la) + 233.0 * la) / na; b = (b * a * (1.0 - la) + 246.0 * la) / na; a = na; }

                if (uHl[0].w == 2 && uHl[0].x == cellX && uHl[0].y == cellY) {
                    float kk = 0.55;
                    r = r * (1.0 - kk) + 255.0 * kk; g = g * (1.0 - kk) + 255.0 * kk; b = b * (1.0 - kk) + 255.0 * kk;
                    a = max(a, 0.7);
                }
                return vec4(r, g, b, a);
            }

            vec4 sampleAt(float px, float py) {
                setRay(px, py);
                if (trace()) {
                    vec4 v = shadeHit();
                    // The guide grid sits in the middle of the active layer: it shows over voxels behind that plane
                    if (DECORATIONS && abs(D.z) >= 1e-12) {
                        float tf = (ACTIVE + 0.5 - O.z) / D.z;
                        // (a face lying exactly on the plane, like the middle of a slab, covers it)
                        if (tf >= 0.0 && tf < hitT - 1e-3) {
                            vec4 f = shadeFloor();
                            v = vec4(f.rgb * f.a + v.rgb * (1.0 - f.a), 1.0);
                        }
                    }
                    return v;
                }
                if (DECORATIONS) return shadeFloor();
                return vec4(0.0);
            }

            void main() {
                loadParams();
                float px = floor(gl_FragCoord.x), py = floor(gl_FragCoord.y);
                int ss = int(SS + 0.5);
                float st = 1.0 / float(ss);
                float accA = 0.0;
                vec3 acc = vec3(0.0);
                for (int j = 0; j < ss; j++) {
                    for (int i = 0; i < ss; i++) {
                        vec4 s = sampleAt(px + (float(i) + 0.5) * st, py + (float(j) + 0.5) * st);
                        if (s.a > 0.0) {
                            accA += s.a;
                            acc += s.rgb * s.a;
                        }
                    }
                }
                if (accA <= 0.0) { fragColor = vec4(0.0); return; }
                float n = float(ss * ss);
                float a = clamp255(accA / n * 255.0);
                vec3 c = vec3(clamp255(acc.r / accA), clamp255(acc.g / accA), clamp255(acc.b / accA));
                fragColor = vec4(c, a) / 255.0;
            }
            """;

    /** Pass 2: soft dark outline just outside the model's silhouette, read from the raw image. */
    public static final String OUTLINE_FRAGMENT = """
            uniform sampler2D uRaw;
            uniform ivec4 uOutline; // radius, decorations, width, height
            out vec4 fragColor;

            float clamp255(float v) { return v < 0.0 ? 0.0 : min(255.0, floor(v)); }

            void main() {
                ivec2 p = ivec2(gl_FragCoord.xy);
                vec4 src = texelFetch(uRaw, p, 0);
                ivec4 px = ivec4(floor(src * 255.0 + 0.5));
                int radius = uOutline.x, w = uOutline.z, h = uOutline.w;
                if (uOutline.y == 0 || px.a > 230) { fragColor = src; return; }
                int nearest = 0;
                for (int d = 1; d <= radius && nearest == 0; d++) {
                    for (int yy = p.y - d; yy <= p.y + d && nearest == 0; yy++) {
                        if (yy < 0 || yy >= h) continue;
                        bool edgeRow = yy == p.y - d || yy == p.y + d;
                        for (int xx = p.x - d; xx <= p.x + d; xx++) {
                            if (xx < 0 || xx >= w) continue;
                            if (!edgeRow && xx != p.x - d && xx != p.x + d) continue;
                            if (int(floor(texelFetch(uRaw, ivec2(xx, yy), 0).a * 255.0 + 0.5)) > 230) { nearest = d; break; }
                        }
                    }
                }
                if (nearest == 0) { fragColor = src; return; }
                float oa = 0.85 * (1.0 - float(nearest - 1) / (float(radius) + 0.5));
                float da = float(px.a) / 255.0;
                float na = oa + da * (1.0 - oa);
                float r = (22.0 * oa + float(px.r) * da * (1.0 - oa)) / na;
                float g = (31.0 * oa + float(px.g) * da * (1.0 - oa)) / na;
                float b = (51.0 * oa + float(px.b) * da * (1.0 - oa)) / na;
                fragColor = vec4(clamp255(r), clamp255(g), clamp255(b), clamp255(na * 255.0)) / 255.0;
            }
            """;
}
