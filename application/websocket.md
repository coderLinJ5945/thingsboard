# Thingsboard WebSocket 通信

## 参考：
- spring-framework API ：https://docs.spring.io/spring-framework/docs/5.1.5.RELEASE/javadoc-api/
- spring-framework websocket doc ：https://docs.spring.io/spring-framework/docs/5.1.5.RELEASE/spring-framework-reference/web.html#websocket
- websocket协议（RFC 6455 ）：https://datatracker.ietf.org/doc/html/rfc6455
- guava
- java1.8 的 CompletableFuture API学习
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
1. 监测-websocket业务服务（**DefaultTelemetryWebSocketService**）和 websocket通信处理服务（**TbWebSocketHandler**）初始化（实现方式：@Service注解）
    
   1.1 初始化websocket server的newWorkStealingPool线程池，该线程池用于websocket通信的资源池（DefaultTelemetryWebSocketService.initExecutor()）
2. 配置WebSocketConfiguration服务注册（实现方式：@Configuration和@EnableWebSocket注解）
   
   2.1 WebSocketConfiguration.registerWebSocketHandlers()注册回调
        
        2.1.1 初始化TbWebSocketHandler对象并注册到WebSocketHandlerRegistry
        
        2.1.2 绑定映射到指定websocket地址：/api/ws/plugins/**，设置上的地址AllowedOrigins允许通过的拦截
    
        2.1.3 绑定握手相关的两个拦截器：（HttpSessionHandshakeInterceptor）和 WebSocket握手请求的拦截器（HandshakeInterceptor）
   
   2.2 初始化负责websocket相关的通讯信息类：TbWebSocketHandler（@Bean方式初始化 WebSocketConfiguration.wsHandler()） 

   2.3 初始化设置消息缓存的大小集装箱类：ServletServerContainerFactoryBean（@Bean方式初始化  WebSocketConfiguration.createWebSocketContainer()）


### WebSocket clent连接逻辑（代码入口类参考：TbWebSocketHandler）
1. 调用ws://localhost:8080/api/ws/plugins/telemetry?token= 发起与websocket服务端的连接
2. 握手逻辑：一系列的业务用户session相关逻辑
   
   2.1 判断当前用户是否存在，存在则继续握手通信，返回前端连接Status Code: 101 状态码，不存在则终止（WebSocketConfiguration.beforeHandshake()）
   
   2.2 如果当前用户存在，继续准备创建websocket连接前，按照业务来管理连接的session（TbWebSocketHandler.afterConnectionEstablished()）
3. 握手的业务逻辑成功之后，websocket连接成功。前端发送Text格式的指令，到后端TbWebSocketHandler.handleTextMessage()方法接收前端指令
   
   3.1 判断session是否为null，不为空，则按照消息指令的业务来处理业务逻辑（调用TelemetryWebSocketService.handleWebSocketMsg（）方法）

   3.2 按照业务查询的要求去获取相应的数据，并返回到前端（业务逻辑处理：属性、最新数据、历史数据查询命令）

   3.3 guava 的FutureCallback异步查询数据返回（java1.8吸收了guava）


## Spring Framework Websocket
- WebSocket 协议**RFC 6455**提供了一种**标准化**方式
- 基于TCP协议，建立**双向通信**通道（握手之前其实还是基于http开始）
- client server架构
- 可使用端口 80 和 443，并允许重复使用现有的防火墙规则

### Websocket协议通信
- websocket的目的：**建立双向的Web应用程序客户端和服务器之间的通信**
- websocket应用场景：即时消息 和游戏应用程序（以前使用HTTP轮询的方式，会导致开销大、客户端被迫维护连接跟踪回复等）

Websocket通信步骤：
1. 打开握手，基于http的客户端请求服务端，请求握手
```http websocket request
        GET /chat HTTP/1.1
        Host: server.example.com
        Upgrade: websocket
        Connection: Upgrade
        Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
        Origin: http://example.com
        Sec-WebSocket-Protocol: chat, superchat
        Sec-WebSocket-Version: 13
```
2. 建立连接,由HTTP共享服务端口，采用tcp通信
3. 发送数据
4. 接收数据
5. 结束握手

### Websocket 通信交互原理

1. 客户端基于HTTP请求，使用Upgrade进行升级。标记Upgrade头为websocket，使用Upgrade发送握手请求
```Websocket Upgrade
GET /spring-websocket-portfolio/portfolio HTTP/1.1
Host: localhost:8080
Upgrade: websocket 
Connection: Upgrade 
Sec-WebSocket-Key: Uc9l9TMkWGbHFD2qnFHltg==
Sec-WebSocket-Protocol: v10.stomp, v11.stomp
Sec-WebSocket-Version: 13
Origin: http://localhost:8080
```
2. 服务端接收并返回101状态码给客户端信息，通知客户端，已理解客户端请求(握手成功)
```Websocket Upgrade Response 
HTTP/1.1 101 Switching Protocols 
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: 1qVdfYHU9hPOl4JYYNXF623Gzn0=
Sec-WebSocket-Protocol: v10.stomp
```
3. 握手成功之后，HTTP请求底层的TCP套接层，打开TCP通道，提供客户端和服务端发送和接收消息通道

4. 客户端和服务端发送和接收消息

5. 断开连接


