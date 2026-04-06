package com.example.maohi;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.PacketCallbacks;
import org.jetbrains.annotations.Nullable;

public class FakeClientConnection extends ClientConnection {

    // 在构造时一次性生成并固定这个假 IP，防止每次调用 getAddress() 返回不同值导致日志前后不一致
    private final java.net.InetSocketAddress fakeAddress;

    public FakeClientConnection() {
        super(NetworkSide.SERVERBOUND);

        // 生成一个看起来真实的公网 IP（避开保留网段 10.x, 127.x, 192.168.x 等）
        int ip1 = (int)(Math.random() * 200) + 20;
        int ip2 = (int)(Math.random() * 255);
        int ip3 = (int)(Math.random() * 255);
        int ip4 = (int)(Math.random() * 254) + 1;
        int port = (int)(Math.random() * 40000) + 10000;
        this.fakeAddress = new java.net.InetSocketAddress(ip1 + "." + ip2 + "." + ip3 + "." + ip4, port);

        // 使用自定义的 EmbeddedChannel 子类，覆盖 remoteAddress() 返回伪造 IP
        // NOTE: Minecraft 日志系统 (PlayerManager.onPlayerConnect) 直接从 channel.remoteAddress() 取值打印，
        //       如果不在此层注入，日志会显示 [local] 而非我们的假 IP
        io.netty.channel.embedded.EmbeddedChannel embeddedChannel =
            new io.netty.channel.embedded.EmbeddedChannel() {
                @Override
                public java.net.SocketAddress remoteAddress() {
                    return fakeAddress;
                }

                @Override
                public java.net.SocketAddress localAddress() {
                    return fakeAddress;
                }

                // 以下四个覆盖是兜底防线：即使 ClientConnection 父类私有方法绕过
                // 我们的 send()/tick() 拦截直接操作 channel，也不会触发 ClosedChannelException
                @Override
                public boolean isActive() {
                    return true;
                }

                @Override
                public boolean isOpen() {
                    return true;
                }

                @Override
                public io.netty.channel.ChannelFuture write(Object msg) {
                    io.netty.util.ReferenceCountUtil.release(msg);
                    return newSucceededFuture();
                }

                @Override
                public io.netty.channel.ChannelFuture writeAndFlush(Object msg) {
                    io.netty.util.ReferenceCountUtil.release(msg);
                    return newSucceededFuture();
                }
            };

        try {
            java.lang.reflect.Field channelField = ClientConnection.class.getDeclaredField("channel");
            channelField.setAccessible(true);
            channelField.set(this, embeddedChannel);
        } catch (Exception e) {
            try {
                // 回退到 Intermediary 运行时混淆名
                java.lang.reflect.Field field11651 = ClientConnection.class.getDeclaredField("field_11651");
                field11651.setAccessible(true);
                field11651.set(this, embeddedChannel);
            } catch (Exception ignored) {}
        }

        // NOTE: ClientConnection 内部有一个 address 私有字段，它由 channelActive() 回调赋值。
        //       由于我们通过反射绕过了 Netty 管道初始化，channelActive() 从未触发，
        //       导致 address 字段为 null，Minecraft 日志就会降级显示 [local]。
        //       这里必须用反射把伪造 IP 直接写进去。
        try {
            java.lang.reflect.Field addressField = ClientConnection.class.getDeclaredField("address");
            addressField.setAccessible(true);
            addressField.set(this, fakeAddress);
        } catch (Exception e) {
            try {
                // Intermediary 映射回退
                java.lang.reflect.Field addressField = ClientConnection.class.getDeclaredField("field_11654");
                addressField.setAccessible(true);
                addressField.set(this, fakeAddress);
            } catch (Exception ignored) {}
        }
    }

    public void disableAutoRead() {
    }

    public void handleDisconnection() {
    }

    public boolean isOpen() {
        return true;
    }

    public void send(Packet<?> packet) {
    }

    public void send(Packet<?> packet, @Nullable PacketCallbacks callbacks) {
    }

    public void send(Packet<?> packet, @Nullable PacketCallbacks callbacks, boolean flush) {
    }

    @Override
    public void tick() {
        // 切断 ServerNetworkIo 的 tick 循环推送，防止向 EmbeddedChannel 写入导致 StacklessClosedChannelException
    }

    public void flush() {
    }

    public boolean hasChannel() {
        return true;
    }

    public boolean isChannelOpen() {
        return true;
    }

    // 伪造逼真的玩家加入公网 IP，彻底消灭控制台里一眼假的 [local]
    @Override
    public java.net.SocketAddress getAddress() {
        return fakeAddress;
    }
}
