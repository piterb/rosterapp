package com.ryr.ros2cal_api.roster;

public class CallUsage {
    private int inputTokens;
    private int outputTokens;
    private int cachedInputTokens;
    private int cachedOutputTokens;
    private Integer totalTokens;

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }

    public int getCachedInputTokens() {
        return cachedInputTokens;
    }

    public void setCachedInputTokens(int cachedInputTokens) {
        this.cachedInputTokens = cachedInputTokens;
    }

    public int getCachedOutputTokens() {
        return cachedOutputTokens;
    }

    public void setCachedOutputTokens(int cachedOutputTokens) {
        this.cachedOutputTokens = cachedOutputTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public int getEffectiveTotal() {
        if (totalTokens != null) {
            return totalTokens;
        }
        return Math.max(0, inputTokens) + Math.max(0, outputTokens);
    }
}
