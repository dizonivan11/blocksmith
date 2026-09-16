package net.adeptfrog.blocksmith.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record WeaponOffset(int x, int y) {
    public static final WeaponOffset ZERO = new WeaponOffset(0, 0);

    public static final Codec<WeaponOffset> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("x").forGetter(WeaponOffset::x),
                    Codec.INT.fieldOf("y").forGetter(WeaponOffset::y)
            ).apply(instance, WeaponOffset::new)
    );

    public static final StreamCodec<ByteBuf, WeaponOffset> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeaponOffset::x,
            ByteBufCodecs.VAR_INT, WeaponOffset::y,
            WeaponOffset::new
    );
}