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

2. 创建并启动 Sentinel 容器

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

### 司机微服务的用户注册功能下

1. 写 bff-driver 里面的 feign#DrServiceApi#registerNewDriver
2. 写 bff-driver 里面的 service#DriverService#registerNewDriver
3. 写 bff-driver 里面的 controller#