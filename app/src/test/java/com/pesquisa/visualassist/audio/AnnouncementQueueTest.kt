package com.pesquisa.visualassist.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementQueueTest {

  private class FakeClock(var now: Long = 0L)

  @Test
  fun highPriorityJumpsAhead() {
    val q = AnnouncementQueue()
    q.offer(Announcement("normal 1"))
    q.offer(Announcement("normal 2"))
    q.offer(Announcement("urgente", priority = AudioPriority.HIGH))

    assertEquals("urgente", q.poll()?.text)
    assertEquals("normal 1", q.poll()?.text)
    assertEquals("normal 2", q.poll()?.text)
  }

  @Test
  fun debounceBlocksRepeatedLabelWithinWindow() {
    val clock = FakeClock(0L)
    val q = AnnouncementQueue(debounceMs = 3000L, clock = { clock.now })

    assertTrue(q.offer(Announcement("cadeira à sua frente", dedupeKey = "cadeira")))
    assertEquals("cadeira à sua frente", q.poll()?.text) // registra debounce em t=0

    clock.now = 1000L
    assertFalse(q.offer(Announcement("cadeira à sua frente", dedupeKey = "cadeira"))) // dentro da janela

    clock.now = 3500L
    assertTrue(q.offer(Announcement("cadeira à sua frente", dedupeKey = "cadeira"))) // fora da janela
  }

  @Test
  fun verbosityFiltersAnnouncements() {
    val q = AnnouncementQueue()
    q.setVerbosity(Verbosity.MINIMAL)
    // exige NORMAL -> deve ser rejeitado em MINIMAL
    assertFalse(q.offer(Announcement("detalhe", minVerbosity = Verbosity.NORMAL)))
    // exige MINIMAL -> aceito
    assertTrue(q.offer(Announcement("essencial", minVerbosity = Verbosity.MINIMAL)))
  }

  @Test
  fun pollEmptyReturnsNull() {
    val q = AnnouncementQueue()
    assertNull(q.poll())
    assertTrue(q.isEmpty())
  }

  @Test
  fun stableOrderWithinSamePriority() {
    val q = AnnouncementQueue()
    q.offer(Announcement("a"))
    q.offer(Announcement("b"))
    q.offer(Announcement("c"))
    assertEquals("a", q.poll()?.text)
    assertEquals("b", q.poll()?.text)
    assertEquals("c", q.poll()?.text)
  }
}
