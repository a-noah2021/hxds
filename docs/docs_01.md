# 搭建环境

这个项目对硬件的要求还是不低的，最低需要 16G 内存。我的笔记本运行起来很勉强，所以买了两个云服务器来搭建环境。两个服务器都是 2C4G 的，其中 MySQL 集群和 NoSQL 部署在一台，云存储和其他中间件部署在另一台。

## 搭建MySQL集群

### 目标

搭建 5 个 MySQL 节点，其中四个作为集群节点，剩下的一个作为分布式配置存储节点

### 背景

#### 数据切分

​		MySQL单表数据超过两千万，CRUD性能就会急速下降，所以我们需要把同一张表的数据切分到不同的MySQL节点中。这需要引入MySQL中间件，其实就是个SQL路由器而已。MyCat、ProxySQL、ShardingSphere等等。课程中选择了 ShardingSphere 而且还是Apache负责维护的，国内也有很多项目组在用这个产品，手册资料相对齐全，所以相对来说是个主流的中间件。

  ![https://img1.sycdn.imooc.com/62f4d68d0001d47504480348.jpg](https://img1.sycdn.imooc.com/62f4d68d0001d47504480348.jpg)



​		规则是：在 MySQL_1 和 MySQL_2 两个节点上分别创建订单表，然后在 ShardingSphere 做好设置。如果 INSERT 语句主键值对2求模余0，这个 INSERT 语句就路由给 MySQL_1 节点；如果余数是1，INSERT 语句就被路由给 MySQL_2 执行，通过控制 SQL 语句的转发就能把订单数据切分到不同的 MySQL 节点上。将来查询的数据的时候，ShardingSphere 把SELECT语句发送给每个 MySQL 节点执行，然后 ShardingSphere 把得到的数据做汇总返回给Navicat就行了。我们在Navicat上面执行CRUD操作，几乎跟操作单节点 MySQL 差不多，但是这背后确实通过路由 SQL 语句来实现的。

​		解释：我们可以将海量得数据切分到不同得 MySQL 节点。但是时间日积月累每个 MySQL 里面得数据还是会超过几千万条， 这时候 就必须做归档。 对于一年以上得业务数据,可以看作过期得冷数据，我们可以把这部分得数据转移到归档数据库， 例如 ToKuDB、 MongoDB 或者 HBase 里面。这样 MySQL 节点就实现缩表了，性能也就上去了。比方说你在银行APP上面只能插到12个月以内的流水账单，再早的账单是查不到的。这就是银行做了冷数据归档操作，只有银行内部少数人可以查阅这些过期的冷数据

#### 数据同步

​		数据切分虽然能应对大量业务数据的存储，但是 MySQL_1 和 MySQL_2 节点数据是不同的，而且还没有备用的冗余节点，一旦宕机就会严重影响线上业务。接下来我们要考虑怎么给MySQL节点设置冗余节点。

​		MySQL自带了 Master-Slave 数据同步模式，也被称作主从同步模式。例如 MySQL_A 节点开启了 binlog 日志文件之后，MySQL_A上面执行SQL语句都会被记录在binlog日志里面。MySQL_B节点通过订阅MySQL_A的binlog文件，能实时下载到这个日志文件，然后在MySQL_B节点上运行这些SQL语句，于是就保证了自己的数据和MySQL_A节点一致

  ![https://img1.sycdn.imooc.com/62f4d7b60001f9e504960270.jpg](https://img1.sycdn.imooc.com/62f4d7b60001f9e504960270.jpg)



 		MySQL_A被称作Master（主节点），MySQL_B被称作Slave（从节点）。主从同步模式里面，数据同步是单项的，如果你在MySQL_A 上写入数据，可以同步到MySQL_B上面；如果在MySQL_B上面写入数据，是不能同步到MySQL_A节点的

  ![https://img1.sycdn.imooc.com/62f4d7dd0001f2a105010319.jpg](https://img1.sycdn.imooc.com/62f4d7dd0001f2a105010319.jpg)



​		于是我们要配置双向主从同步，也就是互为主从节点。MySQL_A订阅MySQL_B的日志文件，MySQL_B订阅MySQL_A的日志文件

#### 读写分离

​		绝大多数Web系统都是读多写少的，比如电商网站，我们都是要货比三家，然后再下单购买。所以搭建MySQL集群的时候，就要划定某些节点是读节点，某些节点是写节点

​		主从同步有个问题就是Master和Slave身份是固定，如果MySQL_1宕机，MySQL_2和MySQL_3都不能升级成写节点。那怎么办呢，给MySQL_1加上双向同步的MySQL_4节点。



  ![https://img3.sycdn.imooc.com/62f4d84000018f0d08060280.jpg](https://img3.sycdn.imooc.com/62f4d84000018f0d08060280.jpg)



​    ShardingSphere会轮询的方式给MySQL_1和MySQL_4发送写操作的SQL语句（INSERT、DELETE、UPDATE等）；如果是查询语句，ShardingSphere会发给其余四个读节点去执行，这就实现了读写分离。假设MySQL_1宕机，ShardingSphere通过心跳检测能知道，于是所有的写操作就转发给MySQL_4。反之如果MySQL_4宕机，MySQL_1也会接替工作。在上面示意图中的6个MySQL节点，无论哪一个宕机都不影响数据库整体的使用，都有各自的冗余节点。

#### 数据分片

  ![https://img1.sycdn.imooc.com/62f4d8b0000189ba08070390.jpg](https://img1.sycdn.imooc.com/62f4d8b0000189ba08070390.jpg)

  前6个节点组成了第一个MySQL分片，后6个MySQL节点组成了另一个MySQL分片，两个分片之间没有任何的数据同步。这时候ShardingSphere把各种SQL语句路由给相应的MySQL分片，数据就实现了切分。

#### ShardingSphere

  ShardingSphere是开源免费的数据库集群中间件，自带了各种切分数据的算法和雪花主键生成算法，甚至我们自己也可以写代码订制新的算法，相对来说比MyCat扩展性更强

### 步骤

1. 首先下载并导入 [MySQL 镜像](https://www.baidu.com/) 到 Docker

```
docker load < MySQL.tar.gz
```

2. 创建 Docker 内网网段。为了给 Docker 中的容器分配固定的 Docker 内网 IP 地址，而且和其他现存容器 IP 不发生冲突，我们需要创建一个 Docker 内网的网段 mynet: 172.18.0.X，以后我们创建的容器都分配到这个网段上。需要注意的是，172.18.0.1 是网关的 IP ( 不可用 )

```
docker network create --subnet=172.18.0.0/18 mynet
```

3. 启动 MySQL 容器

```
docker run -it -d --name mysql_1 -p 12001:3306 \
--net mynet --ip 172.18.0.2 \
-m 400m -v /www/evmt/mysql_1/data/:/var/lib/mysql \
-v /www/evmt/mysql_1/config:/etc/mysql/conf.d \
-e MYSQL_ROOT_PASSWORD=abc123456 \
-e TZ=Asia/Shanghai --privileged=true \
mysql:8.0.23 \
--lower_case_table_names=1
```

按照上面的命令修改 --name/-p/--ip/-v/-v 信息以此创建其余四个节点的 MySQL 容器，然后通过 Navicat 连接这四个节点，可能存在不能 SSH 连接的问题，看这篇：[Host is not allowed to connect to this MySQL server解决方法](https://blog.csdn.net/weixin_43989637/article/details/112009123)

这是创建的第五个 MySQL 节点，用作分布式配置存储节点

```
docker run -it -d --name mysql_5 -p 12005:3306 \
--net mynet --ip 172.18.0.6 \
-m 400m -v /www/evmt/mysql_5/data/:/var/lib/mysql \
-v /www/evmt/mysql_5/config:/etc/mysql/conf.d \
-e MYSQL_ROOT_PASSWORD=abc123456 \
-e TZ=Asia/Shanghai --privileged=true \
mysql:8.0.23 \
--lower_case_table_names=1
```

以上节点都已经搭建完成，接下来就要配置 ShardingSphere

4. 下载并导入 JDK 镜像。因为 ShardingSphere 是基于 Java 的中间件，所以我们要先导入 JDK 镜像然后创建容器，再放入 ShardingSphere 程序，

```shell
docker load < JDK.tar.gz
```

5. 创建 JDK 容器，视频里给 300M 内存可能导致 Navicat 打不开 ( 或刚开始能打开过会儿就打不开 ) 集群数据库，所以我给了 700M

```shell
docker run -it -d --name ss -p 3307:3307 \
--net mynet --ip 172.18.0.7 \
-m 700m -v /www/evmt/ss:/root/ss \
-e TZ=Asia/Shanghai --privileged=true \
jdk bash
```

6. 下载并解压缩 ShardingSphere.zip

```shell
yum install unzip -y # 先为os下载unzip的指令
unzip ShardingSphere.zip # 将压缩文件移到/www/evmt/ss目录下再解压缩
cd ShardingSphere/bin # 进入bin目录
chmod -R 777 ./* # 给脚本文件赋权限
docker exec -it ss bash # 进入容器
cd /root/ss/ShardingSphere/bin # 进入bin目录
./start.sh # 启动ShardingSphere
```

## 安装NoSQL

### 安装MongoDB

1. 下载并导入 MongoDB 镜像

```shell
docker load < MongoDB.tar.gz
```

2. 创建 MongoDB 容器

创建 /www/evmt/mongo/mongod.conf 文件，然后在文件中添加如下内容：

```conf
storage:
  dbPath: "/data/db"
security:
  authorization: enabled
net:
  port: 27017
  bindIp: "0.0.0.0"
```

启动 MongoDB 容器

```shell
docker run -it -d --name mongo \
-p 27017:27017 \
--net mynet --ip 172.18.0.8 \
-v /www/evmt/mongo:/etc/mongo \
-v /www/evmt/mongo/data/db:/data/db \
-m 400m --privileged=true \
-e MONGO_INITDB_ROOT_USERNAME=admin \
-e MONGO_INITDB_ROOT_PASSWORD=abc123456 \
-e TZ=Asia/Shanghai \
docker.io/mongo --config /etc/mongo/mongod.conf
```

### 安装Redis

1. 下载并导入 Redis 镜像

```shell
docker load < Redis.tar.gz
```

2. 创建 Redis 容器

创建 /www/evmt/redis/conf/redis.conf 文件，然后在文件中添加如下内容：

```yaml
# 指定 redis 只接收来自于该IP地址的请求，如果不进行设置，那么将处理所有请求
# bind 127.0.0.1
bind 0.0.0.0

# 是否开启保护模式，默认开启。要是配置里没有指定bind和密码。开启该参数后，redis只会本地进行访问，拒绝外部访问。
protected-mode yes

# redis监听的端口号。
port 6379

# 此参数确定了TCP连接中已完成队列(完成三次握手之后)的长度， 当然此值必须不大于Linux系统定义的/proc/sys/net/core/somaxconn值，默认是511，而Linux的默认参数值是128。当系统并发量大并且客户端速度缓慢的时候，可以将这二个参数一起参考设定。该内核参数默认值一般是128，对于负载很大的服务程序来说大大的不够。一般会将它修改为2048或者更大。在/etc/sysctl.conf中添加:net.core.somaxconn = 2048，然后在终端中执行sysctl -p。
tcp-backlog 511

# 此参数为设置客户端空闲超过timeout，服务端会断开连接，为0则服务端不会主动断开连接，不能小于0。
timeout 0

# tcp keepalive参数。如果设置不为0，就使用配置tcp的SO_KEEPALIVE值，使用keepalive有两个好处:检测挂掉的对端。降低中间设备出问题而导致网络看似连接却已经与对端端口的问题。在Linux内核中，设置了keepalive，redis会定时给对端发送ack。检测到对端关闭需要两倍的设置值。
tcp-keepalive 0

# 指定了服务端日志的级别。级别包括：debug（很多信息，方便开发、测试），verbose（许多有用的信息，但是没有debug级别信息多），notice（适当的日志级别，适合生产环境），warn（只有非常重要的信息）
loglevel notice

# 指定了记录日志的文件。空字符串的话，日志会打印到标准输出设备。后台运行的redis标准输出是/dev/null。
logfile ""

# 数据库的数量，默认使用的数据库是DB 0。可以通过SELECT命令选择一个db
databases 12

# redis是基于内存的数据库，可以通过设置该值定期写入磁盘。
# 注释掉“save”这一行配置项就可以让保存数据库功能失效
# 900秒（15分钟）内至少1个key值改变（则进行数据库保存--持久化） 
# 300秒（5分钟）内至少10个key值改变（则进行数据库保存--持久化） 
# 60秒（1分钟）内至少10000个key值改变（则进行数据库保存--持久化）
save 900 1
save 300 10
save 60 10000

# 当RDB持久化出现错误后，是否依然进行继续进行工作，yes：不能进行工作，no：可以继续进行工作，可以通过info中的rdb_last_bgsave_status了解RDB持久化是否有错误
stop-writes-on-bgsave-error yes

# 使用压缩rdb文件，rdb文件压缩使用LZF压缩算法，yes：压缩，但是需要一些cpu的消耗。no：不压缩，需要更多的磁盘空间
rdbcompression yes

# 是否校验rdb文件。从rdb格式的第五个版本开始，在rdb文件的末尾会带上CRC64的校验和。这跟有利于文件的容错性，但是在保存rdb文件的时候，会有大概10%的性能损耗，所以如果你追求高性能，可以关闭该配置。
rdbchecksum yes

# rdb文件的名称
dbfilename dump.rdb

# 数据目录，数据库的写入会在这个目录。rdb、aof文件也会写在这个目录
dir ./

# requirepass配置可以让用户使用AUTH命令来认证密码，才能使用其他命令。这让redis可以使用在不受信任的网络中。为了保持向后的兼容性，可以注释该命令，因为大部分用户也不需要认证。使用requirepass的时候需要注意，因为redis太快了，每秒可以认证15w次密码，简单的密码很容易被攻破，所以最好使用一个更复杂的密码。注意只有密码没有用户名。
requirepass abc123456
```

3. 启动 Redis 容器

```shell
docker run -it -d --name redis -m 200m \
-p 6379:6379 --privileged=true \
--net mynet --ip 172.18.0.9 \
-v /www/evmt/redis/conf:/usr/local/etc/redis \
-e TZ=Asia/Shanghai redis:6.0.10 \
redis-server /usr/local/etc/redis/redis.conf
```

## 安装云存储

### 安装Minio

1. 下载并导入 Minio 镜像文件

```shell
docker load < Minio.tar.gz
```

2. 创建 Minio 文件存储路径

```shell
mkdir -p /www/evmt/minio/data # 创建文件夹
chmod -R 777 /www/evmt/minio/data # 给其设置权限，否则Minio无法使用该文件夹保存文件
```

3. 启动 Minio 容器：其中 9000 是上传文件端口，9001 是 Minio 自带的 Web 端管理系统的端口

```shell
docker run -it -d --name minio -m 400m \
-p 9000:9000 -p 9001:9001 \
--net mynet --ip 172.18.0.10 \
-v /www/evmt/minio/data:/data \
-e TZ=Asia/Shanghai --privileged=true \
--env MINIO_ROOT_USER="root" \
--env MINIO_ROOT_PASSWORD="abc123456" \
bitnami/minio:latest
```

4. 登陆 Minio [控制台](http://43.143.134.158:9001/login)，账号是：root，密码是：abc123456，进入即可查看具体状况

## 安装其他中间件

### 安装RabbitMQ

1. 下载并导入 RabbitMQ 镜像文件

```shell
docker load < RabbitMQ.tar,gz
```

2. 创建并启动 RabbitMQ 容器

```shell
docker run -it -d --name mq \
--net mynet --ip 172.18.0.11 \
-p 5672:5672 -m 200m \
-e TZ=Asia/Shanghai --privileged=true \
rabbitmq
```

### 安装Nacos

1. 下载并导入 Nacos 镜像文件

```shell
docker load < Nacos.tar.gz
```

2. 创建并启动 Nacos 容器

```shell
docker run -it -d -p 8848:8848 --env MODE=standalone \
--net mynet --ip 172.18.0.12 -e TZ=Asia/Shanghai \
--name nacos nacos/nacos-server
```

3. 登陆 Nacos [控制台](http://43.143.134.158:8848/nacos)，账密都是：nacos，进入即可查看具体状况

### 安装Sentinel

1. 下载并导入 Sentinel 镜像文件

```shell
docker load < Sentinel.tar.gz
```

2. 创建并启动 Sentinel 容器，和上面一样如果像视频那样只给 200M 的话可能会造成容器崩溃，所以我给了 400M

```shell
docker run -it -d --name sentinel \
-p 8719:8719 -p 8858:8858 \
--net mynet --ip 172.18.0.13 \
-e TZ=Asia/Shanghai -m 400m \
bladex/sentinel-dashboard
```

3. 登陆 Sentinel [控制台](http://43.143.134.158:8858/#/dashboard)，账密都是：sentinel，进入即可查看具体状况

### 配置腾讯云COS

1. 购买腾讯云COS后，进入对象存储菜单栏的[密钥管理](https://console.cloud.tencent.com/cos/secret)，它会引导进入访问密钥这个页面
2. 点击新建密钥，然后生成 Appid、secretId、secretKey，这个后面会用到
3. 接着退回对象存储菜单栏的[概览](https://console.cloud.tencent.com/cos)，然后点击新建存储桶，填写必要信息
4. 接着进入[文件列表](https://console.cloud.tencent.com/cos/bucket?bucket=hxds-private-1309124223&region=ap-beijing)，新建文件夹 driver/auth
5. 同理创建新的存储桶 hxds-public，设置成公有读私有写

# 代码实现
## 基于微服务的司机注册与实名认证
### 司机微服务的用户注册功能上

1. 写 hxds-dr 里面 dao#DriverDao/DriverSettingDao/WalletDao 五个 SQL 以及对应的 Mapper 文件
2. 写 hxds-dr 里面 service#DriverService#registerNewDriver 的接口和实现类
3. 写 hxds-dr 里面 controller#DriverController#registerNewDriver 和 form#RegisterNewDriverForm
4. 启动项目后，进入 [Swagger](http://localhost:8001/swagger-ui/index.html?configUrl=/doc-api.html/swagger-config)，可查看 API 文档

### 司机微服务的用户注册功能下

1. 写 bff-driver 里面的 feign#DrServiceApi#registerNewDriver 定义远程调用的 API
2. 写 bff-driver 里面的 service#DriverService#registerNewDriver 返回给上层 UserId
3. 写 bff-driver 里面的 controller#DriverController#registerNewDriver 经过 SaToken 登陆验证返回给前端 token
这里介绍一下 SaToken 的常用 API
```java
StpUtil.setLoginId(10001);              // 标记当前会话登录的账号id
StpUtil.getLoginId();                   // 获取当前会话登录的账号id
StpUtil.isLogin();                      // 获取当前会话是否已经登录, 返回true或false
StpUtil.logout();                       // 当前会话注销登录
StpUtil.logoutByLoginId(10001);         // 让账号为10001的会话注销登录（踢人下线）
StpUtil.hasRole("super-admin");         // 查询当前账号是否含有指定角色标识, 返回true或false
StpUtil.hasPermission("user:add");      // 查询当前账号是否含有指定权限, 返回true或false
StpUtil.getSession();                   // 获取当前账号id的Session 
StpUtil.getSessionByLoginId(10001);     // 获取账号id为10001的Session
StpUtil.getTokenValueByLoginId(10001);  // 获取账号id为10001的token令牌值
StpUtil.setLoginId(10001, "PC");        // 指定设备标识登录
StpUtil.logoutByLoginId(10001, "PC");   // 指定设备标识进行强制注销 (不同端不受影响)
StpUtil.switchTo(10044);                // 将当前会话身份临时切换为其它账号
```

### 小程序获取用户微信信息

1. 修改 main.js 里面的 IP 地址，不要 localhost 或者 127.0.0.1 (127.0.0.1如果用微信开发工具测试可以端，只是真机调试不行，这时用 NatApp 内网穿透可以解决这个问题)
2. 在 hxds-driver-wx/pages/login/login.vue 实现司机注册的逻辑
3. 在 hxds-driver-wx/pages/register/register.vue 实现司机注册的逻辑

测试：依次启动 hxds-tm、bff-driver、hxds-dr、gateway 项目然后至少等待一分钟 ( 否则会出现503 )，再运行小程序测试司机注册
其中启动 hxds-tm 节点后可以进入其[后台管理系统](http://localhost:7970/admin/index.html#/)，密码在 tx-lcn.manager.admin-key=abc123456 上配置
在启动的后 bff-driver、hxds-dr 会注册到 hxds-tm，而 bff-driver、hxds-dr、gateway 会注册到 nacos，我一直报错 nacos 连不上，谷歌后将配置文件名改成 bootstrap.yml 就好了
其中 yml 配置文件中的 spring.cloud.nacos.config/discovery.namespace 为 Nacos Web 管理界面的命名空间-命名空间ID

【题外话】截止目前感觉单纯跟着视频敲代码甚至直接 COPY 原作者的代码收获不大，从下一小节开始会把相关代码复制到文档里，这里更有针对性。所以之前的代码大家也跟着 COPY 原代码，部分有坑 ( 存在部分人是学习跟着敲的，部分代码涉及到后面内容记个 TODO 没写下去的情况 ) 也不好复现就不填了

### 司机实名认证

1. 在腾讯云开通对象存储服务和数据万象服务
   COS-SDK 使用文档:[进入](https://cloud.tencent.com/document/product/436/10199)，CI-SDK 使用文档:[进入](https://cloud.tencent.com/document/product/460/49286)
2. 写 common 里面的 util#CosUtil 实现对腾讯云 COS 的增删文件
   写 bff-driver 里面的 controller#form#DeleteCosFileForm/RegisterNewDriverForm 
   写 bff-driver 里面的 controller#CosController#uploadCosPrivateFile/deleteCosPrivateFile 实现对接腾讯云 SDK 的上传/删除文件接口
```java
@Data
@Schema(description = "新司机注册表单")
public class RegisterNewDriverForm {

    @NotBlank(message = "code不能为空")
    @Schema(description = "微信小程序临时授权")
    private String code;

    @NotBlank(message = "nickname不能为空")
    @Schema(description = "用户昵称")
    private String nickname;

    @NotBlank(message = "photo不能为空")
    @Schema(description = "用户头像")
    private String photo;

}

@PostMapping("/uploadCosPrivateFile")
@SaCheckLogin
@Operation(summary = "上传文件")
public R uploadCosPrivateFile(@Param("file") MultipartFile file,@Param("module") String module){
   if(file.isEmpty()){
      throw new HxdsException("上传文件不能为空");
   }
   try{
      String path=null;
      if("driverAuth".equals(module)){
         path="/driver/auth/";
      }
      else{
         throw new HxdsException("module错误");
      }
      HashMap map=cosUtil.uploadPrivateFile(file,path);
      return R.ok(map);
   }catch (Exception e){
      log.error("文件上传到腾讯云错误", e);
      throw new HxdsException("文件上传到腾讯云错误");
   }
}

@PostMapping("/deleteCosPrivateFile")
@SaCheckLogin
@Operation(summary = "删除文件")
public R deleteCosPrivateFile(@Valid @RequestBody DeleteCosFileForm form){
   cosUtil.deletePrivateFile(form.getPathes());
   return R.ok();
}
```
3. 在微信公众平台开通 OCR 识别插件，OCR 使用文档:[进入](https://mp.weixin.qq.com/wxopen/plugindevdoc?appid=wx4418e3e031e551be&token=1126410043&lang=zh_CN)
   在 hxds-driver-wx/manifest.json-源码视图 里面包含了插件配置项，可以在那进行补充
4. 在 hxds-driver-wx/main.js#url 里面添加上传、删除文件的对应的后端接口
```vue
Vue.prototype.url = {
	registerNewDriver: `${baseUrl}/driver/registerNewDriver`,
	uploadCosPrivateFile: `${baseUrl}/cos/uploadCosPrivateFile`,
	deleteCosPrivateFile: `${baseUrl}/cos/deleteCosPrivateFile`
}
```
5. 写 hxds-driver-wx/main.js#uploadCos ，实现上传文件到腾讯云 COS 的请求
   写 hxds-driver-wx/identity/filling/filling.vue#scanIdcardFront/scanIdcardBack ，实现 OCR 识别证件正/反面信息
   注意：在真机调试的时候还要买 [OCR识别次数](https://fuwu.weixin.qq.com/service/detail/000ce4cec24ca026d37900ed551415)，要不然会报下面的错误
   `{base_resp: {err_code: 101002, err_msg: "not enough market quota"}}`
```vue
Vue.prototype.uploadCos = function(url, path, module, fun) {
	uni.uploadFile({
		url: url,
		filePath: path,
		name: "file",
		header: {
			token: uni.getStorageSync("token")
		},
		formData: {
			"module": module
		},
		success: function(resp) {
			let data = JSON.parse(resp.data)
			if (resp.statusCode == 401) {
				uni.redirectTo({
					url: "/pages/login/login.vue"
				})
			} else if (resp.statusCode == 200 && data.code == 200) {
				fun(resp)
			} else {
				uni.showToast({
					icon: "none",
					title: data.error
				})
			}
		}
	})
}

scanIdcardFront: function(resp) {
   let that = this;
   let detail = resp.detail;
   that.idcard.pid = detail.id.text;
   that.idcard.name = detail.name.text;
   that.idcard.sex = detail.gender.text;
   that.idcard.address = detail.address.text;
   //需要缩略身份证地址，文字太长页面显示不了
   that.idcard.shortAddress = detail.address.text.substr(0, 15) + '...';
   that.idcard.birthday = detail.birth.text;
   //OCR插件拍摄到的身份证正面照片存储地址
   that.idcard.idcardFront = detail.image_path;
   //让身份证View标签加载身份证正面照片
   that.cardBackground[0] = detail.image_path;
   that.uploadCos(that.url.uploadCosPrivateFile, detail.image_path, 'driverAuth', function(resp) {
      let data = JSON.parse(resp.data);
      let path = data.path;
      that.currentImg['idcardFront'] = path;
      that.cosImg.push(path);
   });
}

scanIdcardBack: function(resp) {
   let that = this;
   let detail = resp.detail;
   //OCR插件拍摄到的身份证背面照片存储地址
   that.idcard.idcardBack = detail.image_path;
   //View标签加载身份证背面照片
   that.cardBackground[1] = detail.image_path;
   let validDate = detail.valid_date.text.split('-')[1];
   that.idcard.expiration = dayjs(validDate, 'YYYYMMDD').format('YYYY-MM-DD');
   that.uploadCos(that.url.uploadCosPrivateFile, detail.image_path, 'driverAuth', function(resp) {
      let data = JSON.parse(resp.data);
      let path = data.path;
      that.currentImg['idcardBack'] = path;
      that.cosImg.push(path);
   });
},
```

6. 写 hxds-driver-wx/identity/identity_camera/identity_camera.vue#clickBtn/afresh，实现拍摄手持身份证的拍照点击事件和重拍点击事件
   写 hxds-driver-wx/identity/filling/filling.vue#takePhoto/uploadPhoto，实现拍摄/上传照片

```vue
clickBtn:function(){
	let that=this
	if(that.btnText=="拍照"){
		let ctx=uni.createCameraContext()
		ctx.takePhoto({
			quality:"high",
			success:function(resp){
				that.photoPath= resp.tempImagePath
				that.showCamera=false
				that.showImage=true
				that.btnText="提交"
			}
		})
	}else{
		let pages=getCurrentPages();
		let prevPage=pages[pages.length-2]
		prevPage.$vm.updatePhoto(that.type,that.photoPath)
		uni.navigateBack({
			delta:1
		})
	}
},
afresh:function(){
	let that = this;
	that.showCamera = true;
	that.showImage = false;
	that.btnText = '拍照';
}

updatePhoto: function(type, path) {
	let that = this;
	that.uploadCos(that.url.uploadCosPrivateFile, path, 'driverAuth', function(resp) {
		let data = JSON.parse(resp.data);
		that.cosImg.push(data.path);
		if (type == 'idcardHolding') {
			that.cardBackground[2] = path;
			that.currentImg['idcardHolding'] = data.path;
			that.idcard.idcardHolding = data.path;
        }/* else if (type == 'drcardBack') {
            that.cardBackground[4] = path;
            that.currentImg['drcardBack'] = data.path;
            that.idcard.drcardBack = data.path;
        } else if (type == 'drcardHolding') {
            that.cardBackground[5] = path;
            that.currentImg['drcardHolding'] = data.path;
            that.idcard.drcardHolding = data.path;
        }*/ //先注释掉，等写到拍摄驾驶证背面和手持驾驶证时再恢复
	});
	that.$forceUpdate(); //强制刷新视图层
},
takePhoto: function(type) {
	uni.navigateTo({
		url: '../identity_camera/identity_camera?type=' + type
	});
}
```
7. 写 hxds-driver-wx/identity/filling/filling.vue#scanDrcardFront，实现驾驶证正面的信息扫描
   删除掉上面代码块的注释内容
```vue
scanDrcardFront: function(resp) {
   let that = this;
   let detail = resp.detail;
   that.drcard.issueDate = detail.issue_date.text; //初次领证日期
   that.drcard.carClass = detail.car_class.text; //准驾车型
   that.drcard.validFrom = detail.valid_from.text; //驾驶证起始有效期
   that.drcard.validTo = detail.valid_to.text; //驾驶证截止有效期
   that.drcard.drcardFront = detail.image_path;
   that.cardBackground[3] = detail.image_path;
   that.uploadCos(that.url.uploadCosPrivateFile, detail.image_path, 'driverAuth', function(resp) {
       let data = JSON.parse(resp.data);
       let path = data.path;
       that.currentImg['drcardFront'] = path;
       that.cosImg.push(path);
   });
},
```
8. 写 hxds-dr/src/main/resource/mapper/DriverDao.xml#updateDriverAuth 及其对应接口
    写 service/DriverService#updateDriverAuth 及其实现类
    写 controller/form/UpdateDriverAuthForm
    写 controller/DriverController#updateDriverAuth
    实现后端实名认证中的联络方式/紧急联系人数据持久化到 tb_driver
```java
<update id="updateDriverAuth" parameterType="Map">
    UPDATE tb_driver
    SET `name`            = #{name},
    sex               = #{sex},
    pid               = #{pid},
    birthday          = #{birthday},
    tel               = #{tel},
    mail_address      = #{mailAddress},
    contact_name      = #{contactName},
    contact_tel       = #{contactTel},
    email             = #{email},
    real_auth         = 3,
    idcard_address    = #{idcardAddress},
    idcard_expiration = #{idcardExpiration},
    idcard_front      = #{idcardFront},
    idcard_back       = #{idcardBack},
    idcard_holding    = #{idcardHolding},
    drcard_type       = #{drcardType},
    drcard_expiration = #{drcardExpiration},
    drcard_issue_date = #{drcardIssueDate},
    drcard_front      = #{drcardFront},
    drcard_back       = #{drcardBack},
    drcard_holding    = #{drcardHolding}
    WHERE id = #{driverId}
</update>

int updateDriverAuth(Map param);

int updateDriverAuth(Map param);

@Override
@Transactional
@LcnTransaction
public int updateDriverAuth(Map param) {
    int rows = driverDao.updateDriverAuth(param);
    return rows;
}

@Data
@Schema(description = "更新司机认证信息表单")
public class UpdateDriverAuthForm {

    @NotNull(message = "driverId不能为空")
    @Min(value = 1, message = "driverId不能小于1")
    @Schema(description = "司机ID")
    private Long driverId;

    @NotBlank(message = "name不能为空")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5]{2,10}$", message = "name内容不正确")
    @Schema(description = "姓名")
    private String name;

    @NotBlank(message = "sex不能为空")
    @Pattern(regexp = "^男$|^女$", message = "sex内容不正确")
    @Schema(description = "性别")
    private String sex;

    @NotBlank(message = "pid不能为空")
    @Pattern(regexp = "^[1-9]\\d{5}(18|19|([23]\\d))\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$", message = "pid内容不正确")
    @Schema(description = "身份证")
    private String pid;

    @NotBlank(message = "birthday不能为空")
    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$", message = "birthday内容不正确")
    @Schema(description = "生日")
    private String birthday;

    @NotBlank(message = "tel不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "tel内容不正确")
    @Schema(description = "电话")
    private String tel;

    @NotBlank(message = "email不能为空")
    @Email(message = "email内容不正确")
    @Schema(description = "电子信箱")
    private String email;

    @NotBlank(message = "mailAddress不能为空")
    @Pattern(regexp = "^[0-9a-zA-Z\\u4e00-\\u9fa5\\-]{6,50}$", message = "mailAddress内容不正确")
    @Schema(description = "收信地址")
    private String mailAddress;

    @NotBlank(message = "contactName不能为空")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5]{2,10}$", message = "contactName内容不正确")
    @Schema(description = "应急联络人")
    private String contactName;

    @NotBlank(message = "contactTel不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "contactTel内容不正确")
    @Schema(description = "应急联络人电话")
    private String contactTel;

    @NotBlank(message = "idcardAddress不能为空")
    @Pattern(regexp = "^[0-9a-zA-Z\\u4e00-\\u9fa5\\-]{6,50}$", message = "idcardAddress内容不正确")
    @Schema(description = "身份证地址")
    private String idcardAddress;

    @NotBlank(message = "idcardFront不能为空")
    @Schema(description = "身份证正面")
    private String idcardFront;

    @NotBlank(message = "idcardBack不能为空")
    @Schema(description = "身份证背面")
    private String idcardBack;

    @NotBlank(message = "idcardHolding不能为空")
    @Schema(description = "手持身份证")
    private String idcardHolding;

    @NotBlank(message = "idcardExpiration不能为空")
    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$", message = "idcardExpiration内容不正确")
    @Schema(description = "身份证有效期")
    private String idcardExpiration;

    @NotBlank(message = "drcardType不能为空")
    @Pattern(regexp = "^(A[1-3]|B[12]|C[1-4]|[D-FMNP])$", message = "drcardType内容不正确")
    @Schema(description = "驾驶证类别")
    private String drcardType;

    @NotBlank(message = "drcardExpiration不能为空")
    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$", message = "drcardExpiration内容不正确")
    @Schema(description = "驾驶证有效期")
    private String drcardExpiration;

    @NotBlank(message = "drcardIssueDate不能为空")
    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$", message = "drcardIssueDate内容不正确")
    @Schema(description = "驾驶证初次领证日期")
    private String drcardIssueDate;

    @NotBlank(message = "drcardFront不能为空")
    @Schema(description = "驾驶证正面")
    private String drcardFront;

    @NotBlank(message = "drcardBack不能为空")
    @Schema(description = "驾驶证背面")
    private String drcardBack;

    @NotBlank(message = "drcardHolding不能为空")
    @Schema(description = "手持驾驶证")
    private String drcardHolding;

}

@PostMapping("/updateDriverAuth")
@Operation(summary = "更新实名认证信息")
public R updateDriverAuth(@RequestBody @Valid UpdateDriverAuthForm form) {
    Map param = BeanUtil.beanToMap(form);
    int rows = driverService.updateDriverAuth(param);
    return R.ok().put("rows", rows);
}
```
9. 写 bff-driver/src/main/controller/form/UpdateDriverAuthForm
   写 feign/DrServiceApi#updateDriverAuth
   写 service/DriverService#updateDriverAuth 及其实现类
   写 controller/DriverController#updateDriverAuth
      实现后端远程 feign 调用数据持久化
```java
@Data
@Schema(description = "更新司机认证信息表单")
public class UpdateDriverAuthForm {

    @Schema(description = "司机ID")
    private Long driverId;

    @NotBlank(message = "name不能为空")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5]{2,10}$", message = "name内容不正确")
    @Schema(description = "姓名")
    private String name;

    @NotBlank(message = "sex不能为空")
    @Pattern(regexp = "^男$|^女$", message = "sex内容不正确")
    @Schema(description = "性别")
    private String sex;

    @NotBlank(message = "pid不能为空")
    @Pattern(regexp = "^[1-9]\\d{5}(18|19|([23]\\d))\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$", message = "pid内容不正确")
    private String pid;

    @NotBlank(message = "birthday不能为空")
    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$", message = "birthday内容不正确")
    @Schema(description = "生日")
    private String birthday;

    @NotBlank(message = "tel不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "tel内容不正确")
    @Schema(description = "电话")
    private String tel;

    @NotBlank(message = "email不能为空")
    @Email(message = "email内容不正确")
    @Schema(description = "电子信箱")
    private String email;

    @NotBlank(message = "mailAddress不能为空")
    @Pattern(regexp = "^[0-9a-zA-Z\\u4e00-\\u9fa5\\-]{6,50}$", message = "mailAddress内容不正确")
    @Schema(description = "收信地址")
    private String mailAddress;

    @NotBlank(message = "contactName不能为空")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5]{2,10}$", message = "contactName内容不正确")
    @Schema(description = "应急联络人")
    private String contactName;

    @NotBlank(message = "contactTel不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "contactTel内容不正确")
    @Schema(description = "应急联络人电话")
    private String contactTel;

    @NotBlank(message = "idcardAddress不能为空")
    @Pattern(regexp = "^[0-9a-zA-Z\\u4e00-\\u9fa5\\-]{6,50}$", message = "idcardAddress内容不正确")
    @Schema(description = "身份证地址")
    private String idcardAddress;

    @NotBlank(message = "idcardFront不能为空")
    @Schema(description = "身份证正面")
    private String idcardFront;

    @NotBlank(message = "idcardBack不能为空")
    @Schema(description = "身份证背面")
    private String idcardBack;

    @NotBlank(message = "idcardHolding不能为空")
    @Schema(description = "手持身份证")
    private String idcardHolding;

    @NotBlank(message = "idcardExpiration不能为空")
    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$", message = "idcardExpiration内容不正确")
    @Schema(description = "身份证有效期")
    private String idcardExpiration;

    @NotBlank(message = "drcardType不能为空")
    @Pattern(regexp = "^(A[1-3]|B[12]|C[1-4]|[D-FMNP])$", message = "drcardType内容不正确")
    @Schema(description = "驾驶证类别")
    private String drcardType;

    @NotBlank(message = "drcardExpiration不能为空")
    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$", message = "drcardExpiration内容不正确")
    @Schema(description = "驾驶证有效期")
    private String drcardExpiration;

    @NotBlank(message = "drcardIssueDate不能为空")
    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$", message = "drcardIssueDate内容不正确")
    @Schema(description = "驾驶证初次领证日期")
    private String drcardIssueDate;

    @NotBlank(message = "drcardFront不能为空")
    @Schema(description = "驾驶证正面")
    private String drcardFront;

    @NotBlank(message = "drcardBack不能为空")
    @Schema(description = "驾驶证背面")
    private String drcardBack;

    @NotBlank(message = "drcardHolding不能为空")
    @Schema(description = "手持驾驶证")
    private String drcardHolding;

}

@PostMapping("/driver/updateDriverAuth")
R updateDriverAuth(UpdateDriverAuthForm form);

int updateDriverAuth(UpdateDriverAuthForm form);

@Override
@Transactional
@LcnTransaction
public int updateDriverAuth(UpdateDriverAuthForm form) {
    R r = drServiceApi.updateDriverAuth(form);
    int rows = Convert.toInt(r.get("rows"));
    return rows;
}

@PostMapping("/updateDriverAuth")
@Operation(summary = "更新实名认证信息")
@SaCheckLogin
public R updateDriverAuth(@RequestBody @Valid UpdateDriverAuthForm form){
    long driverId=StpUtil.getLoginIdAsLong();
    form.setDriverId(driverId);
    int rows=driverService.updateDriverAuth(form);
    return R.ok().put("rows",rows);
}
```
8. 写 hxds-driver-wx/identity/filling/filling.vue#enterContent/save/showAddressContent，实现移动端 ( 输入-保存-展示 ) 联络方式/紧急联系人一整套链路。小程序视图层上面的联系方式排版设计比较简单，直接引用的 uView 组件库里的列表控件，相关文档：[传送门](https://v1.uviewui.com/components/cell.html)，每个列表项都设置点击事件 enterContent

​	   写 hxds-driver-wx/main.js 定义全局 URL 路径

```vue
<!--filling.vue-->
enterContent: function(title, key) {
   let that = this;
   uni.showModal({
       title: title,
       editable: true,
       content: that.contact[key],
       success: function(resp) {
           if (resp.confirm) {
               if (key == 'mailAddress') {
                   that.contact['shortMailAddress'] = resp.content.substr(0, 15) + (resp.content.length > 15 ? '...' : '');
               } else if (key == 'email') {
                   that.contact['shortEmail'] = resp.content.substr(0, 25) + (resp.content.length > 25 ? '...' : '');
               }
               that.contact[key] = resp.content;
           }
       }
   });
},
save: function() {
   let that = this;
   //判断是否设置了6张照片
   if (Object.keys(that.currentImg).length != 6) {
       that.$refs.uToast.show({
           title: '证件上传不完整',
           type: 'error'
       });
   }
   //执行前端验证
   else if (
       that.checkValidTel(that.contact.tel, '手机号码') &&
       that.checkValidEmail(that.contact.email, '电子信箱') &&
       that.checkValidAddress(that.contact.mailAddress, '收信地址') &&
       that.checkValidName(that.contact.contactName, '联系人') &&
       that.checkValidTel(that.contact.contactTel, '联系人电话')
   ) {
       uni.showModal({
           title: '提示信息',
           content: '确认提交实名资料？',
           success: function(resp) {
               if (resp.confirm) {
                   //比较哪些照片需要删除
                   let temp = [];
                   let values = [];
                   //从JSON中获取6张证件照片的云端存储地址
               for (let key in that.currentImg) {
                       let path = that.currentImg[key];
                       values.push(path);
                   }
                   //判断cosImg数组里面哪些图片的云端地址不是6张图片的，这些图片要在云端删除
                   for (let one of that.cosImg) {
                       if (!values.includes(one)) {
                           temp.push(one);
                       }
                   }
                   if (temp.length > 0) {
                       //删除云端文件
                       that.ajax(that.url.deleteCosPrivateFile, 'POST', JSON.stringify({ pathes: temp }), function() {
                           console.log('文件删除成功');
                       });
                   }
                   //需要上传的实名认证数据
                   let data = {
                       pid: that.idcard.pid,
                       name: that.idcard.name,
                       sex: that.idcard.sex,
                       birthday: that.idcard.birthday,
                       tel: that.contact.tel,
                       email: that.contact.email,
                       mailAddress: that.contact.mailAddress,
                       contactName: that.contact.contactName,
                       contactTel: that.contact.contactTel,
                       idcardAddress: that.idcard.address,
                       idcardFront: that.currentImg.idcardFront,
                       idcardBack: that.currentImg.idcardBack,
                       idcardHolding: that.currentImg.idcardHolding,
                       idcardExpiration: that.idcard.expiration,
                       drcardType: that.drcard.carClass,
                       drcardExpiration: that.drcard.validTo,
                       drcardIssueDate: that.drcard.issueDate,
                       drcardFront: that.currentImg.drcardFront,
                       drcardBack: that.currentImg.drcardBack,
                       drcardHolding: that.currentImg.drcardHolding
                   };
                   //提交Ajax请求，上传数据
                   that.ajax(that.url.updateDriverAuth, 'POST', data, function(resp) {
                       console.log('更新成功');
                       that.$refs.uToast.show({
                           title: '资料提交成功',
                           type: 'success',
                           callback: function() {
                               uni.setStorageSync('realAuth', 3); //更新小程序Storage
                               that.realAuth = 3; //更新模型层
                               if (that.mode == 'create') {
                                   //TODO: 提示新注册的司机采集面部数据
                                   uni.navigateTo({
                                       url:"../face_camera/face_camera?mode=create"
                                   })
                               } else {
                                   //跳转到工作台页面
                                   uni.switchTab({
                                       url: '../../pages/workbench/workbench'
                                   });
                               }
                           }
                       });
                   });
               }
           }
       });
   }
},
showAddressContent: function() {
   if (this.idcard.address.length > 0) {
       uni.showModal({
           title: '身份证地址',
           content: this.idcard.address,
           showCancel: false
       });
   }
}
<!--main.js-->
updateDriverAuth: `${baseUrl}/driver/updateDriverAuth`,
```
### 开通活体检测，甄别真实注册司机
1. 进入腾讯云-[人脸识别](https://console.cloud.tencent.com/aiface)-人员库管理-人员管理-新建人员库，填写信息创建完人员库后在 application-common.yml 配置人脸识别
2. 腾讯云没有提供 Java 版本的人员库 API 文档，现在只能看 Web 版本的 API [文档](https://cloud.tencent.com/document/api/867/45014)来逆向研究 Java 版本人员库 SDK
3. 写 hxds-dr/src/main/resource/mapper/DriverDao.xml#searchDriverNameAndSex&updateDriverArchive 及其对应接口
   写 service/DriverService#createDriverFaceModel 及其实现类
   写 controller/form/CreateDriverFaceModelForm
   写 controller/DriverController#createDriverFaceModel
```java
<select id="searchDriverNameAndSex" parameterType="long" resultType="HashMap">
  SELECT name, sex
  FROM tb_driver
  WHERE id = #{driverId}
</select>
<update id="updateDriverArchive" parameterType="long">
  update tb_driver
  set archive = 1
  WHERE id = #{driverId}
</update>


HashMap searchDriverNameAndSex(long driverId);

int updateDriverArchive(long driverId);

String createDriverFaceModel(long driverId, String photo);
        
@Override
@Transactional
@LcnTransaction
public String createDriverFaceModel(long driverId, String photo) {
     HashMap map = driverDao.searchDriverNameAndSex(driverId);
     String name = MapUtil.getStr(map, "name");
     String sex = MapUtil.getStr(map, "sex");
     Credential cred = new Credential(secretId, secretKey); //import v20200303.models.CreatePersonRequest
     IaiClient client = new IaiClient(cred, region);
     try {
        CreatePersonRequest req = new CreatePersonRequest();
        req.setGroupId(groupName);
        req.setPersonId(driverId + "");
        long gender = sex.equals("男") ? 1L : 2L;
        req.setGender(gender);
        req.setQualityControl(4L);  //照片质量等级
        req.setUniquePersonControl(4L); //重复人员识别等级
        req.setPersonName(name);
        req.setImage(photo); //base图片
        CreatePersonResponse resp = client.CreatePerson(req);
        if (StrUtil.isNotBlank(resp.getFaceId())) {
        int rows = driverDao.updateDriverArchive(driverId);
        if (rows != 1) {
            return "更新司机归档字段失败";
        }
     }
     } catch (TencentCloudSDKException e) {
         log.error("创建腾讯云端司机档案失败", e);
        return "创建腾讯云端司机档案失败";
     }
     return "";
}

@Data
@Schema(description = "创建司机人脸模型归档的表单")
public class CreateDriverFaceModelForm {
   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

   @NotBlank(message = "photo不能为空")
   @Schema(description = "司机面部照片Base64字符串")
   private String photo;
}

@PostMapping("/createDriverFaceModel")
@Operation(summary = "创建司机人脸模型归档")
public R createDriverFaceModel(@RequestBody @Valid CreateDriverFaceModelForm form) {
     String result = driverService.createDriverFaceModel(form.getDriverId(), form.getPhoto());
     return R.ok().put("result", result);
}
```
4. 写 bff-driver/src/main/controller/form/CreateDriverFaceModelForm
   写 feign/DrServiceApi#createDriverFaceModel
   写 service/DriverService#createDriverFaceModel 及其实现类
   写 controller/DriverController#createDriverFaceModel
   通过后端远程 feign 调用实现数据持久化
```java
@Data
@Schema(description = "创建司机人脸模型归档的表单")
public class CreateDriverFaceModelForm {

    @Schema(description = "司机ID")
    private Long driverId;

    @NotBlank(message = "photo不能为空")
    @Schema(description = "司机面部照片Base64字符串")
    private String photo;

}

@PostMapping("/driver/createDriverFaceModel")
R createDriverFaceModel(CreateDriverFaceModelForm form);

String createDriverFaceModel(long driverId, String photo);

@Override
@Transactional
@LcnTransaction
public String createDriverFaceModel(CreateDriverFaceModelForm form) {
   R r = drServiceApi.createDriverFaceModel(form);
   String result = MapUtil.getStr(r, "result");
   return result;
}

@PostMapping("/createDriverFaceModel")
@Operation(summary = "创建司机人脸模型归档")
@SaCheckLogin
public R createDriverFaceModel(@RequestBody @Valid CreateDriverFaceModelForm form){
   long driverId = StpUtil.getLoginIdAsLong(); //satoken获取当前会话登录id, 并转化为long类型
   form.setDriverId(driverId);
   String result = driverService.createDriverFaceModel(form);
   return R.ok().put("result", result);
}
```

5. 写 hxds-driver-wx/identity/face_camera/face_camera.vue
   写 hxds-driver-wx/main.js 定义全局 URL 路径
   实现司机注册时必须的人脸识别认证。需要注意的一点是：腾讯云人脸识别-人员库的信息每人只能有一个，如果想重写注册不仅要删除 MySQL 还要删除人员库的数据

【拓展】 1、小程序初始化完成后，页面首次加载触发 onLoad()，只会触发一次；而 onShow() 可以执行多次。
				2、当小程序进入到后台(比如打电话去了)，先执行页面 onHide() 方法再执行应用 onHide() 方法。
				3、当小程序从后台进入到前台，先执行应用 onShow() 方法再执行页面 onShow() 方法

```vue
<!--face_camera.vue-->
<view>
  <view class="face-container">
    <camera device-position="front" flash="off" class="camera" @error="error" v-if="showCamera">
      <cover-image src="../static/face_camera/bg.png" class="bg"></cover-image>
    </camera>
    <view class="image-container" v-if="showImage">
      <image mode="widthFix" class="photo" :src="photoPath"></image>
      <view class="cover"></view>
    </view>
  </view>
  <view class="desc">
    <block v-if="mode == 'verificate'">
      <image src="../static/face_camera/tips.png" mode="widthFix" class="tips"></image>
      <text>请把面部放在圆圈内</text>
      <text>拍摄脸部来确认身份</text>
    </block>
    <block v-if="mode == 'create'">
      <image src="../static/face_camera/face.png" mode="widthFix" class="face"></image>
      <text>请把完整面部放在圆圈内</text>
      <text>拍摄脸部来保存身份识别数据</text>
    </block>
  </view>
  <button class="btn" @tap="confirmHandle">{{ mode == 'create' ? '录入面部信息' : '身份核实' }}</button>
</view>

	data() {
		return {
			mode: 'verificate',
			photoPath: '',
			showCamera: true,
			showImage: false,
			audio: null
		};
	},
	methods: {
		confirmHandle:function(){
			let that=this
			that.audio.stop()
			let ctx=uni.createCameraContext()
			ctx.takePhoto({
				quality:"high",
				success:function(resp){
					that.photoPath=resp.tempImagePath
					that.showCamera=false
					that.showImage=true
					uni.getFileSystemManager().readFile({
						filePath:that.photoPath,
						encoding:"base64",
						success:function(resp){
							let base64='data:image:/png;base64,'+resp.data
							let url=null
							if(that.mode=="create"){
								//创建司机面部模型档案
								url = that.url.createDriverFaceModel;
							}
							else{
								//验证司机面部模型
								url = that.url.verificateDriverFace;
							}
							that.ajax(url,"POST",{photo:base64},function(resp){
								let result=resp.data.result
								if(that.mode=="create"){
									if(result!=null&&result.length>0){
										console.error(result);
										uni.showToast({
											icon: 'none',
											title: '面部录入失败，请重新录入'
										});
										setTimeout(function() {
											that.showCamera = true;
											that.showImage = false;
										}, 2000);
									}
									else{
										uni.showToast({
											title: '面部录入成功'
										});
										setTimeout(function() {
											uni.switchTab({
												url: '../../pages/workbench/workbench'
											});
										}, 2000);
									}
								}
								else{
									//TODO 判断人脸识别结果
								}
							})
						}
					})
				}
			})
		}
	},
	onLoad: function(options) {
		let that=this
		that.mode=options.mode
		let audio=uni.createInnerAudioContext();
		that.audio=audio
		audio.src="/static/voice/voice_5.mp3"
		audio.play()
	},
	onHide: function() {
		if(this.audio!=null){
			this.audio.stop()
		}
	}
<!--main.js-->
createDriverFaceModel: `${baseUrl}/driver/createDriverFaceModel`,
verificateDriverFace: `${baseUrl}/driver/recognition/verificateDriverFace`,
```
### 司机微服务封装登陆过程
1. 写 hxds-dr/src/main/resource/mapper/DriverDao.xml#login 及其对应接口
   写 service/DriverService#login 及其实现类
   写 controller/form/LoginForm
   写 controller/DriverController#login
   【拓展】在 MySQL 中数字的查询速度要快于字符串的查询速度
```java
<select id="login" parameterType="String" resultType="HashMap">
  SELECT CAST(id AS CHAR) AS id,
         real_auth        AS realAuth,
         archive,
         tel
  FROM tb_driver
  WHERE `status` != 2 AND open_id = #{openId}
</select>

HashMap login(String openId);

HashMap login(String code);

@Override
public HashMap login(String code) {
  	String openId = microAppUtil.getOpenId(code);
  	HashMap result = driverDao.login(openId);
  	// 之前已经注册过，同时还要判断是否经历过人脸识别认证
  	if (result != null && result.containsKey("archive")) {
 		   int temp = MapUtil.getInt(result, "archive");
 		   boolean archive = (temp == 1) ? true : false;
 		   // HashMap.replace(K key, V oldValue, V newValue) 如果 oldValue 不存，则替换 key 对应的值，返回 key 对应的旧值
 		   // 如果存在 oldValue，替换成功返回 true，如果 key 不存在，则返回 null。
 		   result.replace("archive", archive);
 		}
  	return result;
}

@Data
@Schema(description = "司机登陆表单")
public class LoginForm {

   @NotBlank(message = "code不能为空")
   @Schema(description = "微信小程序临时授权")
   private String code;

}

@PostMapping("/login")
@Operation(summary = "登陆系统")
public R login(@RequestBody @Valid LoginForm form) {
   HashMap map = driverService.login(form.getCode());
   return R.ok().put("result", map);
}
```
2. 写 bff-driver/src/main/controller/form/LoginForm
   写 feign/DrServiceApi#login
   写 service/DriverService#login 及其实现类
   写 controller/DriverController#login
   通过后端远程 feign 调用实现司机登陆
   【总结】截止目前决定注册司机的状态有2个字段。archive:是否在腾讯云归档存放司机面部信息。0未录入，1录入；real_auth: 认证状态。1未认证，2已认证，3审核中
```java
@Data
@Schema(description = "司机登陆表单")
public class LoginForm {

    @NotBlank(message = "code不能为空")
    @Schema(description = "微信小程序临时授权")
    private String code;
  
}

@PostMapping("/driver/login")
public R login(LoginForm form);

HashMap login(LoginForm form);

@Override
public HashMap login(LoginForm form) {
   R r = drServiceApi.login(form);
   HashMap map = (HashMap) r.get("result");
   return map;
}

@PostMapping("/login")
@Operation(summary = "登陆系统")
public R login(@RequestBody @Valid LoginForm form){
   HashMap map=driverService.login(form);
   if(map!=null){
      long driverId= MapUtil.getLong(map,"id");
      byte realAuth=Byte.parseByte(MapUtil.getStr(map,"realAuth"));
      boolean archive=MapUtil.getBool(map,"archive");
      StpUtil.login(driverId);
      String token=StpUtil.getTokenInfo().getTokenValue();
      return R.ok().put("token",token).put("realAuth",realAuth).put("archive",archive);
   }
   return R.ok();
}
```
3. 写 hxds-driver-wx/pages/login/login.vue
   写 hxds-driver-wx/main.js 定义全局 URL 路径
   实现司机登陆

【拓展】1、redirectTo：关闭当前页，跳转到指定页；2、navigateTo：保留当前页，跳转到指定页；3、switchTab：只能用于跳转到 tabBar 页面，并关闭其他非 tabBar 页面

tabBar 页面：小程序的底部有图标加文字的几个按钮，每个按钮对应一个页面，而整个小程序中有很多页面，小程序底部图标加文字对应的几个页面是 tabBar 页面，这个在 pages.json 中有设置

```vue
<!--修改View-->
<button class="btn" @tap="login()">微信登陆</button>

login: function() {
    let that = this;
    uni.login({
        provider: 'weixin',
        success: function(resp) {
            let code = resp.code;
            let data = {
                code: code
            };
            console.log(data);
            that.ajax(that.url.login, 'POST', data,
            function(resp) {
                if (!resp.data.hasOwnProperty('token')) {
                    that.$refs.uToast.show({
                        title: '请先注册',
                        type: 'error'
                    });
                } else {
                    let token = resp.data.token;
                    let realAuth = resp.data.realAuth;
                    let archive = resp.data.archive;
                    uni.setStorageSync('token', token);
                    uni.setStorageSync('realAuth', realAuth);
                    uni.removeStorageSync('executeOrder');
                    that.$refs.uToast.show({
                        title: '登陆成功',
                        type: 'success',
                        callback: function() {
                            uni.setStorageSync('workStatus', '停止接单');
                            //检查用户是否没有填写实名信息
                            if (realAuth == 1) {
                                uni.redirectTo({
                                    url: '../../identity/filling/filling?mode=create'
                                });
                            } else if (archive == false) {
                                //检查系统是否存有司机的面部数据
                                uni.showModal({
                                    title: '提示消息',
                                    content: '您还没有录入用于核实身份的面部特征信息，如果不录入将无法接单',
                                    confirmText: '录入',
                                    cancelText: '取消',
                                    success: function(resp) {
                                        if (resp.confirm) {
                                            //跳转到面部识别页面，采集人脸数据
                                            uni.redirectTo({
                                                url: '../../identity/face_camera/face_camera?mode=create'
                                            });
                                        } else {
                                            uni.switchTab({
                                                url: '../workbench/workbench'
                                            });
                                        }
                                    }
                                });
                            } else {
                                uni.switchTab({
                                    url: '../workbench/workbench'
                                });
                            }
                        }
                    });
                }
            });
        }
    });
},

login: `${baseUrl}/driver/login`,
```
### 司机微服务查询司机个人汇总信息
1. 写 hxds-dr/src/main/resource/mapper/DriverDao.xml#searchDriverBaseInfo 及其对应接口
   写 service/DriverService#searchDriverBaseInfo 及其实现类
   写 controller/form/SearchDriverBaseInfoForm
   写 controller/DriverController#searchDriverBaseInfo
```java
<select id="searchDriverBaseInfo" parameterType="long" resultType="HashMap">
  SELECT d.open_id               AS openId,
         d.`name`,
         d.nickname,
         d.sex,
         d.photo,
         d.tel,
         d.email,
         d.pid,
         d.real_auth             AS realAuth,
         d.summary,
         d.`status`,
         CAST(w.balance AS CHAR) AS balance,
         d.create_time           AS createTime
  FROM tb_driver d
           JOIN tb_wallet w ON d.id = w.driver_id
  WHERE d.id = #{driverId};
</select>

HashMap searchDriverBaseInfo(long driverId);

HashMap searchDriverBaseInfo(long driverId);

@Override
public HashMap searchDriverBaseInfo(long driverId) {
     HashMap result = driverDao.searchDriverBaseInfo(driverId);
     JSONObject summary = JSONUtil.parseObj(MapUtil.getStr(result, "summary"));
     result.replace("summary", summary);
     return result;
}

@Data
@Schema(description = "查询司机基本信息的表单")
public class SearchDriverBaseInfoForm {
   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
}

@PostMapping("/searchDriverBaseInfo")
@Operation(summary = "查询司机基本信息")
public R searchDriverBaseInfo(@RequestBody @Valid SearchDriverBaseInfoForm form) {
     HashMap result = driverService.searchDriverBaseInfo(form.getDriverId());
     return R.ok().put("result", result);
}
```
2. 写 bff-driver/src/main/controller/form/SearchDriverBaseInfoForm
   写 feign/DrServiceApi#searchDriverBaseInfo
   写 service/DriverService#searchDriverBaseInfo 及其实现类
   写 controller/DriverController#searchDriverBaseInfo
   通过后端远程 feign 调用实现查询司机基础信息
   在[Swagger-dr](http://localhost:8001/swagger-ui/index.html?configUrl=/doc-api.html/swagger-config#/DriverController/searchDriverBaseInfo)和
   [Swagger-bff](http://localhost:8101/swagger-ui/index.html?configUrl=/doc-api.html/swagger-config)测试 searchDriverBaseInfo 接口
```java
@Data
@Schema(description = "查询司机基本信息的表单")
public class SearchDriverBaseInfoForm {
    @Schema(description = "司机ID")
    private Long driverId;
}

@PostMapping("/driver/searchDriverBaseInfo")
public R searchDriverBaseInfo(SearchDriverBaseInfoForm form);

HashMap searchDriverBaseInfo(SearchDriverBaseInfoForm form);

@Override
public HashMap searchDriverBaseInfo(SearchDriverBaseInfoForm form) {
   R r = drServiceApi.searchDriverBaseInfo(form);
   HashMap map = (HashMap) r.get("result");
   return map;
}

@PostMapping("/searchDriverBaseInfo")
@Operation(summary = "查询司机基本信息")
@SaCheckLogin
public R searchDriverBaseInfo(){
   long driverId=StpUtil.getLoginIdAsLong();
   SearchDriverBaseInfoForm form=new SearchDriverBaseInfoForm();
   form.setDriverId(driverId);
   HashMap map = driverService.searchDriverBaseInfo(form);
   return R.ok().put("result",map);
}
```
3. 写 hxds-driver-wx/pages/mine/mine.vue
   写 hxds-driver-wx/main.js 定义全局 URL 路径
```vue
methods: {
    logoutHandle: function() {
        let that = this;
        uni.vibrateShort({});
        uni.showModal({
            title: '提示信息',
            content: '确认退出系统？',
            success: function(resp) {
                if (resp.confirm) {
                    that.ajax(that.url.logout, 'GET', null,
                    function(resp) {
                        uni.removeStorageSync('realAuth');
                        uni.removeStorageSync('token');
                        uni.showToast({
                            title: '已经退出系统',
                            success: function() {
                                setTimeout(function() {
                                    uni.redirectTo({
                                        url: '../login/login'
                                    });
                                },
                                1500);
                            }
                        });
                    });
                }
            }
        });
    },
    serviceHandle: function() {
        uni.vibrateShort({});
        uni.makePhoneCall({
            phoneNumber: '10086'
        });
    },
    clearHandle: function() {
        uni.vibrateShort({});
        uni.showModal({
            title: '提示消息',
            content: '清理本地缓存',
            success: function(resp) {
                if (resp.confirm) {
                    uni.vibrateShort({});
                    uni.showLoading({
                        title: '执行中'
                    });
                    let cache = uni.getStorageInfoSync();
                    for (let key of cache.keys) {
                        if (key == 'token' || key == 'realAuth') {
                            continue;
                        }
                        uni.removeStorageSync(key);
                        console.log('删除Storage缓存成功');
                    }
                    uni.getSavedFileList({
                        success: function(resp) {
                            for (let one of resp.fileList) {
                                let path = one.filePath;
                                uni.removeSavedFile({
                                    filePath: path,
                                    success: function() {
                                        console.log('缓存文件删除成功');
                                    }
                                });
                            }
                        }
                    });
                    setTimeout(function() {
                        uni.hideLoading();
                        uni.showToast({
                            title: '清理完毕'
                        });
                    },
                    500);
                }
            }
        });
    }
},
onShow: function() {
    let that = this;
    that.ajax(that.url.searchDriverBaseInfo, 'POST', null,
    function(resp) {
        let result = resp.data.result;
        that.name = result.name;
        that.photo = result.photo;
        that.realAuth = uni.getStorageSync('realAuth') == 1;

        let createTime = dayjs(result.createTime, 'YYYY-MM-DD');
        let current = dayjs();
        let years = current.diff(createTime, 'years');
        that.years = years;
        that.level = result.summary.level;
        if (that.level < 10) {
            that.levelName = '初级代驾';
        } else if (that.level < 30) {
            that.levelName = '中级代驾';
        } else if (that.level < 50) {
            that.levelName = '高级代驾';
        } else {
            that.levelName = '王牌代驾';
        }
        that.balance = result.balance;
        that.totalOrder = result.summary.totalOrder;
        that.weekOrder = result.summary.weekOrder;
        that.weekComment = result.summary.weekComment;
        that.appeal = result.summary.appeal;
    });
},

searchDriverBaseInfo: `${baseUrl}/driver/searchDriverBaseInfo`,
```
### 司机微服务查询首页信息
1. 写 hxds-order/src/main/resource/mapper/OrderDao.xml#searchDriverTodayBusinessData 及其对应接口
   写 service/OrderService#searchDriverTodayBusinessData 及其实现类
   写 controller/form/SearchDriverTodayBusinessDataForm
   写 controller/OrderController#searchDriverTodayBusinessData
   【拓展】ShardingSphere 不支持 count(*) 语句[出处](https://shardingsphere.apache.org/document/current/cn/features/sharding/limitation/)
   对于为空的字段如果转换成 JSON 字符串会不显示该字段，这时需要用到 IFNULL 转化
   CURRENT_DATE 返回一个 DATE 值，表示本地时间的当前日期。 与所有无参数的 SQL 函数一样，不需要，也不接受任何括号。 在一个节点的处理过程中所有对 CURRENT_DATE 的调用保证都返回相同的值
```java
<select id="searchDriverTodayBusinessData" parameterType="long" resultType="HashMap">
     SELECT IFNULL(SUM(TIMESTAMPDIFF(HOUR, end_time, start_time)), 0) AS duration,
     CAST(IFNULL(SUM(real_fee),0) AS CHAR) AS income,
     COUNT(id) AS orders
     FROM tb_order
     WHERE driver_id = #{driverId}
     AND `status` IN (5,6,7,8)
     AND date = CURRENT_DATE
</select>
        
HashMap searchDriverTodayBusinessData(long driverId);

@Override
public HashMap searchDriverTodayBusinessData(long driverId) {
     return orderDao.searchDriverTodayBusinessData(driverId);
}

@Data
@Schema(description = "SearchDriverTodayBusinessDataForm")
public class SearchDriverTodayBusinessDataForm {

   @NotNull(message = "driverId cannot be null")
   @Min(value = 1, message = "driverId cannot less than 1")
   @Schema(description = "driverId")
   private Long driverId;

}

@PostMapping("/searchDriverTodayBusinessData")
@Operation(summary = "Search Driver Today Business Data")
public R searchDriverTodayBusinessData(@RequestBody @Valid SearchDriverTodayBusinessDataForm form) {
   HashMap result = orderService.searchDriverTodayBusinessData(form.getDriverId());
   return R.ok().put("result", result);
}
```
2. 写 hxds-dr/src/main/resource/mapper/DriverSettingDao.xml#searchDriverSettings 及其对应接口
   写 service/DriverService#searchDriverSettings 及其实现类
   写 controller/form/SearchDriverSettingsForm
   写 controller/DriverController#searchDriverSettings
```java
<select id="searchDriverSettings" parameterType="long" resultType="String">
   SELECT settings
   FROM tb_driver_settings
   WHERE driver_id = #{driverId}
</select>

String searchDriverSettings(long driverId);

HashMap searchDriverSettings(long driverId);

@Override
public HashMap searchDriverSettings(long driverId) {
     String settings = driverSettingsDao.searchDriverSettings(driverId);
     HashMap map = JSONUtil.parseObj(settings).toBean(HashMap.class);
     boolean bool = MapUtil.getInt(map,"listenService")==1;
     map.replace("listenService",bool);

     bool = MapUtil.getInt(map,"autoAccept")==1;
     map.replace("autoAccept",bool);

     return map;
}

@Data
@Schema(description = "查询司机设置的表单")
public class SearchDriverSettingsForm {
   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
}

@RestController
@RequestMapping("/settings")
@Tag(name = "SettingsController", description = "Driver Settings Web Controller")
public class DriverSettingsController {
   @Resource
   private DriverSettingsService driverSettingsService;

   @PostMapping("/searchDriverSettings")
   @Operation(summary = "Search Driver Settings")
   public R searchDriverSettings(@RequestBody @Valid SearchDriverSettingsForm form){
      HashMap result = driverSettingsService.searchDriverSettings(form.getDriverId());
      return R.ok().put("result", result);
   }
}
```
3. 写 bff-driver/src/main/controller/form/SearchDriverTodayBusinessDataForm & SearchDriverSettingsForm
   写 feign/OdrServiceApi#searchDriverTodayBusinessData
   写 feign/DrServiceApi#searchDriverSettings
   写 service/DriverService#searchWorkbenchData 及其实现类
   写 controller/DriverController#searchWorkbenchData
```java
@Data
@Schema(description = "Search Driver Today Business Data Form")
public class SearchDriverTodayBusinessDataForm {
    @Schema(description = "DriverId")
    private Long driverId;
}

@Data
@Schema(description = "SearchDriverSettingsForm")
public class SearchDriverSettingsForm {
   @Schema(description = "DriverId")
   private Long driverId;
}

@PostMapping("/settings/searchDriverSettings")
public R searchDriverSettings(SearchDriverSettingsForm form);

@PostMapping("/order/searchDriverTodayBusinessData")
public R searchDriverTodayBusinessData(SearchDriverTodayBusinessDataForm form);

public HashMap searchWorkbenchData(long driverId);

@Override
public HashMap searchWorkbenchData(long driverId) {
   SearchDriverTodayBusinessDataForm form_1 = new SearchDriverTodayBusinessDataForm();
   form_1.setDriverId(driverId);
   R r = odrServiceApi.searchDriverTodayBusinessData(form_1);
   HashMap order = (HashMap) r.get("result");

   SearchDriverSettingsForm form_2 = new SearchDriverSettingsForm();
   form_2.setDriverId(driverId);
   r = drServiceApi.searchDriverSettings(form_2);
   HashMap settings = (HashMap) r.get("result");

   HashMap result = new HashMap<>() {{
      put("business", order);
      put("settings", settings);
   }};
   return result;
}

@PostMapping("/searchWorkbenchData")
@Operation(summary = "查找司机工作台数据")
@SaCheckLogin
public R searchWorkbenchData(){
   HashMap result = driverService.searchWorkbenchData(StpUtil.getLoginIdAsLong());
   return R.ok().put("result", result);
}
```
4. 写 hxds-driver-wx/pages/workbench/workbench.vue
   写 hxds-driver-wx/main.js 定义全局 URL 路径
   给 tb_driver 表里的 real_auth 字段修改成 2已认证，然后启动后端5个子系统进行测试
```vue
methods: {
    changeListenService: function(bool) {
       if (bool) {
           this.service.listenIcon = '../../static/workbench/service-icon-3.png';
           this.service.listenStyle = 'color:#46B68F';
           this.service.listenText = '收听订单';
       } else {
            this.service.listenIcon = '../../static/workbench/service-icon-7.png';
            this.service.listenStyle = 'color:#FF4D4D';
            this.service.listenText = '不听订单';
       }
    },
},
onLoad: function() {},
onShow: function() {
   let that = this;
      if (!that.reviewAuth) {
      that.ajax(that.url.searchWorkbenchData, 'POST', null, function(resp) {
         let result = resp.data.result;
         that.hour = result.business.duration;
         that.income = result.business.income;
         that.orders = result.business.orders;
         
         let settings = result.settings;
         uni.setStorageSync('settings', settings);
         that.settings.listenService = settings.listenService;
         that.settings.autoAccept = settings.autoAccept;
         that.changeListenService(that.settings.listenService);
      });
   }
},
onHide: function() {}

searchWorkbenchData: `${baseUrl}/driver/searchWorkbenchData`,
```
### 司机微服务查询司机分页记录
1. 写 hxds-dr/src/main/resource/mapper/DriverDao.xml#searchDriverCount & searchDriverByPage 及其对应接口
   写 service/DriverService#searchDriverByPage 及其实现类
   写 controller/form/SearchDriverByPageForm
   写 controller/DriverController#searchDriverByPage
```java
 <select id="searchDriverByPage" parameterType="Map" resultType="HashMap">
     SELECT CAST(id AS CHAR) AS id,
     IFNULL(`name`,"") AS `name`,
     IFNULL(sex,"") AS sex,
     IFNULL(pid,"") AS pid,
     IFNULL(tel,"") AS tel,
     IFNULL(contact_name,"") AS contactName,
     IFNULL(contact_tel,"") AS contactTel,
     IFNULL(real_auth,"") AS realAuth,
     `status`,
     DATE_FORMAT(create_time, '%Y-%m-%d') AS createTime
     FROM tb_driver
     WHERE 1=1
     <if test="name!=null">
         AND `name` = #{name}
     </if>
     <if test="tel!=null">
         AND tel = #{tel}
     </if>
     <if test="pid!=null">
         AND pid = #{pid}
     </if>
     <if test="sex!=null">
         AND sex = #{sex}
     </if>
     <if test="realAuth!=null">
         AND `real_auth` = #{realAuth}
     </if>
     <if test="status!=null">
         AND `status` = #{status}
     </if>
     LIMIT #{start}, #{length}
 </select>
 <select id="searchDriverCount" parameterType="Map" resultType="long">
     SELECT COUNT(*)
     FROM tb_driver
     WHERE 1=1
     <if test="name!=null">
         AND `name` = #{name}
     </if>
     <if test="tel!=null">
         AND tel = #{tel}
     </if>
     <if test="pid!=null">
         AND pid = #{pid}
     </if>
     <if test="sex!=null">
         AND sex = #{sex}
     </if>
     <if test="realAuth!=null">
         AND `real_auth` = #{realAuth}
     </if>
     <if test="status!=null">
         AND `status` = #{status}
     </if>
 </select>

public ArrayList<HashMap> searchDriverByPage(Map param);
public long searchDriverCount(Map param);

public PageUtils searchDriverByPage(Map param);

@Override
public PageUtils searchDriverByPage(Map param) {
     long count = driverDao.searchDriverCount(param);
     ArrayList<HashMap> list = null;
     if (count == 0) {
         list = new ArrayList<>();
     } else {
         list = driverDao.searchDriverByPage(param);
     }
     int start = (Integer) param.get("start");
     int length = (Integer) param.get("length");
     PageUtils pageUtils = new PageUtils(list, count, start, length);
     return pageUtils;
}

@Data
@Schema(description = "查询司机分页记录的表单")
public class SearchDriverByPageForm {

   @NotNull(message = "page不能为空")
   @Min(value = 1, message = "page不能小于1")
   @Schema(description = "页数")
   private Integer page;

   @NotNull(message = "length不能为空")
   @Range(min = 10, max = 50, message = "length必须在10~50之间")
   @Schema(description = "每页记录数")
   private Integer length;

   @Pattern(regexp = "^[\\u4e00-\\u9fa5]{2,10}$", message = "name内容不正确")
   @Schema(description = "姓名")
   private String name;

   @Pattern(regexp = "^1\\d{10}$", message = "tel内容不正确")
   @Schema(description = "电话")
   private String tel;

   @Pattern(regexp = "^[1-9]\\d{5}(18|19|([23]\\d))\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$", message = "pid内容不正确")
   @Schema(description = "身份证")
   private String pid;

   @Pattern(regexp = "^男$|^女$", message = "sex内容不正确")
   @Schema(description = "性别")
   private String sex;

   @Range(min = 1, max = 3, message = "realAuth范围不对")
   @Schema(description = "实名认证")
   private Byte realAuth;

   @Range(min = 1, max = 3, message = "status范围不对")
   @Schema(description = "状态")
   private Byte status;
}

@PostMapping("/searchDriverByPage")
@Operation(summary = "查询司机分页记录")
public R searchDriverByPage(@RequestBody @Valid SearchDriverByPageForm form){
     Map param = BeanUtil.beanToMap(form);
     int page = form.getPage();
     int length = form.getLength();
     int start = (page-1)*length;
     param.put("start", start);
     PageUtils pageUtils = driverService.searchDriverByPage(param);
     return R.ok().put("result", pageUtils);
}
```
2. 写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchDriverByPageForm
   写 feign/DrServiceApi#searchDriverByPage
   写 service/DriverService#searchDriverByPage 及其实现类
   写 controller/DriverController#searchDriverByPage
   修改数据表 tb_user 的 username:admin,password:e523a41fc563203575d9b07be9c84872。运行 hxds-mis-vue，账号：admin,密码：abc123456 登陆
```java
@Data
@Schema(description = "查询司机分页记录的表单")
public class SearchDriverByPageForm {

   @NotNull(message = "page不能为空")
   @Min(value = 1, message = "page不能小于1")
   @Schema(description = "页数")
   private Integer page;

   @NotNull(message = "length不能为空")
   @Range(min = 10, max = 50, message = "length必须在10~50之间")
   @Schema(description = "每页记录数")
   private Integer length;

   @Pattern(regexp = "^[\\u4e00-\\u9fa5]{2,10}$", message = "name内容不正确")
   @Schema(description = "姓名")
   private String name;

   @Pattern(regexp = "^1\\d{10}$", message = "tel内容不正确")
   @Schema(description = "电话")
   private String tel;

   @Pattern(regexp = "^[1-9]\\d{5}(18|19|([23]\\d))\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$", message = "pid内容不正确")
   @Schema(description = "身份证")
   private String pid;

   @Pattern(regexp = "^男$|^女$", message = "sex内容不正确")
   @Schema(description = "性别")
   private String sex;

   @Range(min = 1, max = 3, message = "realAuth范围不对")
   @Schema(description = "实名认证")
   private Byte realAuth;

   @Range(min = 1, max = 3, message = "status范围不对")
   @Schema(description = "状态")
   private Byte status;
}

@PostMapping("/driver/searchDriverByPage")
public R searchDriverByPage(SearchDriverByPageForm form);

public PageUtils searchDriverByPage(SearchDriverByPageForm form);

@Override
public PageUtils searchDriverByPage(SearchDriverByPageForm form) {
   R r = drServiceApi.searchDriverByPage(form);
   PageUtils pageUtils = BeanUtil.toBean(r.get("result"), PageUtils.class);
   return pageUtils;
}

@PostMapping("/searchDriverByPage")
@SaCheckPermission(value = {"ROOT", "DRIVER:SELECT"}, mode = SaMode.OR)
@Operation(summary = "查询司机分页记录")
public R searchDriverByPage(@RequestBody @Valid SearchDriverByPageForm form) {
   PageUtils pageUtils = driverService.searchDriverByPage(form);
   return R.ok().put("result", pageUtils);
}
```
3. 写 hxds-mis-vue/src/views/driver.vue
   写 hxds-mis-vue/main.js 定义全局 URL 路径
```vue
  methods: {
    loadDataList: function() {
      let that = this;
      that.dataListLoading = true;
      let data = {
        page: that.pageIndex,
        length: that.pageSize,
        name: that.dataForm.name == '' ? null : that.dataForm.name,
        sex: that.dataForm.sex == '' ? null : that.dataForm.sex,
        tel: that.dataForm.tel == '' ? null : that.dataForm.tel,
        pid: that.dataForm.pid == '' ? null : that.dataForm.pid,
        realAuth: that.dataForm.realAuth == '' ? null : that.dataForm.realAuth,
        status: that.dataForm.status == '' ? null : that.dataForm.status
      };
      that.$http('driver/searchDriverByPage', 'POST', data, true, function(resp) {
        let result = resp.result;
        let list = result.list;
        let status = {
          '1': '正常',
          '2': '禁用',
          '3': '降低接单'
        };
        let realAuth = {
          '1': '未认证',
          '2': '已认证',
          '3': '审核中'
        };
        for (let one of list) {
          one.status = status[one.status + ''];
          one.realAuth = realAuth[one.realAuth + ''];
        }
        that.dataList = list;
        that.totalCount = Number(result.totalCount);
        that.dataListLoading = false;
      });
    },
    sizeChangeHandle: function(val) {
      this.pageSize = val;
      this.pageIndex = 1;
      this.loadDataList();
    },
    currentChangeHandle: function(val) {
      this.pageIndex = val;
      this.loadDataList();
    },
    searchHandle: function() {
      this.$refs['dataForm'].validate(valid => {
        if (valid) {
          this.$refs['dataForm'].clearValidate();
          this.loadDataList();
        } else {
          return false;
        }
      });
    },
    expand: function(row, expandedRows){
      let that=this
      that.expands=[]
      if(expandedRows.length>0){
        if(row){
          if(row.realAuth=="未认证"){
            return
          }
          that.expands.push(row.id)
          let data={
            realAuth:row.realAuth=="已认证"?2:3,
            driverId:row.id
          }
          that.$http("driver/searchDriverComprehensiveData","POST",data,false,function(resp){
            let summaryMap=resp.result.summaryMap
            that.content.year=summaryMap.year
            that.content.birthday = summaryMap.birthday;
            that.content.email = summaryMap.email;
            that.content.mailAddress = summaryMap.mailAddress;
            that.content.idcardAddress = summaryMap.idcardAddress;
            that.content.idcardFront = summaryMap.idcardFront;
            that.content.idcardFrontList = [summaryMap.idcardFront];
            that.content.idcardBack = summaryMap.idcardBack;
            that.content.idcardBackList = [summaryMap.idcardBack];
            that.content.idcardHolding = summaryMap.idcardHolding;
            that.content.idcardHoldingList = [summaryMap.idcardHolding];
            that.content.drcardFront = summaryMap.drcardFront;
            that.content.drcardFrontList = [summaryMap.drcardFront];
            that.content.drcardBack = summaryMap.drcardBack;
            that.content.drcardBackList = [summaryMap.drcardBack];
            that.content.drcardHolding = summaryMap.drcardHolding;
            that.content.drcardHoldingList = [summaryMap.drcardHolding];
          })
        }
      }
    },
    showApproveModel:function(id){
      this.approveModelVisible=true
      this.driverId=id
    },
    approveHandle:function(bool){
      let that=this
      let data={
        realAuth:(bool==true)?2:1,
        driverId:that.driverId
      }
      that.$http('driver/updateDriverRealAuth','POST',data,true,function(resp){
        if(resp.rows==1){
          that.approveModelVisible=false
          that.$message.success('数据更新成功');
          that.expands=[]
          that.loadDataList()
        }
      })
    },
    showRepealModel:function(id){
      this.repealModelVisible=true
      this.driverId=id
    },
    repealHandle:function(){
      let that=this
      let data={
        realAuth:3,
        driverId:that.driverId
      }
      that.$http('driver/updateDriverRealAuth','POST',data,true,function(resp){
        if(resp.rows==1){
          that.repealModelVisible=false
          that.$message.success('撤销审批成功');
          that.expands=[]
          that.loadDataList()
        }
      })
    }
  },
  created: function() {
    this.loadDataList();
  }


let baseUrl = "http://127.0.0.1:8201/hxds-mis-api/"
```
### 司机微服务修改司机个人信息
1. 写 hxds-dr/src/main/resource/mapper/DriverDao.xml#searchDriverAuth 及其对应接口
   写 service/DriverService#searchDriverAuth 及其实现类
   写 controller/form/SearchDriverAuthForm
   写 controller/DriverController#searchDriverAuth
```java
<select id="searchDriverAuth" parameterType="long" resultType="HashMap">
  SELECT IFNULL(`name`, '')            AS `name`,
         IFNULL(sex, '')               AS sex,
         IFNULL(pid, '')               AS pid,
         IFNULL(birthday, '')          AS birthday,
         IFNULL(tel, '')               AS tel,
         IFNULL(mail_address, '')      AS mailAddress,
         IFNULL(contact_name, '')      AS contactName,
         IFNULL(contact_tel, '')       AS contactTel,
         IFNULL(email, '')             AS email,
         IFNULL(real_auth, '')         AS realAuth,
         IFNULL(idcard_address, '')    AS idcardAddress,
         IFNULL(idcard_expiration, '') AS idcardExpiration,
         IFNULL(idcard_front, '')      AS idcardFront,
         IFNULL(idcard_back, '')       AS idcardBack,
         IFNULL(idcard_holding, '')    AS idcardHolding,
         IFNULL(drcard_type, '')       AS drcardType,
         IFNULL(drcard_expiration, '') AS drcardExpiration,
         IFNULL(drcard_issue_date, '') AS drcardIssueDate,
         IFNULL(drcard_front, '')      AS drcardFront,
         IFNULL(drcard_back, '')       AS drcardBack,
         IFNULL(drcard_holding, '')    AS drcardHolding
  FROM tb_driver
  WHERE id = #{driverId}
</select>

HashMap searchDriverAuth(long driverId);

HashMap searchDriverAuth(long driverId);

@Override
public HashMap searchDriverAuth(long driverId) {
     HashMap map = driverDao.searchDriverAuth(driverId);
     return map;
}

@Data
@Schema(description = "查询司机认证信息表单")
public class SearchDriverAuthForm {

   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
   
}

@PostMapping("/searchDriverAuth")
@Operation(summary = "查询司机认证信息")
public R searchDriverAuth(@RequestBody @Valid SearchDriverAuthForm form){
   HashMap result =driverService.searchDriverAuth(form.getDriverId());
   return R.ok().put("result", result);
}
```
2. 写 bff-driver/src/main/controller/form/SearchDriverAuthForm
   写 feign/OdrServiceApi#searchDriverAuth
   写 service/DriverService#searchDriverAuth 及其实现类
   写 controller/DriverController#searchDriverAuth
```java
@Data
@Schema(description = "查询司机认证信息表单")
public class SearchDriverAuthForm {

   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
}

@PostMapping("/driver/searchDriverAuth")
R searchDriverAuth(SearchDriverAuthForm form);

HashMap searchDriverAuth(SearchDriverAuthForm form);

@Override
public HashMap searchDriverAuth(SearchDriverAuthForm form) {
   R r = drServiceApi.searchDriverAuth(form);
   HashMap map = (HashMap) r.get("result");
   String idcardFront = MapUtil.getStr(map, "idcardFront");
   String idcardBack = MapUtil.getStr(map, "idcardBack");
   String idcardHolding = MapUtil.getStr(map, "idcardHolding");
   String drcardFront = MapUtil.getStr(map, "drcardFront");
   String drcardBack = MapUtil.getStr(map, "drcardBack");
   String drcardHolding = MapUtil.getStr(map, "drcardHolding");

   String idcardFrontUrl = idcardFront.length() > 0 ? cosUtil.getPrivateFileUrl(idcardFront) : "";
   String idcardBackUrl = idcardBack.length() > 0 ? cosUtil.getPrivateFileUrl(idcardBack) : "";
   String idcardHoldingUrl = idcardHolding.length() > 0 ? cosUtil.getPrivateFileUrl(idcardHolding) : "";
   String drcardFrontUrl = drcardFront.length() > 0 ? cosUtil.getPrivateFileUrl(drcardFront) : "";
   String drcardBackUrl = drcardBack.length() > 0 ? cosUtil.getPrivateFileUrl(drcardBack) : "";
   String drcardHoldingUrl = drcardHolding.length() > 0 ? cosUtil.getPrivateFileUrl(drcardHolding) : "";

   map.put("idcardFrontUrl", idcardFrontUrl);
   map.put("idcardBackUrl", idcardBackUrl);
   map.put("idcardHoldingUrl", idcardHoldingUrl);
   map.put("drcardFrontUrl", drcardFrontUrl);
   map.put("drcardBackUrl", drcardBackUrl);
   map.put("drcardHoldingUrl", drcardHoldingUrl);
   return map;
}

@GetMapping("/searchDriverAuth")
@Operation(summary = "查询司机认证信息")
@SaCheckLogin
public R searchDriverAuth(){
   long driverId = StpUtil.getLoginIdAsLong();
   SearchDriverAuthForm form = new SearchDriverAuthForm();
   form.setDriverId(driverId);
   HashMap map = driverService.searchDriverAuth(form);
   return R.ok().put("result",map);
}
```
3. 写 hxds-driver-wx/identify/filling/filling.vue#onLoad
   注意 hxds-driver-wx/pages/mine/mine.vue 奢姿点击事件跳转到 account.vue 页面( main.js 里封装了跳转语句)
   注意 hxds-driver-wx/pages/user/account/account.vue 奢姿点击事件跳转到 filling.vue 页面( main.js 里封装了跳转语句)
   写 hxds-driver-wx/main.js 定义全局 URL 路径
   启动5个子系统后进行真机调试，依次进入：个人中心--账号与安全--统一实名认证，当 real_auth 为3时说明认证信息在审核中，不可修改；为2时说明已认证可以修改
```vue
	onLoad: function(options) {
	    let that = this;
	    that.mode = options.mode;
	    if (uni.getStorageSync('realAuth') == 1) {
	        uni.showModal({
	            title: '提示信息',
	            content: '新注册的代驾司机请填写实名认证信息，并且上传相关证件照片',
	            showCancel: false
	        });
	    } else {
	        that.ajax(that.url.searchDriverAuth, 'GET', null, function(resp) {
	            let json = resp.data.result;
	            that.idcard.pid = json.pid;
	            that.idcard.name = json.name;
	            that.idcard.sex = json.sex;
	            that.idcard.birthday = json.birthday;
	            that.idcard.address = json.idcardAddress;
	            that.idcard.shortAddress = json.idcardAddress.substr(0, 15) + (json.idcardAddress.length > 15 ? '...' : '');
	            that.idcard.expiration = json.idcardExpiration;
	            that.idcard.idcardFront = json.idcardFront;
	            if (json.idcardFrontUrl.length > 0) {
	                that.cardBackground[0] = json.idcardFrontUrl;
	            }
	            that.idcard.idcardBack = json.idcardBack;
	            if (json.idcardBackUrl.length > 0) {
	                that.cardBackground[1] = json.idcardBackUrl;
	            }
	            that.idcard.idcardHolding = json.idcardHolding;
	            if (json.idcardHoldingUrl.length > 0) {
	                that.cardBackground[2] = json.idcardHoldingUrl;
	            }
	            that.contact.tel = json.tel;
	            that.contact.email = json.email;
	            that.contact.shortEmail = json.email.substr(0, 25) + (json.email.length > 25 ? '...' : '');
	            that.contact.mailAddress = json.mailAddress;
	            that.contact.shortMailAddress = json.mailAddress.substr(0, 15) + (json.mailAddress.length > 15 ? '...' : '');
	            that.contact.contactName = json.contactName;
	            that.contact.contactTel = json.contactTel;
	            that.drcard.carClass = json.drcardType;
	            that.drcard.validTo = json.drcardExpiration;
	            that.drcard.issueDate = json.drcardIssueDate;
	            that.drcard.drcardFront = json.drcardFront;
	            if (json.drcardFrontUrl.length > 0) {
	                that.cardBackground[3] = json.drcardFrontUrl;
	            }
	            that.drcard.drcardBack = json.drcardBack;
	            if (json.drcardBackUrl.length > 0) {
	                that.cardBackground[4] = json.drcardBackUrl;
	            }
	            that.drcard.drcardHolding = json.drcardHolding;
	            if (json.drcardHoldingUrl.length > 0) {
	                that.cardBackground[5] = json.drcardHoldingUrl;
	            }
	            if (that.idcard.idcardFront.length > 0) {
	                that.cosImg.push(that.idcard.idcardFront);
	                that.currentImg['idcardFront'] = that.idcard.idcardFront;
	            }
	            if (that.idcard.idcardBack.length > 0) {
	                that.cosImg.push(that.idcard.idcardBack);
	                that.currentImg['idcardBack'] = that.idcard.idcardBack;
	            }
	            if (that.idcard.idcardHolding.length > 0) {
	                that.cosImg.push(that.idcard.idcardHolding);
	                that.currentImg['idcardHolding'] = that.idcard.idcardHolding;
	            }
	            if (that.drcard.drcardFront.length > 0) {
	                that.cosImg.push(that.drcard.drcardFront);
	                that.currentImg['drcardFront'] = that.drcard.drcardFront;
	            }
	            if (that.drcard.drcardBack.length > 0) {
	                that.cosImg.push(that.drcard.drcardBack);
	                that.currentImg['drcardBack'] = that.drcard.drcardBack;
	            }
	            if (that.drcard.drcardHolding.length > 0) {
	                that.cosImg.push(that.drcard.drcardHolding);
	                that.currentImg['drcardHolding'] = that.drcard.drcardHolding;
	            }
	        });
	    }
	}

searchDriverAuth: `${baseUrl}/driver/searchDriverAuth`,
```
### 司机微服务查询司机实名认证申请
1. 写 hxds-dr/src/main/resource/mapper/DriverDao.xml#searchDriverRealSummary 及其对应接口
   写 service/DriverService#searchDriverRealSummary 及其实现类
   写 controller/form/SearchDriverRealSummaryForm
   写 controller/DriverController#searchDriverRealSummary
   【拓展】timestampdiff(YEAR, drcard_issue_date, NOW())) 查询当前年与 drcard_issue_date 差的年份
```java
 <select id="searchDriverRealSummary" parameterType="long" resultType="HashMap">
     SELECT timestampdiff(YEAR,drcard_issue_date, NOW()) AS `year`,
            birthday,
            email,
            mail_address                                 AS mailAddress,
            idcard_address                               AS idcardAddress,
            idcard_front                                 AS idcardFront,
            idcard_back                                  AS idcardBack,
            idcard_holding                               AS idcardHolding,
            drcard_front                                 AS drcardFront,
            drcard_back                                  AS drcardBack,
            drcard_holding                               AS drcardHolding
     FROM tb_driver
     WHERE id = #{driverId}
 </select>

HashMap searchDriverRealSummary(long driverId);

HashMap searchDriverRealSummary(long driverId);

@Override
public HashMap searchDriverRealSummary(long driverId) {
     HashMap map = driverDao.searchDriverRealSummary(driverId);
     return map;
}

@Data
@Schema(description = "查询司机")
public class SearchDriverRealSummaryForm {
   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
}

@PostMapping("/searchDriverRealSummary")
@Operation(summary = "查询司机实名信息摘要")
public R searchDriverRealSummary(@RequestBody @Valid SearchDriverRealSummaryForm form){
     HashMap map = driverService.searchDriverRealSummary(form.getDriverId());
     return R.ok().put("result", map);
}
```
2. 写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchDriverRealSummaryForm & SearchDriverComprehensiveDataForm
   写 feign/OdrServiceApi#searchDriverRealSummary
   写 service/DriverService#searchDriverComprehensiveData 及其实现类
   写 controller/DriverController#searchDriverComprehensiveData
   启动 tm、dr、mis、gateway 四个子系统然后获取最新 token 进行调试
```java
@Data
@Schema(description = "查询司机")
public class SearchDriverRealSummaryForm {
    @NotNull(message = "driverId不能为空")
    @Min(value = 1, message = "driverId不能小于1")
    @Schema(description = "司机ID")
    private Long driverId;
}

@Data
@Schema(description = "查询司机综合数据的表单")
public class SearchDriverComprehensiveDataForm {
   @NotNull(message = "realAuth不能为空")
   @Range(min = 1, max = 3, message = "realAuth范围不正确")
   @Schema(description = "是否已经实名认证")
   private Byte realAuth;

   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
}

@PostMapping("/driver/searchDriverRealSummary")
R searchDriverRealSummary(SearchDriverRealSummaryForm form);

HashMap searchDriverComprehensiveData(byte realAuth, Long driverId);

public HashMap searchDriverComprehensiveData(byte realAuth, Long driverId) {
   HashMap map = new HashMap();
   if (realAuth == 2 || realAuth == 3) {
      SearchDriverRealSummaryForm form_1 = new SearchDriverRealSummaryForm();
      form_1.setDriverId(driverId);
      R r = drServiceApi.searchDriverRealSummary(form_1);
      HashMap summaryMap = (HashMap) r.get("result");

      String idcardFront = MapUtil.getStr(summaryMap, "idcardFront");
      String idcardBack = MapUtil.getStr(summaryMap, "idcardBack");
      String idcardHolding = MapUtil.getStr(summaryMap, "idcardHolding");
      String drcardFront = MapUtil.getStr(summaryMap, "drcardFront");
      String drcardBack = MapUtil.getStr(summaryMap, "drcardBack");
      String drcardHolding = MapUtil.getStr(summaryMap, "drcardHolding");
      idcardFront = idcardFront.length() > 0 ? cosUtil.getPrivateFileUrl(idcardFront) : "";
      idcardBack = idcardBack.length() > 0 ? cosUtil.getPrivateFileUrl(idcardBack) : "";
      idcardHolding = idcardHolding.length() > 0 ? cosUtil.getPrivateFileUrl(idcardHolding) : "";
      drcardFront = drcardFront.length() > 0 ? cosUtil.getPrivateFileUrl(drcardFront) : "";
      drcardBack = drcardBack.length() > 0 ? cosUtil.getPrivateFileUrl(drcardBack) : "";
      drcardHolding = drcardHolding.length() > 0 ? cosUtil.getPrivateFileUrl(drcardHolding) : "";
      summaryMap.replace("idcardFront", idcardFront);
      summaryMap.replace("idcardBack", idcardBack);
      summaryMap.replace("idcardHolding", idcardHolding);
      summaryMap.replace("drcardFront", drcardFront);
      summaryMap.replace("drcardBack", drcardBack);
      summaryMap.replace("drcardHolding", drcardHolding);
      map.put("summaryMap", summaryMap);

      //TODO 这里以后还有很多要写的东西
   }
   return map;
}

@PostMapping("/searchDriverComprehensiveData")
@SaCheckPermission(value = {"ROOT", "DRIVER:SELECT"}, mode = SaMode.OR)
@Operation(summary = "查询司机综合数据")
public R searchDriverComprehensiveData(@RequestBody @Valid SearchDriverComprehensiveDataForm form) {
   HashMap map = driverService.searchDriverComprehensiveData(form.getRealAuth(), form.getDriverId());
   return R.ok().put("result", map);
}
```
3. 写 hxds-mis-vue/src/views/driver.vue#expand
```vue
 expand: function(row, expandedRows){
   let that=this
   that.expands=[]
   if(expandedRows.length>0){
     if(row){
       if(row.realAuth=="未认证"){
         return
       }
       that.expands.push(row.id)
       let data={
         realAuth:row.realAuth=="已认证"?2:3,
         driverId:row.id
       }
       that.$http("driver/searchDriverComprehensiveData","POST",data,false,function(resp){
         let summaryMap=resp.result.summaryMap
         that.content.year=summaryMap.year
         that.content.birthday = summaryMap.birthday;
         that.content.email = summaryMap.email;
         that.content.mailAddress = summaryMap.mailAddress;
         that.content.idcardAddress = summaryMap.idcardAddress;
         that.content.idcardFront = summaryMap.idcardFront;
         that.content.idcardFrontList = [summaryMap.idcardFront];
         that.content.idcardBack = summaryMap.idcardBack;
         that.content.idcardBackList = [summaryMap.idcardBack];
         that.content.idcardHolding = summaryMap.idcardHolding;
         that.content.idcardHoldingList = [summaryMap.idcardHolding];
         that.content.drcardFront = summaryMap.drcardFront;
         that.content.drcardFrontList = [summaryMap.drcardFront];
         that.content.drcardBack = summaryMap.drcardBack;
         that.content.drcardBackList = [summaryMap.drcardBack];
         that.content.drcardHolding = summaryMap.drcardHolding;
         that.content.drcardHoldingList = [summaryMap.drcardHolding];
       })
     }
   }
 },
```
### 司机微服务更新司机备案状态
1. 写 hxds-dr/src/main/resource/mapper/DriverDao.xml#updateDriverRealAuth 及其对应接口
   写 service/DriverService#updateDriverRealAuth 及其实现类
   写 controller/form/UpdateDriverRealAuthForm
   写 controller/DriverController#updateDriverRealAuth
```java
 <update id="updateDriverRealAuth" parameterType="Map">
     UPDATE tb_driver
     SET real_auth = #{realAuth}
     WHERE id = #{driverId}
 </update>

int updateDriverRealAuth(Map param);

int updateDriverRealAuth(Map param);

@Override
@LcnTransaction
@Transactional
public int updateDriverRealAuth(Map param) {
     int rows = driverDao.updateDriverRealAuth(param);
     return rows;
}

@Data
@Schema(description = "更新司机实名认证状态的表单")
public class UpdateDriverRealAuthForm {
   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

   @NotNull(message = "realAuth不能为空")
   @Range(min = 1, max = 3, message = "realAuth范围不正确")
   @Schema(description = "实名认证状态")
   private Byte realAuth;
}

@PostMapping("/updateDriverRealAuth")
@Operation(summary = "更新司机实名认证状态")
public R updateDriverRealAuth(@RequestBody @Valid UpdateDriverRealAuthForm form){
     int rows = driverService.updateDriverRealAuth(BeanUtil.beanToMap(form));
     return R.ok().put("rows", rows);
}
```
2. 写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/UpdateDriverRealAuthForm
   写 feign/OdrServiceApi#updateDriverRealAuth
   写 service/DriverService#updateDriverRealAuth 及其实现类
   写 controller/DriverController#updateDriverRealAuth
   启动 tm、dr、mis、gateway 四个子系统然后获取最新 token 进行调试
```java
@Data
@Schema(description = "更新司机实名认证状态的表单")
public class UpdateDriverRealAuthForm {
   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

   @NotNull(message = "realAuth不能为空")
   @Range(min = 1, max = 3, message = "realAuth范围不正确")
   @Schema(description = "实名认证状态")
   private Byte realAuth;
}

@PostMapping("/driver/updateDriverRealAuth")
public R updateDriverRealAuth(UpdateDriverRealAuthForm form);

int updateDriverRealAuth(UpdateDriverRealAuthForm form);

@Override
@Transactional
@LcnTransaction
public int updateDriverRealAuth(UpdateDriverRealAuthForm form) {
   R r = drServiceApi.updateDriverRealAuth(form);
   int rows = MapUtil.getInt(r, "rows");
   //TODO 调用消息子系统发送消息
   return rows;
}

@PostMapping("/updateDriverRealAuth")
@SaCheckPermission(value = {"ROOT", "DRIVER:UPDATE"}, mode = SaMode.OR)
@Operation(summary = "更新司机实名认证状态")
public R updateDriverRealAuth(@RequestBody @Valid UpdateDriverRealAuthForm form) {
   int rows = driverService.updateDriverRealAuth(form);
   return R.ok().put("rows", rows);
}
```
3. 写 hxds-mis-vue/src/views/driver.vue#approveHandle & repealHandle 即 审批认证/撤销认证
```vue
showApproveModel:function(id){
   this.approveModelVisible=true
   this.driverId=id
},
approveHandle:function(bool){
   let that=this
   let data={
       realAuth:(bool==true)?2:1,
       driverId:that.driverId
   }
   that.$http('driver/updateDriverRealAuth','POST',data,true,function(resp){
       if(resp.rows==1){
           that.approveModelVisible=false
           that.$message.success('数据更新成功');
           that.expands=[]
           that.loadDataList()
       }
   })
},
showRepealModel:function(id){
   this.repealModelVisible=true
   this.driverId=id
},
repealHandle:function(){
   let that=this
   let data={
       realAuth:3,
       driverId:that.driverId
   }
   that.$http('driver/updateDriverRealAuth','POST',data,true,function(resp){
       if(resp.rows==1){
           that.repealModelVisible=false
           that.$message.success('撤销审批成功');
           that.expands=[]
           that.loadDataList()
       }
   })
}
```
## 乘客下单与司机抢单
### 开通腾讯位置服务，封装腾讯地图服务

进入[腾讯位置服务官网](https://lbs.qq.com/)，注册登录后创建应用、Key。
创建 Key 时要选择 WebServiceAPI--白名单、微信小程序，并填写自己的小程序 APP ID。菜单的 开发文档 里有多端的接入教程。
创建完成后将得到的 Key 填写进 hxds/common/src/main/resources/application-common.yml

#### 封装预估里程和时间
1. 腾讯位置服务[距离矩阵文档](https://lbs.qq.com/service/webService/webServiceGuide/webServiceMatrix)
   写 hxds-mps/src/main/java/com/example/hxds/mps/service/MapService#estimateOrderMileageAndMinute 及其实现类
   写 controller/form/EstimateOrderMileageAndMinuteForm
   写 controller/MapController#estimateOrderMileageAndMinute
```java
HashMap estimateOrderMileageAndMinute(String mode,String startPlaceLatitude,
        String startPlaceLongitude,String endPlaceLatitude,String endPlaceLongitude);

@Service
public class MapServiceImpl implements MapService {

   private String distanceUrl = "https://apis.map.qq.com/ws/distance/v1/matrix/";


   private String directionUrl = "https://apis.map.qq.com/ws/direction/v1/driving/";

   @Value("${tencent.map.key}")
   private String key;

   @Override
   public HashMap estimateOrderMileageAndMinute(String mode,
                                                String startPlaceLatitude,
                                                String startPlaceLongitude,
                                                String endPlaceLatitude,
                                                String endPlaceLongitude) {
      HttpRequest req = new HttpRequest(distanceUrl);
      req.form("mode", mode);
      req.form("from", startPlaceLatitude + "," + startPlaceLongitude);
      req.form("to", endPlaceLatitude + "," + endPlaceLongitude);
      req.form("key", key);
      HttpResponse resp = req.execute();
      JSONObject json = JSONUtil.parseObj(resp.body());
      int status = json.getInt("status");
      String message = json.getStr("message");
      System.out.println(message);
      if (status != 0) {
         Console.log(message);
         throw new HxdsException("预估里程异常：" + message);
      }
      JSONArray rows = json.getJSONObject("result").getJSONArray("rows");
      JSONObject element = rows.get(0, JSONObject.class).getJSONArray("elements").get(0, JSONObject.class);
      int distance = element.getInt("distance");
      String mileage = new BigDecimal(distance).divide(new BigDecimal(1000)).toString();
      int duration = element.getInt("duration");
      String temp = new BigDecimal(duration).divide(new BigDecimal(60), 0, RoundingMode.CEILING).toString();
      int minute = Integer.parseInt(temp);

      HashMap map = new HashMap() {{
         put("mileage", mileage);
         put("minute", minute);
      }};
      return map;
   }
}

@Data
@Schema(description = "预估里程和时间的表单")
public class EstimateOrderMileageAndMinuteForm {
   @NotBlank(message = "mode不能为空")
   @Pattern(regexp = "^driving$|^walking$|^bicycling$")
   @Schema(description = "计算方式")
   private String mode;

   @NotBlank(message = "startPlaceLatitude不能为空")
   @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "startPlaceLatitude内容不正确")
   @Schema(description = "订单起点的纬度")
   private String startPlaceLatitude;

   @NotBlank(message = "startPlaceLongitude不能为空")
   @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "startPlaceLongitude内容不正确")
   @Schema(description = "订单起点的经度")
   private String startPlaceLongitude;

   @NotBlank(message = "endPlaceLatitude不能为空")
   @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "endPlaceLatitude内容不正确")
   @Schema(description = "订单终点的纬度")
   private String endPlaceLatitude;

   @NotBlank(message = "endPlaceLongitude不能为空")
   @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "endPlaceLongitude内容不正确")
   @Schema(description = "订单起点的经度")
   private String endPlaceLongitude;
}

@RestController
@RequestMapping("/map")
@Tag(name = "MapController", description = "地图Web接口")
public class MapController {
   @Resource
   private MapService mapService;

   @PostMapping("/estimateOrderMileageAndMinute")
   @Operation(summary = "估算里程和时间")
   public R estimateOrderMileageAndMinute(@RequestBody @Valid EstimateOrderMileageAndMinuteForm form) {
      HashMap map = mapService.estimateOrderMileageAndMinute(form.getMode(),
              form.getStartPlaceLatitude(), form.getStartPlaceLongitude(),
              form.getEndPlaceLatitude(), form.getEndPlaceLongitude());
      return R.ok().put("result", map);
   }
}
```
2. 腾讯位置服务[路线规划文档](https://lbs.qq.com/service/webService/webServiceGuide/webServiceRoute)
   写 hxds-mps/src/main/java/com/example/hxds/mps/service/MapService#calculateDriverLine 及其实现类
   写 controller/form/CalculateDriverLineForm
   写 controller/MapController#calculateDriverLine
```java
HashMap calculateDriveLine(String startPlaceLatitude,String startPlaceLongitude,
                  String endPlaceLatitude,String endPlaceLongitude);

public HashMap calculateDriveLine(String startPlaceLatitude,
     String startPlaceLongitude,
     String endPlaceLatitude,
     String endPlaceLongitude) {
     HttpRequest req = new HttpRequest(directionUrl);
     req.form("from", startPlaceLatitude + "," + startPlaceLongitude);
     req.form("to", endPlaceLatitude + "," + endPlaceLongitude);
     req.form("key", key);
   
     HttpResponse resp = req.execute();
     JSONObject json = JSONUtil.parseObj(resp.body());
     int status = json.getInt("status");
     if (status != 0) {
     throw new HxdsException("执行异常");
        }
     JSONObject result = json.getJSONObject("result");
     HashMap map = result.toBean(HashMap.class);
     return map;
}

@Data
@Schema(description = "计算行驶路线的表单")
public class CalculateDriveLineForm {
   @NotBlank(message = "startPlaceLatitude不能为空")
   @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "startPlaceLatitude内容不正确")
   @Schema(description = "订单起点的纬度")
   private String startPlaceLatitude;

   @NotBlank(message = "startPlaceLongitude不能为空")
   @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "startPlaceLongitude内容不正确")
   @Schema(description = "订单起点的经度")
   private String startPlaceLongitude;

   @NotBlank(message = "endPlaceLatitude不能为空")
   @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "endPlaceLatitude内容不正确")
   @Schema(description = "订单终点的纬度")
   private String endPlaceLatitude;

   @NotBlank(message = "endPlaceLongitude不能为空")
   @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "endPlaceLongitude内容不正确")
   @Schema(description = "订单起点的经度")
   private String endPlaceLongitude;
}

@PostMapping("/calculateDriveLine")
@Operation(summary = "计算行驶路线")
public R calculateDriveLine(@RequestBody @Valid CalculateDriveLineForm form) {
     HashMap map = mapService.calculateDriveLine(form.getStartPlaceLatitude(), form.getStartPlaceLongitude(),
     form.getEndPlaceLatitude(), form.getEndPlaceLongitude());
     return R.ok().put("result", map);
}
```
3. 去腾讯地图[坐标拾取](https://lbs.qq.com/getPoint/)找两个点进行测试，比如
```java
/map/estimateOrderMileageAndMinute
{
   "mode": "driving",
   "startPlaceLatitude": "40.007972",
   "startPlaceLongitude": "116.551499",
   "endPlaceLatitude": "39.992185",
   "endPlaceLongitude": "116.484264"
}
```
### 乘客端显示地图定位，地图选点设置起点和终点
1. 配置腾讯位置密钥：在 hxds-customer-wx/main.js 文件中，定义腾讯位置服务的密钥，接入腾讯地图组件时需要用到
   开启实时定位：把开启实时定位的代码写到 hxds-customer-wx/App.vue#onLaunch 函数里
   捕获定位事件：写 hxds-customer-wx/pages/workbench/workbench.vue#onShow 捕获自定义事件
   点击回位：写 hxds-customer-wx/pages/workbench/workbench.vue#returnLocationHandle 回归初始定位
   选择起点和终点：写 hxds-customer-wx/pages/workbench/workbench.vue#chooseLocationHandle 实现地图选点
   【拓展】小程序使用 startLocationUpdate 函数可以在使用过程中实时获取定位，挂到后台就不获取定位，相关文档 [传送门](https://developers.weixin.qq.com/miniprogram/dev/api/location/wx.startLocationUpdate.html)
   而 startLocationUpdateBackground 和它正相反，这里我们不使用它就不详细介绍了
   小程序启动后会运行 onLaunch 函数，之前用过的 onShow、onLoad 只针对的是小程序页面启动
   把坐标转换成地址的操作叫"逆地址解析"，相关文档 [传送门](https://lbs.qq.com/miniProgram/jsSdk/jsSdkGuide/methodReverseGeocoder)
```vue
Vue.prototype.tencent = {
   map: {
      referer: "华夏代驾",
      key: "FGRBZ-GS266-44VSO-ER7OG-IW5S7-ANB47"
   }
}

onLaunch: function() {
   //开启GPS后台刷新
   wx.startLocationUpdate({
      success(resp) {
      console.log('开启定位成功');
      },
      fail(resp) {
      console.log('开启定位失败');
      }
   });
   //GPS定位变化就自动提交给后端
   wx.onLocationChange(function(resp) {
      let latitude = resp.latitude;
      let longitude = resp.longitude;
      let location = { latitude: latitude, longitude: longitude };
      //触发自定义事件
      uni.$emit('updateLocation', location);
   });
},

<script>
const chooseLocation = requirePlugin('chooseLocation');
let QQMapWX = require('../../lib/qqmap-wx-jssdk.min.js');

export default {
   data() {
      return {
         from: {
            address: '',
            longitude: 0,
            latitude: 0
         },
         to: {
            address: '输入你的目的地',
            longitude: 0,
            latitude: 0
         },
         longitude: 116.397505,
         latitude: 39.908675,
         contentStyle: '',
         windowHeight: 0,
         map: null,
         flag: null
      };
   },
   methods: {
      returnLocationHandle: function() {
         this.map.moveToLocation();
      },
      chooseLocationHandle: function(flag) {
         let that = this;
         let key = that.tencent.map.key; //使用在腾讯位置服务申请的key
         let referer = that.tencent.map.referer; //调用插件的app的名称
         let latitude = that.latitude;
         let longitude = that.longitude;
         that.flag = flag;
         let data = JSON.stringify({
            latitude: latitude,
            longitude: longitude
         });
         uni.navigateTo({
            url: `plugin://chooseLocation/index?key=${key}&referer=${referer}&location=${data}`
         });
      },
   },
   onShow: function() {
      let that = this;
      that.map = uni.createMapContext('map');
      let qqmapsdk = new QQMapWX({
         key: that.tencent.map.key
      });
      //实时获取定位
      uni.$on('updateLocation', function(location) {
         //console.log(location);
         //避免地图选点的内容被逆地址解析覆盖
         if(that.flag!=null){
            return
         }
         let latitude = location.latitude;
         let longitude = location.longitude;
         that.latitude = latitude;
         that.longitude = longitude;
         that.from.latitude = latitude;
         that.from.longitude = longitude;
         //把坐标解析成地址
         qqmapsdk.reverseGeocoder({
            location: {
               latitude: latitude,
               longitude: longitude
            },
            success: function(resp) {
               //console.log(resp);
               that.from.address = resp.result.address;
            },
            fail: function(error) {
               console.log(error);
            }
         });
      });
      let location = chooseLocation.getLocation();
      if (location != null) {
         let place = location.name;
         let latitude = location.latitude;
         let longitude = location.longitude;
         if (that.flag == 'from') {
            that.from.address = place;
            that.from.latitude = latitude;
            that.from.longitude = longitude;
         } else {
            that.to.address = place;
            that.to.latitude = latitude;
            that.to.longitude = longitude;
            uni.setStorageSync("from", that.from)
            uni.setStorageSync("to", that.to)
            uni.navigateTo({
               url: `../create_order/create_order`
            });
         }
      }
   },
   onHide: function() {
      uni.$off('updateLocation');
      chooseLocation.setLocation(null);
   },
   onLoad: function() {
      let that = this;
      let windowHeight = uni.getSystemInfoSync().windowHeight;
      that.windowHeight = windowHeight;
      that.contentStyle = `height:${that.windowHeight}px;`;
   },
   onUnload: function() {
      chooseLocation.setLocation(null);
   }
};
</script>
```
### 乘客端创建预览订单