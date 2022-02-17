# 技术点
1. springboot、spring-security、spring-data-jpa（web基础架构）
2. cassandra（解决：分布式存储）  学习文档：https://docs.datastax.com/en/developer/java-driver/3.6/
3. Actor and akka（解决：node之间的数据高效可靠传输等问题） 学习：https://pan.baidu.com/s/1N5bDrv4SydCwjY5AmCXrFg  提取码：  ej6y
4. netty（解决：多种物联网协议的设备连接等问题） API: https://netty.io/4.1/api/index.html
5. 前端 angular
6. jdk1.8新特性（解决：多线程等）：https://www.runoob.com/java/java8-new-features.html
7. HAProxy （解决：高可用性、负载均衡等）
8. guava（解决：异步/同步、并发 和回调等） 学习：https://github.com/google/guava
9. websocket：https://docs.spring.io/spring-framework/docs/5.2.19.RELEASE/spring-framework-reference/web.html#websocket

# 使用参考文档：
1. HTTP设备API参考（设备主动发送）：https://thingsboard.io/docs/reference/http-api/
2. MQTT设备API参考（设备主动发送）：https://thingsboard.io/docs/reference/mqtt-api/
3. 规则引擎：创建和清除警报 https://thingsboard.io/docs/user-guide/rule-engine-2-0/tutorials/create-clear-alarms/
4. 网关接入设备：https://thingsboard.io/docs/iot-gateway/getting-started/
5. 2.2x之后的集群部署：https://thingsboard.io/docs/user-guide/install/cluster-setup/
6. 规则引擎：数据增量计算 https://thingsboard.io/docs/user-guide/rule-engine-2-0/tutorials/telemetry-delta-validation/
7. 组合设备：https://thingsboard.io/docs/user-guide/rule-engine-2-0/tutorials/function-based-on-telemetry-from-two-devices/
8. 规则引擎：数据自定义计算 https://thingsboard.io/docs/user-guide/rule-engine-2-0/tutorials/transform-incoming-telemetry/
9. 规则引擎：历史记录数据自定义计算 https://thingsboard.io/docs/user-guide/rule-engine-2-0/tutorials/transform-telemetry-using-previous-record/
10. 规则引擎：报警与解除报警 https://thingsboard.io/docs/user-guide/rule-engine-2-0/tutorials/create-clear-alarms/
11. 规则引擎：报警处理 https://thingsboard.io/docs/user-guide/rule-engine-2-0/tutorials/create-clear-alarms-with-details/
12. 规则引擎：报警邮件 https://thingsboard.io/docs/user-guide/rule-engine-2-0/tutorials/send-email/
13. 规则引擎：设备离线报警 https://thingsboard.io/docs/user-guide/rule-engine-2-0/tutorials/create-inactivity-alarm/
14. 规则引擎：双向设备rpc请求 https://thingsboard.io/docs/user-guide/rule-engine-2-0/tutorials/rpc-request-tutorial/
15. 规则引擎：主动调用REST API https://thingsboard.io/docs/user-guide/rule-engine-2-0/tutorials/get-weather-using-rest-api-call/
16. 真实设备对接学习：https://thingsboard.io/docs/samples/arduino/temperature/
17. 规则引擎：Telegram Bot到手机端对接 https://thingsboard.io/docs/iot-gateway/integration-with-telegram-bot/
18. 规则引擎：检测实体之间关系 https://thingsboard.io/docs/user-guide/rule-engine-2-0/tutorials/check-relation-tutorial/
19. 模拟温度计使用入门： http://note.youdao.com/noteshare?id=859e12661f4c200db1d2efb565855e19
20. 文档翻译及相关知识内容： https://www.yuque.com/wudision0416/pliowa
21. tb 组态配置说明：https://thingsboard.io/docs/user-guide/install/config/#thingsboard-core-settings
22. tb 手动集群部署：http://note.youdao.com/s/3Fy7EGai


#数据库
Cassandra 和 TimescaleDB（PostgreSQL）对比文档：
https://db-engines.com/en/system/Cassandra%3BTimescaleDB

TimescaleDB（PostgreSQL） 安装参考 window系统：
https://blog.csdn.net/weixin_44739010/article/details/102817309

https://blog.csdn.net/qq_28289405/article/details/80535878

注意：从https://www.enterprisedb.com/downloads/postgres-postgresql-downloads#windows 下载的 postgreSQL 安装后缺少libeay32.dll 和 ssleay32.dll 会导致不识别TimescaleDB 启动文件

libeay32.dll 和 ssleay32.dll
链接：https://pan.baidu.com/s/1_9jI9cv5O_nMwxYfGMaIyQ  提取码：k19v


# 压力（性能）测试
thingsboard压测：http://note.youdao.com/s/6nWT6dgL

cassandra集群部署及压测：http://note.youdao.com/s/U8FHUIj


### 模拟设备发送数据

1. 需要有node环境
2. 使用node 安装 mqtt
   npm install mqtt
3. 压力测试工具以及代码：
   链接：https://pan.baidu.com/s/1MefAKFjsTeIUUmqqZHR3-Q
   提取码：5sl4

安装问题备注：https://github.com/timescale/timescaledb/issues/1398



# 源码

## 源码模块整理（详细源码解析在源代码注释中）
```thingsboard
thingsboard
    |-application               应用API入口
    |-common
        |--dao-api                  数据持久化交互接口
        |--data                     数据持久化层相关的公共复用实体
        |--message                  TB相关的消息数据格式体定义
        |--queue                    kafka消息队列
        |--transport            多种协议传输层
            |---coap                COAP协议数据传输（包含COAP服务端）
            |---http                HTTP 设备相关属性、数据传输接口
            |---mqtt                MQTT公共数据传输层（包含MQTT服务端）
            |---transport-api       数据传输公共API管理（适配器、会话、加密解密、回调等）
        |--util                     基于guava的处理线程异步/同步工具包
    |-dao                           数据持久化交互接口实现（Cassandra，pqsql，TimescaleDB，后面版本删除了TimescaleDB）
    |-msa
        |--black-box-tests
        |--js-executor
        |--tb
        |--tb-node
        |--transport
            |---coap
            |---http
            |---mqtt
        |--web-ui
    |-netty-mqtt                    mqtt客户端操作代码
    |-rule-engine                   规则引擎
        |--rule-engine-api          规则引擎核心元API
        |--rule-engine-components   规则引擎组件
    |-tools
    |-transport
        |--coap
        |--http
        |--mqtt
    |-ui    web前端代码
    

```



# 规则引擎



# 设备概念

### 设备数据类型
1. telemetry 遥测数据
2. attribute 设备属性（客户端数据）
3. RPC call RPC命令

### 设备事件（DataConstants.java） 和 设备事件属性（DeviceState.java）：合为设备事件生命周期
设备事件：
1. INACTIVITY_EVENT 不活跃事件
2. CONNECT_EVENT 连接事件
3. DISCONNECT_EVENT 断开连接事件
4. ACTIVITY_EVENT 活跃事件

设备事件状态属性：
1. active 活跃中
2. lastConnectTime 最近一次连接事件时间
3. lastDisconnectTime 最近一次断开连接事件时间
4. lastInactivityAlarmTime 最近一次不活跃报警事件时间
5. inactivityTimeout 不活跃超时时间
6. lastActivityTime 最近一次活时间

## 消息

### 消息规则
1. Message ID：基于时间的通用唯一标识符
2. Originator of the message ：消息源，设备，资产或其他实体标识符
3. Metadata：元数据，基础属性源数据
4. Payload of the message：消息有效负载，消息载体
5. **Type of the message：消息类型**

### 消息类型
预定义消息类型：https://thingsboard.io/docs/user-guide/rule-engine-2-0/overview/
org.thingsboard.server.common.msg.session.SessionMsgType

## 关系类型

## 规则节点

## websocket 转 application.socket.md


## 源码分析

### 注释说明定义 --->转到详细代码注释
```annotation
1. root-XXX ： 根-接口、类、抽象类等，例如： root-接口
```

# 思考
- 怎么实现设备状态实时监管的？ 解答：转到 DefaultDeviceStateService.java

# todo：
1. 学习akka 和netty 在thingsboad中分别的使用场景