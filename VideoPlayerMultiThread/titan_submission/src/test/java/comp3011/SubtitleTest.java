package comp3011;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class SubtitleTest {
    @Test
    void selectsTextBasedOnVideoTime() {
        List<Subtitle> lines = List.of(new Subtitle(1, 2, "Hello"));
        assertEquals("", Subtitle.findText(lines, 0));
        assertEquals("Hello", Subtitle.findText(lines, 1.5));
        assertEquals("", Subtitle.findText(lines, 2));
    }
}