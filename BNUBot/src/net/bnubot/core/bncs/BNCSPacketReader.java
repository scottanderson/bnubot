/**
 * This file is distributed under the GPL
 * $Id$
 */

package net.bnubot.core.bncs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import net.bnubot.settings.GlobalSettings;
import net.bnubot.util.BNetInputStream;
import net.bnubot.util.BNetOutputStream;
import net.bnubot.util.HexDump;
import net.bnubot.util.Out;

public class BNCSPacketReader {
	BNCSPacketId packetId;
	int packetLength;
	byte data[];
	
	public BNCSPacketReader(InputStream rawis) throws IOException {
		BNetInputStream is = new BNetInputStream(rawis);
		
		byte magic;
		do {
			magic = is.readByte();
		} while(magic != (byte)0xFF);
		
		packetId = BNCSPacketId.values()[is.readByte() & 0x000000FF];
		packetLength = is.readWord() & 0x0000FFFF;
		assert(packetLength >= 4);
		
		data = new byte[packetLength-4];
		for(int i = 0; i < packetLength-4; i++) {
			data[i] = is.readByte();
		}
		
		if(GlobalSettings.packetLog) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			BNetOutputStream os = new BNetOutputStream(baos);
			os.writeByte(0xFF);
			os.writeByte(packetId.ordinal());
			os.writeWord(packetLength);
			os.write(data);
			
			if(Out.isDebug())
				Out.debugAlways(getClass(), "RECV " + packetId.name() + "\n" + HexDump.hexDump(baos.toByteArray()));
			else
				Out.debugAlways(getClass(), "RECV " + packetId.name());
		}
	}
	
	public BNetInputStream getData() {
		return new BNetInputStream(new ByteArrayInputStream(data));
	}
}
