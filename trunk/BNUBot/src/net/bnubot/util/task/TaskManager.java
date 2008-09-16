/**
 * This file is distributed under the GPL
 * $Id$
 */

package net.bnubot.util.task;

import java.awt.Container;

import net.bnubot.bot.gui.GuiDesktop;
import net.bnubot.settings.GlobalSettings;

/**
 * @author scotta
 */
public class TaskManager {
	private static final long serialVersionUID = 641763656953338296L;

	private static Container box = null;
	static {
		boolean enableGUI;
		try {
			enableGUI = GlobalSettings.enableGUI;
		} catch(Throwable t) {
			enableGUI = true;
		}

		if(enableGUI)
			box = GuiDesktop.getTasksLocation();
	}

	public static Task createTask(String title) {
		return createTask(title, 0, null);
	}

	public static Task createTask(String title, String currentStep) {
		Task t = createTask(title);
		t.updateProgress(currentStep);
		return t;
	}

	public static Task createTask(String title, int max, String units) {
		if(box == null)
			return new Task();

		TaskGui t = new TaskGui(title, max, units);
		box.add(t.getComponent());
		return t;
	}

	protected static void complete(TaskGui t) {
		box.remove(t.getComponent());
		GuiDesktop.getInstance().pack();
	}
}
