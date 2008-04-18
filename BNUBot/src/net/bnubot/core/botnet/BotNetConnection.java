/**
 * This file is distributed under the GPL
 * $Id$
 */

package net.bnubot.core.botnet;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;

import net.bnubot.core.Connection;
import net.bnubot.core.EventHandler;
import net.bnubot.core.Profile;
import net.bnubot.core.UnsupportedFeatureException;
import net.bnubot.core.bncs.BNCSConnection;
import net.bnubot.core.bncs.ProductIDs;
import net.bnubot.settings.ConnectionSettings;
import net.bnubot.settings.GlobalSettings;
import net.bnubot.util.BNetInputStream;
import net.bnubot.util.BNetUser;
import net.bnubot.util.HexDump;
import net.bnubot.util.MirrorSelector;
import net.bnubot.util.Out;
import net.bnubot.util.UserProfile;
import net.bnubot.util.task.Task;

/**
 * @author sanderson
 *
 */
public class BotNetConnection extends Connection {
	public static final String BOTNET_TYPE = "BotNet";

	private BNCSConnection master;

	private HashMap<Integer, BotNetUser> users = new HashMap<Integer, BotNetUser>();
	private boolean userInit = false;

	private InputStream bnInputStream = null;
	private DataOutputStream bnOutputStream = null;

	private int botNetServerRevision = 0;
	private int botNetCommunicationRevision = 0;

	public BotNetConnection(BNCSConnection master, ConnectionSettings cs, Profile p) {
		super(cs, p);
		this.master = master;
	}

	@Override
	protected String getServer() {
		return GlobalSettings.botNetServer;
	}

	@Override
	protected int getPort() {
		return GlobalSettings.botNetPort;
	}

	@Override
	protected void initializeConnection(Task connect) throws Exception {
		botNetServerRevision = 0;
		botNetCommunicationRevision = 0;

		// Set up BotNet
		connect.updateProgress("Connecting to BotNet");
		int port = getPort();
		InetAddress address = MirrorSelector.getClosestMirror(getServer(), port);
		recieveInfo("Connecting to " + address + ":" + port + ".");
		socket = new Socket(address, port);
		socket.setKeepAlive(true);
		bnInputStream = socket.getInputStream();
		bnOutputStream = new DataOutputStream(socket.getOutputStream());

		// Connected
		connectionState = ConnectionState.CONNECTED;
		connect.updateProgress("Connected");
	}

	@Override
	protected boolean sendLoginPackets(Task connect) throws Exception {
		sendLogon("RivalBot", "b8f9b319f223ddcc38");

		while(isConnected() && !socket.isClosed() && !disposed) {
			if(bnInputStream.available() > 0) {
				BotNetPacketReader pr = new BotNetPacketReader(bnInputStream);
				BNetInputStream is = pr.getInputStream();

				switch(pr.packetId) {
				case PACKET_BOTNETVERSION: {
					botNetServerRevision = is.readDWord();
					recieveInfo("BotNet server version is " + botNetServerRevision);
					sendBotNetVersion(1, 1);
					break;
				}
				case PACKET_LOGON: {
					int result = is.readDWord();
					switch(result) {
					case 0:
						recieveError("Logon failed!");
						disconnect(false);
						return false;
					case 1:
						recieveInfo("Logon success!");
						return true;
					default:
						recieveError("Unknown PACKET_LOGON result 0x" + Integer.toHexString(result));
						disconnect(false);
						return false;
					}
				}
				default:
					Out.debugAlways(getClass(), "Unexpected packet " + pr.packetId.name() + "\n" + HexDump.hexDump(pr.data));
					break;
				}
			} else {
				sleep(200);
				yield();
			}
		}

		return false;
	}

	@Override
	protected void connectedLoop() throws Exception {
		sendStatusUpdate();
		sendUserInfo();

		while(isConnected() && !socket.isClosed() && !disposed) {
			if(bnInputStream.available() > 0) {
				BotNetPacketReader pr = new BotNetPacketReader(bnInputStream);
				BNetInputStream is = pr.getInputStream();

				switch(pr.packetId) {
				case PACKET_CHANGEDBPASSWORD: {
					// Server is acknowledging the communication version
					botNetCommunicationRevision = is.readDWord();
					recieveInfo("BotNet communication version is " + botNetCommunicationRevision);
					break;
				}
				case PACKET_IDLE: {
					sendIdle();
					break;
				}
				case PACKET_STATSUPDATE: {
					int result = is.readDWord();
					switch(result) {
					case 0:
						recieveError("Status update failed");
						break;
					case 1:
						// Success
						break;
					default:
						recieveError("Unknown PACKET_LOGON result 0x" + Integer.toHexString(result));
						disconnect(false);
						return;
					}
					break;
				}
				case PACKET_USERINFO: {
					if(pr.data.length == 0) {
						userInit = false;
						break;
					}

					int number = is.readDWord();
					int dbflag = 0, ztff = 0;
					if(botNetServerRevision >= 4) {
						dbflag = is.readDWord();
						ztff = is.readDWord();
					}
					String name = is.readNTString();

					BotNetUser user = new BotNetUser(this, number, name);
					user.dbflag = dbflag;
					user.ztff = ztff;

					user.channel = is.readNTString();
					user.server = is.readDWord();
					if(botNetServerRevision >= 2)
						user.account = is.readNTString();
					if(botNetServerRevision >= 3)
						user.database = is.readNTString();

					if(myUser == null)
						myUser = user;

					if(userInit)
						botnetUserOnline(user);
					else
						botnetUserStatus(user);
					//recieveInfo(user.toStringEx());
					break;
				}
				case PACKET_USERLOGGINGOFF: {
					int number = is.readDWord();
					botnetUserLogoff(number);
					break;
				}
				default:
					Out.debugAlways(getClass(), "Unexpected packet " + pr.packetId.name() + "\n" + HexDump.hexDump(pr.data));
					break;
				}
			} else {
				sleep(200);
				yield();
			}
		}
	}

	@Override
	public ProductIDs getProductID() {
		return ProductIDs.CHAT;
	}

	@Override
	public boolean isOp() {
		return false;
	}

	/**
	 * @param substring
	 */
	public void processCommand(String text) {
		try {
			String[] commands = text.split(" ", 3);
			if(commands[0].equals("whisper")) {
				if(commands.length != 3) {
					recieveError("Invalid use of whisper");
					return;
				}

				BotNetUser target = getUser(commands[1]);
				if(target == null) {
					recieveError("Invalid whisper target");
					return;
				}

				sendWhisper(target, commands[2]);
				return;
			} else if(commands[0].equals("chat")) {
				sendChat(false, text.substring(5));
				return;
			}

			recieveError("Invalid BotNet command: " + text);
		} catch(Exception e) {
			Out.exception(e);
		}
	}

	/**
	 * @param string
	 * @return
	 */
	private BotNetUser getUser(String string) {
		if(string.charAt(0) == '%')
			return users.get(Integer.parseInt(string.substring(1)));
		return null;
	}

	/**
	 * Broadcast text
	 * @param text Text to send
	 */
	public void sendBroadcast(String text) throws Exception {
		sendBotNetChat(0, false, 0, text);
		// TODO: recieveBroadcast()
	}

	/**
	 * Talk on the database
	 * @param emote True if this is an emote
	 * @param text Text to send
	 */
	public void sendChat(boolean emote, String text) throws Exception {
		sendBotNetChat(1, emote, 0, text);
		super.recieveChat(BOTNET_TYPE, myUser, text);
	}

	/**
	 * Send a whisper
	 * @param target User to whisper
	 * @param text Text to send
	 */
	public void sendWhisper(BotNetUser target, String text) throws Exception {
		sendBotNetChat(2, false, target.number, text);
		super.whisperSent(BOTNET_TYPE, target, text);
	}


	/*
	 * Sending packets
	 *
	 */

	/**
	 * Send PACKET_LOGON
	 * @param user
	 * @param pass
	 * @throws Exception
	 */
	private void sendLogon(String user, String pass) throws Exception {
		BotNetPacket p = new BotNetPacket(BotNetPacketId.PACKET_LOGON);
		p.writeNTString(user);
		p.writeNTString(pass);
		p.SendPacket(bnOutputStream);
	}

	/**
	 * Send PACKET_BOTNETVERSION
	 * @param x
	 * @param y
	 * @throws Exception
	 */
	private void sendBotNetVersion(int x, int y) throws Exception {
		BotNetPacket p = new BotNetPacket(BotNetPacketId.PACKET_BOTNETVERSION);
		p.writeDWord(x);
		p.writeDWord(y);
		p.SendPacket(bnOutputStream);
	}

	/**
	 * Send PACKET_IDLE
	 * @throws Exception
	 */
	private void sendIdle() throws Exception {
		BotNetPacket p = new BotNetPacket(BotNetPacketId.PACKET_IDLE);
		p.SendPacket(bnOutputStream);
	}

	/**
	 * Send PACKET_STATUSUPDATE
	 * @throws Exception
	 */
	public void sendStatusUpdate() throws Exception {
		BNetUser user = master.getMyUser();
		String channel = master.getChannel();
		int ip = -1;
		if(channel == null)
			channel = "<Not Logged On>";
		else
			ip = master.getIp();

		sendStatusUpdate(
				(user == null) ? "BNUBot2" : user.getShortLogonName(),
				channel,
				ip,
				"PubEternalChat",
				false);
	}

	/**
	 * Send PACKET_STATUSUPDATE
	 * @param username
	 * @param channel
	 * @param ip
	 * @param database
	 * @param cycling
	 * @throws Exception
	 */
	private void sendStatusUpdate(String username, String channel, int ip, String database, boolean cycling) throws Exception {
		BotNetPacket p = new BotNetPacket(BotNetPacketId.PACKET_STATSUPDATE);
		p.writeNTString(username);
		p.writeNTString(channel);
		p.writeDWord(ip); // bnet ip address
		p.writeNTString(database); // database
		p.writeDWord(cycling ? 1 : 0); // cycling?
		p.SendPacket(bnOutputStream);
	}

	/**
	 * Send PACKET_USERINFO
	 * @throws Exception
	 */
	public void sendUserInfo() throws Exception {
		BotNetPacket p = new BotNetPacket(BotNetPacketId.PACKET_USERINFO);
		p.SendPacket(bnOutputStream);

		userInit = true;
		myUser = null;
	}

	/**
	 * Send PACKET_BOTNETCHAT
	 * @param command 0=broadcast, 1=database chat, 2=whisper
	 * @param emote True if this is an emote
	 * @param target The id of the person to whisper (command 2)
	 * @param text The text to send
	 */
	private void sendBotNetChat(int command, boolean emote, int target, String message) throws Exception {
		if(message.length() > 496)
			throw new IllegalStateException("Chat length too long");

		BotNetPacket p = new BotNetPacket(BotNetPacketId.PACKET_BOTNETCHAT);
		p.writeDWord(command);
		p.writeDWord(emote ? 1 : 0);
		p.writeDWord(target);
		p.writeNTString(message);
		p.SendPacket(bnOutputStream);
	}

	@Override
	public void sendClanInvitation(Object cookie, String user) throws Exception { throw new UnsupportedFeatureException(null); }
	@Override
	public void sendClanMOTD(Object cookie) throws Exception { throw new UnsupportedFeatureException(null); }
	@Override
	public void sendClanRankChange(Object cookie, String user, int newRank)throws Exception { throw new UnsupportedFeatureException(null); }
	@Override
	public void sendClanSetMOTD(String text) throws Exception { throw new UnsupportedFeatureException(null); }
	@Override
	public void sendJoinChannel(String channel) throws Exception { throw new UnsupportedFeatureException(null); }
	@Override
	public void sendJoinChannel2(String channel) throws Exception { throw new UnsupportedFeatureException(null); }
	@Override
	public void sendLeaveChat() throws Exception { throw new UnsupportedFeatureException(null); }
	@Override
	public void sendLogonRealmEx(String realmTitle) throws Exception { throw new UnsupportedFeatureException(null); }
	@Override
	public void sendQueryRealms2() throws Exception { throw new UnsupportedFeatureException(null); }
	@Override
	public void sendReadUserData(String user) throws Exception { throw new UnsupportedFeatureException(null); }
	@Override
	public void sendWriteUserData(UserProfile profile) throws Exception { throw new UnsupportedFeatureException(null); }

	/*
	 * Event dispatch
	 *
	 */

	@Override
	public void bnetConnected() {
		users.clear();

		synchronized(eventHandlers) {
			for(EventHandler eh : eventHandlers)
				eh.botnetConnected(this);
		}
	}

	@Override
	public void bnetDisconnected() {
		users.clear();
		myUser = null;

		synchronized(eventHandlers) {
			for(EventHandler eh : eventHandlers)
				eh.botnetDisconnected(this);
		}
	}

	public void botnetUserOnline(BotNetUser user) {
		users.put(user.number, user);

		synchronized(eventHandlers) {
			for(EventHandler eh : eventHandlers)
				eh.botnetUserOnline(this, user);
		}
	}

	public void botnetUserStatus(BotNetUser user) {
		users.put(user.number, user);

		synchronized(eventHandlers) {
			for(EventHandler eh : eventHandlers)
				eh.botnetUserStatus(this, user);
		}
	}

	private void botnetUserLogoff(int number) {
		BotNetUser user = users.remove(number);

		synchronized(eventHandlers) {
			for(EventHandler eh : eventHandlers)
				eh.botnetUserLogoff(this, user);
		}
	}
}