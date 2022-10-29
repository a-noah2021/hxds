# 搭建环境

这个项目对硬件的要求还是不低的，最低需要 16G 内存。我的笔记本运行起来很勉强，所以买了两个云服务器来搭建环境。两个服务器都是 2C4G 的，其中 MySQL 集群和 NoSQL 部署在一台，云存储和其他中间件部署在另一台。

## 搭建MySQL集群

### 目标

搭建 5 个 MySQL 节点，其中四个作为集群节点，剩下的一个作为分布式配置存储节点

### 背景

#### 数据切分

​		MySQL单表数据超过两千万，CRUD性能就会急速下降，所以我们需要把同一张表的数据切分到不同的MySQL节点中。这需要引入MySQL中间件，其实就是个SQL路由器而已。MyCat、ProxySQL、ShardingSphere等等。课程中选择了 ShardingSphere 而且还是Apache负责维护的，国内也有很多项目组在用这个产品，手册资料相对齐全，所以相对来说是个主流的中间件。

  ![https://img1.sycdn.imooc.com/62f4d68d0001d47504480348.jpg](https://img1.sycdn.imooc.com/62f4d68d0001d47504480348.jpg)



​		规则是：在MySQL_1和MySQL_2两个节点上分别创建订单表，然后在ShardingSphere做好设置。如果INSERT语句主键值对2求模余0，这个INSERT语句就路由给MySQL_1节点；如果余数是1，INSERT语句就被路由给MyQL_2执行，通过控制SQL语句的转发就能把订单数据切分到不同的MySQL节点上。将来查询的数据的时候，ShardingSphere把SELECT语句发送给每个MySQL节点执行，然后ShardingSphere把得到的数据做汇总返回给Navicat就行了。我们在Navicat上面执行CRUD操作，几乎跟操作单节点MySQL差不多，但是这背后确实通过路由SQL语句来实现的。

​		解释：我们可以将海量得数据切分到不同得mysql节点。但是时间日积月累 每个mysql里面得数据还是会超过几千万条， 这时候 就必须做归档。 对于一年以上得业务数据,可以看作过期得冷数据，我们可以把这部分得数据转移到归档数据库， 例如ToKuDB、MongoDB或者HBase里面（虽然没用过）

   	这样MySQL节点就实现缩表了，性能也就上去了。比方说你在银行APP上面只能插到12个月以内的流水账单，再早的账单是查不到的。这就是银行做了冷数据归档操作，只有银行内部少数人可以查阅这些过期的冷数据

#### 数据同步

​		数据切分虽然能应对大量业务数据的存储，但是MySQL_1和MySQL_2节点数据是不同的，而且还没有备用的冗余节点，一旦宕机就会严重影响线上业务。接下来我们要考虑怎么给MySQL节点设置冗余节点。

​		MySQL自带了Master-Slave数据同步模式，也被称作主从同步模式。例如MySQL_A节点开启了binlog日志文件之后，MySQL_A上面执行SQL语句都会被记录在binlog日志里面。MySQL_B节点通过订阅MySQL_A的binlog文件，能实时下载到这个日志文件，然后在MySQL_B节点上运行这些SQL语句，于是就保证了自己的数据和MySQL_A节点一致

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

2. 创建 Docker 内网网段。为了给 Docker 中的容器分配固定的 Docker 内网 IP 地址，而且和其他现存容器 IP 不发生冲突，我们需要创建一个 Docker 内网的网段 mycat: 172.18.0.X，以后我们创建的容器都分配到这个网段上。需要注意的是，172.18.0.1 是网关的 IP ( 不可用 )

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
8. 写 hxds-driver-wx/identity/filling/filling.vue#enterContent/save/showAddressContent，实现移动端 ( 输入-保存-展示 ) 联络方式/紧急联系人一整套链路
小程序视图层上面的联系方式排版设计比较简单，直接引用的 uView 组件库里的列表控件，相关文档：[传送门](https://v1.uviewui.com/components/cell.html)
每个列表项都设置里点击事件 enterContent
```vue
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
<select id="updateDriverArchive" parameterType="long">
  update tb_driver
  set archive = 1
  WHERE id = #{driverId}
</select>


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
   实现司机每天第一次节点时的人脸识别认证

```vue
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
```

