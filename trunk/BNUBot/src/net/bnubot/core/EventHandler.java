/**
 * This file is distributed under the GPL 
 * $Id$
 */

package net.bnubot.core;

import net.bnubot.core.clan.ClanMember;
import net.bnubot.core.friend.FriendEntry;
import net.bnubot.util.BNetUser;

public interface EventHandler {
	//Initialization
	public void initialize(Connection c);
	
	//Connection
	public void bnetConnected();
	public void bnetDisconnected();
	public void titleChanged();
	
	//Workarounds
	public void parseCommand(BNetUser user, String command, String param, boolean whisperBack);

	//Channel events
	public void joinedChannel(String channel);
	public void channelUser(BNetUser user);
	public void channelJoin(BNetUser user);
	public void channelLeave(BNetUser user);
	public void recieveChat(BNetUser user, String text);
	public void recieveEmote(BNetUser user, String text);
	public void recieveInfo(String text);
	public void recieveError(String text);
	public void whisperSent(BNetUser user, String text);
	public void whisperRecieved(BNetUser user, String text);
	
	//Realms
	public void queryRealms2(String[] realms);
	public void logonRealmEx(int[] MCPChunk1, int ip, int port, int[] MCPChunk2, String uniqueName);
	
	//Friends
	public void friendsList(FriendEntry[] entries);
	public void friendsUpdate(FriendEntry friend);
	public void friendsAdd(FriendEntry friend);
	public void friendsRemove(byte entry);
	public void friendsPosition(byte oldPosition, byte newPosition);
	
	//Clan
	public void clanMOTD(Object cookie, String text);
	public void clanMemberList(ClanMember[] members);
	public void clanMemberRemoved(String username);
	public void clanMemberStatusChange(ClanMember member);
	public void clanMemberRankChange(byte oldRank, byte newRank, String user);
	//CLANMEMBERINFORMATION
}
