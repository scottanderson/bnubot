/**
 * This file is distributed under the GPL
 * $Id$
 */

package net.bnubot.core.chat;

import java.net.Socket;

import net.bnubot.core.Connection;
import net.bnubot.core.Profile;
import net.bnubot.core.UnsupportedFeatureException;
import net.bnubot.core.bncs.ProductIDs;
import net.bnubot.settings.ConnectionSettings;
import net.bnubot.util.BNetInputStream;
import net.bnubot.util.BNetOutputStream;
import net.bnubot.util.Out;
import net.bnubot.util.UserProfile;
import net.bnubot.util.task.Task;

public class ChatConnection extends Connection {
	private Socket s;
	private BNetInputStream is;
	private BNetOutputStream os;
	
	public ChatConnection(ConnectionSettings cs, Profile p) {
		super(cs, p);
	}
	
	/*public void run() {
		try {
			s = new Socket(cs.server, cs.port);
			is = new BNetInputStream(s.getInputStream());
			os = new BNetOutputStream(s.getOutputStream());
			
			//Chat
			//os.writeByte(0x03);
			//os.writeByte(0x04);
			os.writeBytes("c" + cs.username + "\n" + cs.password + "\n");

			os.writeBytes("/join open tech support\n");
			
			Out.info(getClass(), "Connected to " + cs.server + ":" + cs.port);
			
			
			os.writeNTString(cs.username);
			
			while(s.isConnected()) {
				if(is.available() > 0) {
					byte b = is.readByte();
					Out.info(getClass(), Character.toString((char)b));
				} else {
					yield();
					sleep(200);
				}
			}
			
			Out.info(getClass(), "Disconnected");
			
			s.close();
		} catch (Exception e) {
			Out.fatalException(e);
		}
	}*/

	protected void initializeConnection(Task connect) throws Exception {
		s = new Socket(getServer(), getPort());
		is = new BNetInputStream(s.getInputStream());
		os = new BNetOutputStream(s.getOutputStream());
		//Chat
		//os.writeByte(0x03);
		//os.writeByte(0x04);
	}

	protected boolean sendLoginPackets(Task connect) throws Exception {
		os.writeBytes("c" + cs.username + "\n" + cs.password + "\n");
		return false;
	}

	protected void connectedLoop() throws Exception {
		while(s.isConnected() && !disposed) {
			if(is.available() > 0) {
				byte b = is.readByte();
				Out.info(getClass(), Character.toString((char)b));
			} else {
				yield();
				sleep(200);
			}
		}
	}

	@Override
	public boolean isOp() {
		return false;
	}

	@Override
	public void sendLeaveChat() throws Exception {
	}

	@Override
	public void sendJoinChannel(String channel) throws Exception {
	}

	@Override
	public void sendJoinChannel2(String channel) throws Exception {
	}

	@Override
	public void reconnect() {
	}

	@Override
	public void sendClanMOTD(Object cookie) throws Exception {
		throw new UnsupportedFeatureException("Chat clients can not use clans");
	}

	@Override
	public void sendClanInvitation(Object cookie, String user) throws Exception {
		throw new UnsupportedFeatureException("Chat clients can not use clans");
		
	}

	@Override
	public void sendClanRankChange(Object cookie, String user, int newRank) throws Exception {
		throw new UnsupportedFeatureException("Chat clients can not use clans");
		
	}

	@Override
	public void sendClanSetMOTD(String text) throws Exception {
		throw new UnsupportedFeatureException("Chat clients can not use clans");
	}

	@Override
	public ProductIDs getProductID() {
		return ProductIDs.CHAT;
	}

	@Override
	public void sendLogonRealmEx(String realmTitle) throws Exception {
		throw new UnsupportedFeatureException("Chat clients can not use realms");
	}

	@Override
	public void sendQueryRealms2() throws Exception {
		throw new UnsupportedFeatureException("Chat clients can not use realms");
	}

	@Override
	public void sendReadUserData(String user) throws Exception {
		throw new UnsupportedFeatureException("Chat clients can not request profiles");
	}
	
	@Override
	public void sendWriteUserData(UserProfile profile) throws Exception {
		throw new UnsupportedFeatureException("Chat clients can not write profiles");
	}

}
