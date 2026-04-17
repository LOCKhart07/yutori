package com.spendwise.update

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class VersionComparatorTest {
    @Test
    fun `remote newer returns true`() {
        VersionComparator.hasUpdate("0.2.0", "v0.3.0") shouldBe true
    }

    @Test
    fun `remote equal returns false`() {
        VersionComparator.hasUpdate("0.2.0", "v0.2.0") shouldBe false
    }

    @Test
    fun `remote older returns false`() {
        VersionComparator.hasUpdate("0.3.0", "v0.2.0") shouldBe false
    }

    @Test
    fun `patch bump returns true`() {
        VersionComparator.hasUpdate("0.2.0", "v0.2.1") shouldBe true
    }

    @Test
    fun `compares components as ints not lexicographically`() {
        VersionComparator.hasUpdate("0.2.0", "v0.10.0") shouldBe true
    }

    @Test
    fun `leading v on remote is stripped`() {
        VersionComparator.hasUpdate("0.2.0", "v0.2.1") shouldBe true
        VersionComparator.hasUpdate("0.2.0", "0.2.1") shouldBe true
    }

    @Test
    fun `prerelease remote tag treated as not-an-update`() {
        VersionComparator.hasUpdate("0.2.0", "v0.3.0-beta") shouldBe false
    }

    @Test
    fun `dev-build current version treated as not-an-update`() {
        VersionComparator.hasUpdate("0.0.0-dev+38", "v0.3.0") shouldBe false
    }

    @Test
    fun `empty strings return false`() {
        VersionComparator.hasUpdate("", "v0.3.0") shouldBe false
        VersionComparator.hasUpdate("0.2.0", "") shouldBe false
        VersionComparator.hasUpdate("", "") shouldBe false
    }

    @Test
    fun `garbage remote tag returns false`() {
        VersionComparator.hasUpdate("0.2.0", "not-a-version") shouldBe false
    }

    @Test
    fun `extra component on remote counts as newer`() {
        VersionComparator.hasUpdate("0.2.0", "v0.2.0.1") shouldBe true
    }

    @Test
    fun `shorter remote with lower component still returns false`() {
        VersionComparator.hasUpdate("0.2.0.1", "v0.2.0") shouldBe false
    }
}
