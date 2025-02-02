package handling.handlers.login;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import client.MapleCharacter;
import client.MapleClient;
import client.inventory.Item;
import client.inventory.MapleInventory;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import handling.PacketHandler;
import handling.RecvPacketOpcode;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.channel.handler.PlayersHandler;
import handling.login.LoginServer;
import handling.world.World;
import handling.world.guild.MapleGuild;
import server.MapleInventoryManipulator;
import tools.Triple;
import tools.data.LittleEndianAccessor;
import tools.packet.CField;
import tools.packet.CWvsContext;
import tools.packet.CWvsContext.GuildPacket;
import tools.packet.JobPacket.AvengerPacket;

public class PlayerLoggedInHandler {

	@PacketHandler(opcode = RecvPacketOpcode.PLAYER_LOGGEDIN)
	public static void handle(MapleClient c, LittleEndianAccessor lea) throws SQLException {
		lea.skip(4); // Unknown
		int playerid = lea.readInt();
		MapleCharacter player = CashShopServer.getPlayerStorage().getCharacterById(playerid); // Isnt this WorldServer ?
		
		for (ChannelServer cserv : ChannelServer.getAllInstances()) {
			player = cserv.getPlayerStorage().getPendingCharacter(playerid);
			if (player != null) {
				c.setChannel(cserv.getChannel());
				break;
			}
		}
		 if (player == null) {
			Triple<String, String, Integer> ip = LoginServer.getLoginAuth(playerid);
			String theIpAddress = c.getSessionIPAddress();
			if (ip == null|| !theIpAddress.substring(theIpAddress.indexOf('/') + 1, theIpAddress.length()).equals(ip.left)) {
				System.out.println("Player wasn't found in the storage.");
				c.getSession().close();
				return;
			}
			LoginServer.putLoginAuth(playerid, ip.left, ip.mid, ip.right);
			c.setTempIP(ip.mid);
			c.setChannel(ip.right);
			player = MapleCharacter.loadCharFromDB(playerid, c, true);
		} else {
			player = MapleCharacter.loadCharFromDB(playerid, c, true); // Change Channel Port
		}
		 		 
		c.setPlayer(player);
		c.setAccID(player.getAccountID());
		
		int state = c.getLoginState();
		boolean allowLogin = false;
		
		if (state == MapleClient.LOGIN_SERVER_TRANSITION || state == MapleClient.CHANGE_CHANNEL || state == MapleClient.LOGIN_NOTLOGGEDIN) { 
			allowLogin = !World.isCharacterListConnected(c.loadCharacterNames(c.getWorld()));
		}
		
		if (!allowLogin) {
			System.out.println("Error allowLogin == FALSE..");
			c.setPlayer(null);
			c.getSession().close();
			return;
		}
		
		c.updateLoginState(MapleClient.LOGIN_LOGGEDIN, c.getSessionIPAddress());
		c.getChannelServer().addPlayer(player);

		// player.giveCoolDowns(PlayerBuffStorage.getCooldownsFromStorage(player.getId()));
		// player.silentGiveBuffs(PlayerBuffStorage.getBuffsFromStorage(player.getId()));
		// player.giveSilentDebuff(PlayerBuffStorage.getDiseaseFromStorage(player.getId()));
		
		c.getSession().write(CWvsContext.updateCrowns(new int[] { -1, -1, -1, -1, -1 }));
		c.getSession().write(CField.getWarpToMap(player, null, 0, true));
		
		PlayersHandler.calcHyperSkillPointCount(c);
		// c.getSession().write(CSPacket.enableCSUse());
		c.getSession().write(CWvsContext.updateLinkSkill(c.getPlayer().getSkills(), true, false, false));
		// player.getStolenSkills();
		// c.getSession().write(JobPacket.addStolenSkill());

		player.getMap().addPlayer(player);

		try {
			/*// Start of buddylist
			final int buddyIds[] = player.getBuddylist().getBuddyIds();
			World.Buddy.loggedOn(player.getName(), player.getId(), c.getChannel(), buddyIds);
			if (player.getParty() != null) {
				final MapleParty party = player.getParty();
				World.Party.updateParty(party.getId(), PartyOperation.LOG_ONOFF, new MaplePartyCharacter(player));

				if (party != null && party.getExpeditionId() > 0) {
					final MapleExpedition me = World.Party.getExped(party.getExpeditionId());
					if (me != null) {
						c.getSession().write(CWvsContext.ExpeditionPacket.expeditionStatus(me, false, true));
					}
				}
			}
			final CharacterIdChannelPair[] onlineBuddies = World.Find.multiBuddyFind(player.getId(), buddyIds);
			for (CharacterIdChannelPair onlineBuddy : onlineBuddies) {
				player.getBuddylist().get(onlineBuddy.getCharacterId()).setChannel(onlineBuddy.getChannel());
			}
			// c.getSession().write(BuddylistPacket.updateBuddylist(player.getBuddylist().getBuddies()));

			// Start of Messenger
			final MapleMessenger messenger = player.getMessenger();
			if (messenger != null) {
				World.Messenger.silentJoinMessenger(messenger.getId(), new MapleMessengerCharacter(c.getPlayer()));
				World.Messenger.updateMessenger(messenger.getId(), c.getPlayer().getName(), c.getChannel());
			}*/

			// Start of Guild and alliance
			if (player.getGuildId() > 0) {
				World.Guild.setGuildMemberOnline(player.getMGC(), true, c.getChannel());
				c.getSession().write(GuildPacket.showGuildInfo(player));
				final MapleGuild gs = World.Guild.getGuild(player.getGuildId());
				if (gs != null) {
					final List<byte[]> packetList = World.Alliance.getAllianceInfo(gs.getAllianceId(), true);
					if (packetList != null) {
						for (byte[] pack : packetList) {
							if (pack != null) {
								c.getSession().write(pack);
							}
						}
					}
				} else { // guild not found, change guild id
					player.setGuildId(0);
					player.setGuildRank((byte) 5);
					player.setAllianceRank((byte) 5);
					player.saveGuildStatus();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		player.getClient().getSession().write(CWvsContext.broadcastMsg(c.getChannelServer().getServerMessage()));
		player.sendMacros();
		player.sendKeymaps();
		
		
		// player.showNote();
		// player.sendImp();
		// player.updatePartyMemberHP();
		// player.startFairySchedule(false);
		// player.baseSkills(); // fix people who've lost skills.
		if (GameConstants.isZero(player.getJob())) {
			c.getSession().write(CWvsContext.updateLinkSkill(c.getPlayer().getSkills(), true, false, false));
		}	
		
		// player.updatePetAuto();
		// player.expirationTask(true, transfer == null);
		// c.getSession().write(CWvsContext.updateMaplePoint(player.getCSPoints(2)));
		
		if (player.getJob() == 132) { // Dark Knight
			player.checkBerserk();
		}
		if (GameConstants.isXenon(player.getJob())) {
			player.startXenonSupply();
		}
		if (GameConstants.isDemonAvenger(player.getJob())) {
			c.getSession().write(AvengerPacket.giveAvengerHpBuff(player.getStat().getHp()));
		}
		// player.spawnClones();
		// player.spawnSavedPets();
		if (player.getStat().equippedSummon > 0) {
			// SkillFactory.getSkill(player.getStat().equippedSummon + (GameConstants.getBeginnerJob(player.getJob()) * 1000)).getEffect(1).applyTo(player);
		}
		// c.getSession().write(CWvsContext.getFamiliarInfo(player));
		MapleInventory equipped = player.getInventory(MapleInventoryType.EQUIPPED);
		List<Short> slots = new ArrayList<>();
		for (Item item : equipped.newList()) {
			slots.add(item.getPosition());
		}
		for (short slot : slots) {
			if (GameConstants.isIllegalItem(equipped.getItem(slot).getItemId())) {
				MapleInventoryManipulator.removeFromSlot(player.getClient(), MapleInventoryType.EQUIPPED, slot, (short) 1, false);
			}
		}
	}
}