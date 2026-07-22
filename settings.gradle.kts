rootProject.name = "BabyMonitor"

apply(from = "gradle/repositories.gradle.kts")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":common")

include(":androidService")

include(":codec")
include(":filters")

include(":network:common")
include(":network:model")
include(":network:client")
include(":network:server")
include(":network:presentation")

include(":camera:data")
include(":camera:model")
include(":camera:presentation")

include(":settings:data")
include(":settings:model")
include(":settings:presentation")

include(":model")
include(":data")
include(":presentation")

include(":appCommon")
include(":appAndroid")
include(":appDesktop")
include(":appRelay")
