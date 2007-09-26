/**
 * This file is distributed under the GPL 
 * $Id$
 */

package net.bnubot.vercheck;

import java.io.File;
import java.net.URL;

import javax.swing.JOptionPane;

import org.jbls.util.Constants;

import net.bnubot.core.ConnectionSettings;
import net.bnubot.util.Out;
import net.bnubot.util.URLDownloader;

public class VersionCheck {
	protected static XMLElementDecorator elem = null;
	protected static VersionNumber vnLatest = null;
	
	public static boolean checkVersion() throws Exception {
		{
			String url = "http://www.clanbnu.ws/bnubot/version.php?";
			if(CurrentVersion.version().revision() != null)
				url += "svn=" + CurrentVersion.version().revision() + "&";
			url += "release=" + ConnectionSettings.releaseType.toString();
			elem = XMLElementDecorator.parse(url);
		}

		XMLElementDecorator error = elem.getChild("error");
		if(error != null) {
			Out.error(VersionCheck.class, error.getString());
			return false;
		}

		XMLElementDecorator motd = elem.getPath("bnubot/motd");
		if((motd != null) && (motd.getString() != null))
			Out.info(VersionCheck.class, motd.getString());

		XMLElementDecorator downloads = elem.getPath("bnubot/downloads");
		if(downloads != null) {
			for(XMLElementDecorator file : downloads.getChildren("file"))
				URLDownloader.downloadURL(
					new URL(file.getChild("from").getString()),
					new File(file.getChild("to").getString()));
		}
		
		XMLElementDecorator gamesElem = elem.getPath("bnubot/games");
		if(gamesElem != null)
			for(int i = 0; i < Constants.prods.length; i++) {
				String game = Constants.prods[i];
				int verByte = Constants.IX86verbytes[i];
				
				XMLElementDecorator gameElem = gamesElem.getPath(game);
				if(gameElem == null)
					continue;
				
				int vb = gameElem.getPath("verbyte").getInt();
				
				if(verByte != vb) {
					Out.error(VersionCheck.class, "Verbyte for game " + game + " is updating from 0x" + Integer.toHexString(verByte) + " to 0x" + Integer.toHexString(vb));
					Constants.IX86verbytes[i] = vb;
				}
			}

		XMLElementDecorator verLatest = elem.getPath("bnubot/latestVersion");
		if(verLatest == null)
			return false;
		
		vnLatest = new VersionNumber(
				Enum.valueOf(ReleaseType.class, verLatest.getChild("type").getString()),
				verLatest.getChild("major").getInt(),
				verLatest.getChild("minor").getInt(),
				verLatest.getChild("revision").getInt(),
				verLatest.getChild("alpha").getInt(),
				verLatest.getChild("beta").getInt(),
				verLatest.getChild("rc").getInt(),
				verLatest.getChild("svn").getInt(),
				verLatest.getChild("built").getString());
		
		VersionNumber vnCurrent = CurrentVersion.version();

		if(!vnLatest.isNewerThan(vnCurrent))
			return false;
		
		Out.error(VersionCheck.class, "Latest version: " + vnLatest.toString());
		
		String url = verLatest.getChild("url").getString();
		if(url != null) {
			try {
				File thisJar = new File("BNUBot.jar");
				if(thisJar.exists()) {
					String msg = "There is an update to BNU-Bot avalable.\nCurrent version: " + vnCurrent.toString();
					if(JOptionPane.showConfirmDialog(null, msg, "Update?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
						Out.error(VersionCheck.class, "Downloading updated jar!");
						File nextJar = new File("BNUBot-r" + vnLatest.revision() + ".jar");
						URLDownloader.downloadURL(new URL(url), nextJar);
						thisJar.renameTo(new File("BNUBot-r" + vnCurrent.revision() + ".jar"));
						nextJar.renameTo(new File("BNUBot.jar"));
						System.exit(0);
					}
					return true;
				}
			} catch(Exception e) {}
			
			Out.error(VersionCheck.class, "Update: " + url);
		}
		return true;
	}
}