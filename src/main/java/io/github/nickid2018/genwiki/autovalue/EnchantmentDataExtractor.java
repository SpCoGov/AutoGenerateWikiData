package io.github.nickid2018.genwiki.autovalue;

import io.github.nickid2018.genwiki.autovalue.wikidata.*;
import io.github.nickid2018.genwiki.inject.InjectedProcess;
import io.github.nickid2018.genwiki.inject.SourceClass;
import lombok.SneakyThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EnchantmentDataExtractor {

    public static final Class<?> ENCHANTMENT_CLASS;
    public static final Class<?> ITEM_STACK_CLASS;
    public static final Class<?> ITEM_CLASS;
    public static final Class<?> TAG_KEY_CLASS;

    public static final MethodHandle ENCHANTMENT_GET_RARITY;
    public static final MethodHandle ENCHANTMENT_GET_MAX_LEVEL;
    public static final MethodHandle ENCHANTMENT_IS_TREASURE_ONLY;
    public static final MethodHandle ENCHANTMENT_IS_TRADEABLE;
    public static final MethodHandle ENCHANTMENT_IS_DISCOVERABLE;
    public static final MethodHandle ENCHANTMENT_IS_COMPATIBLE_WITH;
    public static final MethodHandle ENCHANTMENT_GET_MIN_COST;
    public static final MethodHandle ENCHANTMENT_GET_MAX_COST;
    public static final MethodHandle ENCHANTMENT_GET_MATCH;
    public static final MethodHandle ITEM_GET_DEFAULT_INSTANCE;
    public static final MethodHandle ITEM_STACK_IS;

    static {
        try {
            ENCHANTMENT_CLASS = Class.forName("net.minecraft.world.item.enchantment.Enchantment");
            ITEM_STACK_CLASS = Class.forName("net.minecraft.world.item.ItemStack");
            ITEM_CLASS = Class.forName("net.minecraft.world.item.Item");
            TAG_KEY_CLASS = Class.forName("net.minecraft.tags.TagKey");
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            ENCHANTMENT_GET_RARITY = lookup.unreflect(ENCHANTMENT_CLASS.getMethod("getRarity"));
            ENCHANTMENT_GET_MAX_LEVEL = lookup.unreflect(ENCHANTMENT_CLASS.getMethod("getMaxLevel"));
            ENCHANTMENT_IS_TREASURE_ONLY = lookup.unreflect(ENCHANTMENT_CLASS.getMethod("isTreasureOnly"));
            ENCHANTMENT_IS_TRADEABLE = lookup.unreflect(ENCHANTMENT_CLASS.getMethod("isTradeable"));
            ENCHANTMENT_IS_DISCOVERABLE = lookup.unreflect(ENCHANTMENT_CLASS.getMethod("isDiscoverable"));
            ENCHANTMENT_IS_COMPATIBLE_WITH = lookup.unreflect(ENCHANTMENT_CLASS.getMethod("isCompatibleWith", ENCHANTMENT_CLASS));

            ENCHANTMENT_GET_MIN_COST = lookup.unreflect(ENCHANTMENT_CLASS.getMethod("getMinCost", int.class));
            ENCHANTMENT_GET_MAX_COST = lookup.unreflect(ENCHANTMENT_CLASS.getMethod("getMaxCost", int.class));
            ENCHANTMENT_GET_MATCH = lookup.unreflect(ENCHANTMENT_CLASS.getMethod("getMatch"));

            ITEM_GET_DEFAULT_INSTANCE = lookup.unreflect(ITEM_CLASS.getMethod("getDefaultInstance"));
            ITEM_STACK_IS = lookup.unreflect(ITEM_STACK_CLASS.getMethod("is", TAG_KEY_CLASS));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static final StringWikiData ENCHANTMENT_RARITY = new StringWikiData();
    public static final NumberWikiData ENCHANTMENT_MAX_LEVEL = new NumberWikiData();
    public static final StringWikiData ENCHANTMENT_FLAG = new StringWikiData();
    public static final StringListWikiData ENCHANTMENT_INCOMPATIBLE = new StringListWikiData();
    public static final PairMapWikiData<Integer, Integer> ENCHANTMENT_COST = new PairMapWikiData<>();
    public static final StringListWikiData ENCHANTMENT_MATCH = new StringListWikiData();

    @SneakyThrows
    private static String getRegistryName(Object enchantment) {
        return InjectedProcess.getResourceLocationPath(InjectedProcess.RESOURCE_KEY_LOCATION.invoke(enchantment));
    }

    @SneakyThrows
    private static Object getEnchantmentObject(Object enchantment) {
        return InjectedProcess.REGISTRY_GET.invoke(InjectedProcess.getRegistry("ENCHANTMENT"), enchantment);
    }

    @SneakyThrows
    public static void extractEnchantmentData() {
        @SourceClass("Registry<Enchantment>")
        Object enchantmentRegistry = InjectedProcess.getRegistry("ENCHANTMENT");
        @SourceClass("Set<ResourceKey<Enchantment>>")
        Set<?> enchantmentKeySet = InjectedProcess.getRegistryKeySet(enchantmentRegistry);
        Map<String, ?> enchantmentMap = enchantmentKeySet.stream().collect(Collectors.toMap(
                EnchantmentDataExtractor::getRegistryName,
                EnchantmentDataExtractor::getEnchantmentObject
        ));

        for (Map.Entry<String, ?> entry : enchantmentMap.entrySet()) {
            String name = entry.getKey();
            Object enchantment = entry.getValue();
            Object rarity = ENCHANTMENT_GET_RARITY.invoke(enchantment);
            ENCHANTMENT_RARITY.put(name, InjectedProcess.ENUM_NAME.invoke(rarity).toString());
            int maxLevel = (int) ENCHANTMENT_GET_MAX_LEVEL.invoke(enchantment);
            ENCHANTMENT_MAX_LEVEL.put(name, maxLevel);

            boolean treasureOnly = (boolean) ENCHANTMENT_IS_TREASURE_ONLY.invoke(enchantment);
            boolean tradeable = (boolean) ENCHANTMENT_IS_TRADEABLE.invoke(enchantment);
            boolean discoverable = (boolean) ENCHANTMENT_IS_DISCOVERABLE.invoke(enchantment);
            List<String> flagSet = new ArrayList<>();
            if (treasureOnly)
                flagSet.add("TREASURE");
            if (!tradeable)
                flagSet.add("UNTRADEABLE");
            if (!discoverable)
                flagSet.add("UNDISCOVERABLE");
            String flag = flagSet.isEmpty() ? "ALL_CONDITIONS" : String.join("_", flagSet);
            ENCHANTMENT_FLAG.put(name, flag);

            List<String> incompatibles = enchantmentMap.entrySet().stream()
                    .filter(e -> e.getValue() != enchantment)
                    .filter(e -> {
                        Object other = e.getValue();
                        try {
                            return !(boolean) ENCHANTMENT_IS_COMPATIBLE_WITH.invoke(enchantment, other);
                        } catch (Throwable throwable) {
                            throw new RuntimeException(throwable);
                        }
                    })
                    .map(Map.Entry::getKey).sorted()
                    .collect(Collectors.toList());
            ENCHANTMENT_INCOMPATIBLE.put(name, incompatibles);

            for (int i = 1; i <= maxLevel; i++) {
                int minCost = (int) ENCHANTMENT_GET_MIN_COST.invoke(enchantment, i);
                int maxCost = (int) ENCHANTMENT_GET_MAX_COST.invoke(enchantment, i);
                ENCHANTMENT_COST.putNew(name, minCost, maxCost);
            }
        }

        Map<String, ?> tagKeys = enchantmentMap.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                ((Function<? super Map.Entry<String, ?>, ?>) Map.Entry::getValue).andThen(ENCHANTMENT_GET_MATCH::invoke)
        ));
        Map<String, List<String>> enchantItems = new HashMap<>();

        @SourceClass("DefaultedRegistry<Item>")
        Object itemRegistry = InjectedProcess.getRegistry("ITEM");
        @SourceClass("Set<ResourceKey<Item>>")
        Set<?> itemKeySet = InjectedProcess.getRegistryKeySet(itemRegistry);
        for (Object itemKey : itemKeySet) {
            @SourceClass("ResourceLocation")
            Object itemLocation = InjectedProcess.RESOURCE_KEY_LOCATION.invoke(itemKey);
            String itemID = InjectedProcess.getResourceLocationPath(itemLocation);
            @SourceClass("Item")
            Object item = InjectedProcess.REGISTRY_GET.invoke(itemRegistry, itemKey);
            @SourceClass("ItemStack")
            Object itemStack = ITEM_GET_DEFAULT_INSTANCE.invoke(item);
            for (Map.Entry<String, ?> entry : tagKeys.entrySet()) {
                String enchantmentName = entry.getKey();
                Object tagKey = entry.getValue();
                boolean isEnchanted = (boolean) ITEM_STACK_IS.invoke(itemStack, tagKey);
                if (isEnchanted) {
                    List<String> items = enchantItems.computeIfAbsent(enchantmentName, k -> new ArrayList<>());
                    items.add(itemID);
                }
            }
        }

        for (Map.Entry<String, List<String>> entry : enchantItems.entrySet()) {
            String enchantmentName = entry.getKey();
            List<String> items = entry.getValue();
            items.sort(Comparator.naturalOrder());
            ENCHANTMENT_MATCH.put(enchantmentName, items);
        }

        WikiData.write(ENCHANTMENT_RARITY, "enchantment_rarity.txt");
        WikiData.write(ENCHANTMENT_MAX_LEVEL, "enchantment_max_level.txt");
        WikiData.write(ENCHANTMENT_FLAG, "enchantment_flag.txt");
        WikiData.write(ENCHANTMENT_INCOMPATIBLE, "enchantment_incompatible.txt");
        WikiData.write(ENCHANTMENT_COST, "enchantment_cost.txt");
        WikiData.write(ENCHANTMENT_MATCH, "enchantment_match.txt");
    }
}
