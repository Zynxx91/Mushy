/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc> 
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
package handling.login;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import constants.ServerConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import net.netty.ServerInitializer;
import tools.Triple;
import tools.data.output.LittleEndianByteBufAllocator;

public class LoginServer  {

    //private static InetSocketAddress InetSocketadd;
    //private static IoAcceptor acceptor;
    private static Map<Integer, Integer> load = new HashMap<>();
    private static String serverName, eventMessage;
    private static int maxCharacters, userLimit, usersOn = 0;
    private static boolean finishedShutdown = true;
    private static final HashMap<Integer, Triple<String, String, Integer>> loginAuth = new HashMap<>();
    private static final HashSet<String> loginIPAuth = new HashSet<>();
    private static EventLoopGroup acceptorGroup;
    private static EventLoopGroup clientGroup;
    private Channel acceptor;

    public static void putLoginAuth(int chrid, String ip, String tempIP, int channel) {
        loginAuth.put(chrid, new Triple<>(ip, tempIP, channel));
        loginIPAuth.add(ip);
    }

    public static Triple<String, String, Integer> getLoginAuth(int chrid) {
        return loginAuth.remove(chrid);
    }

    public static boolean containsIPAuth(String ip) {
        return loginIPAuth.contains(ip);
    }

    public static void removeIPAuth(String ip) {
        loginIPAuth.remove(ip);
    }

    public static void addIPAuth(String ip) {
        loginIPAuth.add(ip);
    }

    public static final void addChannel(final int channel) {
        load.put(channel, 0);
    }

    public static final void removeChannel(final int channel) {
        load.remove(channel);
    }
    
    public static void getLoginInstance() {
    	LoginServer theLoginServer = new LoginServer();
    	theLoginServer.run_startup_configurations();
    }

    public final void run_startup_configurations() {
        userLimit = ServerConfig.USER_LIMIT;
        serverName = ServerConfig.SERVER_NAME;
        eventMessage = ServerConfig.EVENT_MSG;
        maxCharacters = ServerConfig.MAX_CHARACTERS;

        /*
        ByteBuffer.setUseDirectBuffers(false);
        ByteBuffer.setAllocator(new SimpleByteBufferAllocator());

        acceptor = new SocketAcceptor();
        final SocketAcceptorConfig cfg = new SocketAcceptorConfig();
        cfg.getSessionConfig().setTcpNoDelay(true);
        cfg.setDisconnectOnUnbind(true);
        cfg.getFilterChain().addLast("codec", new ProtocolCodecFilter(new MapleCodecFactory()));
        */
        
        acceptorGroup = new NioEventLoopGroup(4);
        clientGroup = new NioEventLoopGroup(10);

        acceptor = new ServerBootstrap()
                .group(acceptorGroup, clientGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ServerInitializer())
                .option(ChannelOption.SO_BACKLOG, 64)
                .option(ChannelOption.ALLOCATOR, new LittleEndianByteBufAllocator(UnpooledByteBufAllocator.DEFAULT))
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .bind(8484).syncUninterruptibly().channel();  
            	System.out.println("Login Server is listening on port 8484.");
    }

    public static final void shutdown() {
        if (finishedShutdown) {
            return;
        }
        System.out.println("Shutting down login...");
        //acceptor.unbindAll();
        finishedShutdown = true; //nothing. lol
    }

    public static final String getServerName() {
        return serverName;
    }

    public static final String getTrueServerName() {
        return serverName.substring(0, serverName.length() - 2);
    }

    public static String getEventMessage() {
        return eventMessage;
    }

    public static int getMaxCharacters() {
        return maxCharacters;
    }

    public static Map<Integer, Integer> getLoad() {
        return load;
    }

    public static void setLoad(final Map<Integer, Integer> load_, final int usersOn_) {
        load = load_;
        usersOn = usersOn_;
    }

    public static String getEventMessage(int world) { // TODO: Finish this
        switch (world) {
            case 0:
                return null;
        }
        return null;
    }

    public static final int getUserLimit() {
        return userLimit;
    }

    public static final int getUsersOn() {
        return usersOn;
    }

    public static final void setUserLimit(final int newLimit) {
        userLimit = newLimit;
    }
    
    public static final boolean isShutdown() {
        return finishedShutdown;
    }

    public static final void setOn() {
        finishedShutdown = false;
    }
}
