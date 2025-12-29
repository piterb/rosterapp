package com.ryr.ros2cal_api.roster;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class RosterIcsExporter {

    private static final Map<String, String> COLOR_MAP = Map.of(
            "FLIGHT", "#4285F4",
            "DH", "#DB4437",
            "HSBY", "#F4B400",
            "A/L", "#0F9D58");

    private static final DateTimeFormatter ICS_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    public String jsonToIcs(Map<String, Object> roster, String calendarName, String localTz) {
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//" + calendarName + "//RosterToICS//EN");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:PUBLISH");

        Object eventsObj = roster.get("events");
        if (eventsObj instanceof List<?> events) {
            for (Object eventObj : events) {
                if (eventObj instanceof Map<?, ?> eventMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> event = (Map<String, Object>) eventMap;
                    lines.addAll(eventToIcs(event, calendarName, localTz));
                }
            }
        }

        lines.add("END:VCALENDAR");
        return String.join("\r\n", lines);
    }

    private List<String> eventToIcs(Map<String, Object> event, String calendarName, String localTz) {
        List<String> lines = new ArrayList<>();
        String dutyType = stringValue(event.get("duty_type"), "DUTY");
        String startUtc = stringValue(event.get("start_utc"), null);
        String endUtc = stringValue(event.get("end_utc"), null);
        if (startUtc == null || endUtc == null) {
            return lines;
        }

        OffsetDateTime start = parseUtc(startUtc);
        OffsetDateTime end = parseUtc(endUtc);
        String uid = dutyType + "|" + startUtc + "|" + endUtc;

        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + uid);
        lines.add("DTSTAMP:" + ICS_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.UTC)));

        boolean isAllDay = booleanValue(event.get("is_all_day"), false) || "A/L".equals(dutyType);
        if (isAllDay) {
            LocalDate startDate = start.toLocalDate();
            LocalDate endDate = startDate.plusDays(1);
            lines.add("DTSTART;VALUE=DATE:" + ICS_DATE.format(startDate));
            lines.add("DTEND;VALUE=DATE:" + ICS_DATE.format(endDate));
        } else {
            lines.add("DTSTART:" + ICS_DATE_TIME.format(start));
            lines.add("DTEND:" + ICS_DATE_TIME.format(end));
        }

        lines.add("SUMMARY:" + escapeIcs(dutyType));
        lines.add("DESCRIPTION:" + escapeIcs(buildDescription(event, localTz)));
        String color = COLOR_MAP.get(dutyType);
        if (color != null) {
            lines.add("COLOR:" + color);
        }
        lines.add("END:VEVENT");
        return lines;
    }

    private String buildDescription(Map<String, Object> event, String localTz) {
        String dutyType = stringValue(event.get("duty_type"), "");
        OffsetDateTime start = parseUtc(stringValue(event.get("start_utc"), ""));
        OffsetDateTime end = parseUtc(stringValue(event.get("end_utc"), ""));

        String checkinZ = formatTimeZ(start);
        String checkoutZ = formatTimeZ(end);
        String checkinLt = formatTimeLocal(start, localTz);
        String checkoutLt = formatTimeLocal(end, localTz);

        List<String> lines = new ArrayList<>();

        if ("FLIGHT".equals(dutyType) || "DH".equals(dutyType)) {
            lines.add("CHECK-IN " + checkinZ + " (" + checkinLt + ")");
            Object flightsObj = event.get("flights");
            if (flightsObj instanceof List<?> flights) {
                for (Object flightObj : flights) {
                    if (flightObj instanceof Map<?, ?> flightMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> flight = (Map<String, Object>) flightMap;
                        String fn = stringValue(flight.get("flight_number"), "UNKNOWN");
                        String depAp = stringValue(flight.get("departure_airport"), "???");
                        String arrAp = stringValue(flight.get("arrival_airport"), "???");
                        OffsetDateTime dep = parseUtc(stringValue(flight.get("departure_time_utc"), ""));
                        OffsetDateTime arr = parseUtc(stringValue(flight.get("arrival_time_utc"), ""));
                        lines.add(fn + " " + depAp + " " + formatTimeZ(dep) + " " + arrAp + " " + formatTimeZ(arr));
                    }
                }
            }
            lines.add("CHECK-OUT " + checkoutZ + " (" + checkoutLt + ")");
        } else if ("HSBY".equals(dutyType)) {
            lines.add("Standby (HSBY)");
            lines.add(checkinZ + " – " + checkoutZ + " (" + checkinLt + " – " + checkoutLt + ")");
            String location = stringValue(event.get("location"), null);
            if (location != null && !location.isBlank()) {
                lines.add("Location: " + location);
            }
        } else if ("A/L".equals(dutyType)) {
            lines.add("Annual leave");
        } else {
            lines.add("Duty: " + dutyType);
            lines.add("CHECK-IN " + checkinZ + " (" + checkinLt + ")");
            Object activitiesObj = event.get("activities");
            if (activitiesObj instanceof List<?> activities) {
                for (Object activityObj : activities) {
                    if (activityObj instanceof Map<?, ?> activityMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> activity = (Map<String, Object>) activityMap;
                        String startPlace = stringValue(activity.get("start_place"), "???");
                        String endPlace = stringValue(activity.get("end_place"), "???");
                        OffsetDateTime startAct = parseUtc(stringValue(activity.get("start_time_utc"), ""));
                        OffsetDateTime endAct = parseUtc(stringValue(activity.get("end_time_utc"), ""));
                        lines.add(startPlace + " " + formatTimeZ(startAct) + " -> " + endPlace + " " + formatTimeZ(endAct));
                    }
                }
            }
            lines.add("CHECK-OUT " + checkoutZ + " (" + checkoutLt + ")");
        }
        return String.join("\n", lines);
    }

    private OffsetDateTime parseUtc(String value) {
        if (value == null) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (value.endsWith("Z")) {
            value = value.replace("Z", "+00:00");
        }
        return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private String formatTimeZ(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HH:mm")) + "z";
    }

    private String formatTimeLocal(OffsetDateTime value, String localTz) {
        ZoneId zone = ZoneId.of(localTz);
        return value.atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("HH:mm")) + " LT";
    }

    private String escapeIcs(String text) {
        if (text == null) {
            return "";
        }
        String escaped = text.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;");
        return escaped.replace("\n", "\\n");
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return value.toString();
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return fallback;
    }
}
