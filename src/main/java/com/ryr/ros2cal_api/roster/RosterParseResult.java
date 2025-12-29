package com.ryr.ros2cal_api.roster;

import java.util.Map;

public class RosterParseResult {
    private final Map<String, Object> data;
    private final CallUsage ocrUsage;
    private final CallUsage parseUsage;

    public RosterParseResult(Map<String, Object> data, CallUsage ocrUsage, CallUsage parseUsage) {
        this.data = data;
        this.ocrUsage = ocrUsage;
        this.parseUsage = parseUsage;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public CallUsage getOcrUsage() {
        return ocrUsage;
    }

    public CallUsage getParseUsage() {
        return parseUsage;
    }
}
