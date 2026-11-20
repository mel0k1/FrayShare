package com.frayshare.app.core.webshare

import com.frayshare.app.core.model.DataKind
import com.frayshare.app.core.model.TransferItem
import org.junit.Assert.assertEquals
import org.junit.Test

class WebListingTest {

    @Test
    fun buildsSequentialIdsAcrossGroups() {
        val json = WebListing.json(
            "Pixel",
            listOf(
                "Фото" to listOf(
                    TransferItem("p1", "a.jpg", DataKind.PHOTO, 100),
                    TransferItem("p2", "b.jpg", DataKind.PHOTO, 200)
                ),
                "Загрузки" to listOf(TransferItem("f1", "doc.pdf", DataKind.FILE, 50))
            )
        )
        assertEquals(
            "{\"device\":\"Pixel\",\"groups\":[" +
                "{\"title\":\"Фото\",\"items\":[" +
                "{\"id\":0,\"name\":\"a.jpg\",\"size\":100,\"kind\":\"PHOTO\"}," +
                "{\"id\":1,\"name\":\"b.jpg\",\"size\":200,\"kind\":\"PHOTO\"}]}," +
                "{\"title\":\"Загрузки\",\"items\":[" +
                "{\"id\":2,\"name\":\"doc.pdf\",\"size\":50,\"kind\":\"FILE\"}]}]}",
            json
        )
    }

    @Test
    fun escapesSpecialCharacters() {
        val json = WebListing.json(
            "",
            listOf(
                "" to listOf(TransferItem("x", "we\"ird\\na\tme.jpg", DataKind.FILE, 1))
            )
        )
        assertEquals(
            "{\"device\":\"\",\"groups\":[{\"title\":\"\",\"items\":[" +
                "{\"id\":0,\"name\":\"we\\\"ird\\\\na\\tme.jpg\",\"size\":1,\"kind\":\"FILE\"}]}]}",
            json
        )
    }

    @Test
    fun emptyGroupsProduceEmptyItems() {
        val json = WebListing.json("D", listOf("Фото" to emptyList()))
        assertEquals("{\"device\":\"D\",\"groups\":[{\"title\":\"Фото\",\"items\":[]}]}", json)
    }
}
