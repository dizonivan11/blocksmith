package net.adeptfrog.blocksmith.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ClientVortexHandler {
    private static final List<ClientVortex> VORTICES = new ArrayList<>();

    public static void addVortex(Vec3 center, int duration) {
        VORTICES.add(new ClientVortex(center, duration));
    }

    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || VORTICES.isEmpty()) return;

        Iterator<ClientVortex> it = VORTICES.iterator();
        while (it.hasNext()) {
            ClientVortex vortex = it.next();
            vortex.duration--;
            vortex.renderParticles(level);
            if (vortex.duration <= 0) {
                it.remove();
            }
        }
    }

    private static class ClientVortex {
        final Vec3 center;
        final int totalDuration;
        int duration;

        ClientVortex(Vec3 center, int duration) {
            this.center = center;
            this.totalDuration = duration;
            this.duration = duration;
        }

        void renderParticles(ClientLevel level) {
            int age = totalDuration - duration;
            double coreRadius = 0.45;

            // Event Horizon
            for (int i = 0; i < 8; i++) {
                double angle = (i / 8.0) * (Math.PI * 2) + (age * 0.1);
                double kx = center.x + Math.cos(angle) * (coreRadius * 0.7);
                double kz = center.z + Math.sin(angle) * (coreRadius * 0.7);
                level.addParticle(ParticleTypes.SQUID_INK, kx, center.y + 0.5, kz, 0, 0, 0);
            }
            level.addParticle(ParticleTypes.SQUID_INK, center.x, center.y + 0.5, center.z, 0, 0, 0);

            // Tilted down near the player, highest point furthest away
            Minecraft client = Minecraft.getInstance();
            Vec3 playerPos = (client.player != null) ? client.player.getEyePosition() : center.add(0, 0, 5);

            // Unit vector from black hole center towards the player (depth axis)
            double toPlayerX = playerPos.x - center.x;
            double toPlayerZ = playerPos.z - center.z;
            double horizDist = Math.sqrt(toPlayerX * toPlayerX + toPlayerZ * toPlayerZ);

            double uX = (horizDist > 0.001) ? (toPlayerX / horizDist) : 0.0;
            double uZ = (horizDist > 0.001) ? (toPlayerZ / horizDist) : 1.0;

            // Perpendicular lateral vector (side-to-side axis relative to player)
            double wX = -uZ;

            // 12-degree tilt angle
            double tiltAngle = Math.toRadians(12.0);
            double cosTilt = Math.cos(tiltAngle);
            double sinTilt = Math.sin(tiltAngle);

            int streamArms = 4;
            for (int s = 0; s < streamArms; s++) {
                double streamOffset = (s * (Math.PI * 2.0 / streamArms));

                for (double r = coreRadius + 0.2; r <= 3.6; r += 0.45) {
                    double angularVelocity = 1.4 / Math.pow(r, 1.1);
                    double theta = streamOffset + (age * angularVelocity) + (r * 0.75);

                    // Disk local planar coordinates
                    double xOrb = Math.cos(theta) * r; // Lateral offset
                    double zOrb = Math.sin(theta) * r; // Depth offset relative to player (positive = near, negative = far)

                    // Position transformed to face player: lower near (zOrb > 0), higher far (zOrb < 0)
                    double worldX = center.x + (xOrb * wX) + (zOrb * cosTilt * uX);
                    double worldZ = center.z + (xOrb * uX) + (zOrb * cosTilt * uZ);
                    double worldY = center.y + 0.5 - (zOrb * sinTilt); // Negative Y towards player, positive Y away

                    // Swirl & suction velocity transformed to the tilted player-facing plane
                    double speed = 0.24;
                    double tx = -Math.sin(theta);
                    double tz = Math.cos(theta);
                    double inX = -Math.cos(theta);
                    double inZ = -Math.sin(theta);

                    double vLat = (tx * 0.65 + inX * 0.35) * speed;
                    double vDep = (tz * 0.65 + inZ * 0.35) * speed;

                    double vx = (vLat * wX) + (vDep * cosTilt * uX);
                    double vz = (vLat * uX) + (vDep * cosTilt * uZ);
                    double vy = -(vDep * sinTilt);

                    level.addParticle(ParticleTypes.END_ROD, worldX, worldY, worldZ, vx, vy, vz);

                    if (r < 2.0) {
                        level.addParticle(ParticleTypes.ENCHANT, worldX, worldY, worldZ, vx * 0.5, vy * 0.5, vz * 0.5);
                    }
                }
            }
        }
    }
}