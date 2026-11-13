package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IllustrationDslParserTest {

  @Test
  fun `parseLines parses streaming vector primitives and caption`() {
    val lines = listOf(
      "BG|#5D9CE6|#FFE5B8",
      "CIRCLE|0.5|0.4|0.2|#E0961E|filled",
      "RECT|0.1|0.6|0.8|0.3|0.05|#D95338|filled",
      "OVAL|0.35|0.55|0.3|0.16|#F4A236|filled",
      "POLY|0.2,0.6;0.5,0.3;0.8,0.6|#1D5B9E|filled",
      "ARC|0.3|0.3|0.4|0.2|0|180|4|#1B1F2A",
      "LINE|0.5|0.2|0.5|0.1|3|#FFFFFF",
      "LABEL|0.5|0.52|le croissant|#1B1F2A",
      "CAPTION|Un croissant, s'il vous plaît|uhn krwah-sahn seel voo pleh|A croissant, please|Greet the baker with Bonjour first.",
    )

    val parsed = IllustrationDslParser.parseLines(lines)
    assertEquals(8, parsed.primitives.size)
    assertTrue(parsed.primitives[0] is IllustrationPrimitive.Background)
    assertTrue(parsed.primitives[1] is IllustrationPrimitive.Circle)
    assertTrue(parsed.primitives[4] is IllustrationPrimitive.Polygon)
    assertTrue(parsed.primitives[7] is IllustrationPrimitive.Label)

    assertNotNull(parsed.caption)
    assertEquals("Un croissant, s'il vous plaît", parsed.caption?.targetPhrase)
    assertEquals("A croissant, please", parsed.caption?.englishMeaning)
  }

  @Test
  fun `parseLines ignores malformed lines safely`() {
    val lines = listOf(
      "```",
      "CIRCLE|invalid|0.5|0.2|#FFF",
      "BG|#FFF8EE|#F4A236",
    )
    val parsed = IllustrationDslParser.parseLines(lines)
    assertEquals(1, parsed.primitives.size)
  }
}
