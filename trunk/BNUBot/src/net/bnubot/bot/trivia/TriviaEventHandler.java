/**
 * This file is distributed under the GPL
 * $Id$
 */

package net.bnubot.bot.trivia;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import net.bnubot.core.Connection;
import net.bnubot.core.EventHandler;
import net.bnubot.core.bncs.BNCSConnection;
import net.bnubot.core.clan.ClanMember;
import net.bnubot.core.friend.FriendEntry;
import net.bnubot.db.Account;
import net.bnubot.db.conf.DatabaseContext;
import net.bnubot.settings.GlobalSettings;
import net.bnubot.util.BNetUser;
import net.bnubot.util.HexDump;
import net.bnubot.util.Out;

import org.apache.cayenne.ObjectContext;

public class TriviaEventHandler implements EventHandler {
	private boolean triviaEnabled = false;
	private final List<TriviaItem> trivia = new LinkedList<TriviaItem>();
	private TriviaItem triviaCurrent = null;
	private BNetUser answerUser = null;
	private String answerUsed = null;
	private int unanswered = 0;
	private Connection initializedConnection = null;
	private boolean disposed = false;
	
	public TriviaEventHandler() {}
	
	private void readFile(String fileName) {
		BufferedReader is = null;
		try {
			File f = new File(fileName);
			if(!f.exists())
				throw new FileNotFoundException(fileName);
			is = new BufferedReader(new FileReader(f));
		} catch (Exception e) {
			Out.fatalException(e);
		}
		
		String defaultCategory = fileName;
		if(defaultCategory.indexOf('.') != -1)
			defaultCategory = defaultCategory.split("\\.", 2)[0];
		while(defaultCategory.indexOf('/') != -1)
			defaultCategory = defaultCategory.split("\\/", 2)[1];
		while(defaultCategory.indexOf('\\') != -1)
			defaultCategory = defaultCategory.split("\\\\", 2)[1];
		
		long linenumber = 0;
		do {
			String line = null;
			linenumber++;
			try {
				line = is.readLine();
			} catch (IOException e) {
				Out.fatalException(e);
			}
			if(line == null)
				break;
			
			line = line.trim();
			if(line.length() == 0)
				continue;
			
			try {
				trivia.add(new TriviaItem(line, defaultCategory));
			} catch(IllegalArgumentException e) {
				Out.error(getClass(), "Failed to parse line #" + linenumber + " from " + fileName + ": " + line);
			}
		} while(true);
		
		try { is.close(); } catch (Exception e) {}
	}
	
	private void reloadTrivia(Connection source) {
		File f = new File("trivia");
		if(!f.exists())
			f.mkdir();
		if(f.isDirectory())
			for(String fname : f.list())
				readFile(f.getPath() + System.getProperty("file.separator") + fname);
		
		source.recieveInfo("Trivia initialized with " + trivia.size() + " questions");
	}

	public void bnetConnected(Connection source) {}
	public void bnetDisconnected(Connection source) {}
	public void titleChanged(Connection source) {}

	public void channelJoin(Connection source, BNetUser user) {}
	public void channelLeave(Connection source, BNetUser user) {}
	public void channelUser(Connection source, BNetUser user) {}
	
	private void showLeaderBoard(Connection source) {
		try {
			ObjectContext context = DatabaseContext.getContext();
			if(context == null)
				return;
			
			List<Account> leaders = Account.getTriviaLeaders();
			if(leaders == null)
				return;
			
			StringBuilder out = new StringBuilder("Trivia Leader Board: ");
			for(Account a : leaders) {
				out.append(a.getName()).append('(');
				out.append(a.getTriviaCorrect()).append(") ");
			}
			out.append("Total=").append(getTriviaSum());
			source.sendChat(out.toString(), false);
		} catch (Exception e) {
			Out.exception(e);
		}
	}
	
	private long getTriviaSum() {
		long total = 0;
		for(Account a : Account.getTriviaLeaders())
			total += a.getTriviaCorrect();
		return total;
	}
	
	private long[] getTriviaTopTwo() {
		List<Account> leaders = Account.getTriviaLeaders();
		if((leaders == null) || (leaders.size() == 0))
			return null;
		
		if(leaders.size() == 1)
			return new long[] {leaders.get(0).getTriviaCorrect()};
		
		return null;
	}
	
	private String resetTrivia() {
		List<Account> leaders = Account.getTriviaLeaders();
		if((leaders != null) && (leaders.size() > 0)) {
			Account winner = leaders.get(0);
			// Increment the winner's wins
			winner.setTriviaWin(winner.getTriviaWin() + 1);
			// Reset all scores to zero
			for(Account a : leaders)
				a.setTriviaCorrect(0);
			try {
				// Save changes
				winner.updateRow();
				// Return the winner's name
				return winner.getName();
			} catch(Exception e) {
				Out.exception(e);
			}
		}
		return null;
	}
	
	private void triviaLoop(Connection source) {
		while(!disposed) {
			try {
				if(triviaEnabled && source.canSendChat()) {
					if(trivia.size() == 0) {
						source.sendChat("There are no trivia questions left; game over.", false);
						triviaEnabled = false;
						continue;
					}
					
					if(DatabaseContext.getContext() != null) {
						try {
							long max[] = getTriviaTopTwo();
							if(max != null) {
								final long total = getTriviaSum();
								final long target = GlobalSettings.triviaRoundLength;
								boolean condition = false;
								// There are no questions left
								condition |= (total >= target);
								// First place has half of the points
								condition |= (max[0] > (target/2));
								if(max.length > 1) {
									long questionsLeft = (target - total);
									long bestTop2Score = max[1] + questionsLeft;
									// Second place can't pass first place
									condition |= (bestTop2Score < max[1]);
								}
								if(condition) {
									String out = "The trivia round is over! Congratulations to ";
									out += resetTrivia();
									out += " for winning the round!";
									source.sendChat(out, false);
								}
							}
						} catch (Exception e) {
							Out.exception(e);
						}
					}
					
					triviaCurrent = trivia.remove((int)(Math.random() * trivia.size()));
					answerUser = null;
					
					if(true) {
						String q = "/me";
						if(triviaCurrent.getCategory() != null)
							q += " - Category: " + triviaCurrent.getCategory();
						q += " - Question: " + triviaCurrent.getQuestion();
						q += " - Hint: " + triviaCurrent.getHint0();
						source.sendChat(q, false);
						//c.recieveInfo("Answer: " + ti.getAnswer());
					}
					
					long timeQuestionAsked = System.currentTimeMillis();
					long timeElapsed = 0;
					int numHints = 0;
					do {
						if(answerUser != null)
							break;
						
						timeElapsed = System.currentTimeMillis() - timeQuestionAsked;
						timeElapsed /= 1000;

						if((timeElapsed > 10) && (numHints < 1)) {
							source.sendChat("/me - 20 seconds left! Hint: " + triviaCurrent.getHint1(), false);
							numHints++;
						}
						
						if((timeElapsed > 20) && (numHints < 2)) {
							source.sendChat("/me - 10 seconds left! Hint: " + triviaCurrent.getHint2(), false);
							numHints++;
						}
						
						Thread.sleep(200);
						Thread.yield();
					} while((timeElapsed < 30) && triviaEnabled);

					if(answerUser != null) {
						unanswered = 0;
						String extra = "!";

						if(DatabaseContext.getContext() != null) {
							try {
								Account answeredBy = Account.get(answerUser);
								if(answeredBy != null) {
									int score = answeredBy.getTriviaCorrect();
									score++;
									answeredBy.setTriviaCorrect(score);
									answeredBy.updateRow();
									extra += " Your score is " + score + ".";
								}
							} catch(Exception e) {
								Out.exception(e);
							}
						}

						String[] triviaAnswersAN = triviaCurrent.getAnswersAlphaNumeric();
						if(triviaAnswersAN.length > 1) {
							extra += " Other acceptable answers were: ";
							boolean first = true;
							String answerUsedAN = HexDump.getAlphaNumerics(answerUsed);
							String[] triviaAnswers = triviaCurrent.getAnswers();
							for(int i = 0; i < triviaAnswersAN.length; i++) {
								if(!triviaAnswersAN[i].equals(answerUsedAN)) {
									if(first)
										first = false;
									else
										extra += ", or ";
									extra += "\"" + triviaAnswers[i] + "\"";
								}
							}
						}
						
						source.sendChat("/me - \"" + answerUsed + "\" is correct, " + answerUser.toString() + extra, false);
						
						showLeaderBoard(source);
					} else {
						String[] triviaAnswers = triviaCurrent.getAnswers();
						String correct = " The correct answer was \"" + triviaAnswers[0] + "\"";
						for(int i = 1; i < triviaAnswers.length; i++)
							correct += ", or \"" + triviaAnswers[i] + "\"";
						
						if(triviaEnabled) {
							unanswered++;
							source.sendChat("/me - Time's up!" + correct, false);
						} else {
							source.sendChat("/me - Game over!" + correct, false);
							continue;
						}
					}

					if(unanswered == 9)
						source.sendChat("Trivia will automaticly shut off after the next question. To extend trivia, type [ trivia on ]", false);
					if(unanswered >= 10) {
						source.sendChat("Auto-disabling trivia.", false);
						triviaEnabled = false;
					}
				}
			
				Thread.sleep(1000);
				Thread.yield();
			} catch (InterruptedException e) {}
		}
	}
	
	public void initialize(final Connection source) {
		if(initializedConnection == null) {
			initializedConnection = source;
			new Thread() {
				public void run() {
					triviaLoop(source);
				}
			}.start();
		}
	}
	public void disable(final Connection source) {
		if(initializedConnection == source)
			disposed = true;
	}
	
	public void triviaOn(Connection source) {
		if(trivia.size() == 0)
			reloadTrivia(source);
			
		unanswered = 0;
		triviaEnabled = true;
	}
	
	public void triviaOff() {
		triviaEnabled = false;
	}
	
	public void joinedChannel(Connection source, String channel) {}
	public void recieveChat(Connection source, BNetUser user, String text) {
		if(!triviaEnabled) {
			if("trivia on".equals(text)) {
				triviaOn(source);
			} else if("trivia score".equals(text)) {
				if(!triviaEnabled)
					showLeaderBoard(source);
			}
		} else {
			if("trivia off".equals(text)) {
				triviaOff();
			} else if(triviaCurrent != null) {
				String textAN = HexDump.getAlphaNumerics(text);
				String[] triviaAnswers = triviaCurrent.getAnswers();
				String[] triviaAnswersAN = triviaCurrent.getAnswersAlphaNumeric();
				for(int i = 0; i < triviaAnswers.length; i++) {
					if(triviaAnswersAN[i].equalsIgnoreCase(textAN)) {
						answerUser = user;
						answerUsed = triviaAnswers[i];
					}
				}
			}
		}
	}
	public void recieveEmote(Connection source, BNetUser user, String text) {}
	public void recieveError(Connection source, String text) {}
	public void recieveInfo(Connection source, String text) {}
	public void recieveDebug(Connection source, String text) {}
	public void whisperRecieved(Connection source, BNetUser user, String text) {}
	public void whisperSent(Connection source, BNetUser user, String text) {}

	public void friendsList(BNCSConnection source, FriendEntry[] entries) {}
	public void friendsUpdate(BNCSConnection source, FriendEntry friend) {}
	public void friendsAdd(BNCSConnection source, FriendEntry friend) {}
	public void friendsPosition(BNCSConnection source, byte oldPosition, byte newPosition) {}
	public void friendsRemove(BNCSConnection source, byte entry) {}
	
	public void logonRealmEx(BNCSConnection source, int[] MCPChunk1, int ip, int port, int[] MCPChunk2, String uniqueName) {}
	public void queryRealms2(BNCSConnection source, String[] realms) {}

	public boolean parseCommand(Connection source, BNetUser user, String command, boolean whisperBack) {return false;}

	public void clanMOTD(BNCSConnection source, Object cookie, String text) {}
	public void clanMemberList(BNCSConnection source, ClanMember[] members) {}
	public void clanMemberRemoved(BNCSConnection source, String username) {}
	public void clanMemberStatusChange(BNCSConnection source, ClanMember member) {}
	public void clanMemberRankChange(BNCSConnection source, byte oldRank, byte newRank, String user) {}
}
