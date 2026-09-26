package tachiyomi.domain.items.episode.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class EpisodeRecognitionTest {

    @Test
    fun `Basic e prefix`() {
        val animeTitle = "Kaguya-sama"

        assertEpisode(animeTitle, "Kaguya-sama wa kokurasetai - s01e01v2 (BD 1080p HEVC)", 1.0)
    }

    @Test
    fun `Basic e prefix ignores other numbers in the name`() {
        val animeTitle = "Kaguya-sama"

        // s01 and v2 must lose to the e01 the basic pattern anchors on.
        assertEpisode(animeTitle, "Kaguya-sama - s01e01v2 1080p", 1.0)
    }

    @Test
    fun `Basic ep prefix`() {
        val animeTitle = "Kaguya-sama"

        assertEpisode(animeTitle, "Kaguya-sama - ep 12 [1080p]", 12.0)
    }

    @Test
    fun `Spelled out episode prefix`() {
        val animeTitle = "Kaguya-sama"

        assertEpisode(animeTitle, "Kaguya-sama - episode 12", 12.0)
    }

    @Test
    fun `Name containing one number`() {
        val animeTitle = "Bleach"

        assertEpisode(animeTitle, "Bleach 567 Down With Snowwhite", 567.0)
    }

    @Test
    fun `Name containing one number and decimal`() {
        val animeTitle = "Bleach"

        assertEpisode(animeTitle, "Bleach 567.1 Down With Snowwhite", 567.1)
    }

    @Test
    fun `Name containing one number and alpha postfix`() {
        val animeTitle = "Bleach"

        assertEpisode(animeTitle, "Bleach 567.a Down With Snowwhite", 567.1)
        assertEpisode(animeTitle, "Bleach 567.b Down With Snowwhite", 567.2)
    }

    @Test
    fun `Name containing one number and extra`() {
        val animeTitle = "Bleach"

        assertEpisode(animeTitle, "Bleach 567.extra Down With Snowwhite", 567.99)
    }

    @Test
    fun `Special and omake get their own fractional numbers`() {
        val animeTitle = "One Piece"

        // The space before the keyword is stripped so the suffix reads as one token.
        assertEpisode(animeTitle, "One Piece 12 special", 12.97)
        assertEpisode(animeTitle, "One Piece 12 omake", 12.98)
    }

    @Test
    fun `Leading and trailing tags are removed`() {
        val animeTitle = "Kaguya-sama"

        assertEpisode(animeTitle, "[flugel] Kaguya-sama - s01e01v2 [multi audio]", 1.0)
    }

    @Test
    fun `A known episode number is returned untouched`() {
        EpisodeRecognition.parseEpisodeNumber("Bleach", "Bleach 567 Down With Snowwhite", 5.0) shouldBe 5.0
    }

    @Test
    fun `An unknown episode number is re-parsed from the name`() {
        // -1.0 is the "not recognised" sentinel, so the name has to be read instead.
        EpisodeRecognition.parseEpisodeNumber("Bleach", "Bleach 567 Down With Snowwhite", -1.0) shouldBe 567.0
    }

    @Test
    fun `A name with no number returns -1`() {
        EpisodeRecognition.parseEpisodeNumber("Bleach", "The Beginning") shouldBe -1.0
    }

    private fun assertEpisode(animeTitle: String, name: String, expected: Double) {
        EpisodeRecognition.parseEpisodeNumber(animeTitle, name) shouldBe expected
    }
}
