package armeria.playground.app

import io.netty.util.AttributeKey

object AttrKeys {
    @JvmField
    val FOO_KEY: AttributeKey<String> = AttributeKey.valueOf("FOO_KEY")
}
