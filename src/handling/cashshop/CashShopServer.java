package handling.cashshop;

import constants.ServerConfig;
import handling.channel.PlayerStorage;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import net.netty.ServerInitializer;
import tools.data.output.LittleEndianByteBufAllocator;

public class CashShopServer {

    private static String ip;
    private final static int PORT = 8610;
    private static PlayerStorage players;
    private static boolean finishedShutdown = false;
    private static EventLoopGroup acceptorGroup;
    private static EventLoopGroup clientGroup;
    private Channel acceptor;

    public final void run_startup_configurations() {
        ip = ServerConfig.IP_ADDRESS + ":" + PORT;

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

        setAcceptor(new ServerBootstrap()
                .group(acceptorGroup, clientGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ServerInitializer())
                .option(ChannelOption.SO_BACKLOG, 64)
                .option(ChannelOption.ALLOCATOR, new LittleEndianByteBufAllocator(UnpooledByteBufAllocator.DEFAULT))
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .bind(PORT).syncUninterruptibly().channel());  
            	System.out.println("Cash Shop Server is listening on port 8610.");
        players = new PlayerStorage(-10);
        System.out.println("Cash Shop Server is listening on port " + PORT + ".");
    }

    public static String getIP() {
        return ip;
    }

    public static PlayerStorage getPlayerStorage() {
        return players;
    }

    public void shutdown() {
        if (finishedShutdown) {
            return;
        }
        System.out.println("Saving all connected clients (CS)...");
        players.disconnectAll();
        System.out.println("Shutting down CS...");
        acceptor.close();
        finishedShutdown = true;
    }

    public static boolean isShutdown() {
        return finishedShutdown;
    }
   
	public Channel getAcceptor() {
		return acceptor;
	}

	public void setAcceptor(Channel acceptor) {
		this.acceptor = acceptor;
	}
	
	public static void getCashInstance() {
    	CashShopServer theCashServer = new CashShopServer();
    	theCashServer.shutdown();
    }
}
