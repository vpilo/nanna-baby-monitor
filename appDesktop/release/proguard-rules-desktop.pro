
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }

-keep class org.bytedeco.** { *; }
-keep class * implements org.slf4j.spi.SLF4JServiceProvider { *; }
-keep class dev.whyoleg.cryptography.providers.** { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
}

-dontwarn org.apache.maven.**
-dontwarn org.bytedeco.javacpp.tools.**
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn org.osgi.annotation.**
-dontwarn org.osgi.framework.**
-dontwarn java.lang.invoke.**
-dontwarn io.github.alexzhirkevich.qrose.options.Image

# log4j-api carries build-time-only annotations.
-dontwarn aQute.bnd.annotation.spi.ServiceConsumer
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn com.google.errorprone.annotations.InlineMe

# Ktor's Netty engine references every codec, TLS provider and logging backend it can ever use...
-dontwarn ch.qos.logback.**
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.google.protobuf.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn io.netty.pkitesting.**
-dontwarn lzma.sdk.lzma.**
-dontwarn net.jpountz.lz4.**
-dontwarn net.jpountz.xxhash.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.jboss.marshalling.**
-dontwarn reactor.blockhound.**
