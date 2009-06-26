/**
 * This file is distributed under the GPL
 * $Id$
 */
package net.bnubot.bot.commands;

import net.bnubot.core.Connection;
import net.bnubot.core.commands.CommandRunnable;
import net.bnubot.db.Account;
import net.bnubot.util.BNetUser;

/**
 * @author scotta
 */
public final class CommandHome implements CommandRunnable {
	public void run(Connection source, BNetUser user, String param, String[] params, boolean whisperBack, Account commanderAccount, boolean superUser)
	throws Exception {
		source.sendJoinChannel(source.getConnectionSettings().channel);
	}
}