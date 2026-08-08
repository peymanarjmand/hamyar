package com.hesabyar.garage.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperContactTest {
    @Test fun `developer messenger links point to requested public ids`() {
        assertEquals("@peyman_arjmand", DeveloperContact.BALE_ID)
        assertEquals("https://ble.ir/peyman_arjmand", DeveloperContact.bale.primaryUri)
        assertEquals("@Peymanarjmand77", DeveloperContact.TELEGRAM_ID)
        assertEquals("tg://resolve?domain=Peymanarjmand77", DeveloperContact.telegram.primaryUri)
        assertTrue(DeveloperContact.telegram.fallbackUri.startsWith("https://t.me/"))
    }
}
