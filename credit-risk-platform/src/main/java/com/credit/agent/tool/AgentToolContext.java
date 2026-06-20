package com.credit.agent.tool;

public final class AgentToolContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private AgentToolContext() {
    }

    public static void set(String sessionId, String traceId) {
        set(sessionId, traceId, null, null);
    }

    public static void set(String sessionId, String traceId, Long userId, String agentName) {
        Context ctx = new Context();
        ctx.sessionId = sessionId;
        ctx.traceId = traceId;
        ctx.userId = userId;
        ctx.agentName = agentName;
        HOLDER.set(ctx);
    }

    public static Context get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static class Context {
        private String sessionId;
        private String traceId;
        private Long userId;
        private String agentName;

        public String getSessionId() {
            return sessionId;
        }

        public String getTraceId() {
            return traceId;
        }

        public Long getUserId() {
            return userId;
        }

        public String getAgentName() {
            return agentName;
        }
    }
}
