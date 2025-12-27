package xyz.mordorx.flacblaster

import org.junit.Test

import org.junit.Assert.*
import xyz.mordorx.flacblaster.fs.FileEntity
import java.io.File

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun fileEntityIsChildOf1() {
        val parent = FileEntity.emptyOfFile(File("/some/dir"))
        val child = FileEntity.emptyOfFile(File("/some/dir/music.mp3"))
        assert(child.isChildOf(parent))
    }


}