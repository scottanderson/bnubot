/**
 * This file is distributed under the GPL
 * $Id$
 */

package net.bnubot.core.bncs;

import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

import net.bnubot.bot.CommandResponseCookie;
import net.bnubot.bot.gui.ProfileEditor;
import net.bnubot.core.Connection;
import net.bnubot.core.EventHandler;
import net.bnubot.core.Profile;
import net.bnubot.core.UnsupportedFeatureException;
import net.bnubot.core.bnls.BNLSPacket;
import net.bnubot.core.bnls.BNLSPacketId;
import net.bnubot.core.bnls.BNLSPacketReader;
import net.bnubot.core.botnet.BotNetConnection;
import net.bnubot.core.clan.ClanCreationInvitationCookie;
import net.bnubot.core.clan.ClanInvitationCookie;
import net.bnubot.core.clan.ClanMember;
import net.bnubot.core.clan.ClanRankIDs;
import net.bnubot.core.clan.ClanStatusIDs;
import net.bnubot.core.friend.FriendEntry;
import net.bnubot.settings.ConnectionSettings;
import net.bnubot.settings.GlobalSettings;
import net.bnubot.util.BNetInputStream;
import net.bnubot.util.BNetUser;
import net.bnubot.util.CookieUtility;
import net.bnubot.util.HexDump;
import net.bnubot.util.MirrorSelector;
import net.bnubot.util.Out;
import net.bnubot.util.StatString;
import net.bnubot.util.TimeFormatter;
import net.bnubot.util.UserProfile;
import net.bnubot.util.task.Task;

import org.jbls.Hashing.BrokenSHA1;
import org.jbls.Hashing.DoubleHash;
import org.jbls.Hashing.HashMain;
import org.jbls.Hashing.SRP;

/**
 * Represents a connection to a Battle.Net Chat Server (BNCS)
 * @author scotta
 */
public class BNCSConnection extends Connection {
	public static final String[] clanRanks = { "Initiate", "Peon", "Grunt",
			"Shaman", "Chieftain" };

	private BotNetConnection botnet = null;

	private InputStream bnlsInputStream = null;
	private OutputStream bnlsOutputStream = null;
	private InputStream bncsInputStream = null;
	private DataOutputStream bncsOutputStream = null;

	private ProductIDs productID = null;
	private int verByte;
	private Integer nlsRevision = null;
	private BNCSWarden warden = null;
	private int serverToken = 0;
	private final int clientToken = Math.abs(new Random().nextInt());
	private SRP srp = null;
	private byte proof_M2[] = null;
	protected Integer myClan = null;
	protected Byte myClanRank = null;
	protected long lastEntryForced;

	public BNCSConnection(ConnectionSettings cs, Profile p) {
		super(cs, p);
	}

	/**
	 * Connect to BNLS and get verbyte
	 *
	 * @throws IOException
	 * @throws SocketException
	 */
	private void initializeBNLS(Task connect) throws IOException,
			SocketException {
		// Connect to BNLS
		connect.updateProgress("Connecting to BNLS");
		setBNLSConnected(true);
		bnlsInputStream = bnlsSocket.getInputStream();
		bnlsOutputStream = bnlsSocket.getOutputStream();
		myClan = null;
		myClanRank = null;

		// Log in to BNLS
		connect.updateProgress("Logging in to BNLS");

		// Send BNLS_AUTHORIZE
		BNLSPacket loginPacket = new BNLSPacket(BNLSPacketId.BNLS_AUTHORIZE);
		loginPacket.writeNTString("bnu2");
		loginPacket.SendPacket(bnlsOutputStream);

		// Recieve BNLS_AUTHORIZE
		BNetInputStream is = new BNLSPacketReader(bnlsInputStream)
				.getInputStream();
		int serverCode = is.readDWord();

		// Calculate checksum
		int checksum = (int) (org.jbls.BNLSProtocol.BNLSlist.BNLSChecksum(
				"bot", serverCode) & 0xFFFFFFFF);

		// Send BNLS_AUTHORIZEPROOF
		loginPacket = new BNLSPacket(BNLSPacketId.BNLS_AUTHORIZEPROOF);
		loginPacket.writeDWord(checksum);
		loginPacket.SendPacket(bnlsOutputStream);

		// Recieve BNLS_AUTHORIZEPROOF
		is = new BNLSPacketReader(bnlsInputStream).getInputStream();
		int statusCode = is.readDWord();
		if (statusCode != 0)
			Out
					.error(getClass(),
							"Login to BNLS failed; logged in anonymously");

		// Get the verbyte locally
		verByte = HashMain.getVerByte(cs.product.getBnls());

		BNLS_REQUESTVERSIONBYTE: {
			// Ask BNLS for the verbyte
			connect.updateProgress("Getting verbyte from BNLS");
			BNLSPacket vbPacket = new BNLSPacket(
					BNLSPacketId.BNLS_REQUESTVERSIONBYTE);
			vbPacket.writeDWord(cs.product.getBnls());
			vbPacket.SendPacket(bnlsOutputStream);

			BNetInputStream vbInputStream = new BNLSPacketReader(
					bnlsInputStream).getInputStream();
			int vbProduct = vbInputStream.readDWord();
			if (vbProduct == 0) {
				dispatchRecieveError("BNLS_REQUESTVERSIONBYTE failed.");
				break BNLS_REQUESTVERSIONBYTE;
			}
			int vb = vbInputStream.readWord();

			if (vb != verByte) {
				dispatchRecieveInfo("BNLS_REQUESTVERSIONBYTE: 0x"
						+ Integer.toHexString(vb) + ".");
				verByte = vb;
			}
		}
	}

	/**
	 * Initialize the connection, send game id
	 *
	 * @throws Exception
	 */
	private void initializeBNCS(Task connect) throws Exception {
		nlsRevision = null;
		warden = null;
		productID = cs.product;

		// Set up BNCS
		connect.updateProgress("Connecting to Battle.net");
		int port = getPort();
		InetAddress address = MirrorSelector
				.getClosestMirror(getServer(), port);
		dispatchRecieveInfo("Connecting to " + address + ":" + port + ".");
		socket = new Socket(address, port);
		socket.setKeepAlive(true);
		bncsInputStream = socket.getInputStream();
		bncsOutputStream = new DataOutputStream(socket.getOutputStream());

		// Game
		bncsOutputStream.writeByte(0x01);
		connect.updateProgress("Connected");
	}

	/**
	 * Send the initial set of packets
	 *
	 * @throws Exception
	 */
	private void sendInitialPackets(Task connect) throws Exception {
		connect.updateProgress("Initializing BNCS");

		BNCSPacket p;
		Locale loc = Locale.getDefault();
		String prodLang = loc.getLanguage() + loc.getCountry();
		int tzBias = TimeZone.getDefault()
				.getOffset(System.currentTimeMillis())
				/ -60000;

		switch (productID) {
		case STAR:
		case SEXP:
		case D2DV:
		case D2XP:
		case WAR3:
		case W3XP: {
			// NLS
			p = new BNCSPacket(BNCSPacketId.SID_AUTH_INFO);
			p.writeDWord(0); // Protocol ID (0)
			p.writeDWord(PlatformIDs.PLATFORM_IX86); // Platform ID (IX86)
			p.writeDWord(productID.getDword()); // Product ID
			p.writeDWord(verByte); // Version byte
			p.writeDWord(prodLang); // Product language
			p.writeDWord(0); // Local IP
			p.writeDWord(tzBias); // TZ bias
			p.writeDWord(0x409); // Locale ID
			p.writeDWord(0x409); // Language ID
			p.writeNTString(loc.getISO3Country()); // Country abreviation
			p.writeNTString(loc.getDisplayCountry()); // Country
			p.SendPacket(bncsOutputStream);
			break;
		}

		case DRTL:
		case DSHR:
		case SSHR:
		case JSTR:
		case W2BN: {
			// OLS
			if (productID == ProductIDs.SSHR) {
				p = new BNCSPacket(BNCSPacketId.SID_CLIENTID);
				p.writeDWord(0); // Registration Version
				p.writeDWord(0); // Registration Authority
				p.writeDWord(0); // Account Number
				p.writeDWord(0); // Registration Token
				p.writeByte(0); // LAN computer name
				p.writeByte(0); // LAN username
				p.SendPacket(bncsOutputStream);
			} else {
				p = new BNCSPacket(BNCSPacketId.SID_CLIENTID2);
				p.writeDWord(1); // Server version
				p.writeDWord(0); // Registration Version
				p.writeDWord(0); // Registration Authority
				p.writeDWord(0); // Account Number
				p.writeDWord(0); // Registration Token
				p.writeByte(0); // LAN computer name
				p.writeByte(0); // LAN username
				p.SendPacket(bncsOutputStream);
			}

			p = new BNCSPacket(BNCSPacketId.SID_LOCALEINFO);
			p.writeQWord(0); // System time
			p.writeQWord(0); // Local time
			p.writeDWord(tzBias); // TZ bias
			p.writeDWord(0x409); // SystemDefaultLCID
			p.writeDWord(0x409); // UserDefaultLCID
			p.writeDWord(0x409); // UserDefaultLangID
			p.writeNTString("ena"); // Abbreviated language name
			p.writeNTString("1"); // Country code
			p.writeNTString(loc.getISO3Country()); // Abbreviated country name
			p.writeNTString(loc.getDisplayCountry()); // Country (English)
			p.SendPacket(bncsOutputStream);

			// TODO: JSTR/SSHR: SID_SYSTEMINFO

			p = new BNCSPacket(BNCSPacketId.SID_STARTVERSIONING);
			p.writeDWord(PlatformIDs.PLATFORM_IX86); // Platform ID (IX86)
			p.writeDWord(productID.getDword()); // Product ID
			p.writeDWord(verByte); // Version byte
			p.writeDWord(0); // Unknown (0)
			p.SendPacket(bncsOutputStream);
			break;
		}

		default:
			dispatchRecieveError("Don't know how to connect with product " + productID);
			disconnect(false);
			break;
		}
	}

	@Override
	protected void initializeConnection(Task connect) throws Exception {
		if (cs.enableBotNet) {
			if (botnet == null) {
				try {
					botnet = new BotNetConnection(this, cs, profile);
					profile.insertConnection(botnet);
				} catch (Exception e) {
					Out.exception(e);
				}
			} else {
				botnet.sendStatusUpdate();
			}
		}

		// Set up BNLS, get verbyte
		try {
			initializeBNLS(connect);
		} catch (EOFException e) {
			completeTask(connect);
			Out.error(getClass(), "BNLS login failed");
			disconnect(false);
			return;
		}

		// Set up BNCS
		initializeBNCS(connect);

		// Begin login
		sendInitialPackets(connect);
	}

	/**
	 * Do the login work up to SID_ENTERCHAT
	 *
	 * @throws Exception
	 */
	@Override
	protected boolean sendLoginPackets(Task connect) throws Exception {
		while (isConnected() && !socket.isClosed() && !disposed) {
			if (bncsInputStream.available() > 0) {
				BNCSPacketReader pr = new BNCSPacketReader(bncsInputStream);
				BNetInputStream is = pr.getData();

				switch (pr.packetId) {
				case SID_OPTIONALWORK:
				case SID_EXTRAWORK:
				case SID_REQUIREDWORK:
					break;

				case SID_NULL: {
					BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_NULL);
					p.SendPacket(bncsOutputStream);
					break;
				}

				case SID_PING: {
					BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_PING);
					p.writeDWord(is.readDWord());
					p.SendPacket(bncsOutputStream);
					break;
				}

				case SID_AUTH_INFO:
				case SID_STARTVERSIONING: {
					if (pr.packetId == BNCSPacketId.SID_AUTH_INFO) {
						nlsRevision = is.readDWord();
						serverToken = is.readDWord();
						is.skip(4); // int udpValue = is.readDWord();
					}
					long MPQFileTime = is.readQWord();
					String MPQFileName = is.readNTString();
					byte[] ValueStr = is.readNTBytes();

					Out.debug(getClass(), "MPQ: " + MPQFileName);

					byte extraData[] = null;
					if (is.available() == 0x80) {
						extraData = new byte[0x80];
						is.read(extraData, 0, 0x80);
					}
					assert (is.available() == 0);

					// Hash the CD key
					byte keyHash[] = null;
					byte keyHash2[] = null;
					if (nlsRevision != null) {
						keyHash = HashMain.hashKey(clientToken, serverToken,
								cs.cdkey).getBuffer();
						if (productID == ProductIDs.D2XP)
							keyHash2 = HashMain.hashKey(clientToken,
									serverToken, cs.cdkey2).getBuffer();
						if (productID == ProductIDs.W3XP)
							keyHash2 = HashMain.hashKey(clientToken,
									serverToken, cs.cdkey2).getBuffer();

						try {
							byte[] warden_seed = new byte[4];
							System.arraycopy(keyHash, 16, warden_seed, 0, 4);
							warden = new BNCSWarden(warden_seed);
						} catch (Exception e) {
							warden = null;
							Out.exception(e);
						}
					}

					int exeHash = 0;
					int exeVersion = 0;
					byte[] exeInfo = null;

					try {
						BNLSPacket bnlsOut = new BNLSPacket(
								BNLSPacketId.BNLS_VERSIONCHECKEX2);
						bnlsOut.writeDWord(productID.getBnls());
						bnlsOut.writeDWord(0); // Flags
						bnlsOut.writeDWord(0); // Cookie
						bnlsOut.writeQWord(MPQFileTime);
						bnlsOut.writeNTString(MPQFileName);
						bnlsOut.writeNTString(ValueStr);
						bnlsOut.SendPacket(bnlsOutputStream);

						long startTime = System.currentTimeMillis();
						Task bnlsTask = createTask("BNLS_VERSIONCHECKEX2",
								5000, "ms");
						while (bnlsInputStream.available() < 3) {
							Thread.sleep(50);
							Thread.yield();

							long timeElapsed = System.currentTimeMillis()
									- startTime;
							if (timeElapsed > 5000) {
								setBNLSConnected(false);
								throw new Exception(
										"BNLS_VERSIONCHECKEX2 timed out");
							}
							bnlsTask.setProgress((int) timeElapsed);
						}
						completeTask(bnlsTask);

						BNLSPacketReader bpr = new BNLSPacketReader(
								bnlsInputStream);
						BNetInputStream bnlsIn = bpr.getInputStream();
						int success = bnlsIn.readDWord();
						if (success != 1) {
							Out.error(getClass(),
									"BNLS_VERSIONCHECKEX2 Failed\n"
											+ HexDump.hexDump(bpr.getData()));
							setBNLSConnected(false);
							throw new Exception("BNLS_VERSIONCHECKEX2 failed");
						}
						exeVersion = bnlsIn.readDWord();
						exeHash = bnlsIn.readDWord();
						exeInfo = bnlsIn.readNTBytes();
						bnlsIn.readDWord(); // cookie
						bnlsIn.readDWord(); // verbyte
						assert (bnlsIn.available() == 0);

						dispatchRecieveInfo("Recieved version check from BNLS.");

						bnlsSocket.close();
						bnlsSocket = null;
					} catch (UnknownHostException e) {
						dispatchRecieveError("BNLS connection failed: "
								+ e.getMessage());
						disconnect(true);
						break;
					}

					if ((exeVersion == 0) || (exeHash == 0)
							|| (exeInfo == null) || (exeInfo.length == 0)) {
						dispatchRecieveError("Checkrevision failed.");
						disconnect(true);
						break;
					}

					// Respond
					if (nlsRevision != null) {
						connect
								.updateProgress("CheckRevision/CD Key challenge");

						BNCSPacket p = new BNCSPacket(
								BNCSPacketId.SID_AUTH_CHECK);
						p.writeDWord(clientToken);
						p.writeDWord(exeVersion);
						p.writeDWord(exeHash);
						if (keyHash2 == null)
							p.writeDWord(1); // Number of keys
						else
							p.writeDWord(2); // Number of keys
						p.writeDWord(0); // Spawn?

						// For each key..
						if (keyHash.length != 36)
							throw new Exception("Invalid keyHash length");
						p.write(keyHash);
						if (keyHash2 != null) {
							if (keyHash2.length != 36)
								throw new Exception("Invalid keyHash2 length");
							p.write(keyHash2);
						}

						// Finally,
						p.writeNTString(exeInfo);
						p.writeNTString(cs.username);
						p.SendPacket(bncsOutputStream);
					} else {
						connect.updateProgress("CheckRevision");

						/*
						 * (DWORD) Platform ID (DWORD) Product ID (DWORD)
						 * Version Byte (DWORD) EXE Version (DWORD) EXE Hash
						 * (STRING) EXE Information
						 */
						BNCSPacket p = new BNCSPacket(
								BNCSPacketId.SID_REPORTVERSION);
						p.writeDWord(PlatformIDs.PLATFORM_IX86);
						p.writeDWord(productID.getDword());
						p.writeDWord(verByte);
						p.writeDWord(exeVersion);
						p.writeDWord(exeHash);
						p.writeNTString(exeInfo);
						p.SendPacket(bncsOutputStream);
					}
					break;
				}

				case SID_REPORTVERSION:
				case SID_AUTH_CHECK: {
					int result = is.readDWord();
					String extraInfo = is.readNTString();
					assert (is.available() == 0);

					if (pr.packetId == BNCSPacketId.SID_AUTH_CHECK) {
						if (result != 0) {
							switch (result) {
							case 0x0100:
								dispatchRecieveError("Update required: " + extraInfo);
								break;
							case 0x0101:
								dispatchRecieveError("Invalid version.");
								break;
							case 0x102:
								dispatchRecieveError("Game version must be downgraded: "
										+ extraInfo);
								break;
							case 0x200:
								dispatchRecieveError("Invalid CD key.");
								break;
							case 0x201:
								dispatchRecieveError("CD key in use by " + extraInfo);
								break;
							case 0x202:
								dispatchRecieveError("Banned key.");
								break;
							case 0x203:
								dispatchRecieveError("Wrong product for CD key.");
								break;
							case 0x210:
								dispatchRecieveError("Invalid second CD key.");
								break;
							case 0x211:
								dispatchRecieveError("Second CD key in use by "
										+ extraInfo);
								break;
							case 0x212:
								dispatchRecieveError("Banned second key.");
								break;
							case 0x213:
								dispatchRecieveError("Wrong product for second CD key.");
								break;
							default:
								dispatchRecieveError("Unknown SID_AUTH_CHECK result 0x"
										+ Integer.toHexString(result));
								break;
							}
							disconnect(false);
							break;
						}
						dispatchRecieveInfo("Passed CD key challenge and CheckRevision.");
					} else {
						if (result != 2) {
							switch (result) {
							case 0:
								dispatchRecieveError("Failed version check.");
								break;
							case 1:
								dispatchRecieveError("Old game version.");
								break;
							case 3:
								dispatchRecieveError("Reinstall required.");
								break;

							default:
								dispatchRecieveError("Unknown SID_REPORTVERSION result 0x"
										+ Integer.toHexString(result));
								break;
							}
							disconnect(false);
							break;
						}
						dispatchRecieveInfo("Passed CheckRevision.");
					}

					connect.updateProgress("Logging in");
					sendKeyOrPassword();
					break;
				}

				case SID_CDKEY:
				case SID_CDKEY2: {
					/*
					 * (DWORD) Result (STRING) Key owner
					 *
					 * 0x01: Ok 0x02: Invalid key 0x03: Bad product 0x04: Banned
					 * 0x05: In use
					 */
					int result = is.readDWord();
					String keyOwner = is.readNTString();

					if (result != 1) {
						switch (result) {
						case 0x02:
							dispatchRecieveError("Invalid CD key.");
							break;
						case 0x03:
							dispatchRecieveError("Bad CD key product.");
							break;
						case 0x04:
							dispatchRecieveError("CD key banned.");
							break;
						case 0x05:
							dispatchRecieveError("CD key in use by " + keyOwner);
							break;
						default:
							dispatchRecieveError("Unknown SID_CDKEY response 0x"
									+ Integer.toHexString(result));
							break;
						}
						disconnect(false);
						break;
					}

					dispatchRecieveInfo("CD key accepted.");
					connect.updateProgress("Logging in");
					sendPassword();
					break;
				}

				case SID_AUTH_ACCOUNTLOGON: {
					/*
					 * (DWORD) Status (BYTE[32]) Salt (socket) (BYTE[32]) Server
					 * Key (B)
					 *
					 * 0x00: Logon accepted, requires proof. 0x01: Account
					 * doesn't exist. 0x05: Account requires upgrade. Other:
					 * Unknown (failure).
					 */
					int status = is.readDWord();
					switch (status) {
					case 0x00:
						dispatchRecieveInfo("Login accepted; requires proof.");
						connect.updateProgress("Login accepted; proving");
						break;
					case 0x01:
						dispatchRecieveError("Account doesn't exist; creating...");
						connect.updateProgress("Creating account");

						if (srp == null) {
							dispatchRecieveError("SRP is not initialized!");
							disconnect(false);
							break;
						}

						byte[] salt = new byte[32];
						new Random().nextBytes(salt);
						byte[] verifier = srp.get_v(salt).toByteArray();

						if (salt.length != 32)
							throw new Exception("Salt length wasn't 32!");
						if (verifier.length != 32)
							throw new Exception("Verifier length wasn't 32!");

						BNCSPacket p = new BNCSPacket(
								BNCSPacketId.SID_AUTH_ACCOUNTCREATE);
						p.write(salt);
						p.write(verifier);
						p.writeNTString(cs.username);
						p.SendPacket(bncsOutputStream);

						break;
					case 0x05:
						dispatchRecieveError("Account requires upgrade");
						disconnect(false);
						break;
					default:
						dispatchRecieveError("Unknown SID_AUTH_ACCOUNTLOGON status 0x"
								+ Integer.toHexString(status));
						disconnect(false);
						break;
					}

					if (status != 0)
						break;

					if (srp == null) {
						dispatchRecieveError("SRP is not initialized!");
						disconnect(false);
						break;
					}

					byte s[] = new byte[32];
					byte B[] = new byte[32];
					is.read(s, 0, 32);
					is.read(B, 0, 32);

					byte M1[] = srp.getM1(s, B);
					proof_M2 = srp.getM2(s, B);
					if (M1.length != 20)
						throw new Exception("Invalid M1 length");

					BNCSPacket p = new BNCSPacket(
							BNCSPacketId.SID_AUTH_ACCOUNTLOGONPROOF);
					p.write(M1);
					p.SendPacket(bncsOutputStream);
					break;
				}

				case SID_AUTH_ACCOUNTCREATE: {
					/*
					 * (DWORD) Status 0x00: Successfully created account name.
					 * 0x04: Name already exists. 0x07: Name is too short/blank.
					 * 0x08: Name contains an illegal character. 0x09: Name
					 * contains an illegal word. 0x0a: Name contains too few
					 * alphanumeric characters. 0x0b: Name contains adjacent
					 * punctuation characters. 0x0c: Name contains too many
					 * punctuation characters. Any other: Name already exists.
					 */
					int status = is.readDWord();
					switch (status) {
					case 0x00:
						dispatchRecieveInfo("Account created; logging in.");
						connect.updateProgress("Logging in");
						sendKeyOrPassword();
						break;
					default:
						dispatchRecieveError("Create account failed with error code 0x"
								+ Integer.toHexString(status));
						break;
					}
					break;
				}

				case SID_AUTH_ACCOUNTLOGONPROOF: {
					/*
					 * (DWORD) Status (BYTE[20]) Server Password Proof (M2)
					 * (STRING) Additional information
					 *
					 * Status: 0x00: Logon successful. 0x02: Incorrect password.
					 * 0x0E: An email address should be registered for this
					 * account. 0x0F: Custom error. A string at the end of this
					 * message contains the error.
					 */
					int status = is.readDWord();
					byte server_M2[] = new byte[20];
					is.read(server_M2, 0, 20);
					String additionalInfo = null;
					if (is.available() != 0)
						additionalInfo = is.readNTStringUTF8();

					switch (status) {
					case 0x00:
						break;
					case 0x02:
						dispatchRecieveError("Incorrect password.");
						disconnect(false);
						break;
					case 0x0E:
						dispatchRecieveError("An email address should be registered for this account.");
						connect.updateProgress("Registering email address");
						sendSetEmail();
						break;
					case 0x0F:
						dispatchRecieveError("Custom bnet error: " + additionalInfo);
						disconnect(false);
						break;
					default:
						dispatchRecieveError("Unknown SID_AUTH_ACCOUNTLOGONPROOF status: 0x"
								+ Integer.toHexString(status));
						disconnect(false);
						break;
					}
					if (!isConnected())
						break;

					for (int i = 0; i < 20; i++) {
						if (server_M2[i] != proof_M2[i])
							throw new Exception(
									"Server couldn't prove password");
					}

					dispatchRecieveInfo("Login successful; entering chat.");
					connect.updateProgress("Entering chat");
					sendEnterChat();
					break;
				}

				case SID_LOGONRESPONSE2: {
					int result = is.readDWord();
					switch (result) {
					case 0x00: // Success
						dispatchRecieveInfo("Login successful; entering chat.");
						connect.updateProgress("Entering chat");
						sendEnterChat();
						sendGetChannelList();
						sendJoinChannel(cs.channel);
						break;
					case 0x01: // Account doesn't exist
						dispatchRecieveInfo("Account doesn't exist; creating...");
						connect.updateProgress("Creating account");

						int[] passwordHash = BrokenSHA1
								.calcHashBuffer(cs.password.toLowerCase()
										.getBytes());

						BNCSPacket p = new BNCSPacket(
								BNCSPacketId.SID_CREATEACCOUNT2);
						p.writeDWord(passwordHash[0]);
						p.writeDWord(passwordHash[1]);
						p.writeDWord(passwordHash[2]);
						p.writeDWord(passwordHash[3]);
						p.writeDWord(passwordHash[4]);
						p.writeNTString(cs.username);
						p.SendPacket(bncsOutputStream);
						break;
					case 0x02: // Invalid password;
						dispatchRecieveError("Incorrect password.");
						disconnect(false);
						break;
					case 0x06: // Account is closed
						dispatchRecieveError("Your account is closed.");
						disconnect(false);
						break;
					default:
						dispatchRecieveError("Unknown SID_LOGONRESPONSE2 result 0x"
								+ Integer.toHexString(result));
						disconnect(false);
						break;
					}
					break;
				}

				case SID_CLIENTID: {
					// Sends new registration values; no longer used
					break;
				}

				case SID_LOGONCHALLENGE: {
					serverToken = is.readDWord();
					break;
				}

				case SID_LOGONCHALLENGEEX: {
					/* int udpToken = */is.readDWord();
					serverToken = is.readDWord();
					break;
				}

				case SID_CREATEACCOUNT2: {
					int status = is.readDWord();
					/* String suggestion = */is.readNTString();

					switch (status) {
					case 0x00:
						dispatchRecieveInfo("Account created");
						connect.updateProgress("Logging in");
						sendKeyOrPassword();
						break;
					case 0x02:
						dispatchRecieveError("Name contained invalid characters");
						disconnect(false);
						break;
					case 0x03:
						dispatchRecieveError("Name contained a banned word");
						disconnect(false);
						break;
					case 0x04:
						dispatchRecieveError("Account already exists");
						disconnect(false);
						break;
					case 0x06:
						dispatchRecieveError("Name did not contain enough alphanumeric characters");
						disconnect(false);
						break;
					default:
						dispatchRecieveError("Unknown SID_CREATEACCOUNT2 status 0x"
								+ Integer.toHexString(status));
						disconnect(false);
						break;
					}
					break;
				}

				case SID_SETEMAIL: {
					dispatchRecieveError("An email address should be registered for this account.");
					connect.updateProgress("Registering email address");
					sendSetEmail();
					break;
				}

				case SID_ENTERCHAT: {
					String uniqueUserName = is.readNTString();
					StatString myStatString = new StatString(is.readNTString());
					/* String accountName = */is.readNTString();

					myUser = new BNetUser(this, uniqueUserName, cs.myRealm);
					myUser.setStatString(myStatString);
					dispatchRecieveInfo("Logged in as " + myUser.getFullLogonName()
							+ ".");
					dispatchTitleChanged();

					// We are officially logged in!

					// Get MOTD
					if (GlobalSettings.displayBattleNetMOTD) {
						BNCSPacket p = new BNCSPacket(
								BNCSPacketId.SID_NEWS_INFO);
						p
								.writeDWord((int) (new java.util.Date()
										.getTime() / 1000)); // timestamp
						p.SendPacket(bncsOutputStream);
					}

					// Get friends list
					BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_FRIENDSLIST);
					p.SendPacket(bncsOutputStream);

					// Join home channel
					if (nlsRevision != null) {
						sendGetChannelList();
						sendJoinChannel(cs.channel);
					}

					return true;
				}

				case SID_GETCHANNELLIST: {
					recieveGetChannelList(is);
					break;
				}

				case SID_CLANINFO: {
					recvClanInfo(is);
					break;
				}

				case SID_WARDEN: {
					if (warden != null)
						try {
							warden.processWardenPacket(is.readFully(),
									bncsOutputStream);
							break;
						} catch (Exception e) {
						}
					Out.error(getClass(),
							"Recieved SID_WARDEN but warden model was not initialized\n"
									+ HexDump.hexDump(pr.data));
					break;
				}

				default:
					Out.debugAlways(getClass(), "Unexpected packet "
							+ pr.packetId.name() + "\n"
							+ HexDump.hexDump(pr.data));
					break;
				}
			}
		}

		return false;
	}

	/**
	 * @param is
	 * @throws IOException
	 * @throws SocketException
	 */
	private void recvClanInfo(BNetInputStream is) throws IOException,
			SocketException {
		/*
		 * (BYTE) Unknown (0)
		 * (DWORD) Clan tag
		 * (BYTE) Rank
		 */
		is.readByte();
		myClan = is.readDWord();
		myClanRank = is.readByte();
		dispatchTitleChanged();

		// TODO: dispatchClanInfo(myClan, myClanRank);

		// Get clan list
		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_CLANMEMBERLIST);
		p.writeDWord(0); // Cookie
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * JSTR: Send SID_CDKEY W2BN: Send SID_CDKEY2 else: Call sendPassword()
	 *
	 * @throws Exception
	 */
	private void sendKeyOrPassword() throws Exception {
		BNCSPacket p;

		switch (productID) {
		case JSTR:
			p = new BNCSPacket(BNCSPacketId.SID_CDKEY);
			p.writeDWord(0); // Spawn
			p.writeNTString(cs.cdkey);
			p.writeNTString(cs.username);
			p.SendPacket(bncsOutputStream);
			break;

		case W2BN:
			byte[] keyHash = HashMain.hashW2Key(clientToken, serverToken,
					cs.cdkey).getBuffer();
			if (keyHash.length != 40)
				throw new Exception("Invalid keyHash length");

			p = new BNCSPacket(BNCSPacketId.SID_CDKEY2);
			p.writeDWord(0); // Spawn
			p.write(keyHash);
			p.writeNTString(cs.username);
			p.SendPacket(bncsOutputStream);
			break;

		default:
			sendPassword();
			break;
		}
	}

	/**
	 * Send SID_LOGONRESPONSE2 (OLS) or SID_AUTH_ACCOUNTLOGON (NLS)
	 *
	 * @throws Exception
	 */
	private void sendPassword() throws Exception {
		if (!cs.enablePlug)
			// Disable the plug by sending SID_UDPPINGRESPONE
			switch (productID) {
			case DSHR:
			case DRTL:
			case SSHR:
			case JSTR:
			case STAR:
			case SEXP:
			case W2BN:
				BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_UDPPINGRESPONSE);
				p.writeDWord("bnet"); // TODO: get this value from a real UDP
										// connection
				p.SendPacket(bncsOutputStream);
				break;
			}

		if ((nlsRevision == null) || (nlsRevision == 0)) {
			int passwordHash[] = DoubleHash.doubleHash(cs.password
					.toLowerCase(), clientToken, serverToken);

			BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_LOGONRESPONSE2);
			p.writeDWord(clientToken);
			p.writeDWord(serverToken);
			p.writeDWord(passwordHash[0]);
			p.writeDWord(passwordHash[1]);
			p.writeDWord(passwordHash[2]);
			p.writeDWord(passwordHash[3]);
			p.writeDWord(passwordHash[4]);
			p.writeNTString(cs.username);
			p.SendPacket(bncsOutputStream);
		} else {
			srp = new SRP(cs.username, cs.password);
			srp.set_NLS(nlsRevision);
			byte A[] = srp.get_A();

			if (A.length != 32)
				throw new Exception("Invalid A length");

			BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_AUTH_ACCOUNTLOGON);
			p.write(A);
			p.writeNTString(cs.username);
			p.SendPacket(bncsOutputStream);
		}
	}

	/**
	 * Send SID_ENTERCHAT
	 *
	 * @throws Exception
	 */
	private void sendEnterChat() throws Exception {
		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_ENTERCHAT);
		p.writeNTString("");
		p.writeNTString("");
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_GETCHANNELLIST
	 *
	 * @throws Exception
	 */
	private void sendGetChannelList() throws Exception {
		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_GETCHANNELLIST);
		p.writeDWord(productID.getDword());
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * This method is the main loop after recieving SID_ENTERCHAT
	 *
	 * @throws Exception
	 */
	@Override
	protected void connectedLoop() throws Exception {
		lastNullPacket = System.currentTimeMillis();
		lastEntryForced = lastNullPacket;
		profile.lastAntiIdle = lastNullPacket;

		if(botnet != null)
			botnet.sendStatusUpdate();

		while(isConnected() && !socket.isClosed() && !disposed) {
			long timeNow = System.currentTimeMillis();

			// Send null packets every 5 seconds
			if(true) {
				long timeSinceNullPacket = (timeNow - lastNullPacket) / 1000;
				// Wait 5 seconds
				if(timeSinceNullPacket > 5) {
					lastNullPacket = timeNow;
					BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_NULL);
					p.SendPacket(bncsOutputStream);
				}
			}

			// Send anti-idles every cs.antiIdleTimer minutes
			if((channelName != null) && cs.enableAntiIdle) {
				synchronized(profile) {
					long timeSinceAntiIdle = timeNow - profile.lastAntiIdle;

					// Wait 5 minutes
					timeSinceAntiIdle /= 1000;
					timeSinceAntiIdle /= 60;
					if(timeSinceAntiIdle >= cs.antiIdleTimer) {
						profile.lastAntiIdle = timeNow;
						sendChat(getAntiIdle(), true);
					}
				}
			}

			if(bncsInputStream.available() > 0) {
				BNCSPacketReader pr = new BNCSPacketReader(bncsInputStream);
				BNetInputStream is = pr.getData();

				switch(pr.packetId) {
				case SID_NULL: {
					lastNullPacket = timeNow;
					BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_NULL);
					p.SendPacket(bncsOutputStream);
					break;
				}

				case SID_PING: {
					BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_PING);
					p.writeDWord(is.readDWord());
					p.SendPacket(bncsOutputStream);
					break;
				}

				case SID_NEWS_INFO: {
					int numEntries = is.readByte();
					// int lastLogon = is.readDWord();
					// int oldestNews = is.readDWord();
					// int newestNews = is.readDWord();;
					is.skip(12);

					for(int i = 0; i < numEntries; i++) {
						int timeStamp = is.readDWord();
						String news = is.readNTStringUTF8().trim();
						if(timeStamp == 0)	// MOTD
							dispatchRecieveInfo(news);
					}

					break;
				}

				case SID_GETCHANNELLIST: {
					recieveGetChannelList(is);
					break;
				}

				case SID_CHATEVENT: {
					BNCSChatEventId eid = BNCSChatEventId.values()[is.readDWord()];
					int flags = is.readDWord();
					int ping = is.readDWord();
					is.skip(12);
				// is.readDWord(); // IP Address (defunct)
				// is.readDWord(); // Account number (defunct)
				// is.readDWord(); // Registration authority (defunct)
					String username = is.readNTString();
					String text = null;
					StatString statstr = null;

					switch(eid) {
					case EID_SHOWUSER:
					case EID_JOIN:
						statstr = is.readStatString();
						break;
					case EID_USERFLAGS:
						// Sometimes USERFLAGS contains a statstring; sometimes
						// it doesn't
						statstr = is.readStatString();
						if(statstr.toString().length() == 0)
							statstr = null;
						break;
					default:
						text = is.readNTString();
						break;
					}

					BNetUser user = null;
					switch(eid) {
					case EID_SHOWUSER:
					case EID_USERFLAGS:
					case EID_JOIN:
					case EID_LEAVE:
					case EID_TALK:
					case EID_EMOTE:
					case EID_WHISPERSENT:
					case EID_WHISPER:
						switch(productID) {
						case D2DV:
						case D2XP:
							int asterisk = username.indexOf('*');
							if(asterisk >= 0)
								username = username.substring(asterisk+1);
							break;
						}

						// Get a BNetUser object for the user
						if(myUser.equals(username))
							user = myUser;
						else
							user = getCreateBNetUser(username, myUser);

						// Set the flags, ping, statstr
						user.setFlags(flags);
						user.setPing(ping);
						if(statstr != null)
							user.setStatString(statstr);
						break;
					}

					switch(eid) {
					case EID_SHOWUSER:
					case EID_USERFLAGS:
						dispatchChannelUser(user);
						break;
					case EID_JOIN:
						dispatchChannelJoin(user);
						break;
					case EID_LEAVE:
						dispatchChannelLeave(user);
						break;
					case EID_TALK:
						dispatchRecieveChat(user, text);
						break;
					case EID_EMOTE:
						dispatchRecieveEmote(user, text);
						break;
					case EID_INFO:
						dispatchRecieveInfo(text);
						break;
					case EID_ERROR:
						dispatchRecieveError(text);
						break;
					case EID_CHANNEL:
						channelName = text;
						dispatchJoinedChannel(text, flags);
						dispatchTitleChanged();
						clearQueue();
						if(botnet != null)
							botnet.sendStatusUpdate();
						break;
					case EID_WHISPERSENT:
						dispatchWhisperSent(user, text);
						break;
					case EID_WHISPER:
						dispatchWhisperRecieved(user, text);
						break;
					case EID_CHANNELDOESNOTEXIST:
						dispatchRecieveError("Channel " + text + " does not exist; creating");
						sendJoinChannel2(text);
						break;
					case EID_CHANNELRESTRICTED:
						if(timeNow - lastEntryForced > 5000) {
							lastEntryForced = timeNow;
							dispatchRecieveError("Channel " + text + " is restricted; forcing entry");
							sendJoinChannel2(text);
						} else {
							dispatchRecieveError("Channel " + text + " is restricted");
						}
						break;
					case EID_CHANNELFULL:
						dispatchRecieveError("Channel " + text + " is full");
						break;
					default:
						dispatchRecieveError("Unknown SID_CHATEVENT " + eid + ": " + text);
						break;
					}

					break;
				}

				case SID_MESSAGEBOX: {
					/* int style = */ is.readDWord();
					String text = is.readNTStringUTF8();
					String caption = is.readNTStringUTF8();

					dispatchRecieveInfo("<" + caption + "> " + text);
					break;
				}

				case SID_FLOODDETECTED: {
					dispatchRecieveError("You have been disconnected for flooding.");
					disconnect(true);
					break;
				}

				/*
				 * .----------. | Realms | '----------'
				 */
				case SID_QUERYREALMS2: {
					/*
					 * (DWORD) Unknown0
					 * (DWORD) Number of Realms
					 *
					 * For each realm:
					 * (DWORD) UnknownR0
					 * (STRING) Realm Name
					 * (STRING) Realm Description
					 */
					is.readDWord();
					int numRealms = is.readDWord();
					String realms[] = new String[numRealms];
					for(int i = 0; i < numRealms; i++) {
						is.readDWord();
						realms[i] = is.readNTStringUTF8();
						is.readNTStringUTF8();
					}
					dispatchQueryRealms2(realms);
					break;
				}

				case SID_LOGONREALMEX: {
					/*
					 * (DWORD) Cookie
					 * (DWORD) Status
					 * (DWORD[2]) MCP Chunk 1
					 * (DWORD) IP
					 * (DWORD) Port
					 * (DWORD[12]) MCP Chunk 2
					 * (STRING) BNCS unique name
					 * (WORD) Unknown
					 */
					if(pr.packetLength < 12)
						throw new Exception("pr.packetLength < 12");
					else if(pr.packetLength == 12) {
						/* int cookie = */ is.readDWord();
						int status = is.readDWord();
						switch(status) {
						case 0x80000001:
							dispatchRecieveError("Realm is unavailable.");
							break;
						case 0x80000002:
							dispatchRecieveError("Realm logon failed");
							break;
						default:
							throw new Exception("Unknown status code 0x" + Integer.toHexString(status));
						}
					} else {
						int MCPChunk1[] = new int[4];
						MCPChunk1[0] = is.readDWord();
						MCPChunk1[1] = is.readDWord();
						MCPChunk1[2] = is.readDWord();
						MCPChunk1[3] = is.readDWord();
						int ip = is.readDWord();
						int port = is.readDWord();
						port = ((port & 0xFF00) >> 8) | ((port & 0x00FF) << 8);
						int MCPChunk2[] = new int[12];
						MCPChunk2[0] = is.readDWord();
						MCPChunk2[1] = is.readDWord();
						MCPChunk2[2] = is.readDWord();
						MCPChunk2[3] = is.readDWord();
						MCPChunk2[4] = is.readDWord();
						MCPChunk2[5] = is.readDWord();
						MCPChunk2[6] = is.readDWord();
						MCPChunk2[7] = is.readDWord();
						MCPChunk2[8] = is.readDWord();
						MCPChunk2[9] = is.readDWord();
						MCPChunk2[10] = is.readDWord();
						MCPChunk2[11] = is.readDWord();
						String uniqueName = is.readNTString();
						/* int unknown = */ is.readWord();
						dispatchLogonRealmEx(MCPChunk1, ip, port, MCPChunk2, uniqueName);
					}

					break;
				}

				/*
				 * .-----------. | Profile | '-----------'
				 */

				case SID_READUSERDATA: {
					/*
					 * (DWORD) Number of accounts
					 * (DWORD) Number of keys
					 * (DWORD) Request ID
					 * (STRING[]) Requested Key Values
					 */
					int numAccounts = is.readDWord();
					int numKeys = is.readDWord();
					@SuppressWarnings("unchecked")
					List<Object> keys = (List<Object>)CookieUtility.destroyCookie(is.readDWord());

					if(numAccounts != 1)
						throw new IllegalStateException("SID_READUSERDATA with numAccounts != 1");

					UserProfile up = new UserProfile((String)keys.remove(0));
					dispatchRecieveInfo("Profile for " + up.getUser());
					for(int i = 0; i < numKeys; i++) {
						String key = (String)keys.get(i);
						String value = is.readNTStringUTF8();
						if((key == null) || (key.length() == 0))
							continue;
						value = prettyProfileValue(key, value);

						if(value.length() != 0) {
							dispatchRecieveInfo(key + " = " + value);
						} else if(
							key.equals(UserProfile.PROFILE_DESCRIPTION) ||
							key.equals(UserProfile.PROFILE_LOCATION) ||
							key.equals(UserProfile.PROFILE_SEX)) {
							// Always report these keys
						} else {
							continue;
						}
						up.put(key, value);
					}

					if(GlobalSettings.enableGUI)
						new ProfileEditor(up, this);
					break;
				}

				/*
				 * .-----------. | Friends | '-----------'
				 */

				case SID_FRIENDSLIST: {
					/*
					 * (BYTE) Number of Entries
					 *
					 * For each member:
					 * (STRING) Account
					 * (BYTE) Status
					 * (BYTE) Location
					 * (DWORD) ProductID
					 * (STRING) Location name
					 */
					byte numEntries = is.readByte();
					FriendEntry[] entries = new FriendEntry[numEntries];

					for(int i = 0; i < numEntries; i++) {
						String uAccount = is.readNTString();
						byte uStatus = is.readByte();
						byte uLocation = is.readByte();
						int uProduct = is.readDWord();
						String uLocationName = is.readNTStringUTF8();

						entries[i] = new FriendEntry(uAccount, uStatus, uLocation, uProduct, uLocationName);
					}

					dispatchFriendsList(entries);
					break;
				}

				case SID_FRIENDSUPDATE: {
					/*
					 * (BYTE) Entry number
					 * (BYTE) Friend Location
					 * (BYTE) Friend Status
					 * (DWORD) ProductID
					 * (STRING) Location
					 */
					byte fEntry = is.readByte();
					byte fLocation = is.readByte();
					byte fStatus = is.readByte();
					int fProduct = is.readDWord();
					String fLocationName = is.readNTStringUTF8();

					dispatchFriendsUpdate(new FriendEntry(fEntry, fStatus, fLocation, fProduct, fLocationName));
					break;
				}

				case SID_FRIENDSADD: {
					/*
					 * (STRING) Account
					 * (BYTE) Friend Type
					 * (BYTE) Friend Status
					 * (DWORD) ProductID
					 * (STRING) Location
					 */
					String fAccount = is.readNTString();
					byte fLocation = is.readByte();
					byte fStatus = is.readByte();
					int fProduct = is.readDWord();
					String fLocationName = is.readNTStringUTF8();

					dispatchFriendsAdd(new FriendEntry(fAccount, fStatus, fLocation, fProduct, fLocationName));
					break;
				}

				case SID_FRIENDSREMOVE: {
					/*
					 * (BYTE) Entry Number
					 */
					byte entry = is.readByte();

					dispatchFriendsRemove(entry);
					break;
				}

				case SID_FRIENDSPOSITION: {
					/*
					 * (BYTE) Old Position
					 * (BYTE) New Position
					 */
					byte oldPosition = is.readByte();
					byte newPosition = is.readByte();

					dispatchFriendsPosition(oldPosition, newPosition);
					break;
				}

				/*
				 * .--------. | Clan | '--------'
				 */

				case SID_CLANINFO: {
					recvClanInfo(is);
					break;
				}

				case SID_CLANFINDCANDIDATES: {
					Object cookie = CookieUtility.destroyCookie(is.readDWord());
					byte status = is.readByte();
					byte numCandidates = is.readByte();
					List<String> candidates = new ArrayList<String>(numCandidates);
					for(int i = 0 ; i < numCandidates; i++)
						candidates.add(is.readNTString());

					String result;
					switch(status) {
					case 0x00:
						result = "Success";
						break;
					case 0x01:
						result = "Clan tag already taken";
						break;
					case 0x08:
						result = "Already in a clan";
						break;
					case 0x0a:
						result = "Invalid clan tag";
						break;
					default:
						result = "Unknown response 0x" + Integer.toHexString(status);
						break;
					}

					dispatchClanFindCandidates(cookie, result, candidates);
					break;
				}
				// SID_CLANINVITEMULTIPLE
				case SID_CLANCREATIONINVITATION: {
					int cookie = is.readDWord();
					int clanTag = is.readDWord();
					String clanName = is.readNTString();
					String inviter = is.readNTString();

					ClanCreationInvitationCookie c = new ClanCreationInvitationCookie(this, cookie, clanTag, clanName, inviter);
					dispatchClanCreationInvitation(c);
					break;
				}
				// SID_CLANDISBAND
				// SID_CLANMAKECHIEFTAIN

				// SID_CLANQUITNOTIFY
				case SID_CLANINVITATION: {
					Object cookie = CookieUtility.destroyCookie(is.readDWord());
					byte status = is.readByte();

					String result;
					switch(status) {
					case 0x00:
						result = "Invitation accepted";
						break;
					case 0x04:
						result = "Invitation declined";
						break;
					case 0x05:
						result = "Failed to invite user";
						break;
					case 0x09:
						result = "Clan is full";
						break;
					default:
						result = "Unknown response 0x" + Integer.toHexString(status);
						break;
					}

					if(cookie instanceof CommandResponseCookie)
						((CommandResponseCookie)cookie).sendChat(this, result);
					else
						Out.info(getClass(), result);

					break;
				}

				// SID_CLANREMOVEMEMBER

				case SID_CLANINVITATIONRESPONSE: {
					/*
					 * (DWORD) Cookie
					 * (DWORD) Clan tag
					 * (STRING) Clan name
					 * (STRING) Inviter
					 */
					int cookie = is.readDWord();
					int clanTag = is.readDWord();
					String clanName = is.readNTString();
					String inviter = is.readNTString();

					ClanInvitationCookie c = new ClanInvitationCookie(this, cookie, clanTag, clanName, inviter);
					dispatchClanInvitation(c);
					break;
				}

				case SID_CLANRANKCHANGE: {
					int cookie = is.readDWord();
					byte status = is.readByte();

					Object obj = CookieUtility.destroyCookie(cookie);
					String statusCode = null;
					switch(status) {
					case ClanStatusIDs.CLANSTATUS_SUCCESS:
						statusCode = "Successfully changed rank";
						break;
					case 0x01:
						statusCode = "Failed to change rank";
						break;
					case ClanStatusIDs.CLANSTATUS_TOO_SOON:
						statusCode = "Cannot change user'socket rank yet";
						break;
					case ClanStatusIDs.CLANSTATUS_NOT_AUTHORIZED:
						statusCode = "Not authorized to change user rank*";
						break;
					case 0x08:
						statusCode = "Not allowed to change user rank**";
						break;
					default:	statusCode = "Unknown ClanStatusID 0x" + Integer.toHexString(status);
					}

					dispatchRecieveInfo(statusCode + "\n" + obj.toString());
					// TODO: clanRankChange(obj, status)

					break;
				}

				case SID_CLANMOTD: {
					/*
					 * (DWORD) Cookie
					 * (DWORD) Unknown (0)
					 * (STRING) MOTD
					 */
					int cookieId = is.readDWord();
					is.readDWord();
					String text = is.readNTStringUTF8();

					Object cookie = CookieUtility.destroyCookie(cookieId);
					dispatchClanMOTD(cookie, text);
					break;
				}

				case SID_CLANMEMBERLIST: {
					/*
					 * (DWORD) Cookie
					 * (BYTE) Number of Members
					 *
					 * For each member:
					 * (STRING) Username
					 * (BYTE) Rank
					 * (BYTE) Online Status
					 * (STRING) Location
					 */
					is.readDWord();
					byte numMembers = is.readByte();
					ClanMember[] members = new ClanMember[numMembers];

					for(int i = 0; i < numMembers; i++) {
						String uName = is.readNTString();
						byte uRank = is.readByte();
						byte uOnline = is.readByte();
						String uLocation = is.readNTStringUTF8();

						members[i] = new ClanMember(uName, uRank, uOnline, uLocation);
					}

					dispatchClanMemberList(members);
					break;
				}

				case SID_CLANMEMBERREMOVED: {
					/*
					 * (STRING) Username
					 */
					String username = is.readNTString();
					dispatchClanMemberRemoved(username);
					break;
				}

				case SID_CLANMEMBERSTATUSCHANGE: {
					/*
					 * (STRING) Username
					 * (BYTE) Rank
					 * (BYTE) Status
					 * (STRING) Location
					 */
					String username = is.readNTString();
					byte rank = is.readByte();
					byte status = is.readByte();
					String location = is.readNTStringUTF8();

					dispatchClanMemberStatusChange(new ClanMember(username, rank, status, location));
					break;
				}

				case SID_CLANMEMBERRANKCHANGE: {
					/*
					 * (BYTE) Old rank
					 * (BYTE) New rank
					 * (STRING) Clan member who changed your rank
					 */
					byte oldRank = is.readByte();
					byte newRank = is.readByte();
					String user = is.readNTString();
					dispatchRecieveInfo("Rank changed from " + ClanRankIDs.ClanRank[oldRank] + " to " + ClanRankIDs.ClanRank[newRank] + " by " + user);
					dispatchClanMemberRankChange(oldRank, newRank, user);
					break;
				}

				// TODO: SID_CLANMEMBERINFORMATION

				case SID_WARDEN: {
					if(warden != null)
						try {
							warden.processWardenPacket(is.readFully(), bncsOutputStream);
							break;
						} catch(Exception e) {}
					Out.error(getClass(), "Recieved SID_WARDEN but warden model was not initialized\n" + HexDump.hexDump(pr.data));
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

	/**
	 * Recieve SID_GETCHANNELLIST
	 *
	 * @param is
	 * @throws IOException
	 */
	private void recieveGetChannelList(BNetInputStream is) throws IOException {
		String channelList = null;
		do {
			String s = is.readNTString();
			if (s.length() == 0)
				break;
			if (channelList == null)
				channelList = s;
			else
				channelList += ", " + s;
		} while (true);

		if (GlobalSettings.displayBattleNetChannels)
			dispatchRecieveInfo("Channels: " + channelList + ".");
	}

	@Override
	public boolean isOp() {
		return (myUser.getFlags() & 0x02) == 0x02;
	}

	/**
	 * Send SID_SETEMAIL
	 *
	 * @throws Exception
	 */
	private void sendSetEmail() throws Exception {
		String email = GlobalSettings.email;
		if (email == null)
			return;
		if (email.length() == 0)
			return;
		dispatchRecieveInfo("Register email address: " + email);
		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_SETEMAIL);
		p.writeNTString(email);
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_LEAVECHAT
	 */
	@Override
	public void sendLeaveChat() throws Exception {
		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_LEAVECHAT);
		p.SendPacket(bncsOutputStream);
		channelName = null;
		dispatchJoinedChannel(null, 0);
	}

	/**
	 * Send SID_JOINCHANNEL
	 */
	@Override
	public void sendJoinChannel(String channel) throws Exception {
		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_JOINCHANNEL);
		p.writeDWord(0); // nocreate join
		p.writeNTString(channel);
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_JOINCHANNEL with create channel flag
	 */
	@Override
	public void sendJoinChannel2(String channel) throws Exception {
		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_JOINCHANNEL);
		p.writeDWord(2); // force join
		p.writeNTString(channel);
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_CHATCOMMAND
	 */
	@Override
	public void sendChatCommand(String text) {
		if ((botnet != null) && text.startsWith("/botnet ")) {
			botnet.processCommand(text.substring(8));
			return;
		}

		super.sendChatCommand(text);

		switch (productID) {
		case D2DV:
		case D2XP:
			if ((text.length() > 1) && (text.charAt(0) == '/')) {
				String cmd = text.substring(1);
				int i = cmd.indexOf(' ');
				if (i != -1) {
					String theRest = cmd.substring(i + 1);
					cmd = cmd.substring(0, i);

					if (cmd.equals("w") || cmd.equals("m")
							|| cmd.equals("whois") || cmd.equals("ignore")
							|| cmd.equals("squelch") || cmd.equals("unignore")
							|| cmd.equals("unsquelch")) {
						if (theRest.charAt(0) != '*')
							text = '/' + cmd + " *" + theRest;
					}

				}
			}
			break;
		}

		// Write the packet
		try {
			BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_CHATCOMMAND);
			p.writeNTString(text);
			p.SendPacket(bncsOutputStream);
		} catch (IOException e) {
			Out.exception(e);
			disconnect(true);
			return;
		}

		if (text.charAt(0) != '/')
			dispatchRecieveChat(myUser, text);
	}

	/**
	 * Require WAR3 or W3XP
	 * @throws UnsupportedFeatureException
	 */
	private void requireW3() throws UnsupportedFeatureException {
		switch (productID) {
		case WAR3:
		case W3XP:
			return;
		}
		throw new UnsupportedFeatureException(
				"Only WAR3/W3XP support this feature");
	}

	/**
	 * Require the user be on W3 and in or out of a clan
	 * @param inClan if true, require the use to be in a clan; false for out of clan
	 */
	private void requireInClan(boolean inClan) throws UnsupportedFeatureException, IllegalStateException {
		requireW3();

		if(inClan) {
			// The user must be in a clan
			if(myClan == null)
				throw new IllegalStateException("You are not in a clan");
		} else {
			// The user must not be in a clan
			if(myClan != null)
				throw new IllegalStateException("You are already in a clan");
		}
	}

	/**
	 * Require D2DV or D2XP
	 * @throws UnsupportedFeatureException
	 */
	private void requireD2() throws UnsupportedFeatureException {
		switch (productID) {
		case D2DV:
		case D2XP:
			return;
		}
		throw new UnsupportedFeatureException(
				"Only D2DV/D2XP support this feature");
	}

	/**
	 * Send SID_CLANFINDCANDIDATES
	 */
	@Override
	public void sendClanFindCandidates(Object cookie, int clanTag) throws Exception {
		requireInClan(false);

		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_CLANFINDCANDIDATES);
		p.writeDWord(CookieUtility.createCookie(cookie)); // Cookie
		p.writeDWord(clanTag); // Clan Tag
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_CLANINVITEMULTIPLE
	 * Use this method to create a clan; invite 9 users
	 * Invitees will reiceve SID_CLANCREATIONINVITATION
	 */
	@Override
	public void sendClanInviteMultiple(Object cookie, String clanName, int clanTag, List<String> invitees) throws Exception {
		requireInClan(false);

		if(invitees.size() != 9)
			throw new IllegalStateException("You should invite exactly 9 people");

		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_CLANINVITEMULTIPLE);
		p.writeDWord(CookieUtility.createCookie(cookie)); // Cookie
		p.writeNTString(clanName); // Clan name
		p.writeDWord(clanTag); // Clan tag
		p.writeByte(invitees.size()); // Number of users to invite
		for(String user : invitees)
			p.writeNTString(user); // Usernames to invite
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_CLANCREATIONINVITATION
	 * Accept or decline an invitation to create a clan
	 * @param response 0x04 = Decline, 0x06 = Accept
	 * TODO Verify these response codes are correct
	 */
	@Override
	public void sendClanCreationInvitation(int cookie, int clanTag, String inviter, int response) throws Exception {
		requireW3();

		switch(response) {
		case 4: // decline
		case 6: // accept
			break;
		default:
			throw new IllegalStateException("Unknown response code 0x" + Integer.toHexString(response));
		}

		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_CLANINVITATIONRESPONSE);
		p.writeDWord(cookie);
		p.writeDWord(clanTag);
		p.writeNTString(inviter);
		p.writeByte(response);
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_CLANINVITATION
	 */
	@Override
	public void sendClanInvitation(Object cookie, String user) throws Exception {
		requireInClan(true);
		if (myClanRank < 3)
			throw new IllegalStateException("Must be " + clanRanks[3] + " or "
					+ clanRanks[4] + " to invite");

		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_CLANINVITATION);
		p.writeDWord(CookieUtility.createCookie(cookie)); // Cookie
		p.writeNTString(user); // Username
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_CLANRANKCHANGE
	 */
	@Override
	public void sendClanRankChange(Object cookie, String user, int newRank)
			throws Exception {
		requireW3();

		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_CLANRANKCHANGE);
		p.writeDWord(CookieUtility.createCookie(cookie)); // Cookie
		p.writeNTString(user); // Username
		p.writeByte(newRank); // New rank
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_CLANMOTD
	 */
	@Override
	public void sendClanMOTD(Object cookie) throws Exception {
		requireW3();

		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_CLANMOTD);
		p.writeDWord(CookieUtility.createCookie(cookie));
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_CLANINVITATIONRESPONSE
	 * @param response 0x04 = Decline, 0x06 = Accept
	 */
	public void sendClanInvitationResponse(int cookie, int clanTag, String inviter, int response) throws Exception {
		requireW3();

		switch(response) {
		case 4: // decline
		case 6: // accept
			break;
		default:
			throw new IllegalStateException("Unknown response code 0x" + Integer.toHexString(response));
		}

		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_CLANINVITATIONRESPONSE);
		p.writeDWord(cookie);
		p.writeDWord(clanTag);
		p.writeNTString(inviter);
		p.writeByte(response);
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_CLANSETMOTD
	 */
	@Override
	public void sendClanSetMOTD(String text) throws Exception {
		requireW3();

		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_CLANSETMOTD);
		p.writeDWord(0); // Cookie
		p.writeNTString(text);
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_QUERYREALMS2
	 */
	@Override
	public void sendQueryRealms2() throws Exception {
		requireD2();

		/*
		 * (DWORD) Unused (0)
		 * (DWORD) Unused (0)
		 * (STRING) Unknown (empty)
		 */
		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_QUERYREALMS2);
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_LOGONREALMEX
	 */
	@Override
	public void sendLogonRealmEx(String realmTitle) throws Exception {
		requireD2();

		/*
		 * (DWORD) Client key
		 * (DWORD[5]) Hashed realm password
		 * (STRING) Realm title
		 */
		int[] hash = DoubleHash
				.doubleHash("password", clientToken, serverToken);

		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_LOGONREALMEX);
		p.writeDWord(clientToken);
		p.writeDWord(hash[0]);
		p.writeDWord(hash[1]);
		p.writeDWord(hash[2]);
		p.writeDWord(hash[3]);
		p.writeDWord(hash[4]);
		p.writeNTString(realmTitle);
		p.SendPacket(bncsOutputStream);
	}

	private String prettyProfileValue(String key, String value) {
		if (UserProfile.SYSTEM_ACCOUNT_CREATED.equals(key)
				|| UserProfile.SYSTEM_LAST_LOGON.equals(key)
				|| UserProfile.SYSTEM_LAST_LOGOFF.equals(key)) {
			String parts[] = value.split(" ", 2);
			long time = Long.parseLong(parts[0]);
			time <<= 32;
			time += Long.parseLong(parts[1]);

			return TimeFormatter.fileTime(time).toString();
		} else if (UserProfile.SYSTEM_TIME_LOGGED.equals(key)) {
			long time = Long.parseLong(value);
			time *= 1000;
			return TimeFormatter.formatTime(time);
		}

		return value;
	}

	/**
	 * Send SID_READUSERDATA
	 */
	@Override
	public void sendReadUserData(String user) throws Exception {
		/*
		 * (DWORD) Number of Accounts
		 * (DWORD) Number of Keys
		 * (DWORD) Request ID
		 * (STRING[]) Requested Accounts
		 * (STRING[]) Requested Keys
		 */
		List<String> keys = new ArrayList<String>(7);
		keys.add(user);
		keys.add(UserProfile.PROFILE_SEX);
		// keys.add(UserProfile.PROFILE_AGE);
		keys.add(UserProfile.PROFILE_LOCATION);
		keys.add(UserProfile.PROFILE_DESCRIPTION);
		keys.add(UserProfile.PROFILE_ + "dbkey1");
		keys.add(UserProfile.PROFILE_ + "dbkey2");
		if (myUser.equals(user)) {
			keys.add(UserProfile.SYSTEM_ACCOUNT_CREATED);
			keys.add(UserProfile.SYSTEM_LAST_LOGON);
			keys.add(UserProfile.SYSTEM_LAST_LOGOFF);
			keys.add(UserProfile.SYSTEM_TIME_LOGGED);
			keys.add(UserProfile.SYSTEM_USERNAME);
		}

		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_READUSERDATA);
		p.writeDWord(1);
		p.writeDWord(keys.size() - 1);
		p.writeDWord(CookieUtility.createCookie(keys));
		// The user isn't actually a key; since it's at the top of the list this
		// works anyways
		for (String key : keys)
			p.writeNTString(key);
		p.SendPacket(bncsOutputStream);
	}

	/**
	 * Send SID_WRITEUSERDATA
	 */
	@Override
	public void sendWriteUserData(UserProfile profile) throws Exception {
		/*
		 * (DWORD) Number of accounts
		 * (DWORD) Number of keys
		 * (STRING[]) Accounts to update
		 * (STRING[]) Keys to update
		 * (STRING[]) New values
		 */
		if (!myUser.equals(profile.getUser()))
			throw new Exception("You may only write your own profile!");

		String user = myUser.getShortLogonName();
		int i = user.lastIndexOf('@');
		if (i != -1)
			user = user.substring(0, i);
		i = user.lastIndexOf('#');
		if (i != -1)
			user = user.substring(0, i);

		List<String> profileKeys = profile.keySetProfile();

		BNCSPacket p = new BNCSPacket(BNCSPacketId.SID_WRITEUSERDATA);
		p.writeDWord(1);
		p.writeDWord(profileKeys.size());
		p.writeNTString(user);
		for (String key : profileKeys)
			p.writeNTString(key.toString());
		for (String key : profileKeys)
			p.writeNTString(profile.get(key));
		p.SendPacket(bncsOutputStream);
	}

	@Override
	public String toString() {
		if (myUser != null) {
			String out = new String();

			if(myClan != null) {
				out += "Clan ";
				out += HexDump.DWordToPretty(myClan);
				out += " ";
			}

			if(myClanRank != null) {
				out += clanRanks[myClanRank];
				out += " ";
			}

			out += myUser.getShortLogonName();

			if (channelName != null)
				out += " - [ #" + channelName + " ]";

			return out;
		}

		return toShortString();
	}

	@Override
	public ProductIDs getProductID() {
		return productID;
	}

	@Override
	public void dispose() {
		super.dispose();
		if (botnet != null)
			botnet.dispose();
	}

	// Realms

	protected void dispatchQueryRealms2(String[] realms) {
		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.queryRealms2(this, realms);
		}
	}

	protected void dispatchLogonRealmEx(int[] MCPChunk1, int ip, int port,
			int[] MCPChunk2, String uniqueName) {
		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.logonRealmEx(this, MCPChunk1, ip, port, MCPChunk2,
						uniqueName);
		}
	}

	// Friends

	protected void dispatchFriendsList(FriendEntry[] entries) {
		if (!isPrimaryConnection())
			return;

		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.friendsList(this, entries);
		}
	}

	protected void dispatchFriendsUpdate(FriendEntry friend) {
		if (!isPrimaryConnection())
			return;

		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.friendsUpdate(this, friend);
		}
	}

	protected void dispatchFriendsAdd(FriendEntry friend) {
		if (!isPrimaryConnection())
			return;

		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.friendsAdd(this, friend);
		}
	}

	protected void dispatchFriendsRemove(byte entry) {
		if (!isPrimaryConnection())
			return;

		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.friendsRemove(this, entry);
		}
	}

	protected void dispatchFriendsPosition(byte oldPosition, byte newPosition) {
		if (!isPrimaryConnection())
			return;

		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.friendsPosition(this, oldPosition, newPosition);
		}
	}

	// Clan

	protected void dispatchClanMOTD(Object cookie, String text) {
		if (!isPrimaryConnection())
			return;

		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.clanMOTD(this, cookie, text);
		}
	}

	protected void dispatchClanMemberList(ClanMember[] members) {
		if (!isPrimaryConnection())
			return;

		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.clanMemberList(this, members);
		}
	}

	protected void dispatchClanMemberRemoved(String username) {
		if (!isPrimaryConnection())
			return;

		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.clanMemberRemoved(this, username);
		}
	}

	protected void dispatchClanMemberRankChange(byte oldRank, byte newRank,
			String user) {
		if (!isPrimaryConnection())
			return;

		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.clanMemberRankChange(this, oldRank, newRank, user);
		}
	}

	protected void dispatchClanMemberStatusChange(ClanMember member) {
		if (!isPrimaryConnection())
			return;

		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.clanMemberStatusChange(this, member);
		}
	}

	protected void dispatchClanFindCandidates(Object cookie, String result, List<String> candidates) {
		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.clanFindCandidates(this, cookie, result, candidates);
		}
	}

	public void dispatchClanCreationInvitation(ClanCreationInvitationCookie c) {
		lastAcceptDecline = c;
		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.clanCreationInvitation(this, c);
		}
	}

	public void dispatchClanInvitation(ClanInvitationCookie c) {
		lastAcceptDecline = c;
		synchronized (eventHandlers) {
			for (EventHandler eh : eventHandlers)
				eh.clanInvitation(this, c);
		}
	}
}
