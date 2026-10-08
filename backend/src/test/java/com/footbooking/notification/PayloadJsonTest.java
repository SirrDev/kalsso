package com.footbooking.notification;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PayloadJsonTest {

    @Test
    void readsTheFormatPostgresReturns() {
        // jsonb renvoie des espaces apres ':' et ','
        Map<String, String> m = PayloadJson.parse(
                "{\"end\": \"2026-10-26T22:00:00Z\", \"price\": \"5000.00\", \"currency\": \"XOF\"}");

        assertEquals(3, m.size());
        assertEquals("2026-10-26T22:00:00Z", m.get("end"));
        assertEquals("5000.00", m.get("price"));
        assertEquals("XOF", m.get("currency"));
    }

    @Test
    void emptyObjectOrNull_givesAnEmptyMap() {
        assertTrue(PayloadJson.parse("{}").isEmpty());
        assertTrue(PayloadJson.parse(" { } ").isEmpty());
        assertTrue(PayloadJson.parse(null).isEmpty());
    }

    @Test
    void readsEscapes() {
        Map<String, String> m = PayloadJson.parse("{\"note\":\"a\\\"b\\nc\\\\d\\u0041\"}");

        assertEquals("a\"b\nc\\dA", m.get("note"));
    }

    @Test
    void roundTripsWithNotificationServiceToJson() {
        Map<String, String> in = new LinkedHashMap<>();
        in.put("terrain", "Terrain \"1\"");
        in.put("note", "ligne1\nligne2\t!");

        assertEquals(in, PayloadJson.parse(NotificationService.toJson(in)));
    }

    @Test
    void invalidJson_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> PayloadJson.parse("{\"a\":"));
        assertThrows(IllegalArgumentException.class, () -> PayloadJson.parse("[1,2]"));
        assertThrows(IllegalArgumentException.class, () -> PayloadJson.parse("{\"a\":\"b\" \"c\":\"d\"}"));
    }
}