package handling.handlers;

import client.KeyBinding;
import client.MapleClient;
import client.Skill;
import client.SkillFactory;
import constants.GameConstants;
import handling.PacketHandler;
import handling.RecvPacketOpcode;
import server.quest.MapleQuest;
import tools.data.LittleEndianAccessor;

public class ChangeKeymapHandler {

	@PacketHandler(opcode = RecvPacketOpcode.CHANGE_KEYMAP)
	public static void handle(MapleClient c, LittleEndianAccessor lea) {
		if (lea.available() >= 8L) {
			handleKeyBindingChanges(c, lea);
		}
	}

	private static void handleKeyBindingChanges(MapleClient c, LittleEndianAccessor lea) {
		int mode = lea.readInt();
		if (mode == 0) {
			int numberChange = lea.readInt();
			for (int i = 0; i < numberChange; i++) {
				int key = lea.readInt();
				byte type = lea.readByte();
				int action = lea.readInt();
				if (type == 1) {
					Skill skill = SkillFactory.getSkill(action);
					if (skill != null && !skill.isFourthJob() && !skill.isBeginnerSkill() && skill.isInvisible()
							&& c.getPlayer().getSkillLevel(skill) <= 0) {
						continue;
					}
				}
				c.getPlayer().changeKeybinding(key, new KeyBinding(type, action));
			}
		}
	}
}