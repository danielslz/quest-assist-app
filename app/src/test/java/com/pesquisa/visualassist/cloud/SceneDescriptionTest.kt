package com.pesquisa.visualassist.cloud

import com.pesquisa.visualassist.vision.Detection
import com.pesquisa.visualassist.vision.Direction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneDescriptionTest {

  @Test
  fun buildRequestBody_hasModelImageAndPrompt() {
    val body = SceneDescriptionProtocol.buildRequestBody("llava", "QUJD", "oi")
    val json = JSONObject(body)
    assertEquals("llava", json.getString("model"))
    val content = json.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
    assertEquals("oi", content.getJSONObject(0).getString("text"))
    val url = content.getJSONObject(1).getJSONObject("image_url").getString("url")
    assertTrue(url.startsWith("data:image/jpeg;base64,QUJD"))
  }

  @Test
  fun parseResponse_extractsContent() {
    val json = """{"choices":[{"message":{"content":"Uma mesa à frente."}}]}"""
    assertEquals("Uma mesa à frente.", SceneDescriptionProtocol.parseResponse(json))
  }

  @Test
  fun parseResponse_malformedReturnsNull() {
    assertNull(SceneDescriptionProtocol.parseResponse("{\"erro\":1}"))
    assertNull(SceneDescriptionProtocol.parseResponse("não é json"))
  }

  @Test
  fun onDevice_emptyGivesFriendlyMessage() {
    assertEquals("Não identifiquei objetos à sua frente.", OnDeviceSceneDescriber.describe(emptyList()))
  }

  @Test
  fun onDevice_listsDetections() {
    val box = floatArrayOf(0.4f, 0.4f, 0.6f, 0.6f)
    val dets = listOf(
      Detection("cadeira", 0.9f, box, Direction.LEFT, Detection.Kind.OBJECT),
      Detection("mesa", 0.8f, box, Direction.CENTER, Detection.Kind.OBJECT),
    )
    val desc = OnDeviceSceneDescriber.describe(dets)
    assertTrue(desc.contains("cadeira à esquerda"))
    assertTrue(desc.contains("mesa à frente"))
  }

  @Test
  fun onDevice_groupsRepeatedLabels() {
    val box = floatArrayOf(0.4f, 0.4f, 0.6f, 0.6f)
    val dets = listOf(
      Detection("cadeira", 0.9f, box, Direction.RIGHT, Detection.Kind.OBJECT),
      Detection("cadeira", 0.8f, box, Direction.RIGHT, Detection.Kind.OBJECT),
    )
    val desc = OnDeviceSceneDescriber.describe(dets)
    assertTrue(desc.contains("2 cadeiras à direita"))
  }
}
