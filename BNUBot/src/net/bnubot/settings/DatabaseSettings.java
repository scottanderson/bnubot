/**
 * This file is distributed under the GPL
 * $Id$
 */

package net.bnubot.settings;

/**
 * @author scotta
 */
public class DatabaseSettings {
	private static final String header = "database";
	public String driver;
	public String url;
	public String username;
	public String password;

	public void load() {
		SettingsSection ss = Settings.getSection(header);
		driver = ss.read("driver", "org.apache.derby.jdbc.EmbeddedDriver");
		url = ss.read("url", "jdbc:derby:database;create=true");
		username = ss.read("username", (String)null);
		password = ss.read("password", (String)null);
	}

	public void save() {
		SettingsSection ss = Settings.getSection(header);
		ss.write("driver", driver);
		ss.write("url", url);
		ss.write("username", username);
		ss.write("password", password);
	}
}