/**
 * This file is distributed under the GPL
 * $Id$
 */
package net.bnubot.bot.commands;

import net.bnubot.bot.CommandEventHandler;
import net.bnubot.core.Connection;
import net.bnubot.core.commands.CommandRunnable;
import net.bnubot.db.Account;
import net.bnubot.util.BNetUser;

/**
 * @author scotta
 */
public final class CommandVoteKick extends CommandRunnable {
	@Override
	public void run(Connection source, BNetUser user, String param, String[] params, boolean whisperBack, Account commanderAccount, boolean superUser)
	throws Exception {
		if((param == null) || (params.length != 1)) {
			user.sendChat("Use: %trigger%votekick <user>[@<realm>]", whisperBack);
			return;
		}

		CommandEventHandler.startVote(source, user, param, whisperBack, Boolean.FALSE);
	}
}