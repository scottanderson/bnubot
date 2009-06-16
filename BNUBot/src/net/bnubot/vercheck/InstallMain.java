/**
 * This file is distributed under the GPL
 * $Id$
 */

package net.bnubot.vercheck;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import net.bnubot.util.OperatingSystem;
import net.bnubot.util.Out;
import net.bnubot.util.task.TaskManager;

/**
 * @author scotta
 */
public class InstallMain {
	public static void main(String[] args) throws Exception {
		boolean gui = true;
		for(String arg : args) {
			if(arg.equals("-cli")) {
				gui = false;
			}
		}

		String downloadFolder = ".";
		if(gui) try {
			JFileChooser jfc = new JFileChooser();
			jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if(jfc.showDialog(null, "Install Here") != JFileChooser.APPROVE_OPTION)
				System.exit(0);
			downloadFolder = jfc.getSelectedFile().getAbsolutePath();
		} catch(Exception e) {
			gui = false;
		}

		Out.setDebug(true);

		String command = "java -jar \"" + downloadFolder + "/BNUBot.jar\"";
		String jarFileName = "BNUBot.jar";

		switch(OperatingSystem.userOS) {
		case OSX:
			// We're on OSX, so let's try to create an application
			String appdir = "BNUBot.app";

			jarFileName = "Contents/Resources/Java/" + jarFileName;
			if(downloadFolder == null)
				downloadFolder = appdir;
			else
				downloadFolder += "/" + appdir;
			command = downloadFolder + "/Contents/MacOS/JavaApplicationStub";
			break;

		case WINDOWS:
			command = downloadFolder + "/BNUBot.exe";
			break;
		}

		// Set up a task location...
		JFrame jf = null;
		if(gui) {
			jf = new JFrame("Installing BNU-Bot");
			Box box = new Box(BoxLayout.Y_AXIS);
			jf.add(box);
			TaskManager.setTaskLocation(box);
			TaskManager.setWindow(jf);
		}

		if(!VersionCheck.checkVersion(true, ReleaseType.Stable, jarFileName, downloadFolder)) {
			try {
				JOptionPane.showMessageDialog(null, "Install failed!", "Error", JOptionPane.ERROR_MESSAGE);
			} catch(Exception e) {
				Out.error(InstallMain.class, "Install failed");
			}

			System.exit(1);
		}

		// Tear down the task location
		if(jf != null)
			jf.dispose();

		Runtime rt = Runtime.getRuntime();

		// If launching in OSX, chmod the stub
		if(command.endsWith("/JavaApplicationStub")) {
			final String cmd_chmod = "chmod 755 " + command;
			Out.info(InstallMain.class, "Fixing JavaApplicationStub: " + cmd_chmod);
			int ret = rt.exec(cmd_chmod).waitFor();
			if(ret != 0)
				throw new IllegalStateException("Failed to execute command [ " + cmd_chmod + " ] error code: " + ret);
		}

		// Ask if we should launch the bot
		if(gui) try {
			int opt = JOptionPane.showConfirmDialog(null, "Install complete. Launch BNU-Bot?", "Installer", JOptionPane.YES_NO_OPTION);
			if(opt == JOptionPane.NO_OPTION)
				System.exit(0);
		} catch(Exception e) {}

		// Launch the program
		Out.info(InstallMain.class, "Launching: " + command);
		rt.exec(command);
		System.exit(0);
	}
}
