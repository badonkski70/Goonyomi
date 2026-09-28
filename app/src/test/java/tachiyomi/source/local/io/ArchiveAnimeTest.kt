package tachiyomi.source.local.io

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ArchiveAnimeTest {

    @Test
    fun `photos are local episodes`() {
        ArchiveAnime.isImageUrl("Holiday/IMG_20240101_120000.jpg") shouldBe true
        ArchiveAnime.isImageUrl("Holiday/portrait.PNG") shouldBe true
        ArchiveAnime.isImageUrl("Holiday/shot.heic") shouldBe true
    }

    @Test
    fun `videos are not photos`() {
        ArchiveAnime.isImageUrl("Holiday/ep 01.mkv") shouldBe false
        ArchiveAnime.isImageUrl("Holiday/ep 01.mp4") shouldBe false
    }

    @Test
    fun `generated art is never gallery content`() {
        // The app writes these into the folder, a folder of videos would otherwise end up with a
        // cover episode as soon as the cover was extracted.
        ArchiveAnime.isImageUrl("Holiday/cover.jpg") shouldBe false
        ArchiveAnime.isImageUrl("Holiday/background.jpg") shouldBe false
    }

    @Test
    fun `names without an extension are not photos`() {
        ArchiveAnime.isImageUrl("Holiday/README") shouldBe false
        ArchiveAnime.isImageUrl("Holiday/.thumbnails") shouldBe false
    }

    @Test
    fun `the folder name is not mistaken for the extension`() {
        ArchiveAnime.isImageUrl("Holiday.jpg/notes") shouldBe false
    }
}
