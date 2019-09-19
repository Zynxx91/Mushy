/*
 This file is part of the OdinMS Maple Story Server And Mushy2 Emulator
 
 Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
 				    Matthias Butz <matze@odinms.de>
                    Jan Christian Meyer <vimes@odinms.de>
 Copyright (C) 2019 Zynxx 
 
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as
 published by the Free Software Foundation version 3 as published by
 the Free Software Foundation. You may not use, modify or distribute
 this program under any other version of the GNU Affero General Public
 License.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.
 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import client.KeyBinding;
import client.MapleCharacter;
import client.MapleMarriage;
import client.MapleTrait;
import client.inventory.Item;
import client.inventory.MapleInventoryType;
import client.inventory.MaplePet;
import handling.SendPacketOpcode;
import handling.world.World;
import handling.world.guild.MapleGuild;
import handling.world.guild.MapleGuildAlliance;
import tools.data.PacketWriter;
import tools.packet.PacketHelper;

public class PacketCreator {

	public static byte[] getKeymap(Map<Integer, KeyBinding> keybindings) {
		PacketWriter pw = new PacketWriter();
		pw.writeShort(SendPacketOpcode.KEYMAP.getValue());
		pw.write(0);
		for (int x = 0; x < 90; x++) {
			KeyBinding binding = keybindings.get(Integer.valueOf(x));
			if (binding != null) {
				pw.write(binding.getType());
				pw.writeInt(binding.getAction());
			} else {
				pw.write(0);
				pw.writeInt(0);
			}
		}
		return pw.getPacket();
	}

	public static byte[] charInfo(MapleCharacter chr, boolean isSelf) {
		PacketWriter pw = new PacketWriter();
		pw.writeShort(SendPacketOpcode.CHAR_INFO.getValue());
		pw.writeInt(chr.getId());
		pw.write(0); // Star Plant
		pw.write(chr.getLevel());
		pw.writeShort(chr.getJob());
		pw.writeShort(chr.getSubcategory());
		pw.write(chr.getStat().pvpRank);
		pw.writeInt(chr.getFame());
		MapleMarriage marriage = chr.getMarriage();
		pw.write(marriage != null && marriage.getId() != 0);
		if (marriage != null && marriage.getId() != 0) {
			pw.writeInt(marriage.getId());
			pw.writeInt(marriage.getHusbandId());
			pw.writeInt(marriage.getWifeId());
			pw.writeShort(3); // The Message Type
			pw.writeInt(chr.getMarriageItemId()); // Husband Ring ID
			pw.writeInt(chr.getMarriageItemId()); // Wife Ring ID
			pw.writeAsciiString(marriage.getHusbandName(), 13);
			pw.writeAsciiString(marriage.getWifeName(), 13);
		}
		List<Integer> prof = chr.getProfessions();
		pw.write(prof.size());
		for (Integer professions : prof) {
			pw.writeShort(professions);
		}
		MapleGuild gs = World.Guild.getGuild(chr.getGuildId());
		if (gs != null) {
			MapleGuildAlliance allianceName = World.Alliance.getAlliance(gs.getAllianceId());
			pw.writeMapleAsciiString(gs.getName());
			pw.writeMapleAsciiString(allianceName != null ? allianceName.getName() : "");
		} else {
			pw.writeMapleAsciiString("-");
			pw.writeMapleAsciiString("");
		}
		pw.write(-1);// nForcedPetIdx
		pw.write(isSelf ? 1 : 0);
		pw.write(!chr.getSummonedPets().isEmpty());
		pw.write(!chr.getSummonedPets().isEmpty());
		byte index = 1;
		for (MaplePet pet : chr.getSummonedPets()) {
			pw.writeInt(0);
			pw.writeInt(pet.getPetItemId());
			pw.writeMapleAsciiString(pet.getName());
			pw.write(pet.getLevel());
			pw.writeShort(pet.getCloseness());
			pw.write(pet.getFullness());
			pw.writeShort(0);
			Item inv = chr.getInventory(MapleInventoryType.EQUIPPED).getItem((short) (byte) (index == 2 ? -130 : index == 1 ? -114 : -138));
			pw.writeInt(inv == null ? 0 : inv.getItemId());
			pw.writeInt(0);
			pw.write(chr.getSummonedPets().size() > index);
			index++;
		}
		int wishlistSize = chr.getWishlistSize();
		pw.write(wishlistSize);
		if (wishlistSize > 0) {
			int[] wishlist = chr.getWishlist();
			for (int x = 0; x < wishlistSize; x++) {
				pw.writeInt(wishlist[x]);
			}
		}
		Item medal = chr.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -46);
		pw.writeInt(medal == null ? 0 : medal.getItemId());

		List<Pair<Integer, Long>> medalQuests = chr.getCompletedMedals();
		pw.writeShort(medalQuests.size());
		for (Pair<Integer, Long> x : medalQuests) {
			pw.writeInt(x.left);
			pw.writeLong(x.right);
		}

		pw.write(1); // hasDamageSkins
		pw.writeInt(0);
		pw.writeInt(2434600); // Basic Damage Skin Id.
		pw.write(0);
		pw.writeMapleAsciiString("Gourmet Damage Skin");
		pw.writeInt(-1);
		pw.writeInt(0);
		pw.write(1);
		pw.writeMapleAsciiString("");
		pw.writeShort(0);
		pw.writeShort(0);

		for (MapleTrait.MapleTraitType t : MapleTrait.MapleTraitType.values()) {
			pw.write(chr.getTrait(t).getLevel());
		}

		pw.writeInt(chr.getAccountID());
		PacketHelper.addFarmInfo(pw, chr.getClient(), (byte) 0);
		pw.writeInt(0);
		pw.writeInt(0);
		List<Integer> chairs = new ArrayList<>();
		for (Item i : chr.getInventory(MapleInventoryType.SETUP).newList()) {
			if (i.getItemId() / 10000 == 301 && !chairs.contains(i.getItemId())) {
				chairs.add(i.getItemId());
			}
		}
		pw.writeInt(chairs.size());
		for (Integer chair : chairs) {
			pw.writeInt(chair);
		}
		pw.writeInt(0);
		pw.writeInt(0x1E);
		pw.writeInt(0);

		return pw.getPacket();
	}

}
