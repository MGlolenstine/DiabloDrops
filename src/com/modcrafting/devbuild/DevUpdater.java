/*
 * Updater for Bukkit.
 *
 * This class provides the means to safetly and easily update a plugin, or check to see if it is updated using dev.bukkit.org
 */

package com.modcrafting.devbuild;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.lang.StringUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 
 * @author H31IX
 * @author Modified by Deathmarine
 */

public class DevUpdater
{
	public Integer build;
	private Plugin plugin;
	private String versionTitle;
	private String versionLink;
	private long totalSize;
	private URL url; // Connecting to RSS
	private static final String DBOUrl = "https://diabloplugins.ci.cloudbees.com/rssLatest";
	private static final int BYTE_SIZE = 1024; // Used for downloading files
	private String updateFolder = YamlConfiguration.loadConfiguration(
			new File("bukkit.yml")).getString("settings.update-folder");
	private DevUpdater.UpdateResult result = DevUpdater.UpdateResult.SUCCESS; 

	/**
	 * Gives the dev the result of the update process. Can be obtained by called
	 * getResult().
	 */
	public enum UpdateResult
	{
		/**
		 * The updater found an update, and has readied it to be loaded the next
		 * time the server restarts/reloads.
		 */
		SUCCESS(1),
		/**
		 * The updater did not find an update, and nothing was downloaded.
		 */
		NO_UPDATE(2);

		private static final Map<Integer, DevUpdater.UpdateResult> valueList = new HashMap<Integer, DevUpdater.UpdateResult>();
		private final int value;

		private UpdateResult(int value)
		{
			this.value = value;
		}

		public int getValue()
		{
			return this.value;
		}

		public static DevUpdater.UpdateResult getResult(int value)
		{
			return valueList.get(value);
		}

		static
		{
			for (DevUpdater.UpdateResult result : DevUpdater.UpdateResult.values())
			{
				valueList.put(result.value, result);
			}
		}
	}

	/**
	 * Initialize the updater
	 * 
	 * @param plugin
	 *            The plugin that is checking for an update.
	 * @param slug
	 *            The dev.bukkit.org slug of the project
	 *            (http://dev.bukkit.org/server-mods/SLUG_IS_HERE)
	 * @param file
	 *            The file that the plugin is running from, get this by doing
	 *            this.getFile() from within your main class.
	 * @param type
	 *            Specify the type of update this will be. See
	 *            {@link UpdateType}
	 * @param announce
	 *            True if the program should announce the progress of new
	 *            updates in console
	 */
	public DevUpdater(Plugin plugin, File file,Integer build)
	{
		if(build!=null){
			this.build=build;
		}
		this.plugin = plugin;
		try
		{
			// Obtain the results of the project's file feed
			url = new URL(DBOUrl);
		}
		catch (MalformedURLException ex)
		{
		}
		if (url != null)
		{
			// Obtain the results of the project's file feed
			readFeed();
			if (versionCheck(versionTitle))
			{
				String fileLink = versionLink+"artifact/dist/DiabloDrops.jar";
				String name = file.getName();
				saveFile(new File("plugins/" + updateFolder), name, fileLink);
			}
		}
	}

	/**
	 * Get the result of the update process.
	 */
	public DevUpdater.UpdateResult getResult()
	{
		return result;
	}

	/**
	 * Get the total bytes of the file (can only be used after running a version
	 * check or a normal run).
	 */
	public long getFileSize()
	{
		return totalSize;
	}
	public int getBuild(){
		return build;
	}
	/**
	 * Get the version string latest file avaliable online.
	 */
	public String getLatestVersionString()
	{
		return versionTitle;
	}

	/**
	 * Save an update from dev.bukkit.org into the server's update folder.
	 */
	private void saveFile(File folder, String file, String u)
	{
		if (!folder.exists())
		{
			folder.mkdir();
		}
		BufferedInputStream in = null;
		FileOutputStream fout = null;
		try
		{
			// Download the file
			URL url = new URL(u);
			int fileLength = url.openConnection().getContentLength();
			in = new BufferedInputStream(url.openStream());
			fout = new FileOutputStream(folder.getAbsolutePath() + "/" + file);

			byte[] data = new byte[BYTE_SIZE];
			int count;
			long downloaded = 0;
			while ((count = in.read(data, 0, BYTE_SIZE)) != -1)
			{
				downloaded += count;
				fout.write(data, 0, count);
				int percent = (int) (downloaded * 100 / fileLength);
				if ((percent % 10 == 0))
				{
					plugin.getLogger().info(
							"Downloading update: " + percent + "% of "
									+ fileLength + " bytes.");
				}
			}
			plugin.getLogger().info("Finished updating.");
			result = UpdateResult.SUCCESS;
		}
		catch (Exception ex)
		{
		}
		finally
		{
			try
			{
				if (in != null)
				{
					in.close();
				}
				if (fout != null)
				{
					fout.close();
				}
			}
			catch (Exception ex)
			{
			}
		}
	}

	/**
	 * Check if the name of a jar is one of the plugins currently installed,
	 * used for extracting the correct files out of a zip.
	 */
	public boolean pluginFile(String name)
	{
		for (File file : new File("plugins").listFiles())
		{
			if (file.getName().equals(name))
			{
				return true;
			}
		}
		return false;
	}


	/**
	 * Check to see if the program should continue by evaluation whether the
	 * plugin is already updated, or shouldn't be updated
	 */
	private boolean versionCheck(String title)
	{
		StringBuilder sb = new StringBuilder();
		for(char c:title.toCharArray()){
			if(Character.isDigit(c)) sb.append(c);
		}
		if(sb.length()<1) return false;
		String bString = sb.toString();
		int buildNum = Integer.parseInt(bString);
		if(build==null){
			build=buildNum;
		}else{
			if(buildNum>build) return true;
		}
		
		return false;
	}
	private void readFeed()
	{
		try
		{
			InputStream in = read();
			if (in == null)
				return;
			SAXParserFactory factory = SAXParserFactory.newInstance();
		    SAXParser saxParser = factory.newSAXParser();
		    saxParser.parse(in, new Handler());
			
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	/**
	 * Open the RSS feed
	 */
	private InputStream read()
	{
		try
		{
			return url.openStream();
		}
		catch (IOException e)
		{
			return null;
		}
	}
	
	class Handler extends DefaultHandler {
		String currentElement;
		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			currentElement = qName;
			if (currentElement.equals("link")) {
				String link = attributes.getValue("href");
				if(StringUtils.containsIgnoreCase(link, "DiabloDrops")){
	 				versionLink=link;
				}
			}
		}
		@Override
	 	public void endElement(String uri, String localName, String qName) throws SAXException {
		 	currentElement = "";
	 	}
	 	@Override
	 	public void characters(char[] chars, int start, int length) throws SAXException {
	 		if (currentElement.equals("title")) {
	 			String s = new String(chars, start, length);
	 			if(StringUtils.containsIgnoreCase(s, "DiabloDrops")){
	 				versionTitle=s;
	 			}
	 		}
	 	}
	}
}
