package comp3011;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SubtitleOptionsTest {
    @Test
    void usesSForSubtitlesAndRForScratches() throws Exception {
        File file = File.createTempFile("video-test", ".mp4");
        file.deleteOnExit();
        CommandLineController cli = new CommandLineController(new String[]{"-sr", file.getAbsolutePath()});
        assertTrue(cli.shouldLaunchApplication());
        assertTrue(cli.isSubtitlesRequested());
        assertEquals(java.util.List.of("scratch"), cli.getEffectNames());
    }
}