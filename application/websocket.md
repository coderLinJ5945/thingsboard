# Thingsboard WebSocket 通信

## 参考：
- spring-framework API ：https://docs.spring.io/spring-framework/docs/5.1.5.RELEASE/javadoc-api/


## Thingsboard 代码逻辑

### WebSocket server 初始化逻辑（todo）

### WebSocket clent连接逻辑（代码入口类参考：TbWebSocketHandler）
1. UI调用ws://localhost:8080/api/ws/plugins/telemetry?token= 发起连接
2. afterConnectionEstablished
3. toRef
4. checkLimits
5. handleTextMessage
6. send
7. sendMsg
8. onResult







