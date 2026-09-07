package io.github.magisk317.mipush.common.cache

import android.content.Context
import android.graphics.Bitmap
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class CacheInvalidationTest {
    private val context = mockk<Context>(relaxed = true)

    @AfterEach
    fun tearDown() {
        IconCache.clearAll()
        ApplicationNameCache.clear()
    }

    @Test
    fun `bitmap cache clear causes the next lookup to reload`() {
        val bitmap = mockk<Bitmap>()
        var loads = 0
        val converter = object : IconCache.Converter<String, Bitmap> {
            override fun convert(ctx: Context, b: String): Bitmap {
                loads += 1
                return bitmap
            }
        }

        assertSame(bitmap, IconCache.getBitmap(context, "image", converter))
        assertSame(bitmap, IconCache.getBitmap(context, "image", converter))
        assertEquals(1, loads)

        IconCache.clearBitmapCaches()

        assertSame(bitmap, IconCache.getBitmap(context, "image", converter))
        assertEquals(2, loads)
    }

    @Test
    fun `abstract cache clear evicts values without recycling them`() {
        val cache = androidx.collection.LruCache<String, Bitmap>(2)
        val bitmap = mockk<Bitmap>()
        val aspect = object : AbstractCacheAspect<Bitmap>(cache) {
            override fun gen(): Bitmap = bitmap
        }

        assertSame(bitmap, aspect.get("key"))
        aspect.clear()

        assertEquals(0, cache.size())
        assertSame(bitmap, aspect.get("key"))
    }
}
