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

import java.util.Map;

import client.KeyBinding;
import handling.SendPacketOpcode;
import tools.data.PacketWriter;

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

}
