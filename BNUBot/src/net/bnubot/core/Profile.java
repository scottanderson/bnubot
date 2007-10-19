/**
 * This file is distributed under the GPL 
 * $Id$
 */

package net.bnubot.core;

import java.util.ArrayList;
import java.util.LinkedList;

import net.bnubot.Main;
import net.bnubot.bot.CommandEventHandler;
import net.bnubot.bot.console.ConsoleEventHandler;
import net.bnubot.bot.database.Database;
import net.bnubot.bot.gui.GuiEventHandler;
import net.bnubot.bot.trivia.TriviaEventHandler;
import net.bnubot.core.bncs.BNCSConnection;
import net.bnubot.settings.ConnectionSettings;
import net.bnubot.settings.DatabaseSettings;
import net.bnubot.util.Out;

public class Profile {
	private static LinkedList<Profile> profiles = new LinkedList<Profile>();

	private static Profile findCreateProfile(String name) {
		synchronized(profiles) {
			for(Profile p : profiles)
				if(p.getName().equals(name))
					return p;
		}
		return new Profile(name);
	}
	
	public static boolean add(ConnectionSettings cs, String[] plugins) throws Exception {
		return findCreateProfile(cs.bncsServer).insertConnection(cs, plugins);
	}

	private LinkedList<Connection> cons = new LinkedList<Connection>();
	private ChatQueue chatQueue;
	private String name;

	public Profile(String name) {
		this.name = name;

		synchronized(profiles) {
			profiles.add(this);
		}
		
		chatQueue = new ChatQueue();
		chatQueue.start();
	}

	private boolean insertConnection(ConnectionSettings cs, String[] plugins) throws Exception {
		BNCSConnection con = new BNCSConnection(cs, chatQueue, this);
		synchronized(cons) {
			if(cons.size() > 0) {
				Connection primary = cons.get(0);

				// Add secondary EHs
				for(EventHandler eh : primary.getEventHandlers())
					con.addSecondaryEventHandler(eh);

				primary.addSlave(con);
			} else {
				// Plugins
				ArrayList<EventHandler> pluginEHs = new ArrayList<EventHandler>();
				if(plugins != null) {
					for(String element : plugins) {
						EventHandler eh = (EventHandler)Class.forName(element).newInstance();
						pluginEHs.add(eh);
						con.addEventHandler(eh);
					}
				}

				// CLI
				if(ConnectionSettings.enableCLI)
					con.addEventHandler(new ConsoleEventHandler());

				// GUI
				GuiEventHandler gui = null;
				if(ConnectionSettings.enableGUI) {
					gui = new GuiEventHandler();
					con.addEventHandler(gui);
				}

				// Commands
				EventHandler cmd = null;
				if(ConnectionSettings.enableCommands) {
					DatabaseSettings ds = new DatabaseSettings();
					ds.load();

					if((ds.driver == null)
							|| (ds.url == null)) {
						String msg = "Database is not configured; disabling commands.";
						if(gui != null)
							con.recieveInfo(msg);
						else
							Out.info(Main.class, msg);
					} else {
						try {
							new Database(ds);
							cmd = new CommandEventHandler();
							con.addEventHandler(cmd);

							ds.save();
						} catch(Exception e) {
							Out.exception(e);
							String msg = "Failed to initialize the database; commands disabled.\n" + e.getMessage();
							if(gui != null)
								con.recieveError(msg);
							else
								Out.error(Main.class, msg);
						}
					}
				}

				// Trivia
				if(ConnectionSettings.enableTrivia)
					con.addEventHandler(new TriviaEventHandler());
			}
			
			// Start the Connection thread
			con.start();
			
			// Wait for the bot to connect
			while(!con.canSendChat())
				Thread.sleep(20);
			
			// Wait an additional 500ms
			Thread.sleep(500);
			
			return cons.add(con);
		}
	}

	public String getName() {
		return name;
	}
}
