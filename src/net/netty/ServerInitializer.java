package net.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import handling.MapleServerHandler;


public class ServerInitializer extends ChannelInitializer<SocketChannel> {

	public ServerInitializer() {
		
	}
	
	@Override
	protected void initChannel(SocketChannel channel) throws Exception {
		ChannelPipeline pipe = channel.pipeline();
		pipe.addLast("decoder", new MaplePacketDecoder());
		pipe.addLast("encoder", new MaplePacketEncoder());
		pipe.addLast("handler", new MapleServerHandler());
	}
}