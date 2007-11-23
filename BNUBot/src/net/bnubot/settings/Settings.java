/**
 * This file is distributed under the GPL
 * $Id$
 */

package net.bnubot.settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import net.bnubot.util.Out;
import net.bnubot.util.SortedProperties;
import net.bnubot.vercheck.CurrentVersion;

public class Settings {
	private static final File propsFile = new File("settings.ini");
	private static final Properties props = new SortedProperties();
	private static Boolean anythingChanged = false;
	
	static {
		if(propsFile.exists()) try {
			props.load(new FileInputStream(propsFile));
		} catch(Exception e) {
			Out.exception(e);
		}
	}
	
	private static String getKey(String Header, String Setting) {
		if(Header == null)
			return "general_" + Setting;
		return Header + "_" + Setting;
	}
	
	public static String read(String Header, String Setting, String Default) {
		String s = props.getProperty(getKey(Header, Setting));
		if(s != null)
			return s;
		return Default;
	}
	
	public static void write(String Header, String Setting, String Value) {
		String key = getKey(Header, Setting);
		if(Value == null)
			Value = new String();
		
		// Don't allow modification of keys unless they haven't changed
		if(props.containsKey(key) && props.getProperty(key).equals(Value))
			return;
		
		anythingChanged = true;
		Out.debug(Settings.class, "Setting " + key + "=" + Value);
		props.setProperty(key, Value);
	}

	public static void store() {
		if(!anythingChanged)
			return;
		
		Out.debug(Settings.class, "Writing settings.ini");
		
		try {
			// Generate the comment first, because the settings.ini file could be lost if CurrentVersion.version() fails
			String comment = CurrentVersion.version().toString();
			props.store(new FileOutputStream(propsFile), comment);
			anythingChanged = false;
		} catch (Exception e) {
			Out.fatalException(e);
		}
	}

}