package com.anthonyhilyard.wikilookup;

import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.commands.Commands;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent.KeyInputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.lwjgl.glfw.GLFW;


@Mod.EventBusSubscriber(modid = Loader.MODID, bus = Bus.FORGE, value = Dist.CLIENT)
public class WikiLookup
{
	private static final Set<String> ALLOWED_PROTOCOLS = Sets.newHashSet("http", "https");
	public static final String DEFAULT_WIKI = "_";

	private static final KeyMapping lookupItem = new KeyMapping("Open wiki page for item under mouse", KeyConflictContext.GUI, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_L), "ket.categories.inventory");

	@SubscribeEvent
	public static void onRegisterCommandsEvent(RegisterCommandsEvent event)
	{
		event.getDispatcher().register(
			Commands.literal("wiki")
			.then(Commands.argument("query", StringArgumentType.greedyString())
			.executes(context -> {
				if (openWiki(StringArgumentType.getString(context, "query"), DEFAULT_WIKI))
				{
					return 0;
				}
				else
				{
					return -1;
				}
			})
		));
	}

	@SubscribeEvent
	public static void onKeyInput(KeyInputEvent event)
	{
		// If the wiki lookup key was pressed, check for an item under the mouse and then look up the wiki page for that item.
		if (lookupItem.consumeClick())
		{
			ItemStack hoveredItem = getHoveredItem();
			if (hoveredItem != ItemStack.EMPTY)
			{
				openWiki(hoveredItem.getHoverName().getString(), ForgeRegistries.ITEMS.getKey(hoveredItem.getItem()).getNamespace());
			}
		}
	}

	private static ItemStack getHoveredItem()
	{
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != null && minecraft.screen instanceof AbstractContainerScreen<?> containerScreen)
		{
			Slot slotUnderMouse = containerScreen.getSlotUnderMouse();
			if (slotUnderMouse != null && slotUnderMouse.hasItem())
			{
				// Okay, we are hovering over an item in an inventory.
				return slotUnderMouse.getItem();
			}
		}

		// Check for REI items or whatever else.
		return ItemStack.EMPTY;
	}

	public static boolean openWiki(String query, String modid)
	{
		String fullURL = "";
		if (WikiLookupConfig.getInstance().attemptResolution.get() && modid.contentEquals(DEFAULT_WIKI))
		{
			fullURL = WikiLookupConfig.getInstance().resolveQueryURL(query);
		}
		else
		{
			fullURL = WikiLookupConfig.getInstance().getQueryURL(modid, query);
		}
		WikiLookupConfig.getInstance().getQueryURL(modid, query);
		try
		{
			URI uri = new URI(fullURL);
			String s = uri.getScheme();
			if (s == null)
			{
				// If we didn't detect a protocol, we will just assume https.
				uri = new URI("https://" + fullURL);
				s = uri.getScheme();
			}

			// If the provided protocol is not http or https, don't allow it.
			if (!ALLOWED_PROTOCOLS.contains(s.toLowerCase(Locale.ROOT)))
			{
				throw new URISyntaxException(fullURL, "Unsupported protocol: " + s.toLowerCase(Locale.ROOT));
			}

			boolean openInNewTab = WikiLookupConfig.getInstance().openInNewTab.get();

			// Not opening in a new tab is actually super complicated, so let's try that.
			if (!openInNewTab)
			{
				// Create a temporary javascript file for the given URL.
				try
				{
					File tempFile = File.createTempFile("wikilookup-", ".html");
					tempFile.deleteOnExit();

					// Copy the contents of the pagebrowser.html file, insert the proper URL, and then write that to a temp file.
					String fileContents = IOUtils.toString(WikiLookup.class.getResourceAsStream("/pagebrowser.html"), StandardCharsets.UTF_8).replace("%url", uri.toString()).replace("%query", query);
					FileUtils.writeStringToFile(tempFile, fileContents, StandardCharsets.UTF_8);

					Util.getPlatform().openFile(tempFile);
				}
				catch (Exception e)
				{
					// Unable to create the temp file, so do it normally I guess.
					Loader.LOGGER.info("Unable to create URL loader temp file, links will open in new tabs. {}", e);

					// It didn't work this way, do it the easy way instead.
					openInNewTab = true;
				}
			}

			if (openInNewTab)
			{
				// Open the page in the normal way.
				Util.getPlatform().openUri(uri);
			}
		}
		catch (URISyntaxException urisyntaxexception)
		{
			Loader.LOGGER.error("Can't open configured wiki at {}", fullURL, urisyntaxexception);
			return false;
		}
		return true;
	}
}
