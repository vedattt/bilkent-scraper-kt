package srs.data

import TestClass
import json
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import java.io.FileNotFoundException

internal class CurriculumTest : TestClass() {
    companion object {
        private lateinit var sample: String

        @BeforeAll
        @JvmStatic
        fun setUp() = runBlocking {
            sample = readSample("/srs/curriculum.html")
        }
    }

    @Test
    fun parseCurriculum() {
        val curriculum = parseCurriculum(Jsoup.parse(sample))
        println(json(curriculum))
    }
}
