# Fucking Netty can't go through R8 at all..
-dontwarn io.netty.internal.tcnative.**
-keep class io.netty.buffer.** { *; }
-keep class io.netty.util.** { *; }
-keep class io.netty.handler.codec.* { *; }
-keep class io.netty.channel.socket.nio.NioServerSocketChannel { <init>(); }
-keep class io.netty.channel.ChannelHandler$Sharable
-keep,allowobfuscation @io.netty.channel.ChannelHandler$Sharable class *

# Yep. Also crap used by Netty.
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn io.netty.pkitesting.**
-dontwarn org.osgi.annotation.bundle.**
-dontwarn com.sun.nio.file.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn reactor.blockhound.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.ldap.**
-dontwarn jdk.jfr.**
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.google.protobuf.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn lzma.sdk.**
-dontwarn net.jpountz.**
-dontwarn org.jboss.marshalling.**

# R8 optimizes _something_ in ktor badly enough that the client's `wss` block is never invoked; everything else is ok.
-keep,allowobfuscation,allowshrinking class io.ktor.** { *; }
