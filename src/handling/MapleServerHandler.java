/*
` This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 ~ 2012 Patrick Huy <patrick.huy@frz.cc> 
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License version 3
 as published by the Free Software Foundation. You may not use, modify
 or distribute this program under any other version of the
 GNU Affero General Public License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package handling;

import java.io.IOException;

import client.MapleCharacter;
import client.MapleClient;
import client.inventory.MaplePet;
import client.inventory.PetDataFactory;
import constants.ServerConstants;
import handling.cashshop.handler.CashShopOperation;
import handling.channel.handler.AllianceHandler;
import handling.channel.handler.BBSHandler;
import handling.channel.handler.BuddyListHandler;
import handling.channel.handler.ChatHandler;
import handling.channel.handler.GuildHandler;
import handling.channel.handler.HiredMerchantHandler;
import handling.channel.handler.InventoryHandler;
import handling.channel.handler.ItemMakerHandler;
import handling.channel.handler.MobHandler;
import handling.channel.handler.MonsterCarnivalHandler;
import handling.channel.handler.NPCHandler;
import handling.channel.handler.PackageHandler;
import handling.channel.handler.PartyHandler;
import handling.channel.handler.PetHandler;
import handling.channel.handler.PlayerHandler;
import handling.channel.handler.PlayerInteractionHandler;
import handling.channel.handler.PlayersHandler;
import handling.channel.handler.StatsHandling;
import handling.channel.handler.SummonHandler;
import handling.channel.handler.UserInterfaceHandler;
import handling.login.LoginServer;
import handling.login.handler.CharLoginHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.netty.MaplePacketDecoder;
import tools.MapleAESOFB;
import tools.data.ByteArrayByteStream;
import tools.data.LittleEndianAccessor;
import tools.packet.CField;
import tools.packet.CSPacket;
import tools.packet.LoginPacket;

public class MapleServerHandler extends ChannelInboundHandlerAdapter {

	private final byte[] aesKey = new byte[] { (byte) 0xB3, 0x00, 0x00, 0x00, (byte) 0x2C, 0x00, 0x00, 0x00, (byte) 0x96,
			0x00, 0x00, 0x00, (byte) 0x65, 0x00, 0x00, 0x00, (byte) 0x99, 0x00, 0x00, 0x00, (byte) 0x32, 0x00, 0x00,
			0x00, (byte) 0xD0, 0x00, 0x00, 0x00, (byte) 0x41, 0x00, 0x00, 0x00 };

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		if (cause instanceof IOException || cause instanceof ClassCastException) {
			return;
		}
		cause.printStackTrace();
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {

		// Start of IP checking
		final String address = ctx.channel().remoteAddress().toString().split(":")[0];

		if (LoginServer.isShutdown()) {
			ctx.channel().close();
			return;
		}
		byte ivRecv[] = { 70, 114, 122, 82 };
		byte ivSend[] = { 82, 48, 120, 115 };
		ivRecv[3] = (byte) (Math.random() * 255);
		ivSend[3] = (byte) (Math.random() * 255);

		MapleClient client = new MapleClient(
				new MapleAESOFB(aesKey, ivSend, (short) (0xFFFF - ServerConstants.MAPLE_VERSION)),
				new MapleAESOFB(aesKey, ivRecv, ServerConstants.MAPLE_VERSION), ctx.channel());
		client.setChannel(-1);

		MaplePacketDecoder.DecoderState decoderState = new MaplePacketDecoder.DecoderState();
		ctx.channel().attr(MaplePacketDecoder.getDecoderStateKey()).set(decoderState);

		ctx.channel().writeAndFlush(LoginPacket.getHello(ServerConstants.MAPLE_VERSION, ivSend, ivRecv));
		ctx.channel().attr(MapleClient.CLIENT_KEY).set(client);

		System.out.println("Connection Established " + address);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {

		MapleClient client = (MapleClient) ctx.channel().attr(MapleClient.CLIENT_KEY).get();
		if (client != null) {
			try {
				client.disconnect(true, false);
			} catch (Throwable t) {
				t.printStackTrace();
			} finally {
				ctx.channel().close();
				ctx.channel().attr(MapleClient.CLIENT_KEY).remove();
			}
		}
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		MapleClient client = (MapleClient) ctx.channel().attr(MapleClient.CLIENT_KEY).get();
		if (client != null) {
			// client.sendPing();
		}
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object message) { // handles the object which a current
																			// connection has sent to the server
		byte[] content = (byte[]) message;
		LittleEndianAccessor lea = new LittleEndianAccessor(new ByteArrayByteStream(content));
		short packetId = lea.readShort();
		MapleClient client = ctx.channel().attr(MapleClient.CLIENT_KEY).get();
		try {
			boolean handled = OpcodeManager.handle(client, packetId, lea);
			if (handled) {
				return;
			}
			RecvPacketOpcode recv = RecvPacketOpcode.getByValue(packetId);
			if (recv == null) {
				return;
			}
			handlePacket(recv, lea, client);
		} catch (final Throwable t) {
			t.printStackTrace();
		}
	}
	
	public static void handlePacket(final RecvPacketOpcode header, final LittleEndianAccessor lea, final MapleClient c)
			throws Exception {
		switch (header) {
		case CLIENT_HELLO:
			// [08] - locale
			// [8E 00] - version
			// [02 00] - patch version
			break;
		case CLIENT_START:
			lea.skip(1); // locale
			lea.skip(2); // version
			lea.skip(1); // patch
			lea.skip(2); // ?
			break;
		case VIEW_SERVERLIST:
			if (lea.readByte() == 0) {
				// CharLoginHandler.ServerListRequest(c);
			}
			break;
		case CREATE_CHAR:
		case CREATE_SPECIAL_CHAR:
			// CharLoginHandler.CreateChar(slea, c);
			break;
		case CREATE_ULTIMATE:
			CharLoginHandler.CreateUltimate(lea, c);
			break;
		case DELETE_CHAR:
			CharLoginHandler.DeleteChar(lea, c);
			break;
		case CHAR_SELECT_NO_PIC:
			// CharLoginHandler.Character_WithoutSecondPassword(slea, c, false, false);
			break;
		case VIEW_REGISTER_PIC:
			// CharLoginHandler.Character_WithoutSecondPassword(slea, c, true, true);
			break;
		case PART_TIME_JOB:
			CharLoginHandler.PartJob(lea, c);
			break;
		case CHAR_SELECT:
			// CharLoginHandler.Character_WithoutSecondPassword(slea, c, true, false);
			break;
		case VIEW_SELECT_PIC:
			// CharLoginHandler.Character_WithSecondPassword(slea, c, true);
			break;
		case AUTH_SECOND_PASSWORD:
			// CharLoginHandler.Character_WithSecondPassword(slea, c, false);
			break;
		case CHARACTER_CARD:
			CharLoginHandler.updateCCards(lea, c);
			break;
		case ENABLE_SPECIAL_CREATION:
			c.getSession().write(LoginPacket.enableSpecialCreation(c.getAccID(), true));
			break;
		// END OF LOGIN SERVER
		case ENTER_PVP:
		case ENTER_PVP_PARTY:
			PlayersHandler.EnterPVP(lea, c);
			break;
		case PVP_RESPAWN:
			PlayersHandler.RespawnPVP(lea, c);
			break;
		case LEAVE_PVP:
			PlayersHandler.LeavePVP(lea, c);
			break;
		case ENTER_AZWAN:
			PlayersHandler.EnterAzwan(lea, c);
			break;
		case ENTER_AZWAN_EVENT:
			PlayersHandler.EnterAzwanEvent(lea, c);
			break;
		case LEAVE_AZWAN:
			PlayersHandler.LeaveAzwan(lea, c);
			c.getSession().write(CField.showMapEffect("hillah/fail"));
			c.getSession().write(CField.UIPacket.sendAzwanResult());
			break;
		case PVP_ATTACK:
			PlayersHandler.AttackPVP(lea, c);
			break;
		case PVP_SUMMON:
			SummonHandler.SummonPVP(lea, c);
			break;
		case SPECIAL_MOVE:
			PlayerHandler.SpecialMove(lea, c, c.getPlayer());
			break;
		case GET_BOOK_INFO:
			PlayersHandler.MonsterBookInfoRequest(lea, c, c.getPlayer());
			break;
		case MONSTER_BOOK_DROPS:
			PlayersHandler.MonsterBookDropsRequest(lea, c, c.getPlayer());
			break;
		case CHANGE_CODEX_SET:
			PlayersHandler.ChangeSet(lea, c, c.getPlayer());
			break;
		case PROFESSION_INFO:
			ItemMakerHandler.ProfessionInfo(lea, c);
			break;
		case CRAFT_DONE:
			ItemMakerHandler.CraftComplete(lea, c, c.getPlayer());
			break;
		case CRAFT_MAKE:
			ItemMakerHandler.CraftMake(lea, c, c.getPlayer());
			break;
		case CRAFT_EFFECT:
			ItemMakerHandler.CraftEffect(lea, c, c.getPlayer());
			break;
		case START_HARVEST:
			ItemMakerHandler.StartHarvest(lea, c, c.getPlayer());
			break;
		case STOP_HARVEST:
			ItemMakerHandler.StopHarvest(lea, c, c.getPlayer());
			break;
		case MAKE_EXTRACTOR:
			ItemMakerHandler.MakeExtractor(lea, c, c.getPlayer());
			break;
		case USE_BAG:
			ItemMakerHandler.UseBag(lea, c, c.getPlayer());
			break;
		case USE_FAMILIAR:
			MobHandler.UseFamiliar(lea, c, c.getPlayer());
			break;
		case SPAWN_FAMILIAR:
			MobHandler.SpawnFamiliar(lea, c, c.getPlayer());
			break;
		case RENAME_FAMILIAR:
			MobHandler.RenameFamiliar(lea, c, c.getPlayer());
			break;
		case MOVE_FAMILIAR:
			MobHandler.MoveFamiliar(lea, c, c.getPlayer());
			break;
		case ATTACK_FAMILIAR:
			MobHandler.AttackFamiliar(lea, c, c.getPlayer());
			break;
		case TOUCH_FAMILIAR:
			MobHandler.TouchFamiliar(lea, c, c.getPlayer());
			break;
		case REVEAL_FAMILIAR:
			break;
		case USE_RECIPE:
			ItemMakerHandler.UseRecipe(lea, c, c.getPlayer());
			break;
		case MOVE_HAKU:
			PlayerHandler.MoveHaku(lea, c, c.getPlayer());
			break;
		case CHANGE_HAKU:
			PlayerHandler.MoveHaku(lea, c, c.getPlayer());
			break;
		case MOVE_ANDROID:
			PlayerHandler.MoveAndroid(lea, c, c.getPlayer());
			break;
		case FACE_EXPRESSION:
			PlayerHandler.ChangeEmotion(lea.readInt(), c.getPlayer());
			break;
		case FACE_ANDROID:
			PlayerHandler.ChangeAndroidEmotion(lea.readInt(), c.getPlayer());
			break;
		case HEAL_OVER_TIME:
			PlayerHandler.Heal(lea, c.getPlayer());
			break;
		case CANCEL_BUFF:
			PlayerHandler.CancelBuffHandler(lea.readInt(), c.getPlayer());
			break;
		case MECH_CANCEL:
			PlayerHandler.CancelMech(lea, c.getPlayer());
			break;
		case CANCEL_ITEM_EFFECT:
			PlayerHandler.CancelItemEffect(lea.readInt(), c.getPlayer());
			break;
		case USE_TITLE:
			PlayerHandler.UseTitle(lea.readInt(), c, c.getPlayer());
			break;
		case ANGELIC_CHANGE:
			PlayerHandler.AngelicChange(lea, c, c.getPlayer());
			break;
		case DRESSUP_TIME:
			PlayerHandler.DressUpTime(lea, c);
			break;
		case WHEEL_OF_FORTUNE:
			break; // whatever
		case USE_ITEMEFFECT:
			PlayerHandler.UseItemEffect(lea.readInt(), c, c.getPlayer());
			break;
		case SKILL_EFFECT:
			PlayerHandler.SkillEffect(lea, c.getPlayer());
			break;
		case QUICK_SLOT:
			PlayerHandler.QuickSlot(lea, c.getPlayer());
			break;
		case PET_BUFF:
			PlayerHandler.ChangePetBuff(lea, c.getPlayer());
			break;
		case UPDATE_ENV:
			// We handle this in MapleMap
			break;
		case TROCK_ADD_MAP:
			PlayerHandler.TrockAddMap(lea, c, c.getPlayer());
			break;
		case LIE_DETECTOR:
		case LIE_DETECTOR_SKILL:
			// PlayersHandler.LieDetector(slea, c, c.getPlayer(), header ==
			// RecvPacketOpcode.LIE_DETECTOR);
			break;
		case LIE_DETECTOR_RESPONSE:
			// PlayersHandler.LieDetectorResponse(slea, c);
			break;
		case ARAN_COMBO:
			PlayerHandler.AranCombo(c, c.getPlayer(), 1);
			break;
		case SKILL_MACRO:
			PlayerHandler.ChangeSkillMacro(lea, c.getPlayer());
			break;
		case GIVE_FAME:
			PlayersHandler.GiveFame(lea, c, c.getPlayer());
			break;
		case TRANSFORM_PLAYER:
			PlayersHandler.TransformPlayer(lea, c, c.getPlayer());
			break;
		case NOTE_ACTION:
			PlayersHandler.Note(lea, c.getPlayer());
			break;
		case USE_DOOR:
			PlayersHandler.UseDoor(lea, c.getPlayer());
			break;
		case USE_MECH_DOOR:
			PlayersHandler.UseMechDoor(lea, c.getPlayer());
			break;
		case CLICK_REACTOR:
		case TOUCH_REACTOR:
			PlayersHandler.TouchReactor(lea, c);
			break;
		case CLOSE_CHALKBOARD:
			c.getPlayer().setChalkboard(null);
			break;
		case ITEM_SORT:
			InventoryHandler.ItemSort(lea, c);
			break;
		case ITEM_GATHER:
			InventoryHandler.ItemGather(lea, c);
			break;
		case MOVE_BAG:
			InventoryHandler.MoveBag(lea, c);
			break;
		case SWITCH_BAG:
			InventoryHandler.SwitchBag(lea, c);
			break;
		case ITEM_MAKER:
			ItemMakerHandler.ItemMaker(lea, c);
			break;
		case USE_CASH_ITEM:
			InventoryHandler.UseCashItem(lea, c);
			break;
		case USE_ITEM:
			InventoryHandler.UseItem(lea, c, c.getPlayer());
			break;
		case USE_COSMETIC:
			InventoryHandler.UseCosmetic(lea, c, c.getPlayer());
			break;
		case USE_MAGNIFY_GLASS:
			InventoryHandler.UseMagnify(lea, c);
			break;
		case USE_SCRIPTED_NPC_ITEM:
			InventoryHandler.UseScriptedNPCItem(lea, c, c.getPlayer());
			break;
		case USE_RETURN_SCROLL:
			InventoryHandler.UseReturnScroll(lea, c, c.getPlayer());
			break;
		case USE_NEBULITE:
			InventoryHandler.UseNebulite(lea, c);
			break;
		case USE_ALIEN_SOCKET:
			InventoryHandler.UseAlienSocket(lea, c);
			break;
		case USE_ALIEN_SOCKET_RESPONSE:
			lea.skip(4); // all 0
			c.getSession().write(CSPacket.useAlienSocket(false));
			break;
		case GOLDEN_HAMMER:
			InventoryHandler.UseGoldenHammer(lea, c);
			break;
		case VICIOUS_HAMMER:
			lea.skip(4); // 3F 00 00 00
			lea.skip(4); // all 0
			c.getSession().write(CSPacket.ViciousHammer(false, 0));
			break;
		case USE_NEBULITE_FUSION:
			InventoryHandler.UseNebuliteFusion(lea, c);
			break;
		case USE_UPGRADE_SCROLL:
			lea.skip(4); // update tick
			InventoryHandler.UseUpgradeScroll(lea.readShort(), lea.readShort(), lea.readShort(), c, c.getPlayer(),
					lea.readByte() > 0);
			break;
		case USE_FLAG_SCROLL:
		case USE_POTENTIAL_SCROLL:
		case USE_EQUIP_SCROLL:
			lea.skip(4); // update tick
			InventoryHandler.UseUpgradeScroll(lea.readShort(), lea.readShort(), (short) 0, c, c.getPlayer(),
					lea.readByte() > 0);
			break;
		case USE_ABYSS_SCROLL:
			InventoryHandler.UseAbyssScroll(lea, c);
			break;
		case USE_CARVED_SEAL:
			InventoryHandler.UseCarvedSeal(lea, c);
			break;
		case USE_CRAFTED_CUBE:
			InventoryHandler.UseCube(lea, c);
			break;
		case USE_SUMMON_BAG:
			InventoryHandler.UseSummonBag(lea, c, c.getPlayer());
			break;
		case USE_TREASURE_CHEST:
			InventoryHandler.UseTreasureChest(lea, c, c.getPlayer());
			break;
		case USE_SKILL_BOOK:
			lea.skip(4); // update tick
			InventoryHandler.UseSkillBook((byte) lea.readShort(), lea.readInt(), c, c.getPlayer());
			break;
		case USE_EXP_POTION:
			InventoryHandler.UseExpPotion(lea, c, c.getPlayer());
			break;
		case USE_CATCH_ITEM:
			InventoryHandler.UseCatchItem(lea, c, c.getPlayer());
			break;
		case USE_MOUNT_FOOD:
			InventoryHandler.UseMountFood(lea, c, c.getPlayer());
			break;
		case REWARD_ITEM:
			InventoryHandler.UseRewardItem(lea, c, c.getPlayer());
			break;
		case SOLOMON_EXP:
			InventoryHandler.UseExpItem(lea, c, c.getPlayer());
			break;
		case HYPNOTIZE_DMG:
			MobHandler.HypnotizeDmg(lea, c.getPlayer());
			break;
		case MOB_NODE:
			MobHandler.MobNode(lea, c.getPlayer());
			break;
		case DISPLAY_NODE:
			MobHandler.DisplayNode(lea, c.getPlayer());
			break;
		case AUTO_AGGRO:
			MobHandler.AutoAggro(lea.readInt(), c.getPlayer());
			break;
		case FRIENDLY_DAMAGE:
			MobHandler.FriendlyDamage(lea, c.getPlayer());
			break;
		case REISSUE_MEDAL:
			PlayerHandler.ReIssueMedal(lea, c, c.getPlayer());
			break;
		case MONSTER_BOMB:
			MobHandler.MonsterBomb(lea.readInt(), c.getPlayer());
			break;
		case MOB_BOMB:
			MobHandler.MobBomb(lea, c.getPlayer());
			break;
		case TOT_GUIDE:
			break;
		case STORAGE:
			NPCHandler.Storage(lea, c, c.getPlayer());
			break;
		case PARTYCHAT:
			lea.skip(4); // update tick
			ChatHandler.Others(lea, c, c.getPlayer());
			break;
		case COMMAND:
			ChatHandler.Command(lea, c);
			break;
		case MESSENGER:
			ChatHandler.Messenger(lea, c);
			break;
		case AUTO_ASSIGN_AP:
			StatsHandling.AutoAssignAP(lea, c, c.getPlayer());
			break;
		case PLAYER_INTERACTION:
			PlayerInteractionHandler.PlayerInteraction(lea, c, c.getPlayer());
			break;
		case ADMIN_CHAT:
			ChatHandler.AdminChat(lea, c, c.getPlayer());
			break;
		case ADMIN_COMMAND:
			PlayerHandler.AdminCommand(lea, c, c.getPlayer());
			break;
		case ADMIN_LOG:
			break;
		case GUILD_OPERATION:
			GuildHandler.Guild(lea, c);
			break;
		case DENY_GUILD_REQUEST:
			lea.skip(1);
			GuildHandler.DenyGuildRequest(lea.readMapleAsciiString(), c);
			break;
		case ALLIANCE_OPERATION:
			AllianceHandler.HandleAlliance(lea, c, false);
			break;
		case DENY_ALLIANCE_REQUEST:
			AllianceHandler.HandleAlliance(lea, c, true);
			break;
		case QUICK_MOVE:
			NPCHandler.OpenQuickMove(lea, c);
			break;
		case BBS_OPERATION:
			BBSHandler.BBSOperation(lea, c);
			break;
		case PARTY_OPERATION:
			PartyHandler.PartyOperation(lea, c);
			break;
		case DENY_PARTY_REQUEST:
			PartyHandler.DenyPartyRequest(lea, c);
			break;
		case ALLOW_PARTY_INVITE:
			PartyHandler.AllowPartyInvite(lea, c);
			break;
		case BUDDYLIST_MODIFY:
			BuddyListHandler.BuddyOperation(lea, c);
			break;
		case CYGNUS_SUMMON:
			UserInterfaceHandler.CygnusSummon_NPCRequest(c);
			break;
		case SHIP_OBJECT:
			UserInterfaceHandler.ShipObjectRequest(lea.readInt(), c);
			break;
		case BUY_CS_ITEM:
			CashShopOperation.BuyCashItem(lea, c, c.getPlayer());
			break;
		case COUPON_CODE:
			lea.skip(2);
			String code = lea.readMapleAsciiString();
			CashShopOperation.CouponCode(code, c);
//                CashShopOperation.doCSPackets(c);
			break;
		case CASH_CATEGORY:
			CashShopOperation.SwitchCategory(lea, c);
			break;
		case TWIN_DRAGON_EGG:
			System.out.println("TWIN_DRAGON_EGG: " + lea.toString());
			// final CashItemInfo item = CashItemFactory.getInstance().getItem(10003055);
			// Item itemz = c.getPlayer().getCashInventory().toItem(item);
			// Aristocat c.getSession().write(CSPacket.sendTwinDragonEgg(true, true, 38,
			// itemz, 1));
			break;
		case XMAS_SURPRISE:
			System.out.println("XMAS_SURPRISE: " + lea.toString());
			break;
		case CS_UPDATE:
			CashShopOperation.CSUpdate(c);
			break;
		case USE_POT:
			ItemMakerHandler.UsePot(lea, c);
			break;
		case CLEAR_POT:
			ItemMakerHandler.ClearPot(lea, c);
			break;
		case FEED_POT:
			ItemMakerHandler.FeedPot(lea, c);
			break;
		case CURE_POT:
			ItemMakerHandler.CurePot(lea, c);
			break;
		case REWARD_POT:
			ItemMakerHandler.RewardPot(lea, c);
			break;
		case DAMAGE_SUMMON:
			lea.skip(4);
			SummonHandler.DamageSummon(lea, c.getPlayer());
			break;
		case MOVE_SUMMON:
			SummonHandler.MoveSummon(lea, c.getPlayer());
			break;
		case SUMMON_ATTACK:
			SummonHandler.SummonAttack(lea, c, c.getPlayer());
			break;
		case MOVE_DRAGON:
			SummonHandler.MoveDragon(lea, c.getPlayer());
			break;
		case SUB_SUMMON:
			SummonHandler.SubSummon(lea, c.getPlayer());
			break;
		case REMOVE_SUMMON:
			SummonHandler.RemoveSummon(lea, c);
			break;
		case SPAWN_PET:
			PetHandler.SpawnPet(lea, c, c.getPlayer());
			break;
		case MOVE_PET:
			PetHandler.MovePet(lea, c.getPlayer());
			break;
		case PET_CHAT:
			// System.out.println("Pet chat: " + slea.toString());
			if (lea.available() < 12) {
				break;
			}
			final int petid = c.getPlayer().getPetIndex((int) lea.readLong());
			lea.skip(4); // update tick
			PetHandler.PetChat(petid, lea.readShort(), lea.readMapleAsciiString(), c.getPlayer());
			break;
		case PET_COMMAND:
			MaplePet pet;
			pet = c.getPlayer().getPet(c.getPlayer().getPetIndex((int) lea.readLong()));
			lea.readByte(); // always 0?
			if (pet == null) {
				return;
			}
			PetHandler.PetCommand(pet, PetDataFactory.getPetCommand(pet.getPetItemId(), lea.readByte()), c,
					c.getPlayer());
			break;
		case PET_FOOD:
			PetHandler.PetFood(lea, c, c.getPlayer());
			break;
		case PET_LOOT:
			// System.out.println("PET_LOOT ACCESSED");
			InventoryHandler.Pickup_Pet(lea, c, c.getPlayer());
			break;
		case PET_AUTO_POT:
			PetHandler.Pet_AutoPotion(lea, c, c.getPlayer());
			break;
		case MONSTER_CARNIVAL:
			MonsterCarnivalHandler.MonsterCarnival(lea, c);
			break;
		case PACKAGE_OPERATION:
			PackageHandler.handleAction(lea, c);
			break;
		case USE_HIRED_MERCHANT:
			HiredMerchantHandler.UseHiredMerchant(c, true);
			break;
		case MERCH_ITEM_STORE:
			HiredMerchantHandler.MerchantItemStore(lea, c);
			break;
		case CANCEL_DEBUFF:
			// Ignore for now
			break;
		// case MAPLETV:
		// break;
		case LEFT_KNOCK_BACK:
			PlayerHandler.leftKnockBack(lea, c);
			break;
		case SNOWBALL:
			PlayerHandler.snowBall(lea, c);
			break;
		case COCONUT:
			PlayersHandler.hitCoconut(lea, c);
			break;
		case START_EVOLUTION:
			PlayersHandler.startEvo(lea, c.getPlayer(), c);
			break;
		case ZERO_TAG:
			MapleCharacter.ZeroTag(lea, c);
		case REPAIR:
			NPCHandler.repair(lea, c);
			break;
		case REPAIR_ALL:
			NPCHandler.repairAll(c);
			break;
		case BUY_SILENT_CRUSADE:
			PlayersHandler.buySilentCrusade(lea, c);
			break;
		// case GAME_POLL:
		// UserInterfaceHandler.InGame_Poll(slea, c);
		// break;
		case OWL:
			InventoryHandler.Owl(lea, c);
			break;
		case OWL_WARP:
			InventoryHandler.OwlWarp(lea, c);
			break;
		case USE_OWL_MINERVA:
			InventoryHandler.OwlMinerva(lea, c);
			break;
		case RPS_GAME:
			NPCHandler.RPSGame(lea, c);
			break;
		case UPDATE_QUEST:
			NPCHandler.UpdateQuest(lea, c);
			break;
		case USE_ITEM_QUEST:
			NPCHandler.UseItemQuest(lea, c);
			break;
		case FOLLOW_REQUEST:
			PlayersHandler.FollowRequest(lea, c);
			break;
		case AUTO_FOLLOW_REPLY:
		case FOLLOW_REPLY:
			PlayersHandler.FollowReply(lea, c);
			break;
		case RING_ACTION:
			PlayersHandler.RingAction(lea, c);
			break;
		case SOLOMON:
			PlayersHandler.Solomon(lea, c);
			break;
		case GACH_EXP:
			PlayersHandler.GachExp(lea, c);
			break;
		case PARTY_SEARCH_START:
			PartyHandler.MemberSearch(lea, c);
			break;
		case PARTY_SEARCH_STOP:
			PartyHandler.PartySearch(lea, c);
			break;
		case EXPEDITION_LISTING:
			PartyHandler.PartyListing(lea, c);
			break;
		case EXPEDITION_OPERATION:
			PartyHandler.Expedition(lea, c);
			break;
		case USE_TELE_ROCK:
			InventoryHandler.TeleRock(lea, c);
			break;
		case AZWAN_REVIVE:
			PlayersHandler.reviveAzwan(lea, c);
			break;
		case INNER_CIRCULATOR:
			InventoryHandler.useInnerCirculator(lea, c);
			break;
		case PAM_SONG:
			InventoryHandler.PamSong(lea, c);
			break;
		case REPORT:
			PlayersHandler.Report(lea, c);
			break;
		// working
		case CANCEL_OUT_SWIPE:
			lea.readInt();
			break;
		// working
		case VIEW_SKILLS:
			PlayersHandler.viewSkills(lea, c);
			break;
		// working
		case SKILL_SWIPE:
			PlayersHandler.StealSkill(lea, c);
			break;
		case CHOOSE_SKILL:
			PlayersHandler.ChooseSkill(lea, c);
			break;
		case MAGIC_WHEEL:
			System.out.println("[MAGIC_WHEEL] [" + lea.toString() + "]");
			PlayersHandler.magicWheel(lea, c);
			break;
		case REWARD:
			PlayersHandler.onReward(lea, c);
			break;
		case BLACK_FRIDAY:
			PlayersHandler.blackFriday(lea, c);
		case UPDATE_RED_LEAF:
			PlayersHandler.updateRedLeafHigh(lea, c);
			break;
		case SPECIAL_STAT:
			PlayersHandler.updateSpecialStat(lea, c);
			break;
		case UPDATE_HYPER:
			StatsHandling.DistributeHyper(lea, c, c.getPlayer());
			break;
		case RESET_HYPER:
			StatsHandling.ResetHyper(lea, c, c.getPlayer());
			break;
		case DF_COMBO:
			PlayerHandler.absorbingDF(lea, c);
			break;
		case MESSENGER_RANKING:
			PlayerHandler.MessengerRanking(lea, c, c.getPlayer());
			break;
		case OS_INFORMATION:
			System.out.println(c.getSessionIPAddress());
			break;
//            case BUFF_RESPONSE://wat does it do?
//                break;
		case BUTTON_PRESSED:
			break;
		default:
			System.out.println("[UNHANDLED] Recv [" + header.toString() + "] found");
			break;
		}
	}
}