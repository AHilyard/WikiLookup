package com.anthonyhilyard.wikilookup.config;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;

import net.minecraftforge.fml.loading.FMLPaths;

public class ConfigParser
{
	public static void clean()
	{
		// Check for a configuration file to clean.
		File legacyConfig = FMLPaths.CONFIGDIR.get().resolve("wikilookup-common.toml").toFile();
		if (legacyConfig.exists() && !legacyConfig.isDirectory())
		{
			try
			{
				List<String> lines = Files.readAllLines(legacyConfig.toPath());
				FileWriter writer = new FileWriter(legacyConfig);

				// Replace references to the old Fandom URL to the new URL.
				for (int i = 0; i < lines.size(); i++)
				{
					lines.set(i, lines.get(i).replace(WikiLookupConfig.FANDOM_WIKI_URL, WikiLookupConfig.MINECRAFT_WIKI_URL));
					writer.write(lines.get(i) + "\r\n");
				}
				writer.close();
			}
			catch (Exception e)
			{
				// Crap, something happened.  Oh well.
			}
		}
	}
}
