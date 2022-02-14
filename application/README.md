

## 程序启动初始化加载顺序
### 一、ThingsboardServerApplication 启动，扫描发现配置
1. 加载thingsboard.yml配置文件
2. Spring Data modules  扫描发现(Spring data jpa 开始工作)
3. 初始化数据库持久化层的： Repository
4. Spring Data Redis 加载初始化
5. BeanPostProcessors ，Spring bean初始化
6. Tomcat 初始化

### 二、 业务代码初始化
1. 加载 @ConditionalOnProperty 注解 对应的配置类，用于控制加载 @Configuration 配置是否生效（配置文件配置加载到Java 对象中）
2. 初始化 RPC CurrentServerInstanceService 配置类
3. 规则引擎所有组件初始化，根据自定义 @RuleNode 自定义注解初始化
4. 初始化 ConsistentClusterRoutingService 集群路由服务（actor 对象地址管理）
5. Actor system对象初始化，RPC服务对象初始化
6. MqttTransportService MQTT transport init（重点）
7. CoapTransportService CoAP transport init，端口：0.0.0.0:5683（重点）
8. ThreadPoolTaskScheduler ，任务调度器的线程池初始化
9. CachingOperationNameGenerator 扫描初始化Controller接口


### 三、 socket通讯 ---> 转 websocket.md


## 踩坑备注

### 一、swagger
swagge-ui 访问地址：http://YOUR_HOST:PORT/swagger-ui.html 调用接口前需要使用一下命令获取 api_key

备注：
A. curl命令有问题，直接进入登陆页面，F12，输入账号密码登陆：http://localhost:8080/api/auth/login 获取该接口的返回值
B. api_key 应该填写为：**Bearer $YOUR_JWT_TOKEN**
例如：
```aidl
Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMjkzMjA4MDQ5QHFxLmNvbSIsInNjb3BlcyI6WyJURU5BTlRfQURNSU4iXSwidXNlcklkIjoiZDRjYmJkYTAtOGY3MS0xMWVhLTk0YTAtYTdjODA2YmM4MGYzIiwiZmlyc3ROYW1lIjoibGluaiIsImVuYWJsZWQiOnRydWUsImlzUHVibGljIjpmYWxzZSwidGVuYW50SWQiOiI1YTEyOGI4MC04ZjRkLTExZWEtYTczNi0wNzhiYWFiZmQ4MWMiLCJjdXN0b21lcklkIjoiMTM4MTQwMDAtMWRkMi0xMWIyLTgwODAtODA4MDgwODA4MDgwIiwiaXNzIjoidGhpbmdzYm9hcmQuaW8iLCJpYXQiOjE1OTA1NjQzNzcsImV4cCI6MTU5MDU3MzM3N30.kDC7MV9-rdydp1e1Wrw1wW6cMbvO2vwPb-cVPFJdH6kBDPIlSUwupL1iDWtl05iCMKmp4brrf1aQ1v14V6YY7A
```
```
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' -d '{"username":"tenant@thingsboard.org", "password":"tenant"}' 'http://THINGSBOARD_URL/api/auth/login'
```

