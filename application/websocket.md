# Thingsboard WebSocket 通信

## 参考：
- spring-framework API ：https://docs.spring.io/spring-framework/docs/5.1.5.RELEASE/javadoc-api/


## Thingsboard WebSocket代码逻辑解析

### Websocket 相关代码类说明
```thingsboard
thingsboard
    |-application                           应用API入口
        |--WebSocketConfiguration           websocket配置初始化入口类
        |--TelemetryWebSocketMsgEndpoint    监测-websocket-消息通信接口（websocket通信消息设计接口）
        |--TelemetryWebSocketService        处理监测数据的websocket服务接口        
        |--TbWebSocketHandler               websocket服务：处理程序（初始化配置信息）
        |--DefaultTelemetryWebSocketService 监测-websocket服务（默认的websocket业务逻辑处理类，初始化逻辑入口）
        |--TelemetryWebSocketSessionRef     监测-websocket-session会话封装对象实体
        |--TelemetryWebSocketTextMsg        websocket的text格式消息对象实体
        |--WsSessionMetaData                websocket session会话元数据对象实体
        
```

### WebSocket server 配置初始化逻辑
1. @Service注解初始化默认监测-websocket服务（DefaultTelemetryWebSocketService）和 websocket服务（TbWebSocketHandler）
2. newWorkStealingPool初始化websocket server的线程池（DefaultTelemetryWebSocketService.initExecutor()）
3. @Configuration和@EnableWebSocket注解开启并配置WebSocketConfiguration服务注册
4. WebSocketConfiguration.registerWebSocketHandlers()注册回调，将TbWebSocketHandler对象注册到WebSocketHandlerRegistry中
5. @Bean方式初始化 TbWebSocketHandler（WebSocketConfiguration.wsHandler()）
6. @Bean方式初始化 ServletServerContainerFactoryBean（WebSocketConfiguration.createWebSocketContainer()）

### WebSocket clent连接逻辑（代码入口类参考：TbWebSocketHandler）
1. UI调用ws://localhost:8080/api/ws/plugins/telemetry?token= 发起连接
2. afterConnectionEstablished
3. toRef
4. checkLimits
5. handleTextMessage
6. send
7. sendMsg
8. onResult







