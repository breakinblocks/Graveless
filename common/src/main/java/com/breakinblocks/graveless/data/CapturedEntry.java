package com.breakinblocks.graveless.data;

import com.breakinblocks.graveless.Graveless;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record CapturedEntry(String handler, String context, int slot, ItemStack stack) {
    public static final String LOOSE_HANDLER = "loose";
    private static final int CODEC_MAX_COUNT = 99;
    private static final int MAX_SPLIT_STACKS = 40;

    public static final Codec<CapturedEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("handler").forGetter(CapturedEntry::handler),
            Codec.STRING.optionalFieldOf("context", "").forGetter(CapturedEntry::context),
            Codec.INT.optionalFieldOf("slot", -1).forGetter(CapturedEntry::slot),
            ItemStack.CODEC.fieldOf("stack").forGetter(CapturedEntry::stack)
    ).apply(instance, CapturedEntry::new));

    public static CapturedEntry loose(ItemStack stack) {
        return new CapturedEntry(LOOSE_HANDLER, "", -1, stack);
    }

    public CapturedEntry withStack(ItemStack newStack) {
        return new CapturedEntry(handler, context, slot, newStack);
    }

    public static List<CapturedEntry> sanitize(List<CapturedEntry> entries) {
        List<CapturedEntry> out = new ArrayList<>(entries.size());
        for (CapturedEntry entry : entries) {
            ItemStack stack = entry.stack();
            if (stack.isEmpty()) {
                continue;
            }
            int max = Math.max(1, Math.min(stack.getMaxStackSize(), CODEC_MAX_COUNT));
            int count = stack.getCount();
            if (count <= max) {
                out.add(entry);
                continue;
            }
            int splits = 0;
            boolean first = true;
            while (count > 0 && splits < MAX_SPLIT_STACKS) {
                int take = Math.min(count, max);
                ItemStack part = stack.copyWithCount(take);
                out.add(first ? entry.withStack(part) : loose(part));
                first = false;
                count -= take;
                splits++;
            }
            if (count > 0) {
                Graveless.LOGGER.warn("Oversized stack of {} exceeded {} split stacks; {} items were discarded",
                        stack.getItem(), MAX_SPLIT_STACKS, count);
            }
        }
        return out;
    }
}
