/**
 * Copyright © 2016-2020 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.controller.plugin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.msg.tools.TbRateLimits;
import org.thingsboard.server.config.WebSocketConfiguration;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.model.UserPrincipal;
import org.thingsboard.server.service.telemetry.SessionEvent;
import org.thingsboard.server.service.telemetry.TelemetryWebSocketMsgEndpoint;
import org.thingsboard.server.service.telemetry.TelemetryWebSocketService;
import org.thingsboard.server.service.telemetry.TelemetryWebSocketSessionRef;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.security.InvalidParameterException;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * websocket服务处理程序（主要负责websocket相关的通讯信息）
 * extends：
 *      TextWebSocketHandler：一个方便的WebSocketHandler实现的基类，它只处理文本消息
 * implements：
 *      TelemetryWebSocketMsgEndpoint：监测-websocket-消息通信接口：发送消息、关闭通道方法接口
 *
 * websocket通信流程：
 * 1. 触发入口：调用 ws://localhost:8080/api/ws/plugins/telemetry
 *
 */
@Service
@Slf4j
public class TbWebSocketHandler extends TextWebSocketHandler implements TelemetryWebSocketMsgEndpoint {

    //内部会话id集合和外部会话id集合：用于控制websocket会话允许的最大连接？
    private static final ConcurrentMap<String, SessionMetaData> internalSessionMap = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> externalSessionMap = new ConcurrentHashMap<>();

    @Autowired
    private TelemetryWebSocketService webSocketService;

    @Value("${server.ws.send_timeout:5000}")
    private long sendTimeout;
    //限制每个服务器上可用的会话和订阅的数量。将值设置为0以禁用特定的限制
    @Value("${server.ws.limits.max_sessions_per_tenant:0}")
    private int maxSessionsPerTenant;
    @Value("${server.ws.limits.max_sessions_per_customer:0}")
    private int maxSessionsPerCustomer;
    @Value("${server.ws.limits.max_sessions_per_regular_user:0}")
    private int maxSessionsPerRegularUser;
    @Value("${server.ws.limits.max_sessions_per_public_user:0}")
    private int maxSessionsPerPublicUser;

    //websocket允许的最大连接数
    @Value("${server.ws.limits.max_queue_per_ws_session:1000}")
    private int maxMsgQueuePerSession;

    @Value("${server.ws.limits.max_updates_per_session:}")
    private String perSessionUpdatesConfiguration;

    private ConcurrentMap<String, TelemetryWebSocketSessionRef> blacklistedSessions = new ConcurrentHashMap<>();
    private ConcurrentMap<String, TbRateLimits> perSessionUpdateLimits = new ConcurrentHashMap<>();

    /**
     * 分角色存储的不同用户的session集合
     * 作用：用于做判断限制每个服务器上可用的会话和订阅的数，来起到限制websocket会话数量的作用
     */
    private ConcurrentMap<TenantId, Set<String>> tenantSessionsMap = new ConcurrentHashMap<>();
    private ConcurrentMap<CustomerId, Set<String>> customerSessionsMap = new ConcurrentHashMap<>();
    private ConcurrentMap<UserId, Set<String>> regularUserSessionsMap = new ConcurrentHashMap<>();
    private ConcurrentMap<UserId, Set<String>> publicUserSessionsMap = new ConcurrentHashMap<>();

    /**
     * 接收处理web发送的指令：{"tsSubCmds":[{"entityType":"DEVICE","entityId":"16afb310-8a21-11ec-9898-ddf00ff45e55","scope":"LATEST_TELEMETRY","cmdId":2}],"historyCmds":[],"attrSubCmds":[{"entityType":"DEVICE","entityId":"16afb310-8a21-11ec-9898-ddf00ff45e55","scope":"CLIENT_SCOPE","cmdId":1}]}
     * 当新的WebSocket消息到达时调用
     * @param session
     * @param message
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            //获取session，session不为空则传递按照业务处理msg
            SessionMetaData sessionMd = internalSessionMap.get(session.getId());
            if (sessionMd != null) {
                log.info("[{}][{}] Processing {}", sessionMd.sessionRef.getSecurityCtx().getTenantId(), session.getId(), message.getPayload());
                //获取到前端的查询命令，处理业务逻辑方法：TelemetryWebSocketService.handleWebSocketMsg
                webSocketService.handleWebSocketMsg(sessionMd.sessionRef, message.getPayload());
            } else {
                log.warn("[{}] Failed to find session", session.getId());
                session.close(CloseStatus.SERVER_ERROR.withReason("Session not found!"));
            }
        } catch (IOException e) {
            log.warn("IO error", e);
        }
    }

    /**
     * WebSocket协商（握手）成功后调用，WebSocket连接打开并准备使用
     * 作用：校验是否是合法session用户，管理用户session（绑定、限制连接等）
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        try {
            if (session instanceof NativeWebSocketSession) {
                Session nativeSession = ((NativeWebSocketSession) session).getNativeSession(Session.class);
                if (nativeSession != null) {
                    nativeSession.getAsyncRemote().setSendTimeout(sendTimeout);
                }
            }
            //internalSessionId 内部会话id，指的是服务内部的sessionid
            //externalSessionId 外部会话id，指的是用户的会话id
            //todo 这两个id有什么用？为什么要绑定
            String internalSessionId = session.getId();
            TelemetryWebSocketSessionRef sessionRef = toRef(session);
            String externalSessionId = sessionRef.getSessionId();
            //检查限制每个服务器上可用的会话和订阅的数量。将max_sessions_per值设置为0以禁用特定的限制
            if (!checkLimits(session, sessionRef)) {
                return;
            }
            //内部会话id绑定到SessionMetaData
            internalSessionMap.put(internalSessionId, new SessionMetaData(session, sessionRef, maxMsgQueuePerSession));
            //将外部sessionid绑定到内部会话id集合上
            externalSessionMap.put(externalSessionId, internalSessionId);
            /*
               从web发起建立连接的事件类型：onEstablished
             */
            processInWebSocketService(sessionRef, SessionEvent.onEstablished());
            log.info("[{}][{}][{}] Session is opened", sessionRef.getSecurityCtx().getTenantId(), externalSessionId, session.getId());
        } catch (InvalidParameterException e) {
            log.warn("[{}] Failed to start session", session.getId(), e);
            session.close(CloseStatus.BAD_DATA.withReason(e.getMessage()));
        } catch (Exception e) {
            log.warn("[{}] Failed to start session", session.getId(), e);
            session.close(CloseStatus.SERVER_ERROR.withReason(e.getMessage()));
        }
    }

    /**
     * 处理来自底层WebSocket消息传输的错误
     * @param session
     * @param tError
     * @throws Exception
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable tError) throws Exception {
        super.handleTransportError(session, tError);
        SessionMetaData sessionMd = internalSessionMap.get(session.getId());
        if (sessionMd != null) {
            processInWebSocketService(sessionMd.sessionRef, SessionEvent.onError(tError));
        } else {
            log.warn("[{}] Failed to find session", session.getId());
        }
        log.trace("[{}] Session transport error", session.getId(), tError);
    }

    /**
     *
     * 在WebSocket连接被任何一方关闭后，或者在传输错误发生后调用
     * @param session
     * @param closeStatus
     * @throws Exception
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        super.afterConnectionClosed(session, closeStatus);
        SessionMetaData sessionMd = internalSessionMap.remove(session.getId());
        if (sessionMd != null) {
            cleanupLimits(session, sessionMd.sessionRef);
            externalSessionMap.remove(sessionMd.sessionRef.getSessionId());
            processInWebSocketService(sessionMd.sessionRef, SessionEvent.onClosed());
        }
        log.info("[{}] Session is closed", session.getId());
    }

    /**
     * 从ui进来的websocket连接session事件
     * @param sessionRef
     * @param event
     */
    private void processInWebSocketService(TelemetryWebSocketSessionRef sessionRef, SessionEvent event) {
        try {
            //发起WebSocketsession事件，事件类型：发起连接、关闭连接 or error
            webSocketService.handleWebSocketSessionEvent(sessionRef, event);
        } catch (BeanCreationNotAllowedException e) {
            log.warn("[{}] Failed to close session due to possible shutdown state", sessionRef.getSessionId());
        }
    }

    /**
     * 从WebSocketSession 获取websocket的url，再从url中末尾中截取关键字
     * 如果关键字匹配telemetry，则创建并返回TelemetryWebSocketSessionRef
     * @param session
     * @return
     * @throws IOException
     */
    private TelemetryWebSocketSessionRef toRef(WebSocketSession session) throws IOException {
        URI sessionUri = session.getUri();
        String path = sessionUri.getPath();
        path = path.substring(WebSocketConfiguration.WS_PLUGIN_PREFIX.length());
        if (path.length() == 0) {
            throw new IllegalArgumentException("URL should contain plugin token!");
        }
        String[] pathElements = path.split("/");
        String serviceToken = pathElements[0];
        if (!"telemetry".equalsIgnoreCase(serviceToken)) {
            throw new InvalidParameterException("Can't find plugin with specified token!");
        } else {
            SecurityUser currentUser = (SecurityUser) ((Authentication) session.getPrincipal()).getPrincipal();
            //LocalAddress:/0:0:0:0:0:0:0:1:8080
            //RemoteAddress:/0:0:0:0:0:0:0:1:53922
            return new TelemetryWebSocketSessionRef(UUID.randomUUID().toString(), currentUser, session.getLocalAddress(), session.getRemoteAddress());
        }
    }

    /**
     * Session元对象实体
     * implements：
     *      SendHandler：接口提供onResult方法接收处理消息结果
     */
    private class SessionMetaData implements SendHandler {
        private final WebSocketSession session;
        private final RemoteEndpoint.Async asyncRemote;
        private final TelemetryWebSocketSessionRef sessionRef;

        private volatile boolean isSending = false;
        private final Queue<String> msgQueue;

        SessionMetaData(WebSocketSession session, TelemetryWebSocketSessionRef sessionRef, int maxMsgQueuePerSession) {
            super();
            this.session = session;
            Session nativeSession = ((NativeWebSocketSession) session).getNativeSession(Session.class);
            this.asyncRemote = nativeSession.getAsyncRemote();
            this.sessionRef = sessionRef;
            this.msgQueue = new LinkedBlockingQueue<>(maxMsgQueuePerSession);
        }

        synchronized void sendMsg(String msg) {
            if (isSending) {
                try {
                    msgQueue.add(msg);
                } catch (RuntimeException e) {
                    if (log.isTraceEnabled()) {
                        log.trace("[{}][{}] Session closed due to queue error", sessionRef.getSecurityCtx().getTenantId(), session.getId(), e);
                    } else {
                        log.info("[{}][{}] Session closed due to queue error", sessionRef.getSecurityCtx().getTenantId(), session.getId());
                    }
                    try {
                        close(sessionRef, CloseStatus.POLICY_VIOLATION.withReason("Max pending updates limit reached!"));
                    } catch (IOException ioe) {
                        log.trace("[{}] Session transport error", session.getId(), ioe);
                    }
                }
            } else {
                isSending = true;
                sendMsgInternal(msg);
            }
        }

        private void sendMsgInternal(String msg) {
            try {
                this.asyncRemote.sendText(msg, this);
            } catch (Exception e) {
                log.trace("[{}] Failed to send msg", session.getId(), e);
                try {
                    close(this.sessionRef, CloseStatus.SESSION_NOT_RELIABLE);
                } catch (IOException ioe) {
                    log.trace("[{}] Session transport error", session.getId(), ioe);
                }
            }
        }

        @Override
        public void onResult(SendResult result) {
            if (!result.isOK()) {
                log.trace("[{}] Failed to send msg", session.getId(), result.getException());
                try {
                    close(this.sessionRef, CloseStatus.SESSION_NOT_RELIABLE);
                } catch (IOException ioe) {
                    log.trace("[{}] Session transport error", session.getId(), ioe);
                }
            } else {
                String msg = msgQueue.poll();
                if (msg != null) {
                    sendMsgInternal(msg);
                } else {
                    isSending = false;
                }
            }
        }
    }

    @Override
    public void send(TelemetryWebSocketSessionRef sessionRef, int subscriptionId, String msg) throws IOException {
        String externalId = sessionRef.getSessionId();
        log.debug("[{}] Processing {}", externalId, msg);
        String internalId = externalSessionMap.get(externalId);
        if (internalId != null) {
            SessionMetaData sessionMd = internalSessionMap.get(internalId);
            if (sessionMd != null) {
                if (!StringUtils.isEmpty(perSessionUpdatesConfiguration)) {
                    TbRateLimits rateLimits = perSessionUpdateLimits.computeIfAbsent(sessionRef.getSessionId(), sid -> new TbRateLimits(perSessionUpdatesConfiguration));
                    if (!rateLimits.tryConsume()) {
                        if (blacklistedSessions.putIfAbsent(externalId, sessionRef) == null) {
                            log.info("[{}][{}][{}] Failed to process session update. Max session updates limit reached"
                                    , sessionRef.getSecurityCtx().getTenantId(), sessionRef.getSecurityCtx().getId(), externalId);
                            sessionMd.sendMsg("{\"subscriptionId\":" + subscriptionId + ", \"errorCode\":" + ThingsboardErrorCode.TOO_MANY_UPDATES.getErrorCode() + ", \"errorMsg\":\"Too many updates!\"}");
                        }
                        return;
                    } else {
                        log.debug("[{}][{}][{}] Session is no longer blacklisted.", sessionRef.getSecurityCtx().getTenantId(), sessionRef.getSecurityCtx().getId(), externalId);
                        blacklistedSessions.remove(externalId);
                    }
                }
                sessionMd.sendMsg(msg);
            } else {
                log.warn("[{}][{}] Failed to find session by internal id", externalId, internalId);
            }
        } else {
            log.warn("[{}] Failed to find session by external id", externalId);
        }
    }

    @Override
    public void close(TelemetryWebSocketSessionRef sessionRef, CloseStatus reason) throws IOException {
        String externalId = sessionRef.getSessionId();
        log.debug("[{}] Processing close request", externalId);
        String internalId = externalSessionMap.get(externalId);
        if (internalId != null) {
            SessionMetaData sessionMd = internalSessionMap.get(internalId);
            if (sessionMd != null) {
                sessionMd.session.close(reason);
            } else {
                log.warn("[{}][{}] Failed to find session by internal id", externalId, internalId);
            }
        } else {
            log.warn("[{}] Failed to find session by external id", externalId);
        }
    }

    /**
     * 检查限制每个服务器上可用的会话和订阅的数量。将max_sessions_per值设置为0以禁用特定的限制
     * 根据角色来：
     * tenantSessionsMap：租户角色
     * customerSessionsMap：客户角色
     * regularUserSessionsMap：普通用户角色
     * publicUserSessionsMap：开放用户角色
     * @param session
     * @param sessionRef
     * @return
     * @throws Exception
     */
    private boolean checkLimits(WebSocketSession session, TelemetryWebSocketSessionRef sessionRef) throws Exception {
        String sessionId = session.getId();
        /*
         * 1.如果限制会话和订阅的数量大于0，
         * 2.去ConcurrentMap获取对应的用户id是否存在
         * 3.如果当前的session个数小于限制的个数，则添加当前用户到Map，并返回true。反之则返回false
         */
        if (maxSessionsPerTenant > 0) {
            Set<String> tenantSessions = tenantSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getTenantId(), id -> ConcurrentHashMap.newKeySet());
            synchronized (tenantSessions) {
                if (tenantSessions.size() < maxSessionsPerTenant) {
                    tenantSessions.add(sessionId);
                } else {
                    log.info("[{}][{}][{}] Failed to start session. Max tenant sessions limit reached"
                            , sessionRef.getSecurityCtx().getTenantId(), sessionRef.getSecurityCtx().getId(), sessionId);
                    session.close(CloseStatus.POLICY_VIOLATION.withReason("Max tenant sessions limit reached!"));
                    return false;
                }
            }
        }

        if (sessionRef.getSecurityCtx().isCustomerUser()) {
            if (maxSessionsPerCustomer > 0) {
                Set<String> customerSessions = customerSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getCustomerId(), id -> ConcurrentHashMap.newKeySet());
                synchronized (customerSessions) {
                    if (customerSessions.size() < maxSessionsPerCustomer) {
                        customerSessions.add(sessionId);
                    } else {
                        log.info("[{}][{}][{}] Failed to start session. Max customer sessions limit reached"
                                , sessionRef.getSecurityCtx().getTenantId(), sessionRef.getSecurityCtx().getId(), sessionId);
                        session.close(CloseStatus.POLICY_VIOLATION.withReason("Max customer sessions limit reached"));
                        return false;
                    }
                }
            }
            if (maxSessionsPerRegularUser > 0 && UserPrincipal.Type.USER_NAME.equals(sessionRef.getSecurityCtx().getUserPrincipal().getType())) {
                Set<String> regularUserSessions = regularUserSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getId(), id -> ConcurrentHashMap.newKeySet());
                synchronized (regularUserSessions) {
                    if (regularUserSessions.size() < maxSessionsPerRegularUser) {
                        regularUserSessions.add(sessionId);
                    } else {
                        log.info("[{}][{}][{}] Failed to start session. Max regular user sessions limit reached"
                                , sessionRef.getSecurityCtx().getTenantId(), sessionRef.getSecurityCtx().getId(), sessionId);
                        session.close(CloseStatus.POLICY_VIOLATION.withReason("Max regular user sessions limit reached"));
                        return false;
                    }
                }
            }
            if (maxSessionsPerPublicUser > 0 && UserPrincipal.Type.PUBLIC_ID.equals(sessionRef.getSecurityCtx().getUserPrincipal().getType())) {
                Set<String> publicUserSessions = publicUserSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getId(), id -> ConcurrentHashMap.newKeySet());
                synchronized (publicUserSessions) {
                    if (publicUserSessions.size() < maxSessionsPerPublicUser) {
                        publicUserSessions.add(sessionId);
                    } else {
                        log.info("[{}][{}][{}] Failed to start session. Max public user sessions limit reached"
                                , sessionRef.getSecurityCtx().getTenantId(), sessionRef.getSecurityCtx().getId(), sessionId);
                        session.close(CloseStatus.POLICY_VIOLATION.withReason("Max public user sessions limit reached"));
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void cleanupLimits(WebSocketSession session, TelemetryWebSocketSessionRef sessionRef) {
        String sessionId = session.getId();
        perSessionUpdateLimits.remove(sessionRef.getSessionId());
        blacklistedSessions.remove(sessionRef.getSessionId());
        if (maxSessionsPerTenant > 0) {
            Set<String> tenantSessions = tenantSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getTenantId(), id -> ConcurrentHashMap.newKeySet());
            synchronized (tenantSessions) {
                tenantSessions.remove(sessionId);
            }
        }
        if (sessionRef.getSecurityCtx().isCustomerUser()) {
            if (maxSessionsPerCustomer > 0) {
                Set<String> customerSessions = customerSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getCustomerId(), id -> ConcurrentHashMap.newKeySet());
                synchronized (customerSessions) {
                    customerSessions.remove(sessionId);
                }
            }
            if (maxSessionsPerRegularUser > 0 && UserPrincipal.Type.USER_NAME.equals(sessionRef.getSecurityCtx().getUserPrincipal().getType())) {
                Set<String> regularUserSessions = regularUserSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getId(), id -> ConcurrentHashMap.newKeySet());
                synchronized (regularUserSessions) {
                    regularUserSessions.remove(sessionId);
                }
            }
            if (maxSessionsPerPublicUser > 0 && UserPrincipal.Type.PUBLIC_ID.equals(sessionRef.getSecurityCtx().getUserPrincipal().getType())) {
                Set<String> publicUserSessions = publicUserSessionsMap.computeIfAbsent(sessionRef.getSecurityCtx().getId(), id -> ConcurrentHashMap.newKeySet());
                synchronized (publicUserSessions) {
                    publicUserSessions.remove(sessionId);
                }
            }
        }
    }

}