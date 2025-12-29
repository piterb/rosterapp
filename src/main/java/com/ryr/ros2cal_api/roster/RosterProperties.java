package com.ryr.ros2cal_api.roster;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.roster")
public class RosterProperties {

    /**
     * Local timezone used for ICS descriptions.
     */
    private String localTz = "Europe/Berlin";

    /**
     * Calendar name used for ICS PRODID.
     */
    private String calendarName = "Roster";

    public String getLocalTz() {
        return localTz;
    }

    public void setLocalTz(String localTz) {
        this.localTz = localTz;
    }

    public String getCalendarName() {
        return calendarName;
    }

    public void setCalendarName(String calendarName) {
        this.calendarName = calendarName;
    }
}
