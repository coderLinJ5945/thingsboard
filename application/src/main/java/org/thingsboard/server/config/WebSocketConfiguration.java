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
package org.thingsboard.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.controller.plugin.TbWebSocketHandler;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.Map;

/**
 * websocket配置初始化入口类
 * implements:
 *      WebSocketConfigurer(作用：通过@EnableWebSocket定义回调方法来配置WebSocket请求处理)
 *
 */
@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    public static final String WS_PLUGIN_PREFIX = "/api/ws/plugins/";
    private static final String WS_PLUGIN_MAPPING = WS_PLUGIN_PREFIX + "**";

    /**
     * ServletServerContainerFactoryBean：个人理解，每个消息快递包裹箱的大小设置对象
     * 配置websocket 的ServerContainer的基础属性（服务器容量）
     * @return
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        //设置最大的缓存区大小，单位，byte、KB？
        container.setMaxTextMessageBufferSize(32768);
        //设置最大二进制消息缓冲区大小，单位，byte、KB？
        container.setMaxBinaryMessageBufferSize(32768);
        return container;
    }

    /**
     * 注册WebSocket通信的处理服务：回调实现（WebSocketConfigurer）
     * @param registry
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        //
        /**
         * 1. registry.addHandler()配置TbWebSocketHandler通信的处理类绑定映射到指定websocket地址(/api/ws/plugins/**)
         * 2. WebSocketHandlerRegistration.addInterceptors:配置握手请求的拦截器：HttpSessionHandshakeInterceptor 和  HandshakeInterceptor
         */
        registry.addHandler(wsHandler(), WS_PLUGIN_MAPPING).setAllowedOrigins("*")
                .addInterceptors(new HttpSessionHandshakeInterceptor(), new HandshakeInterceptor() {

                    /**
                     * 客户端和服务端握手前：获取并判断当前用户是否存在，存在则继续握手通信，不存在则终止
                     * @param request
                     * @param response
                     * @param wsHandler
                     * @param attributes
                     * @return  是否继续握手(true)或中止(false)
                     * @throws Exception
                     */
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                                   Map<String, Object> attributes) throws Exception {
                        SecurityUser user = null;
                        try {
                            user = getCurrentUser();
                        } catch (ThingsboardException ex) {}
                        if (user == null) {
                            response.setStatusCode(HttpStatus.UNAUTHORIZED);
                            return false;
                        } else {
                            //这里就是返回给前端连接Status Code: 101 状态码
                            return true;
                        }
                    }

                    /**
                     *
                     * @param request
                     * @param response
                     * @param wsHandler
                     * @param exception
                     */
                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                               Exception exception) {
                        //Do nothing
                    }
                });
    }

    @Bean
    public WebSocketHandler wsHandler() {
        return new TbWebSocketHandler();
    }

    /**
     * 获取当前用户信息（Spring-Security实现）
     * @return
     * @throws ThingsboardException
     */
    protected SecurityUser getCurrentUser() throws ThingsboardException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser) {
            return (SecurityUser) authentication.getPrincipal();
        } else {
            throw new ThingsboardException("You aren't authorized to perform this operation!", ThingsboardErrorCode.AUTHENTICATION);
        }
    }
}
