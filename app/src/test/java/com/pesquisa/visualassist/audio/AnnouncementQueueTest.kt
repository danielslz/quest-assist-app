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

    assertTrue(q.offer(Announcement("cadeira à sua frente", dedupeKey = "cadeira"))) // t=0 registra
    assertEquals("cadeira à sua frente", q.poll()?.text)

    clock.now = 1000L
    assertFalse(q.offer(Announcement("cadeira à sua frente", dedupeKey = "cadeira"))) // dentro da janela

    clock.now = 3500L
    assertTrue(q.offer(Announcement("cadeira à sua frente", dedupeKey = "cadeira"))) // fora da janela
  }

  @Test
  fun doesNotEnqueueDuplicatePendingLabel() {
    // Sem poll entre os offers: o segundo "notebook" deve ser rejeitado
    // (evita encher a fila com o mesmo rótulo repetido a cada frame).
    val q = AnnouncementQueue()
    assertTrue(q.offer(Announcement("notebook à sua frente", dedupeKey = "notebook")))
    assertFalse(q.offer(Announcement("notebook à sua frente", dedupeKey = "notebook")))
    assertEquals(1, q.size())
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

  @Test
  fun expiredAnnouncementsAreDiscardedOnPoll() {
    val clock = FakeClock(0L)
    val q = AnnouncementQueue(clock = { clock.now })
    q.offer(Announcement("cadeira à sua frente", dedupeKey = "cadeira", ttlMs = 2000L))
    // avança além do TTL
    clock.now = 3000L
    assertNull(q.poll()) // expirou, não fala detecção velha
  }

  @Test
  fun recentAnnouncementSpokenBeforeExpiry() {
    val clock = FakeClock(0L)
    val q = AnnouncementQueue(clock = { clock.now })
    q.offer(Announcement("mesa à sua frente", dedupeKey = "mesa", ttlMs = 2000L))
    clock.now = 1000L // dentro do TTL
    assertEquals("mesa à sua frente", q.poll()?.text)
  }

  @Test
  fun systemMessageNeverExpires() {
    val clock = FakeClock(0L)
    val q = AnnouncementQueue(clock = { clock.now })
    q.offer(Announcement("Assistente visual iniciado.", priority = AudioPriority.HIGH, ttlMs = null))
    clock.now = 60000L // muito depois
    assertEquals("Assistente visual iniciado.", q.poll()?.text)
  }

  @Test
  fun fullQueueRejectsLowerOrEqualPriority() {
    val q = AnnouncementQueue(maxQueue = 2)
    assertTrue(q.offer(Announcement("obj1", dedupeKey = "1")))
    assertTrue(q.offer(Announcement("obj2", dedupeKey = "2")))
    // fila cheia (2); NORMAL não entra
    assertFalse(q.offer(Announcement("obj3", dedupeKey = "3")))
    // HIGH entra, removendo o mais fraco
    assertTrue(q.offer(Announcement("urgente", priority = AudioPriority.HIGH, dedupeKey = "4")))
    assertEquals(2, q.size())
  }
}
