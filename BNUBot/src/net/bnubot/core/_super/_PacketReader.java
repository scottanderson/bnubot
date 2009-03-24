/**
 * This file is distributed under the GPL
 * $Id$
 */
package net.bnubot.core._super;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.bnubot.core.Connection;
import net.bnubot.core.bncs.BNCSChatEventId;
import net.bnubot.core.bncs.BNCSPacketId;
import net.bnubot.settings.GlobalSettings;
import net.bnubot.util.BNetInputStream;
import net.bnubot.util.Out;
import net.bnubot.util.crypto.HexDump;

/**
 * @param <C> The connection type for this packet
 * @param <P> The packetId type for this packet
 * @author scotta
 */
public abstract class _PacketReader<C extends Connection, P extends _PacketId<C>> {
	public P packetId;
	public int packetLength;
	public byte data[];

	public _PacketReader(InputStream rawis) throws IOException {
		parse(new BNetInputStream(rawis));

		if(GlobalSettings.packetLog) {
			Enum<?> pId = Enum.class.cast(packetId);

			String msg = "RECV " + pId.name();
			if(pId == BNCSPacketId.SID_CHATEVENT)
				msg += " " + BNCSChatEventId.values()[BNetInputStream.readDWord(data, 0)].name();
			msg += "[" + packetLength + "]";
			if(Out.isDebug())
				msg += "\n" + HexDump.hexDump(data);
			Out.debugAlways(getClass(), msg);
		}
	}

	protected abstract void parse(BNetInputStream is) throws IOException;

	public BNetInputStream getData() {
		return new BNetInputStream(new ByteArrayInputStream(data));
	}
}
