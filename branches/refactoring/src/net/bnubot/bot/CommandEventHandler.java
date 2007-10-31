/**
 * This file is distributed under the GPL 
 * $Id$
 */

package net.bnubot.bot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

import net.bnubot.bot.database.*;
import net.bnubot.core.*;
import net.bnubot.core.bncs.ProductIDs;
import net.bnubot.core.clan.ClanMember;
import net.bnubot.core.friend.FriendEntry;
import net.bnubot.util.HexDump;
import net.bnubot.util.TimeFormatter;
import net.bnubot.vercheck.CurrentVersion;

public class CommandEventHandler implements EventHandler {
	private Connection c = null;
	private Database d = null;
	private Boolean sweepBanInProgress = false;
	private int sweepBannedUsers;
	
	private long	lastCommandTime = 0;
	private BNetUser lastCommandUser = null;

	private class InvalidUseException extends Exception {
		private static final long serialVersionUID = 3993849990858233332L;
	}
	private class InsufficientAccessException extends Exception {
		private static final long serialVersionUID = -1954683087381833989L;
		public InsufficientAccessException(String string) {
			super(string);
		}
	}
	
	public CommandEventHandler() {
		this.d = Database.getInstance();
		if(this.d == null)
			throw new NullPointerException("There was no Database");
	}
	
	public void initialize(Connection c) {
		this.c = c;
	}
	
	public void touchUser(BNetUser user, String action) {
		try {
			ResultSet rsUser = d.getCreateUser(user);
			if(rsUser.next()) {
				rsUser.updateTimestamp("lastSeen", new Timestamp(new Date().getTime()));
				rsUser.updateString("lastAction", action);
				rsUser.updateRow();
			}
			d.close(rsUser);
		} catch(SQLException e) {
			c.recieveError(e.getClass().getName() + ": " + e.getMessage());
		}
	}
	
	public void parseCommand(BNetUser user, String command, String param, boolean wasWhispered) {
		try {
			String[] params = null;
			if(param != null)
				params = param.split(" ");
			
			Long commanderAccess = null; 
			String commanderAccount = null;
			Long commanderAccountID = null; 
	
			//Don't ask questions if they are a super-user
			boolean superUser = user.equals(c.getMyUser());
			try {
				ResultSet rsAccount = d.getAccount(user);
				if((rsAccount == null) || !rsAccount.next()) {
					if(rsAccount != null)
						d.close(rsAccount);
					if(superUser)
						throw new InvalidUseException();
					else
						return;
				}
					
				commanderAccess = rsAccount.getLong("access");
				commanderAccount = rsAccount.getString("name");
				commanderAccountID = rsAccount.getLong("id");
				d.close(rsAccount);
				if(commanderAccess <= 0)
					return;
			} catch(InvalidUseException e) {
				d.close(d.getCreateUser(user));
			}
			

			// Closed-scope for rsCommand
			{
				ResultSet rsCommand = d.getCommand(command);
				if(!rsCommand.next()) {
					if(!wasWhispered)
						c.recieveError("Command " + command + " not found in database");
					d.close(rsCommand);
					return;
				}
				command = rsCommand.getString("name");
				if(!superUser) {
					long requiredAccess = rsCommand.getLong("access");
					if(commanderAccess < requiredAccess) {
						c.recieveError("Insufficient access (" + commanderAccess + "/" + requiredAccess + ")");
						d.close(rsCommand);
						return;
					}
				}
				d.close(rsCommand);
			}
			
			lastCommandUser = user;
			lastCommandTime = new Date().getTime();
		
			COMMAND: switch(command.charAt(0)) {
			case 'a':
				if(command.equals("access")) {
					try {
						if(params == null)
							throw new InvalidUseException();
						if(params.length != 1)
							throw new InvalidUseException();

						ResultSet rsSubjectCategory;
						if(params[0].equals("all"))
							rsSubjectCategory = d.getCommands(commanderAccess);
						else
							rsSubjectCategory = d.getCommandCategory(params[0], commanderAccess);
						
						if((rsSubjectCategory == null) || !rsSubjectCategory.next()) {
							c.sendChat(user, "The category [" + params[0] + "] does not exist!", wasWhispered);
							if(rsSubjectCategory != null)
								d.close(rsSubjectCategory);							
							break;
						}
						
						String result = "Available commands for rank " + commanderAccess + " in cagegory " + params[0] + ": ";
						result += rsSubjectCategory.getString("name") + " (" + rsSubjectCategory.getLong("access") + ")";
						while(rsSubjectCategory.next())
							result += ", " + rsSubjectCategory.getString("name") + " (" + rsSubjectCategory.getLong("access") + ")";
						
						d.close(rsSubjectCategory);
						
						//Available commands for rank 36 in cagegory war3: invite (32), setrank (35)
						c.sendChat(user, result, wasWhispered);
					} catch(InvalidUseException e) {
						String use = "Use: %trigger%access <category> -- Available categories for rank " + commanderAccess + ": all";
						ResultSet rsCategories = d.getCommandCategories(commanderAccess);
						while(rsCategories.next())
							use += ", " + rsCategories.getString("cmdgroup");
						d.close(rsCategories);
						c.sendChat(user, use, wasWhispered);
						break;
					}
					break;
				}
				if(command.equals("add")) {
					try {
						if(params == null)
							throw new InvalidUseException();
						if(params.length != 2)
							throw new InvalidUseException();
						
						ResultSet rsSubjectAccount = d.getAccount(params[0]);
						if(!rsSubjectAccount.next()) {
							d.close(rsSubjectAccount);
							c.sendChat(user, "That user does not have an account. See %trigger%createaccount and %trigger%setaccount.", wasWhispered);
							break;
						}

						long targetAccess = Long.parseLong(params[1]);
						String subjectAccount = rsSubjectAccount.getString("name");
						
						if(!superUser) {
							if(subjectAccount.equals(commanderAccount))
								throw new InsufficientAccessException("to modify your self");
							if(targetAccess >= commanderAccess)
								throw new InsufficientAccessException("to add users beyond " + (commanderAccess - 1));
						}

						rsSubjectAccount.updateLong("access", targetAccess);
						rsSubjectAccount.updateTimestamp("lastRankChange", new Timestamp(new Date().getTime()));
						rsSubjectAccount.updateRow();
						d.close(rsSubjectAccount);
						c.sendChat(user, "Added user [" + subjectAccount + "] successfully with access " + targetAccess, wasWhispered);
					} catch(InvalidUseException e) {
						c.sendChat(user, "Use: %trigger%add <account> <access>", wasWhispered);
						break;
					}
					break;
				}
				if(command.equals("auth")) {
					try {
						if(params == null)
							throw new InvalidUseException();
						if(params.length != 1)
							throw new InvalidUseException();
						
						ResultSet rsSubectCommand = d.getCommand(params[0]);
						if((rsSubectCommand == null) || !rsSubectCommand.next()) {
							c.sendChat(user, "The command [" + params[0] + "] does not exist!", wasWhispered);
							break;
						}
						
						params[0] = rsSubectCommand.getString("name");
						long access = rsSubectCommand.getLong("access");
						
						c.sendChat(user, "Authorization required for " + params[0] + " is " + access, wasWhispered);
					} catch(InvalidUseException e) {
						c.sendChat(user, "Use: %trigger%auth <command>", wasWhispered);
						break;
					}
					break;
				}
				if(command.equals("autopromotion")) {
					try {
						if((params != null) && (params.length != 1))
							throw new InvalidUseException();
						
						Long subjectAccountId = null;
						Long subjectRank = null;
						if(params == null) {
							subjectAccountId = commanderAccountID;
							subjectRank = commanderAccess;
						} else {
							ResultSet rsSubjectAccount = d.getAccount(params[0]);
							if(rsSubjectAccount.next()) {
								subjectAccountId = rsSubjectAccount.getLong("id");
								subjectRank = rsSubjectAccount.getLong("access");
							}
							d.close(rsSubjectAccount);
						}
						
						if((subjectAccountId == null) || (subjectRank == null)) {
							c.sendChat(user, "The account [" + params[0] + "] does not exist!", wasWhispered);
							break;
						}
						
						ResultSet rsSubjectAccount = d.getAccount(subjectAccountId);
						if(!rsSubjectAccount.next()) {
							d.close(rsSubjectAccount);
							//This isn't actually invalid use, but it's a state we should never encounter
							throw new InvalidUseException();
						}
						
						long wins[] = d.getAccountWinsLevels(subjectAccountId, c.getConnectionSettings().recruitTagPrefix, c.getConnectionSettings().recruitTagSuffix);
						Timestamp ts = rsSubjectAccount.getTimestamp("lastRankChange");
						String timeElapsed;
						if(ts != null) {
							double te = (double)(new Date().getTime() - ts.getTime());
							te /= 1000 * 60 * 60 * 24;
							//Round to 2 decimal places
							timeElapsed = ("00" + ((long)Math.floor(te * 100) % 100));
							timeElapsed = timeElapsed.substring(timeElapsed.length()-2);
							timeElapsed = (long)Math.floor(te) + "." + timeElapsed;
						} else {
							timeElapsed = "?";
						}
						
						ResultSet rsRank = d.getRank(subjectRank);
						if(rsRank.next()) {
							long apDays = rsRank.getLong("apDays");
							long apWins = rsRank.getLong("apWins");
							long apD2Level = rsRank.getLong("apD2Level");
							long apW3Level = rsRank.getLong("apW3Level");
							
							if(rsRank.wasNull()) {
								String result = "Autopromotions are not enabled for rank " + subjectRank + ". ";
								result += rsSubjectAccount.getString("name") + "'s current status is: ";
								result += timeElapsed + " days, ";
								result += wins[0] + " wins, ";
								result += wins[1] + " D2 level, ";
								result += wins[2] + " W3 level";
								
								c.sendChat(user, result, wasWhispered);
							} else {
								String result = "AutoPromotion Info for [" + rsSubjectAccount.getString("name") + "]: ";
								result += timeElapsed + "/" + apDays + " days, ";
								result += wins[0] + "/" + apWins + " wins, ";
								result += wins[1] + "/" + apD2Level + " D2 level, ";
								result += wins[2] + "/" + apW3Level + " W3 level";
								
								c.sendChat(user, result, wasWhispered);
							}
						} else {
							String result = "Rank " + subjectRank + " was not found in the database; please contact the bot master and report this error. ";
							result += rsSubjectAccount.getString("name") + "'s current status is: ";
							result += timeElapsed + " days, ";
							result += wins[0] + " wins, ";
							result += wins[1] + " D2 level, ";
							result += wins[2] + " W3 level";
							
							c.sendChat(user, result, wasWhispered);
						}
						d.close(rsRank);
						d.close(rsSubjectAccount);
					} catch(InvalidUseException e) {
						c.sendChat(user, "Use: %trigger%automaticpromotion [account]", wasWhispered);
					}
					break;
				}
				break;
			case 'b':
				if(command.equals("ban")) {
					if((param == null) || (param.length() == 0)) {
						c.sendChat(user, "Use: %trigger%ban <user>[@<realm>] [reason]", wasWhispered);
						break;
					}
					c.sendChat("/ban " + param);
					break;
				}
				break;
			case 'c':
				if(command.equals("createaccount")) {
					if((params == null) || (params.length != 1)) {
						c.sendChat(user, "Use: %trigger%createaccount <account>", wasWhispered);
						break;
					}
					
					ResultSet rsAccount = d.getAccount(params[0]);
					if(rsAccount.next()) {
						d.close(rsAccount);
						c.sendChat(user, "The account [" + params[0] + "] already exists", wasWhispered);
						break;
					}
					d.close(rsAccount);
					
					rsAccount = d.createAccount(params[0], 0L, commanderAccountID);
					if(!rsAccount.next()) {
						d.close(rsAccount);
						c.sendChat(user, "Failed to create account [" + params[0] + "] for an unknown reason", wasWhispered);
						break;
					}
					d.close(rsAccount);
					
					c.sendChat(user, "The account [" + params[0] + "] has been created", wasWhispered);
					break;
				}
				break;
			case 'd':
				if(command.equals("disconnect")) {
					c.setConnected(false);
					break;
				}
				break;
			case 'i':
				if(command.equals("info")) {
					Properties p = System.getProperties();
					c.sendChat(user, "BNU-Bot " + CurrentVersion.version() + " running on " + p.getProperty("os.name") + " (" + p.getProperty("os.arch") + ")", wasWhispered);
					break;
				}
				break;
			case 'k':
				if(command.equals("kick")) {
					if((params == null) || (params.length != 1)) {
						c.sendChat(user, "Use: %trigger%kick <user>[@<realm>]", wasWhispered);
						break;
					}
					c.sendChat("/kick " + params[0]);
					break;
				}
				break;
			case 'm':
				if(command.equals("mail")) {
					if(commanderAccountID == null) {
						c.sendChat(user, "You must have an account to use mail.", wasWhispered);
						break;
					}
					
					try {
						if((params == null) || (params.length < 1))
							throw new InvalidUseException();
						if(params[0].equals("send")) {
							//send <account> <message>
							params = param.split(" ", 3);
							if(params.length < 3)
								throw new InvalidUseException();
							
							ResultSet rsTargetAccount = d.getAccount(params[1]);
							if(!rsTargetAccount.next()) {
								d.close(rsTargetAccount);
								c.sendChat(user, "The account [" + params[1] + "] does not exist!", wasWhispered);
								break;
							}
							params[1] = rsTargetAccount.getString("name");
							Long targetAccountID = rsTargetAccount.getLong("id");
							d.close(rsTargetAccount);
							
							d.sendMail(commanderAccountID, targetAccountID, params[2]);
							c.sendChat(user, "Mail queued for delivery to " +  params[1], wasWhispered);
						} else if(params[0].equals("read")
								||params[0].equals("get")) {
							//read [number]
							if((params.length < 1) || (params.length > 2))
								throw new InvalidUseException();
							
							Long id = null;
							if(params.length == 2) {
								try {
									id = Long.parseLong(params[1]);
								} catch(Exception e) {
									throw new InvalidUseException();
								}
							}
							
							ResultSet rsMail = d.getMail(commanderAccountID);
							if(id == null) {
								while(rsMail.next()) {
									boolean read = rsMail.getBoolean("read");
									if(read)
										continue;

									String message = "#";
									message += rsMail.getRow();
									message += " of ";
									message += d.getMailCount(commanderAccountID);
									message += ": From ";
									message += rsMail.getString("name");
									message += " [";
									message += rsMail.getString("sent");
									message += "]: ";
									message += rsMail.getString("message");
									
									d.setMailRead(rsMail.getLong("id"));
									d.close(rsMail);
									
									c.sendChat(user, message, true);
									break COMMAND;
								}
								
								String message = "You have no unread mail!";
								long mailCount = d.getMailCount(commanderAccountID);
								if(mailCount > 0)
									message += " To read your " + mailCount + " messages, type [ %trigger%mail read <number> ]";
								c.sendChat(user, message, wasWhispered);
							} else {
								long mailNumber = 0;
								while(rsMail.next()) {
									mailNumber++;
									if(mailNumber != id)
										continue;

									String message = "#";
									message += rsMail.getRow();
									message += " of ";
									message += d.getMailCount(commanderAccountID);
									message += ": From ";
									message += rsMail.getString("name");
									message += " [";
									message += rsMail.getString("sent");
									message += "]: ";
									message += rsMail.getString("message");
									
									d.setMailRead(rsMail.getLong("id"));
									d.close(rsMail);
									
									c.sendChat(user, message, true);
									break COMMAND;
								}
								
								c.sendChat(user, "You only have " + mailNumber + " messages!", wasWhispered);
							}
							d.close(rsMail);
							break;
						} else if(params[0].equals("empty")
								||params[0].equals("delete")
								||params[0].equals("clear")) {
							//empty
							if(params.length != 1)
								throw new InvalidUseException();
							
							if(d.getUnreadMailCount(commanderAccountID) > 0) {
								c.sendChat(user, "You have unread mail!", wasWhispered);
								break;
							}
							
							d.clearMail(commanderAccountID);
							c.sendChat(user, "Mailbox cleaned!", wasWhispered);
						} else
							throw new InvalidUseException();
					} catch(InvalidUseException e) {
						c.sendChat(user, "Use: %trigger%mail (read [number] | empty | send <account> <message>)", wasWhispered);
					}
					break;
				}
				if(command.equals("mailall")) {
					try {
						//<rank> <message>
						if(param == null)
							throw new InvalidUseException();
						params = param.split(" ", 2);
						if((params.length < 2) || (params[1].length() == 0))
							throw new InvalidUseException();
						
						long rank = 0;
						try {
							rank = Long.parseLong(params[0]);
						} catch(Exception e) {
							throw new InvalidUseException();
						}
						
						String message = "[Sent to ranks " + rank + "+] " + params[1];
						
						int numAccounts = 0;
						ResultSet rsAccounts = d.getRankedAccounts(rank);
						while(rsAccounts.next()) {
							long targetAccountID = rsAccounts.getLong("id");
							d.sendMail(commanderAccountID, targetAccountID, message);
							numAccounts++;
						}
						c.sendChat(user, "Mail queued for delivery to " + numAccounts + " accounts", wasWhispered);
					} catch(InvalidUseException e) {
						c.sendChat(user, "Use: %trigger%mailall <minimum rank> <message>", wasWhispered);
					}
				}
				break;
			case 'q':
				if(command.equals("quit")) {
					System.exit(0);
				}
				break;
			case 'r':
				if(command.equals("reconnect")) {
					c.reconnect();
					break;
				}
				if(command.equals("recruit")) {
					if((params == null) || (params.length != 2)) {
						c.sendChat(user, "Use: %trigger%recruit <user>[@<realm>] <account>", wasWhispered);
						break;
					}
					
					BNetUser bnSubject = BNetUser.getBNetUser(params[0], user);
					ResultSet rsSubject = d.getUser(bnSubject);
					if((rsSubject == null) || !rsSubject.next()) {
						c.sendChat(user, "That user does not exist!", wasWhispered);
						break;
					}
					
					long subjectAccountId = rsSubject.getLong("account");
					if(!rsSubject.wasNull()) {
						c.sendChat(user, "That user already has an account!", wasWhispered);
						break;
					}

					String requiredTagPrefix =  c.getConnectionSettings().recruitTagPrefix;
					String requiredTagSuffix =  c.getConnectionSettings().recruitTagSuffix;
					
					if(requiredTagPrefix != null) {
						if(bnSubject.getFullAccountName().substring(0, requiredTagPrefix.length()).compareToIgnoreCase(requiredTagPrefix) != 0) {
							c.sendChat(user, "That user must have the " + requiredTagPrefix + " tag!", wasWhispered);
							break;
						}
					}
					
					if(requiredTagSuffix != null) {
						String s = bnSubject.getFullAccountName();
						int i = s.indexOf("@");
						if(i != -1)
							s = s.substring(0, i);
						s = s.substring(s.length() - requiredTagSuffix.length());
						if(s.compareToIgnoreCase(requiredTagSuffix) != 0) {
							c.sendChat(user, "That user must have the " + requiredTagSuffix + " tag!", wasWhispered);
							break;
						}
					}
					
					ResultSet rsSubjectAccount = d.getAccount(params[1]);
					if(rsSubjectAccount.next()) {
						d.close(rsSubjectAccount);
						c.sendChat(user, "That account already exists!", wasWhispered);
						break;
					}
					
					if(commanderAccountID == null) {
						c.sendChat(user, "You must have an account to use recruit.", wasWhispered);
						break;
					}
					
					rsSubjectAccount = d.createAccount(params[1], 0, commanderAccountID);
					if(!rsSubjectAccount.next()) {
						c.sendChat(user, "Failed to create account [" + params[1] + "] for an unknown reason", wasWhispered);
						break;
					}
					
					subjectAccountId = rsSubjectAccount.getLong("id");
					rsSubject.updateLong("account", subjectAccountId);
					rsSubject.updateRow();
					d.close(rsSubject);
					rsSubjectAccount.updateLong("access", c.getConnectionSettings().recruitAccess);
					rsSubjectAccount.updateRow();
					d.close(rsSubjectAccount);

					bnSubject.resetPrettyName();
					c.sendChat("Welcome to the clan, " + bnSubject.toString() + "!");
					break;
				}
				if(command.equals("recruits")) {
					try {
						if((params != null) && (params.length != 1))
							throw new InvalidUseException();
						
						
						Long subjectAccountId = null;
						String output = null;
						if(params == null) {
							subjectAccountId = commanderAccountID;
							output = "You have recruited: ";
						} else {
							ResultSet rsSubject = d.getAccount(params[0]);
							if(!rsSubject.next()) {
								d.close(rsSubject);
								c.sendChat(user, "The account [" + params[0] + "] does not exist!", wasWhispered);
								break;
							}
							subjectAccountId = rsSubject.getLong("id");
							output = rsSubject.getString("name");
							output += " has recruited: ";
							d.close(rsSubject);
						}
												
						ResultSet rsRecruits = d.getAccountRecruits(subjectAccountId, c.getConnectionSettings().recruitAccess);
						if(rsRecruits.next()) {
							do {
								output += rsRecruits.getString("name") + "(" + rsRecruits.getString("access") + ") ";
							} while(rsRecruits.next());
						} else {
							output += "no one";
						}
						d.close(rsRecruits);
						
						output = output.trim();
						c.sendChat(user, output, wasWhispered);
					} catch(InvalidUseException e) {
						c.sendChat(user, "Use: %trigger%recruits [account]", wasWhispered);
					}
					break;
				}
				if(command.equals("renameaccount")) {
					if((params == null) || (params.length != 2)) {
						c.sendChat(user, "Use: %trigger%renameaccount <old account> <new account>", wasWhispered);
						break;
					}
					
					ResultSet rsSubjectAccount = d.getAccount(params[0]);
					if((rsSubjectAccount == null) || !rsSubjectAccount.next()) {
						c.sendChat(user, "The account [" + params[0] + "] does not exist!", wasWhispered);
						break;
					}
					
					try {
						rsSubjectAccount.updateString("name", params[1]);
						rsSubjectAccount.updateRow();
					} catch(SQLException e) {
						//TODO: Verify that the exception was actually caused by the UNIQUE restriction
						c.sendChat(user, "The account [" + params[1] + "] already exists!", wasWhispered);
						break;
					}
					
					c.sendChat(user, "The account [" + params[0] + "] was successfully renamed to [" + params[1] + "]", wasWhispered);
					
					break;
				}
				break;
			case 's':
				if(command.equals("say")) {
					c.sendChat(param);
					break;
				}
				if(command.equals("seen")) {
					if((params == null) || (params.length != 1)) {
						c.sendChat(user, "Use: %trigger%seen <account>", wasWhispered);
						break;
					}
					
					Timestamp mostRecent = null;
					String mostRecentAction = null;
					
					ResultSet rsSubjectAccount = d.getAccount(params[0]);
					if(!rsSubjectAccount.next()) {
						d.close(rsSubjectAccount);
						
						//They don't have an account by that name, check if it's a user
						BNetUser bnSubject = BNetUser.getBNetUser(params[0], user);
						ResultSet rsSubject = d.getUser(bnSubject);
						if(!rsSubject.next()) {
							d.close(rsSubject);
							c.sendChat(user, "I have never seen [" + bnSubject.getFullAccountName() + "]", wasWhispered);
							break;
						} else {
							mostRecent = rsSubject.getTimestamp("lastSeen");
							mostRecentAction = rsSubject.getString("lastAction");
							params[0] = rsSubject.getString("login");
							if(rsSubject.wasNull())
								mostRecentAction = null;
						}
						d.close(rsSubject);
					} else {
						ResultSet rsSubjectUsers = d.getAccountUsers(rsSubjectAccount.getLong("id"));
						params[0] = rsSubjectAccount.getString("name");
						d.close(rsSubjectAccount);
						if(!rsSubjectUsers.next()) {
						} else {
							//Check the user's accounts						
							do {
								Timestamp nt = rsSubjectUsers.getTimestamp("lastSeen");
								if(mostRecent == null) {
									mostRecent = nt;
									mostRecentAction = rsSubjectUsers.getString("lastAction");
									if(rsSubjectUsers.wasNull())
										mostRecentAction = null;
								} else {
									if((nt != null) && (nt.compareTo(mostRecent) > 0)) {
										mostRecent = nt;
										mostRecentAction = rsSubjectUsers.getString("lastAction");
										if(rsSubjectUsers.wasNull())
											mostRecentAction = null;
									}
								}
							} while(rsSubjectUsers.next());
						}
						d.close(rsSubjectUsers);
					}
					
					if(mostRecent == null) {
						c.sendChat(user, "I have never seen [" + params[0] + "]", wasWhispered);
						break;
					}
					
					String diff = TimeFormatter.formatTime(new Date().getTime() - mostRecent.getTime());
					diff = "User [" + params[0] + "] was last seen " + diff + " ago";
					if(mostRecentAction != null)
						diff += " " + mostRecentAction;
					c.sendChat(user, diff, wasWhispered);
					break;
				}
				if(command.equals("setaccount")) {
					if((params == null) || (params.length < 1) || (params.length > 2)) {
						c.sendChat(user, "Use: %trigger%setaccount <user>[@<realm>] [<account>]", wasWhispered);
						break;
					}

					BNetUser bnSubject = BNetUser.getBNetUser(params[0], user.getFullAccountName());
					ResultSet rsSubject = d.getUser(bnSubject);
					if(!rsSubject.next()) {
						d.close(rsSubject);
						c.sendChat(user, "The user [" + bnSubject.getFullAccountName() + "] does not exist!", wasWhispered);
						break;
					}
					String subject = rsSubject.getString("login");
					
					Long newAccount = null;
					if(params.length == 2) {
						ResultSet rsSubjectAccount = d.getAccount(params[1]);
						if(!rsSubjectAccount.next()) {
							d.close(rsSubjectAccount);
							c.sendChat(user, "The account [" + params[1] + "] does not exist!", wasWhispered);
							break;
						}
						newAccount = rsSubjectAccount.getLong("id");
						d.close(rsSubjectAccount);
					}

					if(newAccount == null) {
						rsSubject.updateNull("account");
						
						params[1] = "NULL";
					} else {
						rsSubject.updateLong("account", newAccount);
						
						ResultSet rsSubjectAccount = d.getAccount(newAccount);
						if(rsSubjectAccount.next())
							params[1] = rsSubjectAccount.getString("name");
						d.close(rsSubjectAccount);
					}
					
					rsSubject.updateRow();
					d.close(rsSubject);
					
					bnSubject.resetPrettyName();
					
					c.sendChat(user, "User [" + subject + "] was added to account [" + params[1] + "] successfully.", wasWhispered);
					break;
				}
				if(command.equals("setbirthday")) {
					if(commanderAccountID == null) {
						c.sendChat(user, "You must have an account to use setbirthday.", wasWhispered);
						break;
					}
					
					try {
						if(params == null)
							throw new InvalidUseException();
						
						Date bd = null;
						try {
							SimpleDateFormat sdf = new SimpleDateFormat("M/d/y");
							bd = sdf.parse(param);
						} catch(Exception e) {
							e.printStackTrace();
						}
						if(bd == null)
							throw new InvalidUseException();
						
						ResultSet rsAccount = d.getAccount(commanderAccountID);
						if(!rsAccount.next())
							throw new SQLException();
						rsAccount.updateDate("birthday", new java.sql.Date(bd.getTime()));
						rsAccount.updateRow();
						d.close(rsAccount);
						
						c.sendChat(user, "Your birthday has been set to [ " + new SimpleDateFormat("M/d/y").format(bd) + " ]", wasWhispered);
					} catch(InvalidUseException e) {
						c.sendChat(user, "Use: %trigger%setbirthday <date:MM/DD/YY>", wasWhispered);
					}
					break;
				}
				if(command.equals("setrank")) {
					int newRank;
					try {
						if(params == null)
							throw new InvalidUseException();
						if(params.length != 2)
							throw new InvalidUseException();
						newRank = Integer.valueOf(params[1]);
						if((newRank < 1) || (newRank > 3))
							throw new InvalidUseException();
					} catch(InvalidUseException e) {
						c.sendChat(user, "Use: %trigger%setrank <user> <rank:1-3>", wasWhispered);
						break;
					}
					
					// TODO: validate that params[0] is in the clan
					c.sendClanRankChange(params[0], newRank);
					// TODO: send this after the response is recieved
					c.sendChat(user, "Success", wasWhispered);
					break;
				}
				if(command.equals("setrecruiter")) {
					if((params == null) || (params.length != 2)) {
						c.sendChat(user, "Use: %trigger%setrecruiter <account> <account>", wasWhispered);
						break;
					}
					
					ResultSet rsSubject = d.getAccount(params[0]);
					if(!rsSubject.next()) {
						d.close(rsSubject);
						c.sendChat(user, "The account [" + params[0] + "] does not exist!", wasWhispered);
						break;
					}
					params[0] = rsSubject.getString("name");
					
					ResultSet rsTarget = d.getAccount(params[1]);
					if(!rsTarget.next()) {
						d.close(rsSubject);
						d.close(rsTarget);
						c.sendChat(user, "The account [" + params[1] + "] does not exist!", wasWhispered);
						break;
					}
					params[1] = rsTarget.getString("name");

					
					long subjectID = rsSubject.getLong("id");
					long targetID = rsTarget.getLong("id");
					
					String recursive = params[0];
					
					do {
						long id = rsTarget.getLong("id");
						Long cb = rsTarget.getLong("createdby");
						if(rsTarget.wasNull())
							cb = null;
						
						recursive += " -> " + rsTarget.getString("name");
						if(id == subjectID) {
							c.sendChat(user, "Recursion detected: " + recursive, wasWhispered);
							break;
						}

						if(cb != null) {
							d.close(rsTarget);
							rsTarget = d.getAccount(cb);
							if(!rsTarget.next())
								cb = null;
						}
						
						if(cb == null) {
							rsSubject.updateLong("createdby", targetID);
							rsSubject.updateRow();
							c.sendChat(user, "Successfully updated recruiter for [ " + params[0] + " ] to [ " + params[1] + " ]" , wasWhispered);
							break;
						}
						
					} while(true);
					
					recursive = null;
					d.close(rsTarget);
					d.close(rsSubject);
					
					break;
				}
				if(command.equals("sweepban")) {
					if((params == null) || (params.length < 1)) {
						c.sendChat(user, "Use: %trigger%sweepban <channel>", wasWhispered);
						break;
					}
					sweepBanInProgress = true;
					sweepBannedUsers = 0;
					c.sendChat("/who " + param);
					break;
				}
				break;
			case 't':
				if(command.equals("trigger")) {
					char trigger = c.getConnectionSettings().trigger.charAt(0);
					String output = "0000" + Integer.toString(trigger);
					output = output.substring(output.length() - 4);
					output = "Current trigger: " + trigger + " (alt+" + output + ")";
					c.sendChat(user, output, wasWhispered);
					break;
				}
				break;
			case 'u':
				if(command.equals("unban")) {
					if((params == null) || (params.length != 1)) {
						c.sendChat(user, "Use: %trigger%unban <user>[@<realm>]", wasWhispered);
						break;
					}
					c.sendChat("/unban " + params[0]);
					break;
				}
				break;
			case 'w':
				if(command.equals("whoami")) {
					parseCommand(user, "whois", user.getShortLogonName(), wasWhispered);
					break;
				}
				if(command.equals("whois")) {
					try {
						if((params == null) || (params.length != 1))
							throw new InvalidUseException();
						

						BNetUser bnSubject = null;
						ResultSet rsSubjectAccount = d.getAccount(params[0]);
						ResultSet rsCreatorAccount = null;
						String result = null;
						if(rsSubjectAccount.next()) {
							result = rsSubjectAccount.getString("name");
						} else {
							bnSubject = BNetUser.getBNetUser(params[0], user);
							ResultSet rsSubject = d.getUser(bnSubject);
							
							if((rsSubject == null) || (!rsSubject.next())) {
								c.sendChat(user, "User [" + params[0] + "] is unknown", wasWhispered);
								break;
							}
							
							bnSubject = BNetUser.getBNetUser(rsSubject.getString("login"));
							d.close(rsSubject);
							d.close(rsSubjectAccount);
							rsSubjectAccount = d.getAccount(bnSubject);
							
							if((rsSubjectAccount == null) || (!rsSubjectAccount.next())) {
								c.sendChat(user, "User [" + params[0] + "] has no account", wasWhispered);
								if(rsSubjectAccount != null)
									d.close(rsSubjectAccount);
								break;
							}
							
							result = bnSubject.toString();
						}
						
						rsCreatorAccount = d.getAccount(rsSubjectAccount.getLong("createdby"));

						long subjectAccountID = rsSubjectAccount.getLong("id");
						long subjectAccess = rsSubjectAccount.getLong("access");
						ResultSet rsSubjectRank = d.getRank(subjectAccess);
						
						Date subjectBirthday = rsSubjectAccount.getDate("birthday");
						if(rsSubjectAccount.wasNull())
							subjectBirthday = null;
						
						if(rsSubjectRank.next()) {
							if(bnSubject == null) {
								String prefix = rsSubjectRank.getString("shortPrefix");
								if(prefix == null)
									prefix = rsSubjectRank.getString("prefix");
								
								if(prefix == null)
									prefix = "";
								else
									prefix += " ";
								
								result = prefix + rsSubjectAccount.getString("name");
							}
							
							result += " " + rsSubjectRank.getString("verbstr");
							result += " (" + subjectAccess + ")";
						} else {
							result += " has access " + subjectAccess;
						}
						d.close(rsSubjectRank);
						
						if(subjectBirthday != null) {
							double age = (double)(new Date().getTime() - subjectBirthday.getTime());
							age /= 1000 * 60 * 60 * 24 * 365.24;
							age = Math.floor(age * 100) / 100;
							result += ", who is " + Double.toString(age) + " years old";
						}
						
						// Append aliases
						ArrayList<String> aliases = new ArrayList<String>();
						Timestamp lastSeen = null;
						ResultSet rsSubject = d.getAccountUsers(subjectAccountID);
						while(rsSubject.next()) {
							if(lastSeen == null)
								lastSeen = rsSubject.getTimestamp("lastSeen");
							else {
								Timestamp nt = rsSubject.getTimestamp("lastSeen");
								if((nt != null) && (nt.compareTo(lastSeen) > 0))
									lastSeen = nt;
							}
							aliases.add(rsSubject.getString("login"));
						}
						d.close(rsSubject);

						if(lastSeen != null) {
							result += ", who was last seen [ ";
							result += TimeFormatter.formatTime(new Date().getTime() - lastSeen.getTime());
							result += " ] ago";
						}
						
						if((rsCreatorAccount != null) && rsCreatorAccount.next()) {
							result += ", was recruited by ";
							result += rsCreatorAccount.getString("name");
						}
						
						boolean andHasAliases = false;
						for(int i = 0; i < aliases.size(); i++) {
							String l = aliases.get(i);
							
							if((bnSubject != null) && (bnSubject.equals(l)))
								continue;
							
							if(!andHasAliases) {
								andHasAliases = true;
								result += ", and has aliases ";
							} else {
								result += ", ";
							}
							
							result += l;
						}

						d.close(rsCreatorAccount);
						d.close(rsSubjectAccount);
						c.sendChat(user, result, wasWhispered);
					} catch(InvalidUseException e) {
						c.sendChat(user, "Use: %trigger%whois <user>[@realm]", wasWhispered);
						break;
					}
					break;
				}
				break;
			}
		
		} catch(InsufficientAccessException e) {
			c.sendChat(user, "You have insufficient access " + e.getMessage(), wasWhispered);
		} catch(Exception e) {
			e.printStackTrace();
			c.sendChat(user, e.getClass().getName() + ": " + e.getMessage(), wasWhispered);
		}
	}

	public void channelJoin(BNetUser user, StatString statstr) {
		touchUser(user, "joining the channel");
		
		try {
			ResultSet rsUser = d.getUser(user);
			if(!rsUser.next()) {
				d.close(rsUser);
				return;
			}
			
			switch(statstr.getProduct()) {
			case ProductIDs.PRODUCT_STAR:
			case ProductIDs.PRODUCT_SEXP:
			case ProductIDs.PRODUCT_W2BN: {
				Integer newWins = statstr.getWins();
				if(newWins != null) {
					String col = "wins" + HexDump.DWordToPretty(statstr.getProduct());
					Integer oldWins = rsUser.getInt(col);
					if(newWins > oldWins) {
						rsUser.updateInt(col, newWins);
						rsUser.updateRow();
					}
				}
				break;
			}
				
			case ProductIDs.PRODUCT_D2DV:
			case ProductIDs.PRODUCT_D2XP: {
				Integer newLevel = statstr.getCharLevel();
				if(newLevel != null) {
					Integer oldLevel = rsUser.getInt("levelD2");
					if(newLevel > oldLevel) {
						rsUser.updateInt("levelD2", newLevel);
						rsUser.updateRow();
					}
				}
				break;
			}

			case ProductIDs.PRODUCT_WAR3:
			case ProductIDs.PRODUCT_W3XP: {
				Integer newLevel = statstr.getLevel();
				if(newLevel != null) {
					Integer oldLevel = rsUser.getInt("levelW3");
					if(newLevel > oldLevel) {
						rsUser.updateInt("levelW3", newLevel);
						rsUser.updateRow();
					}
				}
				break;
			}
			}
			d.close(rsUser);
			
			ResultSet rsAccount = d.getAccount(user);
			if(!rsAccount.next()) {
				d.close(rsAccount);
				return;
			}
			
			//check for birthdays
			Date birthday = rsAccount.getDate("birthday");
			if(!rsAccount.wasNull()) {
				SimpleDateFormat sdf = new SimpleDateFormat("M-d");

				Calendar cal = Calendar.getInstance();
				Date today = cal.getTime();
				String s1 = sdf.format(today);
				String s2 = sdf.format(birthday);
				
				if(s1.equals(s2)) {
					cal.setTime(birthday);
					int age = cal.get(Calendar.YEAR);
					cal.setTime(today);
					age = cal.get(Calendar.YEAR) - age;
					c.sendChat("Happy birthday, " + user + "! Today, you are " + age + " years old!");
				}
			}

			//check for autopromotions
			long rank = rsAccount.getLong("access");
			long id = rsAccount.getLong("id");
			ResultSet rsRank = d.getRank(rank);
			if(rsRank.next()) {
				String greeting = rsRank.getString("greeting");
				if(greeting != null) {
					greeting = String.format(greeting, user.toString(), user.getPing(), user.getFullAccountName());
					c.sendChat(greeting);
				}

				//Autopromotions:
				long apDays = rsRank.getLong("apDays");
				Timestamp ts = rsAccount.getTimestamp("lastRankChange");
				//Check that the 
				apBlock: if(apDays != 0) {
					double timeElapsed = 0;
					if(ts != null) {
						timeElapsed = (double)(new Date().getTime() - ts.getTime());
						timeElapsed /= 1000 * 60 * 60 * 24;
					}
					if((timeElapsed > apDays) || (ts == null)) {
						long apWins = rsRank.getLong("apWins");
						long apD2Level = rsRank.getLong("apD2Level");
						long apW3Level = rsRank.getLong("apW3Level");
						long wins[] = d.getAccountWinsLevels(id, c.getConnectionSettings().recruitTagPrefix, c.getConnectionSettings().recruitTagSuffix);
						if(((apWins > 0) && (wins[0] >= apWins))
						|| ((apD2Level > 0) && (wins[1] >= apD2Level))
						|| ((apW3Level > 0) && (wins[2] >= apW3Level))
						|| ((apWins == 0) && (apD2Level == 0) && (apW3Level == 0))) {
							// Give them a promotion
							rank++;
							rsAccount.updateLong("access", rank);
							rsAccount.updateTimestamp("lastRankChange", new Timestamp(new Date().getTime()));
							rsAccount.updateRow();
							user.resetPrettyName();	//Reset the presentable name
							c.sendChat("Congratulations " + user.toString() + ", you just recieved a promotion! Your rank is now " + rank + ".");
						} else {
							//TODO: Tell the user they need x more wins
							String msg = "You need ";
							switch(statstr.getProduct()) {
							case ProductIDs.PRODUCT_STAR:
							case ProductIDs.PRODUCT_SEXP:
							case ProductIDs.PRODUCT_W2BN:
								msg += Long.toString(apWins - wins[0]) + " more win";
								if(apWins - wins[0] > 1)
									msg += "s";
								break;
							case ProductIDs.PRODUCT_D2DV:
							case ProductIDs.PRODUCT_D2XP:
								msg += "to reach Diablo 2 level " + apD2Level;
								break;
							case ProductIDs.PRODUCT_WAR3:
							case ProductIDs.PRODUCT_W3XP:
								msg += "to reach Warcraft 3 level " + apW3Level;
								break;
							default:
								break apBlock;
							}
							msg += " to recieve a promotion!";
							c.sendChat(user, msg, false);
						}
						
					}
				}
			}
			d.close(rsRank);
			d.close(rsAccount);

			//Mail
			long umc = d.getUnreadMailCount(id);
			if(umc > 0)
				c.sendChat(user, "You have " + umc + " unread messages; type [ %trigger%mail read ] to retrieve them", false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void channelLeave(BNetUser user) {
		touchUser(user, "leaving the channel");
	}
	
	public void channelUser(BNetUser user, StatString statstr) {}
	public void joinedChannel(String channel) {}

	public void recieveChat(BNetUser user, String text) {
		if(text == null)
			return;
		if(text.length() == 0)
			return;
		
		touchUser(user, "chatting in the channel");
		
		char trigger = c.getConnectionSettings().trigger.charAt(0);
		
		if(text.equals("?trigger"))
			parseCommand(user, "trigger", null, false); //c.sendChat(user, "The bot's trigger is: " + trigger);
		else
			if(text.charAt(0) == trigger) {
				String[] command = text.substring(1).split(" ", 2);
				String params = null;
				if(command.length > 1)
					params = command[1];
			
				parseCommand(user, command[0], params, false);
			}
	}

	public void recieveEmote(BNetUser user, String text) {}
	
	private boolean enableSendInfoErrorBack = false;
	private String lastInfo = null;
	private void recieveInfoError(String text) {
		if(!enableSendInfoErrorBack)
			return;
		
		long timeElapsed = new Date().getTime() - lastCommandTime;
		// 200ms
		if(timeElapsed < 200) {
			if(!text.equals(lastInfo)) {
				lastInfo = text;
				c.sendChat(lastCommandUser, text, false);
			}
		}
	}
	
	public void recieveError(String text) {
		recieveInfoError(text);
	}

	/**
	 * If the name is "[NAME]", return "NAME" otherwise pass name through
	 * @param name	The name from the /who response
	 * @return		Name with [] removed
	 */
	private String removeOpUserBrackets(String name) {
		if(name.charAt(0) == '[') {
			if(name.charAt(name.length() - 1) == ']') {
				return name.substring(1, name.length() - 1); 
			}
		}
		return name;
	}
	
	public void recieveInfo(String text) {
		if(sweepBanInProgress) {
			boolean turnItOff = true;
			
			if(text.length() > 17) {
				if(text.substring(0, 17).equals("Users in channel ")) {
					if(sweepBannedUsers == 0) {
						turnItOff = false;
						c.sendChat("Sweepbanning channel " + text.substring(17, text.length() - 1));
					}
				}
			}
			
			String users[] = text.split(", ");
			if(users.length == 2) {
				if(users[0].indexOf(' ') == -1) {
					if(users[1].indexOf(' ') == -1) {
						c.sendChat("/ban " + removeOpUserBrackets(users[0]));
						c.sendChat("/ban " + removeOpUserBrackets(users[1]));
						sweepBannedUsers += 2;
						turnItOff = false;
					}
				}
			} else {
				if(text.indexOf(' ') == -1) {
					c.sendChat("/ban " + removeOpUserBrackets(text));
					sweepBannedUsers++;
					turnItOff = true;
				}
			}
			
			if(turnItOff)
				sweepBanInProgress = false;
		}
		
		if(sweepBanInProgress)
			return;
		
		recieveInfoError(text);
	}

	public void bnetConnected() {}
	public void bnetDisconnected() {}
	public void titleChanged() {}

	public void whisperRecieved(BNetUser user, String text) {
		if(text == null)
			return;
		if(text.length() == 0)
			return;
		
		char trigger = c.getConnectionSettings().trigger.charAt(0);
		if(text.charAt(0) == trigger)
			text = text.substring(1);
		
		int i = text.indexOf(' ');
		if(i == -1) {
			parseCommand(user, text, null, true);
		} else {
			String command = text.substring(0, i);
			String paramString = text.substring(i + 1);
			
			parseCommand(user, command, paramString, true);
		}
	}

	public void whisperSent(BNetUser user, String text) {}

	public void friendsList(FriendEntry[] entries) {}
	public void friendsUpdate(FriendEntry friend) {}
	public void friendsAdd(FriendEntry friend) {}
	public void friendsPosition(byte oldPosition, byte newPosition) {}
	public void friendsRemove(byte entry) {}
	
	public void queryRealms2(String[] realms) {}
	public void logonRealmEx(int[] MCPChunk1, int ip, int port, int[] MCPChunk2, String uniqueName) {}

	public void clanMOTD(Object cookie, String text) {}
	public void clanMemberList(ClanMember[] members) {}
	public void clanMemberRemoved(String username) {}
	public void clanMemberStatusChange(ClanMember member) {}
	public void clanMemberRankChange(byte oldRank, byte newRank, String user) {}
}