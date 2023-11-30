package com.anthonyhilyard.wikilookup.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import com.anthonyhilyard.iceberg.config.IcebergConfig;
import com.anthonyhilyard.iceberg.config.IcebergConfigSpec;
import com.anthonyhilyard.wikilookup.WikiLookup;
import com.electronwill.nightconfig.core.UnmodifiableConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.registries.ForgeRegistries;

public class WikiLookupConfig extends IcebergConfig<WikiLookupConfig>
{
	public static final String MINECRAFT_WIKI_URL = "https://minecraft.wiki/w/Special:Search?search={}";
	public static final String FANDOM_WIKI_URL = "https://minecraft.fandom.com/wiki/Special:Search?go=Search&search={}";
	private static WikiLookupConfig INSTANCE;
	public static WikiLookupConfig getInstance() { return INSTANCE; }

	private final ConfigValue<UnmodifiableConfig> wikiMap;
	public final BooleanValue openInNewTab;
	public final BooleanValue attemptResolution;

	private static final UnmodifiableConfig defaultWikis;

	static
	{
		ForgeConfigSpec.Builder defaultWikisBuilder = new ForgeConfigSpec.Builder();
		defaultWikisBuilder.define(WikiLookup.DEFAULT_WIKI, MINECRAFT_WIKI_URL);
		defaultWikis = defaultWikisBuilder.build();
	}

	public WikiLookupConfig(IcebergConfigSpec.Builder build)
	{
		build.push("client").push("options");

		wikiMap = build.comment(" Set wiki definitions here, in the format <mod id> = \"<URL>\".  Each URL MUST contain one and only one set of curly braces {} to indicate where the query parameter goes.\n" +
								" For mod id, use _ for the default wiki (will be used for any queries not tied to other configured mod id).").defineSubconfig("wiki_definitions", defaultWikis, k -> true, v -> v != null);
		openInNewTab = build.comment(" If enabled, all wiki lookups will be opened in new browser tabs.\n" +
									 " This is the recommended method, since opening in the same tab is surprisingly complicated.  In order to do so, WikiLookup has to open a local file that then redirects to the proper wiki page meanwhile managing tabs in your browser itself.\n" +
									 " Therefore, when opening pages in the same tab, you will not see the proper URL in your browser, and other issues could occur.").define("open_in_new_tabs", true);
		attemptResolution = build.comment(" If enabled, the mod will try to determine which of the currently-configured wikis to use based on the typed query.  No effect if only one wiki is configured.\n" +
										  " The mod will look for matching items/entities/biomes, and so on that match closely to the query, determine the source mod, and check for specific wikis configured for that mod ID.\n" +
										  " Depending on the number of mods loaded, this process could be slow.)").define("attempt_wiki_resolution", true);

		build.pop().pop();
	}

	public String getQueryURL(String modid, String query)
	{
		// Lookup the appropriate URL...
		String url = "";
		if (wikiMap.get().contains(modid))
		{
			url = wikiMap.get().get(modid);
		}
		else if (wikiMap.get().contains(WikiLookup.DEFAULT_WIKI))
		{
			url = wikiMap.get().get(WikiLookup.DEFAULT_WIKI);
		}
		else
		{
			url = MINECRAFT_WIKI_URL;
		}

		// Encode the query ("Iron Sword" -> "Iron+Sword", etc.)
		query = URLEncoder.encode(query, StandardCharsets.UTF_8);

		// And replace the placeholder with the encoded string.
		return url.replace("{}", query);
	}

	private String formatAsID(String query)
	{
		return query.toLowerCase(Locale.ROOT).replace(" ", "_");
	}

	public String resolveQueryURL(String query)
	{
		// Check if the query matches any of the following categories, in order:
		// Mod IDs
		// Items
		// Mobs
		// Biomes
		// Other

		// Check for configured mod wikis first.
		if (wikiMap.get().contains(formatAsID(query)))
		{
			// We found a configured wiki for the queried mod ID.
			// Return the url, but replace the query with Main_Page
			return wikiMap.get().<String>get(formatAsID(query)).replace("{}", "Main_Page");
		}

		final Minecraft minecraft = Minecraft.getInstance();

		// Now do a search for items, sorted by relevance.
		SearchTree<ItemStack> searchTree = minecraft.getSearchTree(SearchRegistry.CREATIVE_NAMES);
		List<ItemStack> results = searchTree.search(query.toLowerCase(Locale.ROOT));

		// TODO: Sort by relevance?

		// We found an item, so return the configured wiki url.
		if (!results.isEmpty())
		{
			return getQueryURL(ForgeRegistries.ITEMS.getKey(results.get(0).getItem()).getNamespace(), query);
		}

		// Now check for entities that match the query.
		// TODO: Use levenshtein distance instead of exact match?
		// TODO: Prioritize mobs over other types of entities?
		for (var entry : ForgeRegistries.ENTITY_TYPES.getValues())
		{
			if (ForgeRegistries.ENTITY_TYPES.getKey(entry).getPath().contentEquals(formatAsID(query)))
			{
				return getQueryURL(ForgeRegistries.ENTITY_TYPES.getKey(entry).getNamespace(), query);
			}
		}

		// Check for biomes in the same way as above.
		for (var entry : ForgeRegistries.BIOMES.getValues())
		{
			if (ForgeRegistries.BIOMES.getKey(entry).getPath().contentEquals(formatAsID(query)))
			{
				return getQueryURL(ForgeRegistries.BIOMES.getKey(entry).getNamespace(), query);
			}
		}

		// TODO: What else could be searched for that hasn't been covered by items? 
		// Fluids, potions, and enchantments should be covered. Villager Professions maybe?
		return getQueryURL(WikiLookup.DEFAULT_WIKI, query);
	}

	@Override
	protected <I extends IcebergConfig<?>> void setInstance(I instance)
	{
		INSTANCE = (WikiLookupConfig) instance;
	}
}
