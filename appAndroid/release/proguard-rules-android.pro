# Ktor's Netty engine references every TLS provider and logging backend it can optionally use.
# We ship none of them, so R8 sees the references but not the classes.
-dontwarn io.netty.internal.tcnative.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn reactor.blockhound.**

# Netty and Ktor reach for JVM-only APIs that do not exist on Android, behind runtime availability checks.
-dontwarn java.lang.management.**
-dontwarn javax.naming.ldap.**
-dontwarn jdk.jfr.**

