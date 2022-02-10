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
package org.thingsboard.server.service.telemetry;

import org.springframework.web.socket.CloseStatus;

import java.io.IOException;

/**
 * Created by ashvayka on 27.03.18.
 * root-接口：监测-websocket-消息通信接口
 */
public interface TelemetryWebSocketMsgEndpoint {

    /**
     * 发送指令消息命令方法
     * @param sessionRef websocket会话对象
     * @param subscriptionId 消息命令的订阅id（根据订阅ID返回对应的数据）
     * @param msg 消息对象
     * @throws IOException
     */
    void send(TelemetryWebSocketSessionRef sessionRef, int subscriptionId, String msg) throws IOException;

    /**
     * websocket 通道关闭方法
     * @param sessionRef websocket会话对象
     * @param withReason 消息命令的订阅id（根据订阅ID返回对应的数据）
     * @throws IOException
     */
    void close(TelemetryWebSocketSessionRef sessionRef, CloseStatus withReason) throws IOException;
}
