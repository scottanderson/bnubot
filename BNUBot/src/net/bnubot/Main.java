/**
 * This file is distributed under the GPL
 * $Id$
 */

package net.bnubot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;

import net.bnubot.bot.gui.settings.GlobalConfigurationFrame;
import net.bnubot.core.PluginManager;
import net.bnubot.core.Profile;
import net.bnubot.settings.GlobalSettings;
import net.bnubot.settings.Settings;
import net.bnubot.util.Out;
import net.bnubot.vercheck.CurrentVersion;
import net.bnubot.vercheck.ExceptionReporter;
import net.bnubot.vercheck.ReleaseType;

import org.apache.commons.logging.impl.NoOpLog;
import org.eclipse.swt.widgets.Display;

/**
 * @author scotta
 */
public class Main {
	static {
		// Set default exception handler
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			public void uncaughtException(Thread thread, Throwable t) {
				Out.fatalException(t);
			}});

		// Delete the bnubot.pid file on application exit
		File f = new File("bnubot.pid");
		if(f.exists())
			f.deleteOnExit();

		// Set the default log file
		if(CurrentVersion.fromJar())
			try {
				File logFile = new File("log.txt");

				if(CurrentVersion.fromJar()
				&& (CurrentVersion.version().getReleaseType() == ReleaseType.Nightly)
				&& logFile.exists()
				&& (logFile.length() > 0))
					ExceptionReporter.reportErrors(logFile);
				Out.setOutputStream(new PrintStream(logFile));
			} catch(Exception e) {
				Out.popupException(e);
			}

		// Disable Cayenne logging!
		System.setProperty("org.apache.commons.logging.Log", NoOpLog.class.getName());
	}

	public static void main(String[] args) {
		JARLoader.getClassLoader();
		GlobalSettings.load();

		boolean forceConfig = !Settings.propsFile.exists();
		for(int i = 0; i < args.length; i++) {
			if(args[i].charAt(0) == '-') {
				switch(args[i].charAt(1)) {
				case 'c':
					if(args[i].equals("-cli")) {
						PluginManager.setEnableCli(true);
						continue;
					}
					if(args[i].equals("-cfg")) {
						forceConfig = true;
						continue;
					}
					break;
				case 'd':
					if(args[i].equals("-debug")) {
						Out.setDebug(true);
						continue;
					}
					break;
				case 'g':
					if(args[i].equals("-gui")) {
						PluginManager.setEnableGui(true);
						continue;
					}
					break;
				case 'l':
					if(args[i].equals("-logfile")) {
						try {
							Out.setOutputStream(new PrintStream(new File(args[++i])));
						} catch (FileNotFoundException e) {
							Out.exception(e);
						}
						continue;
					}
					break;
				case 'n':
					if(args[i].equals("-nocli")) {
						PluginManager.setEnableCli(false);
						continue;
					}
					if(args[i].equals("-nogui")) {
						PluginManager.setEnableGui(false);
						continue;
					}
					break;
				case 'v':
					if(args[i].equals("-v") || args[i].equals("-version")) {
						System.out.println(CurrentVersion.version().toString());
						System.exit(0);
					}
					break;
				}
			}

			Out.error(Main.class, "Invalid argument: " + args[i]);
			System.exit(1);
		}

		if(forceConfig) {
			try {
				new GlobalConfigurationFrame();
			} catch(Exception e) {
				Out.exception(e);
				String error = "There was an error initializing the configuraiton window";
				Out.error(Main.class, error);
				System.exit(1);
			}
		}

		if(PluginManager.getEnableSwt()) {
			Display display = Display.getCurrent();
			while(display.readAndDispatch());
		}

		for(int i = 1; i <= GlobalSettings.numBots; i++)
			Profile.newConnection(i);

		if(CurrentVersion.fromJar() && CurrentVersion.version().getReleaseType().isDevelopment())
			Out.error(CurrentVersion.class, "WARNING: This is a development build, not for distribution!");

		// Write out any modified settings
		Settings.store();

		// SWT requires the main thread to be the event thread
		if(PluginManager.getEnableSwt()) {
			Display display = Display.getCurrent();
			while(!display.isDisposed()) {
				if(!display.readAndDispatch())
					display.sleep();
			}
			System.exit(0);
		}
	}
}
