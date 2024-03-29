# 搭建环境

本章用 Linux 系统和 Docker 环境搭建 4 节点 2 分片的 MySQL 集群，以及部署一些与代驾项目相关 NoSQL 数据库和中间件。搭建好了微服务体系的开发和运行环境，我们还要从远程调用、分布式事务，以及鉴权来理解微服务的运行原理

这个项目对硬件的要求还是不低的，最低需要 16G 内存。我的笔记本运行起来很勉强，所以买了两个云服务器来搭建环境。两个服务器都是 2C4G 的，其中 MySQL 集群和 NoSQL 部署在一台，云存储和其他中间件部署在另一台。

## 搭建MySQL集群

### 目标

搭建 5 个 MySQL 节点，其中四个作为集群节点，剩下的一个作为分布式配置存储节点，并不存储业务数据

### 背景

**数据切分**

MySQL 单表数据超过两千万，CRUD 性能就会急速下降，所以我们需要把同一张表的数据切分到不同的 MySQL 节点中。这需要引入 MySQL 中间件，其实就是个 SQL 路由器而已。这种集群中间件有很多，比如 MyCat、ProxySQL、ShardingSphere 等等。因为 MyCat 弃管了，所以我选择了 ShardingSphere，功能不输给 MyCat，而且还是 Apache 负责维护的，国内也有很多项目组在用这个产品，手册资料相对齐全，所以相对来说是个主流的中间件

  ![https://img1.sycdn.imooc.com/62f4d68d0001d47504480348.jpg](https://img1.sycdn.imooc.com/62f4d68d0001d47504480348.jpg)

在 MySQL_1 和 MySQL_2 两个节点上分别创建订单表，然后在 ShardingSphere 做好设置。如果 INSERT 语句主键值对 2 求模余 0，这个 INSERT 语句就路由给 MySQL_1 节点；如果余数是 1，INSERT 语句就被路由给 MySQL_2 执行。通过控制 SQL 语句的转发就能把订单数据切分到不同的 MySQL 节点上了。将来查询的数据的时候，ShardingSphere 把 SELECT 语句发送给每个 MySQL 节点执行，然后 ShardingSphere 把得到的数据做汇总返回给 Navicat 就行了。我们在 Navicat 上面执行 CRUD 操作，几乎跟操作单节点 MySQL 差不多，但是这背后确实通过路由 SQL 语句来实现的

你可能要问，即便海量数据可以切分到不同的 MySQL 节点，但是日积月累，每个 MySQL 里面的数据还是会超过两千万的，那该怎么办？这个也简单，做数据归档就好了。对于 1 年以上的业务数据，可以看做是过期的冷数据。我们可以把这部分数据转移到归档库里面，例如 ToKuDB、MongoDB 或者 HBase 里面。这样 MySQL 节点就实现缩表了，性能也就上去了。比方说你在银行 APP 上面只能查到 12 个月以内的流水账单，再早的账单是查不到的。这就是银行做了冷数据归档操作，只有银行内部少数人可以查阅这些过期的冷数据

**数据同步**

数据切分虽然能应对大量业务数据的存储，但是 MySQL_1 和 MySQL_2 节点数据是不同的，而且还没有备用的冗余节点，一旦宕机就会严重影响线上业务。接下来我们要考虑怎么给 MySQL 节点设置冗余节点

MySQL 自带了 Master-Slave 数据同步模式，也被称作主从同步模式。例如 MySQL_A 节点开启了 binlog 日志文件之后，MySQL_A上面执行 SQL 语句都会被记录在 binlog 日志里面。MySQL_B 节点通过订阅 MySQL_A 的 binlog 文件，能实时下载到这个日志文件，然后在 MySQL_B 节点上运行这些 SQL 语句，于是就保证了自己的数据和 MySQL_A 节点一致

  ![https://img1.sycdn.imooc.com/62f4d7b60001f9e504960270.jpg](https://img1.sycdn.imooc.com/62f4d7b60001f9e504960270.jpg)



MySQL_A 被称作 Master（主节点），MySQL_B 被称作 Slave（从节点）。需要注意，主从同步模式里面，数据同步是单项的，如果你在 MySQL_A 上写入数据，可以同步到 MySQL_B 上面；如果在 MySQL_B 上面写入数据，是不能同步到 MySQL_A 节点的。于是我们要配置双向主从同步，也就是互为主从节点

  ![https://img1.sycdn.imooc.com/62f4d7dd0001f2a105010319.jpg](https://img1.sycdn.imooc.com/62f4d7dd0001f2a105010319.jpg)



MySQL_A 订阅 MySQL_B 的日志文件，MySQL_B 订阅 MySQL_A 的日志文件，这样无论我们在哪个节点上写入数据，另一个节点就会自动同步到了

**读写分离**

绝大多数Web系统都是读多写少的，比如电商网站，我们都是要货比三家，然后再下单购买。所以搭建MySQL集群的时候，就要划定某些节点是读节点，某些节点是写节点

我规划的是MySQL_1为写节点，MySQL_2和MySQL_3是读节点。多配制一些读节点也没问题，毕竟系统的读任务比较多。但是主从同步有个问题就是Master和Slave身份是固定，如果MySQL_1宕机，MySQL_2和MySQL_3都不能升级成写节点。那怎么办呢，给MySQL_1加上双向同步的MySQL_4节点



  ![https://img3.sycdn.imooc.com/62f4d84000018f0d08060280.jpg](https://img3.sycdn.imooc.com/62f4d84000018f0d08060280.jpg)



ShardingSphere 会轮询的方式给 MySQL_1 和 MySQL_4 发送写操作的SQL语句（INSERT、DELETE、UPDATE等）；如果是查询语句，ShardingSphere 会发给其余四个读节点去执行，这就实现了读写分离。假设 MySQL_1 宕机，ShardingSphere 通过心跳检测能知道，于是所有的写操作就转发给 MySQL_4。反之如果 MySQL_4 宕机，MySQL_1 也会接替工作。在上面示意图中的 6 个 MySQL 节点，无论哪一个宕机都不影响数据库整体的使用，都有各自的冗余节点

**数据分片**

上面的 6 个 MySQL 节点并不是最终的 MySQL 集群方案，因为无论我在 MySQL_1 或者 MySQL_4 写入数据，最终都会同步给其他的节点，也就是说数据不能实现切分。于是我们要引入数据库分片概念，分片内部数据可以做读写分了和主从同步，但是分片之间数据是不能同步的

  <img src="https://img1.sycdn.imooc.com/62f4d8b0000189ba08070390.jpg" style="zoom: 67%;" />

如上图，前 6 个节点组成了第一个 MySQL 分片，后 6个MySQL 节点组成了另一个 MySQL 分片，两个分片之间没有任何的数据同步。这时候 ShardingSphere 把各种 SQL 语句路由给相应的 MySQL 分片，数据就实现了切分。这么看来，我们想要搭建一个最普通的 MySQL 集群，至少需要 12 个 MySQL 节点，但是我们的虚拟机又没有那么大的内存，而且我们是开发环境，没必要配置冗余节点。负载不高，我们也不需要配置读写分离，所以我们只保留数据切分就够了。例如分片 A 中我只保留了一个 MySQL 节点，其余五个节点都不需要。分片 A 和分片 B 切分的是某些数据表，比如司机表和钱包表的数据等等，分片 C 和分片 D 切分其他一些数据表的记录

<img src="https://noah2021.top/pics/Snipaste_2023-09-09_23-51-40.png" style="zoom:50%;" />

你可能要问，既然切分数据，用分片 A 和分片 B 就够了，把所有的数据表都弄到这两个分片上不行吗？这么做也是可以的。但是为了降低负载 A 和 B 分片的负载，我把订单相关的数据表放在了 C 和 D 分片上面。等将来我们正式部署项目的时候，四个分片一共需要 24 个 MySQL 节点，现阶段我们用四个 MySQL 节点就够了

**ShardingSphere**

ShardingSphere 是开源免费的数据库集群中间件，自带了各种切分数据的算法和雪花主键生成算法，甚至我们自己也可以写代码订制新的算法，相对来说比 MyCat 扩展性更强。更多介绍，大家可以去官网自己查阅。这里使用的是 ShardingSphere 5.0 版本，属于最新的版本。5.0 版本的配置文件和 4.0 版本有很大的区别，所以大家百度的时候尽量看清楚 ShardingSphere 的版本号，目前百度上大多数帖子讲 ShardingSphere 配置，都是基于 4.0 版本的

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

7. 【说明】在 /www/evmt/ss/ShardingSphere/conf/server.yaml 里的 rules 部分可以设置远程连接的用户名和密码

```yaml
rules:
 - !AUTHORITY
   users:
     - root@%:abc123456
     - sharding@:abc123456
   provider:
     type: ALL_PRIVILEGES_PERMITTED
 - !TRANSACTION
   defaultType: XA
   providerType: Atomikos
```

在 /www/evmt/ss/ShardingSphere/conf/config-sharding.yaml 里的 dataSources 部分可以设置连接不同逻辑库的 JDBC 连接

```yaml
schemaName: hxds

dataSources:
 rep_s1_mis:
   url: jdbc:mysql://172.18.0.2:3306/hxds_mis?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&nullCatalogMeansCurrent=true&allowPublicKeyRetrieval=true
   username: root
   password: abc123456
   connectionTimeoutMilliseconds: 30000
   idleTimeoutMilliseconds: 60000
   maxLifetimeMilliseconds: 1800000
   maxPoolSize: 50
   minPoolSize: 1
 rep_s1_cst:
   url: jdbc:mysql://172.18.0.2:3306/hxds_cst?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&nullCatalogMeansCurrent=true&allowPublicKeyRetrieval=true
   username: root
   password: abc123456
   connectionTimeoutMilliseconds: 30000
   idleTimeoutMilliseconds: 60000
   maxLifetimeMilliseconds: 1800000
   maxPoolSize: 50
   minPoolSize: 1
 # ...
```

在 /www/evmt/ss/ShardingSphere/conf/config-sharding.yaml 里的 shardingAlgorithms 部分可以创建不同的切分规则：定义了一个切分规则叫做 cst-inline。type 设置成 INLINE 代表使用 ShardingSphere 内置的切分规则。algorithm-expression 代表具体的算法。id 主键值对 2 求模，余数是 0，那么计算出来的连接名字是 rep_s1_cst；如果余数是 1，那么计算出来的连接是 rep_s2_cst。然后 ShardingSphere 就会把 SQL 语句路由给这个连接的逻辑库去执行，数据也就切分好了

```yaml
 shardingAlgorithms:

   cst-inline:
     type: INLINE
     props:
       algorithm-expression: rep_s${(id % 2)+1}_cst
   cst-children-inline: 
     type: INLINE
     props:
       algorithm-expression: rep_s${(customer_id % 2)+1}_cst
   # ...
```

在 /www/evmt/ss/ShardingSphere/conf/config-sharding.yaml 里的 rules 部分可以配置实际 MySQL 节点库与逻辑库的映射关系

```yaml
rules:
- !SHARDING
 tables:
   # ...
   tb_customer:
     actualDataNodes: rep_s${1..2}_cst.tb_customer
     databaseStrategy:
       standard:
         shardingColumn: id
         shardingAlgorithmName: cst-inline
     keyGenerateStrategy:
       column: id
       keyGeneratorName: snowflake
	 
   tb_customer_fine:
     actualDataNodes: rep_s${1..2}_cst.tb_customer_fine
     databaseStrategy:
       standard:
         shardingColumn: customer_id
         shardingAlgorithmName: cst-children-inline
     keyGenerateStrategy:
       column: id
       keyGeneratorName: snowflake
   # ...
```

|      分片      |  逻辑库   |     备注      |              具体用途               |
| :------------: | :-------: | :-----------: | :---------------------------------: |
| 分片1(MySQL_1) | hxds_cst  |  客户逻辑库   |          客户信息、罚款等           |
|                |  hxds_dr  |  司机逻辑库   |  司机信息、实名认证、罚款、钱包等   |
|                | hxds_mis  | MIS系统逻辑库 |         后台管理用的数据表          |
|                | hxds_rule |  规则逻辑库   | 代驾费计算规则,分账规则、取消规则等 |
| 分片2(MySQL_2) | hxds_cst  |  客户逻辑库   |          客户信息、罚款等           |
|                |  hxds_dr  |  司机逻辑库   |  司机信息、实名认证、罚款、钱包等   |
|                | hxds_rule |  规则逻辑库   | 代驾费计算规则,分账规则、取消规则等 |
| 分片3(MySQL_3) | hxds_odr  |  订单逻辑库   |      订单、账单、好评、分账等       |
|                | hxds_vhr  | 代金券逻辑库  |    代金券、领取情况、使用情况等     |
| 分片4(MySQL_4) | hxds_odr  |  订单逻辑库   |      订单、账单、好评、分账等       |
|                | hxds_vhr  | 代金券逻辑库  |    代金券、领取情况、使用情况等     |

平时我们用 MySQL 都喜欢用主键自增长，但是 MySQL 集群中，千万不能让 MySQL 生成主键值，必须有程序或者中间件来生成主键值。你想想看，刚才的切分规则是根据主键值的求模余数来路由SQL语句的，如果 ShardingSphere 接到的 INSERT 语句没有主键值，那么也就无法路由 SQL 语句了。你让 MySQL 节点生成主键值，太晚了，必须由 Java 程序或者 ShardingSphere 先生成主键值，才能路由 SQL 语句。ShardingSphere 内置了雪花算法生成主键值，雪花算法是18位的数字，所以我们创建数据表主键不能是 INT 类型，必须是 BIGINT 类型

此外，因为 ShardingSphere 只是把自己打扮成虚拟的逻辑库，所以不要在 Navicat 上面查看数据库集群数据表的结构。你想看表结构，自己去某个 MySQL 节点上看数据表结构设计。包括你想要删除某个数据表，也不要在虚拟逻辑库上操作，而是需要去操作真实的 MySQL 节点

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

4. 登陆 Minio [控制台](http://host_ip:9001/login)，账号是：root，密码是：abc123456，进入即可查看具体状况

## 安装其他中间件

### 安装RabbitMQ

1. 下载并导入 RabbitMQ 镜像文件

```shell
docker load < RabbitMQ.tar,gz
```

2. 创建并启动 RabbitMQ 容器，其中5672是server端口，15672控制台端口

```shell
docker run -it -d --name mq \
--net mynet --ip 172.18.0.11 \
-p 5672:5672 -p 15672:15672 -m 500m \
-e TZ=Asia/Shanghai --privileged=true \
rabbitmq
```
3. 进入容器内部后，执行`rabbitmq-plugins enable rabbitmq_management`，即可进入[RabbitMQ后台系统](http://host_ip:15672/#/)
4. 执行`rabbitmq-plugins enable rabbitmq_tracing`、`rabbitmqctl trace_on`和`rabbitmqctl trace_on -p myhost`，开启消息追踪
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

3. 登陆 Sentinel [控制台](http://host_ip:8858/#/dashboard)，账密都是：sentinel，进入即可查看具体状况

### 配置腾讯云COS

1. 购买腾讯云COS后，进入对象存储菜单栏的[密钥管理](https://console.cloud.tencent.com/cos/secret)，它会引导进入访问密钥这个页面
2. 点击新建密钥，然后生成 Appid、secretId、secretKey，这个后面会用到
3. 接着退回对象存储菜单栏的[概览](https://console.cloud.tencent.com/cos)，然后点击新建存储桶，填写必要信息
4. 接着进入[文件列表](https://console.cloud.tencent.com/cos/bucket?bucket=hxds-private-1309124223&region=ap-beijing)，新建文件夹 driver/auth
5. 同理创建新的存储桶 hxds-public，设置成公有读私有写

# 代码实现
## 服务总览

子系统总览

| 序号 |    子系统     |   端口号   |      具体用途      |
| :--: | :-----------: | :--------: | :----------------: |
|  1   |    hxds-tm    | 7970和8070 | 分布式事务管理节点 |
|  2   |    hxds-dr    |    8001    |     司机子系统     |
|  3   |   hxds-odr    |    8002    |     订单子系统     |
|  4   |   hxds-snm    |    8003    |   消息通知子系统   |
|  5   |   hxds-mps    |    8004    |     地图子系统     |
|  6   |   hxds-oes    |    8005    |   订单执行子系统   |
|  7   |   hxds-rule   |    8006    |     规则子系统     |
|  8   |   hxds-cst    |    8007    |     客户子系统     |
|  9   |   hxds-vhr    |    8008    |    代金券子系统    |
|  10  |  hxds-nebula  |    8009    |    大数据子系统    |
|  11  | hxds-mis-api  |    8010    |     MIS子系统      |
|  12  | hxds-workflow |    8011    |    工作流子系统    |
|  13  |  bff-driver   |    8101    |   司机bff子系统    |
|  14  | bff-customer  |    8102    |   客户bff子系统    |
|  15  |    gateway    |    8080    |     网关子系统     |

技术栈总览

| 序号 |   技术栈   |                   具体用途                   |
| :--: | :--------: | :------------------------------------------: |
|  1   | SpringBoot |             用于创建微服务子系统             |
|  2   | SpringMVC  |                  Web层框架                   |
|  3   |  MyBatis   |                  持久层框架                  |
|  4   |   Feign    |                   远程调用                   |
|  5   |   TX-LCN   |                  分布式事务                  |
|  6   |  RabbitMQ  |                 系统消息收发                 |
|  7   |  Swagger   |               在线调试Web方法                |
|  8   | QLExpress  |     规则引擎，计算预估费用、取消费用等等     |
|  9   |   Quartz   | 定时器，销毁过期未接单订单、定时自动分账等等 |
|  10  |  Phoenix   |                HBase数据存储                 |
|  11  |   Minio    |                  私有云存储                  |
|  12  |    GEO     |               GPS分区定位计算                |
|  13  |  SaToken   |                认证与授权框架                |
|  14  |   VUE3.0   |                   前端框架                   |
|  15  |   UniAPP   |                  移动端框架                  |

## 基于微服务的司机注册与实名认证

代驾项目理应从司机子系统开始做起。新司机注册的过程中需要实名认证，我们利用OCR插件，快速扫描和提取身份证与驾驶证的信息，然后利用面部识别技术，采集司机的面部信息，为后续每天接单前的身份核验做准备。本章除了开发移动端之外，还要实现Web端的司机管理模块，可以对司机实名认证加以审核

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

【拓展】关于鉴权这部分的链路：小程序获取微信临时凭证 code -> 后端根据 code 去微信接口获取 openId -> 将 openId 存入 tb_driver 并返回其主键 id -> 利用 id 登陆 SaToken 并返回会话值 ( UUID ) 给小程序 -> 小程序接收 token 以进行之后操作 ( eg:查询 ) 的鉴权
code ( 五分钟有效期 ): 用户授权后，微信服务器返回临时凭证，用于换取 access_token
openId: 小程序针对不同的用户在不同的小程序下都有唯一的一个 openId
unionid: 微信下有好多产品，最常见的是公众号和小程序，在申请公众号和小程序的时候需要绑定的主体（也就是公司信息，绑定公司），
如果统一主体（公司）下有好多小程序和公众号，那么我们就可以在微信开放平台上，绑定同一主体，这样我们就可以通过微信提供的unionid，
锁定同一个用户，这样就 打通所有的小程序和公众号的账号系统。
至于为啥返回的是主键 id 而不是 openId，是因为之后的司机信息查询大都由 id 作为筛选条件且主键在 SQL 层面读更快

<img src="https://noah2021.top/pics/wx-openid.jpg"  />

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
2. 写 hxds-driver-wx/pages/login/login.vue 实现司机注册的逻辑
3. 写 hxds-driver-wx/pages/register/register.vue 实现司机注册的逻辑

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

【拓展】

1. uni.navigateTo( object ): 保留当前页面，跳转到应用内的某个页面，使用 uni.navigateBack 可以返回到原页面。 如果一直 navigateTo，当微信小程序使用时，当点击超过10层时，会让微信小程序像卡死一样，点是没有效果的，只有返回上一层，才可以再点一层。这里需要另外的处理方法
2. uni.redirectTo( object ): 关闭当前页面，跳转到应用内的某个页面。 这个随便点击次数，但是当苹果手机或者安卓手机左划时，就会直接退出小程序，而不是返回上一级，这个要注意
3. getCurrentPages(): 用于获取当前页面栈的实例，以数组形式按栈的顺序给出，第一个元素为首页，最后一个元素为当前页面。 注意: getCurrentPages() 仅用于展示页面栈的情况，请勿修改页面栈，以免造成页面状态错误

uni.switchTab( object ): 跳转到 tabBar 页面，并关闭其他所有非 tabBar 页面

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

【拓展】

```ja
prevPage.$vm.updatePhoto(that.type,that.photoPath)// 调用上一个页面的updatePhoto函数，回传图片
uni.navigateBack({ // 返回上一个页面
		delta:1
})
```

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

【拓展】`that.$refs.uToast.show` 获取 `<u-toast ref="uToast" />`  元素并进行修改
vm.$watch: 构建一个对Vue实例中数据仓库中变量 ( data，computed ) 的监控方法

vm.$nextTick: 等同于 nextTick ，将执行函数体延迟到页面 DOM 更新完成后执行

vm.$forceUpdate(): 迫使 Vue 实例重新渲染。注意它仅仅影响实例本身和插入插槽内容的子组件，而不是所有子组件

vm.$refs: 返回一个对象，记录当前 Vue 实例模板中定义了 ref 属性的所有 DOM 元素或者其它 Vue 实例

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

【拓展】

1. 小程序初始化完成后，页面首次加载触发 onLoad，只会触发一次；而 onShow 可以执行多次
2. 当小程序进入到后台 ( 比如打电话去了 )，先执行页面 onHide 方法再执行应用 onHide ( App.vue，下同 ) 方法
3. 当小程序从后台进入到前台，先执行应用 onShow 方法再执行页面 onShow 方法

![生命周期函数](https://s2.51cto.com/images/blog/202112/31150710_61ceac1e28f5e32514.png?x-oss-process=image/watermark,size_16,text_QDUxQ1RP5Y2a5a6i,color_FFFFFF,t_30,g_se,x_10,y_10,shadow_20,type_ZmFuZ3poZW5naGVpdGk=/format,webp/resize,m_fixed,w_1184)

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

【拓展】

1. 在 MySQL 中数字的查询速度要快于字符串的查询速度

2. 关于鉴权这部分的链路：小程序获取微信临时凭证 code -> 后端根据 code 去微信接口获取 openId -> 将 openId 存入 tb_driver 并返回其主键 id -> 利用 id 登陆 SaToken 并返回会话值 ( UUID ) 给小程序 -> 小程序接收 token 以进行之后操作 ( eg:查询 ) 的鉴权

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
### 司机退出小程序

首先后端 bff-driver 要销毁 Redis 缓存的 Token，这样即便移动端提交的 Token 是正确的，SaToken 也不会核验通过。然后是移动端这里，用户点击退出登陆之后，要删除 Storage 上面保存的 Token，然后跳转到登陆页面

1. 写 bff-driver/src/main/controller/DriverController#logout

```java
@PostMapping("/logout")
@Operation(summary = "退出系统")
@SaCheckLogin
public R logout() {
    StpUtil.logout();
    return R.ok();
}
```

2. 写 hxds-driver-wx/pages/mine/mine.vue

```javascript
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
<!--main.js-->
logout: `${baseUrl}/driver/logout`,
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
   通过后端远程 feign 调用实现查询司机基础信息，在 [Swagger-dr](http://localhost:8001/swagger-ui/index.html?configUrl=/doc-api.html/swagger-config#/DriverController/searchDriverBaseInfo) 和 [Swagger-bff](http://localhost:8101/swagger-ui/index.html?configUrl=/doc-api.html/swagger-config) 测试 searchDriverBaseInfo 接口
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
```javascript
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
        uni.vibrateShort({}); // 手机进行一个短时间的震动(15ms)
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
<!--main.js-->
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
   注意 hxds-driver-wx/pages/mine/mine.vue 设置点击事件跳转到 account.vue 页面 ( main.js 里封装了 toPage 跳转语句 )
   注意 hxds-driver-wx/pages/user/account/account.vue 设置点击事件跳转到 filling.vue 页面 ( main.js 里封装了 toPage 跳转语句 )
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
### 乘客微服务的用户注册登陆

略，和司机端类似，不过少了实名认证和面部录入反而更简单。记得要同时写 hxds、hxds-customer-wx 两个项目哦！

## 乘客下单与司机抢单（计算最佳线路，预估里程、定向接单、自动抢单）

乘客下单前，系统自动计算最佳线路的里程和时长，规则引擎预估车费；顾客下单后，使用GEO计算出附近符合接单条件司机（包括定向接单）并推送。司机端采用RR轮询方式接收MQ订单，用语音引擎播报订单，自动或手动抢单。为避免出现抢单超售现象，使用Redis事务机制

### 开通腾讯位置服务，封装腾讯地图服务

进入[腾讯位置服务官网](https://lbs.qq.com/)，注册登录后创建应用、Key。
创建 Key 时要选择 WebServiceAPI--白名单、微信小程序，并填写自己的小程序 APP ID，菜单的 开发文档 里有多端的接入教程。
创建完成后将得到的 Key 填写进 hxds/common/src/main/resources/application-common.yml

详细接入流程：

1. 申请开发者密钥 ( key ) ：[申请密钥](https://lbs.qq.com/dev/console/application/mine)

2. 开通webserviceAPI服务：控制台 -> 应用管理 -> [我的应用](https://lbs.qq.com/dev/console/key/manage) -> 添加key -> 勾选 WebServiceAPI -> 保存

   ( 小程序SDK需要用到 webserviceAPI 的部分服务，所以使用该功能的KEY需要具备相应的权限 )

3. 下载微信小程序 JavaScriptSDK，微信小程序 [JavaScriptSDK v1.1](https://mapapi.qq.com/web/miniprogram/JSSDK/qqmap-wx-jssdk1.1.zip)、[JavaScriptSDK v1.2](https://mapapi.qq.com/web/miniprogram/JSSDK/qqmap-wx-jssdk1.2.zip)

4. 安全域名设置，在 [小程序管理后台](https://mp.weixin.qq.com/wxamp/home/guide) -> 开发 -> 开发管理 -> 开发设置 -> “服务器域名” 中设置 request 合法域名，添加 https://apis.map.qq.com

### 封装预估里程和时间
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
2. 开启实时定位：写 hxds-customer-wx/App.vue#onLaunch
3. 捕获定位事件并携起/终点参数跳转 create_order 页面：写 hxds-customer-wx/pages/workbench/workbench.vue#onShow
4. 点击回位：写 hxds-customer-wx/pages/workbench/workbench.vue#returnLocationHandle
5. 选择起点和终点：写 hxds-customer-wx/pages/workbench/workbench.vue#chooseLocationHandle
6. 对于在真机调试运行失败的问题，解决方案：[分包Error: 分包大小超过限制,main package source](http://chenqichun.com/articleDetails/6151518d9e58dfeb349bed4d)

【拓展】小程序使用 startLocationUpdate 函数可以在使用过程中实时获取定位，挂到后台就不获取定位，相关文档 [传送门](https://developers.weixin.qq.com/miniprogram/dev/api/location/wx.startLocationUpdate.html)。而 startLocationUpdateBackground 和它正相反，这里我们不使用它就不详细介绍了小程序启动后会运行 onLaunch 函数，之前用过的 onShow、onLoad 只针对的是小程序页面启动把坐标转换成地址的操作叫"逆地址解析"，相关文档 [传送门](https://lbs.qq.com/miniProgram/jsSdk/jsSdkGuide/methodReverseGeocoder)

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
            // 跳转到创建订单页面，这个属于下一小节的内容
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
      // 设置地图控件的高度适配屏幕高度
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

1. 接收 URL 传递的参数：写 hxds-customer-wx/pages/create_order/create_order.vue#onLoad 
2. 计算最佳线路，预估里程和时长：写 hxds-customer-wx/pages/create_order/create_order.vue#calculateLine 

【拓展】vue 标签后面的属性：加冒号的说明后面的是一个变量或者表达式；没加冒号的后面就是对应的字符串字面量

`uni.getSystemInfoSync().windowHeight;`：设置地图控件的高度适配屏幕高度

`onLoad: function(options)`：页面加载时触发，一个页面只会调用一次，可以在 options 中获取打开当前页面 ( 上一个页面 ) 路径中的参数

第二步的 API 文档 [传送门](https://lbs.qq.com/miniProgram/jsSdk/jsSdkGuide/methodDirection)

```vue
onLoad: function(options) {
    let that = this;
    //设置地图控件的高度适配屏幕高度
    let windowHeight = uni.getSystemInfoSync().windowHeight;
    that.windowHeight = windowHeight;
    that.contentStyle = `height:${that.windowHeight}px;`;

    that.from = uni.getStorageSync('from');
    that.to = uni.getStorageSync('to');

    qqmapsdk = new QQMapWX({
        key: that.tencent.map.key
    });
    that.map = uni.createMapContext('map');


    <!--if (options.hasOwnProperty('showCar')) {
        that.showCar = options.showCar;
        that.carId = options.carId;
        that.carPlate = options.carPlate;
    }-->
},

calculateLine: function(ref) {
    qqmapsdk.direction({
        mode: 'driving',
        from: {
            latitude: ref.from.latitude,
            longitude: ref.from.longitude
        },
        to: {
            latitude: ref.to.latitude,
            longitude: ref.to.longitude
        },
        success: function(resp) {
            if (resp.status != 0) {
                uni.showToast({
                    icon: 'error',
                    title: resp.message
                });
                return;
            }
            let route = resp.result.routes[0];
            let distance = route.distance;
            let duration = route.duration;
            let polyline = route.polyline;
            ref.distance = Math.ceil((distance / 1000) * 10) / 10;
            ref.duration = duration;
            let points = ref.formatPolyline(polyline);

            ref.polyline = [{
                points: points,
                width: 6,
                color: '#05B473',
                arrowLine: true
            }];
            ref.markers = [{
                id: 1,
                latitude: ref.from.latitude,
                longitude: ref.from.longitude,
                width: 25,
                height: 35,
                anchor: {
                    x: 0.5,
                    y: 0.5
                },
                iconPath: 'https://mapapi.qq.com/web/lbs/javascriptGL/demo/img/start.png'
            },
            {
                id: 2,
                latitude: ref.to.latitude,
                longitude: ref.to.longitude,
                width: 25,
                height: 35,
                anchor: {
                    x: 0.5,
                    y: 0.5
                },
                iconPath: 'https://mapapi.qq.com/web/lbs/javascriptGL/demo/img/end.png'
            }];
        }
    });
},

onShow: function() {
    let that = this;
    that.calculateLine(that);
}
```

### 乘客端选择代驾车型和车牌

1. 写 hxds-cst/src/main/resource/mapper/CustomerCarDao.xml#insert 及其对应接口
   写 service/CustomerCarService#insertCustomerCar 及其实现类
   写 controller/form/InsertCustomerCarForm
   写 controller/CustomerCarController#insertCustomerCar

2. 写 hxds-cst/src/main/resource/mapper/CustomerCarDao.xml#searchCustomerCarList 及其对应接口
   写 service/CustomerCarService#searchCustomerCarList 及其实现类
   写 controller/form/SearchCustomerCarListForm
   写 controller/CustomerCarController#searchCustomerCarList

3. 写 hxds-cst/src/main/resource/mapper/CustomerCarDao.xml#deleteCustomerCarById 及其对应接口
   写 service/CustomerCarService#deleteCustomerCarById 及其实现类
   写 controller/form/DeleteCustomerCarForm
   写 controller/CustomerCarController#deleteCustomerCarById

```java
<insert id="insert" parameterType="com.example.hxds.cst.db.pojo.CustomerCarEntity">
    INSERT INTO tb_customer_car
    SET customer_id = #{customerId},
        car_plate = #{carPlate},
        car_type = #{carType}
</insert>
<select id="searchCustomerCarList" parameterType="long" resultType="HashMap">
    SELECT CAST(id AS CHAR) AS id,
           car_plate        AS carPlate,
           car_type         AS carType
    FROM tb_customer_car
    WHERE customer_id = #{customerId}
</select>
<delete id="deleteCustomerCarById" parameterType="long">
    DELETE
    FROM tb_customer_car
    WHERE id = #{id}
</delete>
  
int insert(CustomerCarEntity entity);

ArrayList<HashMap> searchCustomerCarList(long customerId);

int deleteCustomerCarById(long id);


void insertCustomerCar(CustomerCarEntity entity);

ArrayList<HashMap> searchCustomerCarList(long customerId);

int deleteCustomerCarById(long id);

@Override
@Transactional
@LcnTransaction
public void insertCustomerCar(CustomerCarEntity entity) {
    customerCarDao.insert(entity);
}

@Override
public ArrayList<HashMap> searchCustomerCarList(long customerId) {
    ArrayList list = customerCarDao.searchCustomerCarList(customerId);
    return list;
}

@Override
@Transactional
@LcnTransaction
public int deleteCustomerCarById(long id) {
    int rows = customerCarDao.deleteCustomerCarById(id);
    return rows;
}

@Data
@Schema(description = "添加客户车辆的表单")
public class InsertCustomerCarForm {
    @NotNull(message = "customerId不能为空")
    @Min(value = 1, message = "customerId不能小于1")
    @Schema(description = "客户ID")
    private Long customerId;

    @NotBlank(message = "carPlate不能为空")
    @Pattern(regexp = "^([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领A-Z]{1}[A-Z]{1}(([0-9]{5}[DF])|([DF]([A-HJ-NP-Z0-9])[0-9]{4})))|([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领A-Z]{1}[A-Z]{1}[A-HJ-NP-Z0-9]{4}[A-HJ-NP-Z0-9挂学警港澳]{1})$",
            message = "carPlate内容不正确")
    @Schema(description = "车牌号")
    private String carPlate;

    @NotBlank(message = "carType不能为空")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5A-Za-z0-9\\-\\_\\s]{2,20}$", message = "carType内容不正确")
    @Schema(description = "车型")
    private String carType;
}

@Data
@Schema(description = "查询客户车辆的表单")
public class SearchCustomerCarListForm {
    @NotNull(message = "customerId不能为空")
    @Min(value = 1, message = "customerId不能小于1")
    @Schema(description = "客户ID")
    private Long customerId;
}

@Data
@Schema(description = "删除客户车辆的表单")
public class DeleteCustomerCarByIdForm {
    @NotNull(message = "id不能为空")
    @Min(value = 1, message = "id不能小于1")
    @Schema(description = "车辆ID")
    private Long id;
}

@PostMapping("/insertCustomerCar")
@Operation(summary = "添加客户车辆")
public R insertCustomerCar(@RequestBody @Valid InsertCustomerCarForm form) {
    CustomerCarEntity entity = BeanUtil.toBean(form, CustomerCarEntity.class);
    customerCarService.insertCustomerCar(entity);
    return R.ok();
}

@PostMapping("/searchCustomerCarList")
@Operation(summary = "查询客户车辆列表")
public R searchCustomerCarList(@RequestBody @Valid SearchCustomerCarListForm form) {
    ArrayList<HashMap> list = customerCarService.searchCustomerCarList(form.getCustomerId());
    return R.ok().put("result", list);
}

@PostMapping("/deleteCustomerCarById")
@Operation(summary = "删除客户车辆")
public R deleteCustomerCarById(@RequestBody @Valid DeleteCustomerCarByIdForm form) {
    int rows = customerCarService.deleteCustomerCarById(form.getId());
    return R.ok().put("rows", rows);
}
```

4. 写 bff-customer/src/main/controller/form/InsertCustomerCarForm
   写 feign/CstServiceApi#insertCustomerCar
   写 service/CustomerCarService#insertCustomerCar 及其实现类
   写 controller/CustomerCarController#insertCustomerCar
5. 写 bff-customer/src/main/controller/form/SearchCustomerCarListForm
   写 feign/CstServiceApi#searchCustomerCarList
   写 service/CustomerCarService#searchCustomerCarList 及其实现类
   写 controller/CustomerCarController#searchCustomerCarList
6. 写 bff-customer/src/main/controller/form/deleteCustomerCarById
   写 feign/CstServiceApi#DeleteCustomerCarForm
   写 service/CustomerCarService#deleteCustomerCarById 及其实现类
   写 controller/CustomerCarController#deleteCustomerCarById

```java
@Data
@Schema(description = "添加客户车辆的表单")
public class InsertCustomerCarForm {
    @Schema(description = "客户ID")
    private Long customerId;

    @NotBlank(message = "carPlate不能为空")
    @Pattern(regexp = "^([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领A-Z]{1}[A-Z]{1}(([0-9]{5}[DF])|([DF]([A-HJ-NP-Z0-9])[0-9]{4})))|([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领A-Z]{1}[A-Z]{1}[A-HJ-NP-Z0-9]{4}[A-HJ-NP-Z0-9挂学警港澳]{1})$",
            message = "carPlate内容不正确")
    @Schema(description = "车牌号")
    private String carPlate;

    @NotBlank(message = "carType不能为空")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5A-Za-z0-9\\-\\_\\s]{2,20}$", message = "carType内容不正确")
    @Schema(description = "车型")
    private String carType;
}

@Data
@Schema(description = "查询客户车辆的表单")
public class SearchCustomerCarListForm {
    @Schema(description = "客户ID")
    private Long customerId;
}

@Data
@Schema(description = "删除客户车辆的表单")
public class DeleteCustomerCarByIdForm {
    @NotNull(message = "id不能为空")
    @Min(value = 1, message = "id不能小于1")
    @Schema(description = "车辆ID")
    private Long id;
}

@PostMapping("/customer/car/insertCustomerCar")
R insertCustomerCar(InsertCustomerCarForm form);

@PostMapping("/customer/car/searchCustomerCarList")
R searchCustomerCarList(SearchCustomerCarListForm form);

@PostMapping("/customer/car/deleteCustomerCarById")
R deleteCustomerCarById(DeleteCustomerCarByIdForm form);

void insertCustomerCar(InsertCustomerCarForm form);

ArrayList<HashMap> searchCustomerCarList(SearchCustomerCarListForm form);

int deleteCustomerCarById(DeleteCustomerCarByIdForm form);

@Override
@Transactional
@LcnTransaction
public void insertCustomerCar(InsertCustomerCarForm form) {
    cstServiceApi.insertCustomerCar(form);
}

@Override
public ArrayList<HashMap> searchCustomerCarList(SearchCustomerCarListForm form) {
    R r = cstServiceApi.searchCustomerCarList(form);
    ArrayList<HashMap> list = (ArrayList<HashMap>) r.get("result");
    return list;
}

@Override
@Transactional
@LcnTransaction
public int deleteCustomerCarById(DeleteCustomerCarByIdForm form) {
    R r = cstServiceApi.deleteCustomerCarById(form);
    int rows = MapUtil.getInt(r, "rows");
    return rows;
}

@PostMapping("/insertCustomerCar")
@Operation(summary = "添加客户车辆")
public R insertCustomerCar(@RequestBody @Valid InsertCustomerCarForm form) {
    long customerId = StpUtil.getLoginIdAsLong();
    form.setCustomerId(customerId);
    customerCarService.insertCustomerCar(form);
    return R.ok();
}

@PostMapping("/searchCustomerCarList")
@Operation(summary = "查询客户车辆列表")
public R searchCustomerCarList(@RequestBody @Valid SearchCustomerCarListForm form) {
    long customerId = StpUtil.getLoginIdAsLong();
    form.setCustomerId(customerId);
    ArrayList<HashMap> list = customerCarService.searchCustomerCarList(form);
    return R.ok().put("result", list);
}

@PostMapping("/deleteCustomerCarById")
@Operation(summary = "删除客户车辆")
public R deleteCustomerCarById(@RequestBody @Valid DeleteCustomerCarByIdForm form) {
    int rows = customerCarService.deleteCustomerCarById(form);
    return R.ok().put("rows", rows);
}
```

7. 在 cst、bff-cst 里面加入注册和登录的模块，这个之前讲过就略过，否则下一步无法进行测试
8. 启动 tm、cst、bff-cst 三个子系统并进行测试

​	【拓展】ShardingException: Can not update sharding key, logic table: [tb_customer_car]
​		实现 navicat 直接更新 customer_id 时发现 update 的逻辑是分片的列必须在 where 条件里并且必须同更新的值相同。

9. 添加车型：写 hxds-customer-wx/pages/add_car/add_car.vue#carTypeHandle

10. 添加车牌：写 hxds-customer-wx/pages/add_car/add_car.vue#carPlateHandle

11. 保存信息：写 hxds-customer-wx/pages/add_car/add_car.vue#saveHandle

```vue
carTypeHandle: function () {
  let that = this;
  uni.showModal({
    title: '输入车型',
    editable: true,
    placeholderText: '例如丰田卡罗拉',
    success: function (resp) {
      if (resp.confirm) {
        let carType = resp.content;
        if (that.checkValidCarType(carType, '车型')) {
          that.carType = carType;
        }
      }
    }
  });
},
carPlateHandle: function () {
  let that = this;
  uni.showModal({
    title: '输入车牌',
    editable: true,
    placeholderText: '你的车牌号',
    success: function (resp) {
      if (resp.confirm) {
        let carPlate = resp.content;
        if (that.checkValidCarPlate(carPlate, '车牌')) {
          that.carPlate = carPlate;
        }
      }
    }
  });
},
saveHandle: function () {
  let that = this;
  if (that.checkValidCarType(that.carType, '车型') && that.checkValidCarPlate(that.carPlate, '车牌')) {
    let data = {
      carType: that.carType,
      carPlate: that.carPlate
    };
    that.ajax(that.url.insertCustomerCar, 'POST', data, function (resp) {
      that.$refs.uToast.show({
        title: '添加成功',
        type: 'success',
        callback: function () {
          uni.redirectTo({
            url: '../car_list/car_list'
          });
        }
      });
    });
  }
},
```

12. 加载车辆列表：写 hxds-customer-wx/pages/car_list/car_list.vue#loadDataList

13. 添加车辆：写 hxds-customer-wx/pages/car_list/car_list.vue#addHandle

14. 删除车辆：写 hxds-customer-wx/pages/car_list/car_list.vue#removeHandle

【拓展】@longpress: 长按事件，对应删除车辆

```vue
methods: {
  loadDataList: function (ref) {
    ref.list = [];
    ref.ajax(ref.url.searchCustomerCarList, 'POST', {}, function (resp) {
      let result = resp.data.result;
      for (let one of result) {
        ref.list.push({
          id: one.id,
          carType: one.carType,
          carPlate: one.carPlate
        });
      }
    });
  },
  addHandle: function () {
    uni.redirectTo({
      url: '../add_car/add_car'
    });
  },
  removeHandle: function (id) {
    let that = this;
    uni.vibrateShort({});
    uni.showModal({
      title: '提示消息',
      content: '是否删除这条车辆信息？',
      success: function (resp) {
        if (resp.confirm) {
          //删除记录
          let data = {
            id: id
          };
          that.ajax(that.url.deleteCustomerCarById, 'POST', data, function (resp) {
            if (resp.data.rows == 0) {
              that.$refs.uToast.show({
                title: '删除失败',
                type: 'error'
              });
            } else {
              that.loadDataList(that);
            }
          });
        }
      }
    });
  }
},
onLoad: function () {
  let that = this;
  that.loadDataList(that);
}
```

15. 选择车辆：

​		写 hxds-customer-wx/pages/create_order/create_order.vue#chooseCarHandle

​		写 hxds-customer-wx/pages/car_list/car_list.vue#choseOneHandle

​		补充 hxds-customer-wx/pages/create_order/create_order.vue#onLoad

```javascript
chooseCarHandle: function(){
  uni.navigateTo({
    url: ../car_list/car_list;
  })
}
choseOneHandle: function (id, carPlate) {
  uni.navigateTo({
    url: `../create_order/create_order?showCar=true&carId=${id}&carPlate=${carPlate}`
  });
}
if(options.hasOwnProperty('showCar')){
  that.showCar = options.showCar;
  that.carId = options.carId;
  that.carPlate = options.carPlate;
}
```
16. 启动 tm、cst、bff-cst、gateway 四个子系统并进行测试，其中 gateway 配置文件里面要加入 bff-cst 的部分
### 订单微服务中创建代驾订单保存到MySQL集群
之前已经把创建订单页面的内容开发的差不多了，接下来乘客提交创建订单的请求之后，需要后端Java程序重新计算最佳线路、
里程和时间。这是为了避免有人用PostMan模拟客户端，随便输入代驾的起点、终点、里程和时长，所以后端程序必须重新计算
这些数据，并且写入到订单记录中。
1. 写 bff-customer/src/main/controller/form/EstimateOrderMileageAndMinuteForm
   写 feign/MpsServiceApi#estimateOrderMileageAndMinute
   实现通过 feign 调用 hxds-mps 内的接口来重新预估里程和时间的
```java
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

@FeignClient(value = "hxds-mps")
public interface MpsServiceApi {
   @PostMapping("/map/estimateOrderMileageAndMinute")
   R estimateOrderMileageAndMinute(EstimateOrderMileageAndMinuteForm form);
}
```
规则引擎可以做到把算法剥离出程序，你可以保存到TXT文件或者数据库里面，用的时候再加载回程序。虽然加载回来的算法是字符串，但是规则引擎有办法执行这些
字符串。例如遇到雨雪天气，代驾费用就得上调一些。如果是业务淡季，代驾费用可以下调一点。既然代驾费的算法经常要变动，我们肯定不能把算法写死到程序里面。
我们要把算法从程序中抽离，保存到MySQL里面。将来我们要改动计费算法，直接添加一个新纪录就行了，原有记录不需要删改，华夏代驾的程序默认使用最新的计费方式。
本课程选用的规则引擎是阿里的QLExpress，作为一个嵌入式规则引擎在业务系统中使用，让业务规则定义简便而不失灵活。QLExpress支持标准的JAVA语法，
还可以支持自定义操作符号、操作符号重载、 函数定义、宏定义、数据延迟加载等。至于其他的规则引擎，由于都不支持复杂语法，所以我们只能用QLExpress了。

2. 执行SQL命令
在 hxds_rule 逻辑库的 tb_charge_rule 表中，保存的是计费规则，也就是程序中剥离的算法。
```sql
INSERT INTO `hxds`.`tb_charge_rule` (`code`, `name`, `rule`, `status`, `create_time`) VALUES ('Y2022N1', '2022年代驾费计算规则01版', '86CEB26FE96AA9990FC812A0C35BE788FD4ADC2FEAB3C5B00FBE0CC5DF71E4B3E71BA26C0A73A8DBE7C0842DCA8D3ADA672731FA80020C9214128D7F26E0D89C58CF8EC5493B93CFB336A483E586FC7B198829D7A96E93E66B19CF083228A3529E6EBF29EE37554AA59A23FDBD364A45DC8EFE95BE8549E910605F29438C88AAEB94EF8AB98E7AD69736FB4D546C839840CE534997670BECAEC8CFE1D9E118C1344ACB7905C5C3FD6ED724DDD28DF454F4CF849C5B76B9060F259E4B51F928629C19E066F8D44234EA8AA5B309E171A50F907B5F796241357B3F7AFF26F81E4D5369BAE3CF1AB965001576804CFD593725FAFBCC759172C60857B78E7430DC4C1DBF95096B40A4E663FCDE059742AFA1172783935CF4F4994C3813C7545C1D68D97191F0A7A6CB42708405949D457D96368FA1D76B200F56B75CC04D75B23F617223F34A8F14CAE2AC9296C3A4596E3D2B13BEEA18FCB259045D6FB25D147D1AE81E955AAF93AEB57BF554202F5F5F0829469BFE2171A365824DFCF6AB43F1311A9EA7A4AEDFE4ABAE0BAB682846829D5369BAE3CF1AB965001576804CFD593724CCD796D71CF303AFB1CF7891B7C0512EB389D6E0761D973F7B80FA03F3333C3B56B7C75726EBFC9489FB9456128DADEC5D8CBE23D0FA38F0D13E3CB6C41265B02012BD032A9B3EA280FA71E2FBF4CE3EE93C69ED000B2870DB3AE6695968C4D196E1ECFE9E08FE3DDFC3D7BB9C98F7DE3B4678827FF29303572B3AC774932D0ED694E04810C8B29F55A482EECCD1E6127CB68A5199177115C9D2EFD7B11FEFEDE5A2BB90ED535E8139115C9A4DDFEAF76979DD77C3679B1862F0968D8E6AE8611E8A2099EEC3E6179D1FF4434FBEBE9DC88551439F41334C4A7B96D0E008C7FFDCF1FCD947D793A15E505ADD158F864E7246B8F8434FD61A01F5742C7CE72A30F632BE42AEDBFA743B130B899819E62975F0E4CCA2A566A7C3DD5C1398239CBE265DD8072B2E45FBA6E7083BB6440876F8830AFBF3F51A0E0AE706F3FF2B1FB02012BD032A9B3EA280FA71E2FBF4CEF8981F50FFEFFAA0A5BB3BF8B5BCDA13E7C492F4EF95C2D6B33D55DBA12B6D00058B9C1091E6A3F48E0BCF5C8366BC66807127421C63EC2DE39622136568A5D254234D193664A44C1D9645EF16BABB6913C534CA976EC98C4593FBB9B0EFC2C32606E403F67C3C7B45DC8077957936025B79781FDB00226B6AB5923DEE97AAC774D6528176B2CD43240DB685F2377289ECB56E6F080E15634E94AF7B47844D1D3E92D063E4FE2281C3320BA844A35FDE2B7214A872DE11C4F8861251CEAF0BCD12F23E940EB3FE6E47304CE596FC44E2DD287F168765AE19755A273B5940EADC0E4FDE121F74DFB77481A79983DB8CFD8A798C9598F4980FDFB59347F3D57BBA87EE483CAAFCB31C550BC15CA14CAA7ACA13F18C43945A27BE610D3F7E1FDCB74D1AD16087A6EA14ED259CA3A00EBB97EAD3B18B97F4C2A295067F1F5D5C4F688C60C50CAD1A58EBB4249C28426526AEEAE3AD32B03606FAEA382967C7A959666FAEF0E32F91D98D754082D098DD8146150D9AA6EB36DA71A1F4638C8AC934B88F450A310ABE86FE61A2A994A20DF750703AFA803DD1BE11230F64A5621FCD9BD7006CFB4207AE0551EE5C4948387B4DE7CC8FE40F4DC7DFBFED0B5AE9E7EE1A4C7603C91989CD32B142429EFE65A11944BD0FC5B4BAEF8EC0B3C856EC5D8774E0422C13E3F7D926019042EBD4435655C0903CAB121CA90149F14C96C55A7F3AFC176DDBA32021A4915839D845E73FB0A50B1DA6451764AF81334C8D2E12E258BEB04A4F24A70AF3C2613E4BD9F315D9D2D5D0350764F0E3AF4FA734DF232FB6450262CA461F31EE79ABF87A99F06091C6E2EB6D835BA43FEE16ADB659F1B063B5B174D945C160FAC0A49CB5B38E40BB4D1AD16087A6EA14ED259CA3A00EBB97F20F18593C6C18DB1BD6747B892F38A368C730C1D1063F0489D679219B35F0A3C6E2EB6D835BA43FEE16ADB659F1B0631B6D7909A873D1DA00E0EF547DB97561FBDC59CA1B004067701CA47935A21059383B814013AA31416AF610D12562985786246D362A69992520075BB0415B97A12461C4798BB2170334F0F74E92C59224FF61F6BFB53541ECC9C116A74D22216B0E0DBD635282B0D32557FC1EF08D16485E53F76ECC31BF79DBF65FC677B114E1490F203D4C8D42EBABEBFDA586A8264A282DFA277736DD6839CF7F7543A576399C7EFEC4AD639EA2A2930809AA5C7658393D805D4A5A757D2036BDABE3918DCEDB588F946554C4734191CCC10F01A47BDB9EE7EFA37E2301A23F3199D62979887A2F954D94C5B9AF0F52E722BE45DAB92DC208996B50D2DABD56A9636B35195D225B3F4FDA5263FF354AD1F18D49AAE64698602E1EBAA0DA1FE3D414A313F5AEA2F19B01A9C0A930F836E7A5A5CF121C4750C03AD66D0DF03260FB3A8FEE6E4EDAC80F807FE25DB9F18D58A99261C7F7E6361B18ED5490A5B4F9FF416453F79AE926039917B9959E088394DBDD89DBB212DFB027E8196D0FFC3264C351827857FFDCF1FCD947D793A15E505ADD158F8662422FE0079DCCD95B5433FF370A80EC2439BD1B655C916C02AB65D84C1D86C7B2C004D49751A4C268C26A3BF7D97EEC495FF97F0FFAC370A0CE29236545B632A6AEA66723ABA728EDE935DCAE2BDA2F6E3ECBCE4C8C45BF4947991F52198F7FDC0B5CFDD52C22D5D289CFE3BD0DAEEB9003439060787CD39C60A446A3DB07A70340A3B27EE8C9C783FE108B75C5C51F99969F70B1E36B93CCF3AA0D9BF08A99ECB2D4291A2C1564A4C8BC06A33AE6E1D8F8395C5B0EA66178709B71D27A522192936E8836641EB2E09D80834A87C2D416B1B4B14B0E2F5A09CB086CDAD75B7AFD01BE515888FA87E76E4C68DFE569DEFA033356CFB9ACBE019F6374FCFD9C514C2D7720150D7E559E739996EDE83AEABFDF66C25A4AFEA44A8BBF00957A84E99B67D34C2E2A4F44177D1287FE45F0823097923940B6AB03E6750EE011C6DA71FD01BE515888FA87E76E4C68DFE569DE9C309A8D1D355EF7161C9AB56586EBAAA1DE1C20A230995278BDF2C1A2E0EE2ACB7CBBA60AB4A4C547E747BB52532FB5BFDF66C25A4AFEA44A8BBF00957A84E9B60A24F837FD3991AE5976985DBC5B76FC47B08487CF00E9D7FF78CCB6615101D5E83B7DA1B788B2EEDEF10B572F7F38507FF5A368A9514B6CE19D096A52C88250D23DFA5EE0A3DDD6DF0C6E5B3F139ADB63E0C5356359E172FB72B8BDDF55AA45E1B2E8D58666942DD01351D36E650A3BAD6BFCFBD2A98BC505382BF26E77BB4AD10CB92D847BA92B2526141442D72FF24D60563C28AA123AF43E1DC231DB7762FC548CE5A38583140EBE233E9A4E7FDB052AB155D6F23DA8C6A59812BAB07C507FF5A368A9514B6CE19D096A52C882A61923306ED37CD6C5E344C93C0001542C8F723596B59AD08DD42C78E373BB429B01D054CE24CB46164C7B7B64A81F87F624295D12D95AAA8A7D19BF07DA6285E15CD33640A232DC98247E61A4AC6A6D66C788A571A63D63400FAEF657D4A20C5220EB10D5C5E2D314510708EFA8F9AB0CD0FBC76C5C9DFF9A8DA0A95CA62E05965D2A394330408565E7785217F2382AFD01BE515888FA87E76E4C68DFE569DE83B07CEC35EAD940327388BB3F785186', 1, '2022-05-11 13:42:49');
```

3. 启动tm节点，再启动rule节点，无报错即是成功
```bash
java -jar hxds-rule-0.0.1-SNAPSHOT.jar --spring.config.location=application-common.yml,bootstrap.yml
```
4. 写 bff-customer/src/main/controller/form/EstimateOrderChargeForm
   写 feign/RuleServiceApi#estimateOrderCharge
   实现通过 feign 调用 hxds-rule 内规则引擎的接口来重新预估代驾费用
```java
@Data
@Schema(description = "预估代驾费用的表单")
public class EstimateOrderChargeForm {
    @NotBlank(message = "mileage不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d+$|^0\\.\\d*[1-9]\\d*$|^[1-9]\\d*$", message = "mileage内容不正确")
    @Schema(description = "代驾公里数")
    private String mileage;

    @NotBlank(message = "time不能为空")
    @Pattern(regexp = "^(20|21|22|23|[0-1]\\d):[0-5]\\d:[0-5]\\d$", message = "time内容不正确")
    @Schema(description = "代驾开始时间")
    private String time;

}

@FeignClient(value = "hxds-rule")
public interface RuleServiceApi {
   @PostMapping("/charge/estimateOrderCharge")
   R estimateOrderCharge(EstimateOrderChargeForm form);
}
```
5. 写 bff-customer/src/main/controller/form/CreateNewOrderForm
   写 service/OrderService#createNewOrder 及其实现类
   实现对里程、时间与费用的重新预估
```java
@Data
@Schema(description = "预估订单数据的表单")
public class CreateNewOrderForm {
    @Schema(description = "客户ID")
    private Long customerId;

    @NotBlank(message = "startPlace不能为空")
    @Pattern(regexp = "[\\(\\)0-9A-Z#\\-_\\u4e00-\\u9fa5]{2,50}", message = "startPlace内容不正确")
    @Schema(description = "订单起点")
    private String startPlace;

    @NotBlank(message = "startPlaceLatitude不能为空")
    @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "startPlaceLatitude内容不正确")
    @Schema(description = "订单起点的纬度")
    private String startPlaceLatitude;

    @NotBlank(message = "startPlaceLongitude不能为空")
    @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "startPlaceLongitude内容不正确")
    @Schema(description = "订单起点的经度")
    private String startPlaceLongitude;

    @NotBlank(message = "endPlace不能为空")
    @Pattern(regexp = "[\\(\\)0-9A-Z#\\-_\\u4e00-\\u9fa5]{2,50}", message = "endPlace内容不正确")
    @Schema(description = "订单终点")
    private String endPlace;

    @NotBlank(message = "endPlaceLatitude不能为空")
    @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "endPlaceLatitude内容不正确")
    @Schema(description = "订单终点的纬度")
    private String endPlaceLatitude;

    @NotBlank(message = "endPlaceLongitude不能为空")
    @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "endPlaceLongitude内容不正确")
    @Schema(description = "订单起点的经度")
    private String endPlaceLongitude;

    @NotBlank(message = "favourFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "favourFee内容不正确")
    @Schema(description = "顾客好处费")
    private String favourFee;

    @NotBlank(message = "carPlate不能为空")
    @Pattern(regexp = "^([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领A-Z]{1}[A-Z]{1}(([0-9]{5}[DF])|([DF]([A-HJ-NP-Z0-9])[0-9]{4})))|([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领A-Z]{1}[A-Z]{1}[A-HJ-NP-Z0-9]{4}[A-HJ-NP-Z0-9挂学警港澳]{1})$",
            message = "carPlate内容不正确")
    @Schema(description = "车牌号")
    private String carPlate;

    @NotBlank(message = "carType不能为空")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5A-Za-z0-9\\-\\_\\s]{2,20}$", message = "carType内容不正确")
    @Schema(description = "车型")
    private String carType;

}

public interface OrderService {
   HashMap createNewOrder(CreateNewOrderForm form);
}

   @Override
   @Transactional
   @LcnTransaction
   public Integer createNewOrder(CreateNewOrderForm form) {
      Long customerId = form.getCustomerId();
      String startPlace = form.getStartPlace();
      String startPlaceLatitude = form.getStartPlaceLatitude();
      String startPlaceLongitude = form.getStartPlaceLongitude();
      String endPlace = form.getEndPlace();
      String endPlaceLatitude = form.getEndPlaceLatitude();
      String endPlaceLongitude = form.getEndPlaceLongitude();
      // 虽然下单前前端计算好了路线和时长，但是当用户在前端停留过长时，该状态就会发生变化，此时需要重新预估
      // 重新预估里程和时间
      EstimateOrderMileageAndMinuteForm reEstimate2MForm = new EstimateOrderMileageAndMinuteForm();
      reEstimate2MForm.setMode(DRIVING_MODE);
      BeanUtils.copyProperties(form, reEstimate2MForm);
      R r = mpsServiceApi.estimateOrderMileageAndMinute(reEstimate2MForm);
      HashMap map = (HashMap) r.get(RESULT_MAP_KEY);
      String mileage = MapUtil.getStr(map, MILEAGE_MAP_KEY);
      Integer minute = MapUtil.getInt(map, MINUTE_MAP_KEY);
      // 重新预估代驾费用
      EstimateOrderChargeForm reEstimateChargeForm = new EstimateOrderChargeForm();
      reEstimateChargeForm.setMileage(mileage);
      reEstimateChargeForm.setTime(new DateTime().toTimeStr());
      r = ruleServiceApi.estimateOrderCharge(reEstimateChargeForm);
      map = (HashMap) r.get(RESULT_MAP_KEY);
      String expectsFee = MapUtil.getStr(map, AMOUNT_MAP_KEY);
      String chargeRuleId = MapUtil.getStr(map, RULE_ID_MAP_KEY);
      short baseMileage = MapUtil.getShort(map, BASE_MILEAGE_MAP_KEY);
      String baseMileagePrice = MapUtil.getStr(map, BASE_MILEAGE_PRICE_MAP_KEY);
      String exceedMileagePrice = MapUtil.getStr(map, EXCEED_MILEAGE_PRICE_MAP_KEY);
      short baseMinute = MapUtil.getShort(map, BASE_MINUTE_PRICE_MAP_KEY);
      String exceedMinutePrice = MapUtil.getStr(map, EXCEED_MINUTE_PRICE_MAP_KEY);
      short baseReturnMileage = MapUtil.getShort(map, BASE_RETURN_MILEAGE_MAP_KEY);
      String exceedReturnPrice = MapUtil.getStr(map, EXCEED_RETURN_MILEAGE_MAP_KEY);
      //TODO: 未完
      return 0;
   }
```
6. 写 hxds-odr/src/main/resource/mapper/OrderDao.xml#insert、searchOrderIdByUUID 及其对应接口
   写 hxds-odr/src/main/resource/mapper/OrderBillDao.xml#insert 及其对应接口
   写 service/OrderService#insertOrder 及其实现类
   写 controller/form/InsertOrderForm
   写 controller/OrderController#insertOrder
   实现用户将订单必要信息(里程、时长、价格等)数据持久化到 tb_order、tb_order_bill
```java
int insert(OrderEntity entity);

String searchOrderIdByUUID(String uuid);

<insert id="insert" parameterType="com.example.hxds.odr.db.pojo.OrderEntity">
        INSERT INTO tb_order
        SET uuid = #{uuid},
        customer_id = #{customerId},
        start_place = #{startPlace},
        start_place_location = #{startPlaceLocation},
        end_place = #{endPlace},
        end_place_location = #{endPlaceLocation},
        expects_mileage = #{expectsMileage},
        expects_fee = #{expectsFee},
        favour_fee = #{favourFee},
        charge_rule_id=#{chargeRuleId},
        car_plate=#{carPlate},
        car_type=#{carType},
        date = #{date}
</insert>

<select id="searchOrderIdByUUID" parameterType="String" resultType="String">
        SELECT CAST(id AS CHAR) AS id
        FROM tb_order
        WHERE uuid = #{uuid}
</select>

int insert(OrderBillEntity entity);

<insert id="insert" parameterType="com.example.hxds.odr.db.pojo.OrderBillEntity">
        INSERT INTO tb_order_bill
        SET order_id = #{orderId},
        base_mileage = #{baseMileage},
        base_mileage_price = #{baseMileagePrice},
        exceed_mileage_price = #{exceedMileagePrice},
        base_minute = #{baseMinute},
        exceed_minute_price = #{exceedMinutePrice},
        base_return_mileage = #{baseReturnMileage},
        exceed_return_price = #{exceedReturnPrice}
</insert>

@Data
@Schema(description = "顾客下单的表单")
public class InsertOrderForm {
   @NotBlank(message = "uuid不能为空")
   @Pattern(regexp = "^[0-9A-Za-z]{32}$", message = "uuid内容不正确")
   @Schema(description = "uuid")
   private String uuid;

   @NotNull(message = "customerId不能为空")
   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;

   @NotBlank(message = "startPlace不能为空")
   @Pattern(regexp = "[\\(\\)0-9A-Z#\\-_\\u4e00-\\u9fa5]{2,50}", message = "startPlace内容不正确")
   @Schema(description = "订单起点")
   private String startPlace;

   @NotBlank(message = "startPlaceLatitude不能为空")
   @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "startPlaceLatitude内容不正确")
   @Schema(description = "订单起点的纬度")
   private String startPlaceLatitude;

   @NotBlank(message = "startPlaceLongitude不能为空")
   @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "startPlaceLongitude内容不正确")
   @Schema(description = "订单起点的经度")
   private String startPlaceLongitude;

   @NotBlank(message = "endPlace不能为空")
   @Pattern(regexp = "[\\(\\)0-9A-Z#\\-_\\u4e00-\\u9fa5]{2,50}", message = "endPlace内容不正确")
   @Schema(description = "订单终点")
   private String endPlace;

   @NotBlank(message = "endPlaceLatitude不能为空")
   @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "endPlaceLatitude内容不正确")
   @Schema(description = "订单终点的纬度")
   private String endPlaceLatitude;

   @NotBlank(message = "endPlaceLongitude不能为空")
   @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "endPlaceLongitude内容不正确")
   @Schema(description = "订单终点的经度")
   private String endPlaceLongitude;

   @NotBlank(message = "expectsMileage不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d+$|^0\\.\\d*[1-9]\\d*$|^[1-9]\\d*$", message = "expectsMileage内容不正确")
   @Schema(description = "预估代驾公里数")
   private String expectsMileage;

   @NotBlank(message = "expectsFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "expectsFee内容不正确")
   @Schema(description = "预估代驾费用")
   private String expectsFee;

   @NotBlank(message = "favourFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "favourFee内容不正确")
   @Schema(description = "顾客好处费")
   private String favourFee;


   @NotNull(message = "chargeRuleId不能为空")
   @Min(value = 1, message = "chargeRuleId不能小于1")
   @Schema(description = "规则ID")
   private Long chargeRuleId;

   @NotBlank(message = "carPlate不能为空")
   @Pattern(regexp = "^([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领A-Z]{1}[A-Z]{1}(([0-9]{5}[DF])|([DF]([A-HJ-NP-Z0-9])[0-9]{4})))|([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领A-Z]{1}[A-Z]{1}[A-HJ-NP-Z0-9]{4}[A-HJ-NP-Z0-9挂学警港澳]{1})$",
           message = "carPlate内容不正确")
   @Schema(description = "车牌号")
   private String carPlate;

   @NotBlank(message = "carType不能为空")
   @Pattern(regexp = "^[\\u4e00-\\u9fa5A-Za-z0-9\\-\\_\\s]{2,20}$", message = "carType内容不正确")
   @Schema(description = "车型")
   private String carType;

   @NotBlank(message = "date不能为空")
   @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$", message = "date内容不正确")
   @Schema(description = "日期")
   private String date;

   @NotNull(message = "baseMileage不能为空")
   @Min(value = 1, message = "baseMileage不能小于1")
   @Schema(description = "基础里程（公里）")
   private Short baseMileage;

   @NotBlank(message = "baseMileagePrice不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "baseMileagePrice内容不正确")
   @Schema(description = "基础里程价格")
   private String baseMileagePrice;

   @NotBlank(message = "exceedMileagePrice不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "exceedMileagePrice内容不正确")
   @Schema(description = "超出基础里程的价格")
   private String exceedMileagePrice;

   @NotNull(message = "baseMinute不能为空")
   @Min(value = 1, message = "baseMinute不能小于1")
   @Schema(description = "基础分钟")
   private Short baseMinute;

   @NotBlank(message = "exceedMinutePrice不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "exceedMinutePrice内容不正确")
   @Schema(description = "超出基础分钟的价格")
   private String exceedMinutePrice;

   @NotNull(message = "baseReturnMileage不能为空")
   @Min(value = 1, message = "baseReturnMileage不能小于1")
   @Schema(description = "基础返程里程（公里）")
   private Short baseReturnMileage;

   @NotBlank(message = "exceedReturnPrice不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "exceedReturnPrice内容不正确")
   @Schema(description = "超出基础返程里程的价格")
   private String exceedReturnPrice;

}

@PostMapping("/insertOrder")
@Operation(summary = "顾客下单")
public R insertOrder(@RequestBody @Valid InsertOrderForm form) {
   OrderEntity orderEntity = new OrderEntity();
   orderEntity.setUuid(form.getUuid());
   orderEntity.setCustomerId(form.getCustomerId());
   orderEntity.setStartPlace(form.getStartPlace());
   JSONObject json = new JSONObject();
   json.set(LATITUDE, form.getStartPlaceLatitude());
   json.set(LONGITUDE, form.getStartPlaceLongitude());
   orderEntity.setStartPlaceLocation(json.toString());
   orderEntity.setEndPlace(form.getEndPlace());
   json = new JSONObject();
   json.set(LATITUDE, form.getEndPlaceLatitude());
   json.set(LONGITUDE, form.getEndPlaceLongitude());
   orderEntity.setEndPlaceLocation(json.toString());
   orderEntity.setExpectsMileage(new BigDecimal(form.getExpectsMileage()));
   orderEntity.setExpectsFee(new BigDecimal(form.getExpectsFee()));
   orderEntity.setFavourFee(new BigDecimal(form.getFavourFee()));
   orderEntity.setChargeRuleId(form.getChargeRuleId());
   orderEntity.setCarPlate(form.getCarPlate());
   orderEntity.setCarType(form.getCarType());
   orderEntity.setDate(form.getDate());

   OrderBillEntity orderBillEntity = new OrderBillEntity();
   orderBillEntity.setBaseMileage(form.getBaseMileage());
   orderBillEntity.setBaseMileagePrice(new BigDecimal(form.getBaseMileagePrice()));
   orderBillEntity.setExceedMileagePrice(new BigDecimal(form.getExceedMileagePrice()));
   orderBillEntity.setBaseMinute(form.getBaseMinute());
   orderBillEntity.setExceedMinutePrice(new BigDecimal(form.getExceedMinutePrice()));
   orderBillEntity.setBaseReturnMileage(form.getBaseReturnMileage());
   orderBillEntity.setExceedReturnPrice(new BigDecimal(form.getExceedReturnPrice()));

   String id = orderService.insertOrder(orderEntity, orderBillEntity);
   return R.ok().put(RESULT_MAP_KEY, id);
}
```
7. 写 bff-customer/src/main/controller/form/InsertOrderForm
   写 feign/OdrServiceApi#insertOrder
   补充 service/OrderServiceImpl#createNewOrder 中创建订单部分
   写 controller/OrderController#createNewOrder
   实现下单链路的整体逻辑收口
```java
 @PostMapping("/order/insertOrder")
 R insertOrder(InsertOrderForm form);

InsertOrderForm form_4 = new InsertOrderForm();
//UUID字符串，充当订单号，微信支付时候会用上
form_4.setUuid(IdUtil.simpleUUID());
form_4.setCustomerId(customerId);
form_4.setStartPlace(startPlace);
form_4.setStartPlaceLatitude(startPlaceLatitude);
form_4.setStartPlaceLongitude(startPlaceLongitude);
form_4.setEndPlace(endPlace);
form_4.setEndPlaceLatitude(endPlaceLatitude);
form_4.setEndPlaceLongitude(endPlaceLongitude);
form_4.setExpectsMileage(mileage);
form_4.setExpectsFee(expectsFee);
form_4.setFavourFee(favourFee);
form_4.setDate(new DateTime().toDateStr());
form_4.setChargeRuleId(Long.parseLong(chargeRuleId));
form_4.setCarPlate(form.getCarPlate());
form_4.setCarType(form.getCarType());
form_4.setBaseMileage(baseMileage);
form_4.setBaseMileagePrice(baseMileagePrice);
form_4.setExceedMileagePrice(exceedMileagePrice);
form_4.setBaseMinute(baseMinute);
form_4.setExceedMinutePrice(exceedMinutePrice);
form_4.setBaseReturnMileage(baseReturnMileage);
form_4.setExceedReturnPrice(exceedReturnPrice);
r = odrServiceApi.insertOrder(form_4);
         
 @PostMapping("/createNewOrder")
 @Operation(summary = "创建新订单")
 @SaCheckLogin
 public R createNewOrder(@RequestBody @Valid CreateNewOrderForm form) {
     long customerId = StpUtil.getLoginIdAsLong();
     form.setCustomerId(customerId);
     HashMap result = orderService.createNewOrder(form);
     return R.ok().put("result", result);
 }
```
8. 运行hxds-tm、hxds-odr、hxds-rule、hxds-mps、bff-customer五个子系统，然后用FastRequest插件测试Web方法
### 位置微服务缓存司机实时定位
缓存司机在线信息用的 Key 是 `driver_online#driverId`，对应的 Value 是 `接单范围#订单里程范围#定向接单的坐标`，超时时间为1分钟。当系统接到订单之后，到 Redis 上面根据 driverId 查找缓存，找到了就是在线，找不到就是不在线
接单范围即是对司机到起点坐标的距离进行限制，订单里程范围即是对起点坐标与终点坐标之间的距离进行限制
缓存司机位置信息用的 Key 是 `driver_location`，对应的 lati, longi 是司机位置，Member 是 `driverId`，

1. 写 hxds-mps/src/main/service/DriverLocationService#updateLocationCache、removeLocationCache及其实现类
   写 controller/form/UpdateLocationCacheForm、RemoveLocationCacheForm
   写 controller/DriverLocationController
   通过GEO实现对司机实时定位的修改和删除
```java
void updateLocationCache(Map param);

void removeLocationCache(long driverId);

@Override
public void updateLocationCache(Map param) {
     long driverId = MapUtil.getLong(param, "driverId");
     String latitude = MapUtil.getStr(param, "latitude");
     String longitude = MapUtil.getStr(param, "longitude");
     //接单范围
     int rangeDistance = MapUtil.getInt(param, "rangeDistance");
     //订单里程范围
     int orderDistance = MapUtil.getInt(param, "orderDistance");
     Point point = new Point(Convert.toDouble(longitude), Convert.toDouble(latitude));
     redisTemplate.opsForGeo().add("driver_location", point, driverId + "");
     //定向接单地址的经度
     String orientateLongitude = null;
     if (param.get("orientateLongitude") != null) {
         orientateLongitude = MapUtil.getStr(param, "orientateLongitude");
     }
     //定向接单地址的纬度
     String orientateLatitude = null;
     if (param.get("orientateLatitude") != null) {
         orientateLatitude = MapUtil.getStr(param, "orientateLatitude");
     }
     //定向接单经纬度的字符串
     String orientation = "none";
     if (orientateLongitude != null && orientateLatitude != null) {  
         orientation = orientateLatitude + "," + orientateLongitude;
     }
     String temp = rangeDistance + "#" + orderDistance + "#" + orientation;
     redisTemplate.opsForValue().set("driver_online#" + driverId, temp, 60, TimeUnit.SECONDS);
}

@Override
public void removeLocationCache(long driverId) {
     redisTemplate.opsForGeo().remove("driver_location", driverId + "");
     redisTemplate.delete("driver_online#" + driverId);
}

@Data
@Schema(description = "更新司机GPS坐标缓存的表单")
public class UpdateLocationCacheForm {

   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

   @NotBlank(message = "latitude不能为空")
   @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "latitude内容不正确")
   @Schema(description = "纬度")
   private String latitude;

   @NotBlank(message = "longitude不能为空")
   @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "longitude内容不正确")
   @Schema(description = "经度")
   private String longitude;

   @NotNull(message = "rangeDistance不能为空")
   @Range(min = 1, max = 5, message = "rangeDistance范围错误")
   @Schema(description = "接收几公里内的订单")
   private Integer rangeDistance;

   @NotNull(message = "orderDistance不能为空")
   @Schema(description = "接收代驾里程几公里以上的订单")
   @Range(min = 0, max = 30, message = "orderDistance范围错误")
   private Integer orderDistance;

   @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "orientateLongitude内容不正确")
   @Schema(description = "定向接单的经度")
   private String orientateLongitude;

   @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "orientateLatitude内容不正确")
   @Schema(description = "定向接单的纬度")
   private String orientateLatitude;
}

@Data
@Schema(description = "删除司机定位缓存的表单")
public class RemoveLocationCacheForm {
   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
}

@PostMapping("/updateLocationCache")
@Operation(summary = "更新司机GPS定位缓存")
public R updateLocationCache(@RequestBody @Valid UpdateLocationCacheForm form) {
   Map param = BeanUtil.beanToMap(form);
   driverLocationService.updateLocationCache(param);
   return R.ok();
}

@PostMapping("/removeLocationCache")
@Operation(summary = "删除司机GPS定位缓存")
public R removeLocationCache(@RequestBody @Valid RemoveLocationCacheForm form) {
   driverLocationService.removeLocationCache(form.getDriverId());
   return R.ok();
}
```
2. 运行hxds-tm、hxds-mps两个子系统，然后用FastRequest插件测试Web方法
3. 写 bff-driver/src/main/controller/form/UpdateLocationCacheForm、RemoveLocationCacheForm
   写 feign/MpsServiceApi#updateLocationCache、removeLocationCache
   写 service/DriverLocationService#updateLocationCache、removeLocationCache 及其实现类
   写 controller/DriverLocationController#updateLocationCache、removeLocationCache
```java
@Data
@Schema(description = "更新司机GPS坐标缓存的表单")
public class UpdateLocationCacheForm {

   @Schema(description = "司机ID")
   private Long driverId;

   @NotBlank(message = "latitude不能为空")
   @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "latitude内容不正确")
   @Schema(description = "纬度")
   private String latitude;

   @NotBlank(message = "longitude不能为空")
   @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "longitude内容不正确")
   @Schema(description = "经度")
   private String longitude;

   @NotNull(message = "rangeDistance不能为空")
   @Range(min = 1, max = 5, message = "rangeDistance范围错误")
   @Schema(description = "接收几公里内的订单")
   private Integer rangeDistance;

   @NotNull(message = "orderDistance不能为空")
   @Schema(description = "接收代驾里程几公里以上的订单")
   @Range(min = 0, max = 30, message = "orderDistance范围错误")
   private Integer orderDistance;

   @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "orientateLongitude内容不正确")
   @Schema(description = "定向接单的经度")
   private String orientateLongitude;

   @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "orientateLatitude内容不正确")
   @Schema(description = "定向接单的纬度")
   private String orientateLatitude;
}
@Data
@Schema(description = "删除司机定位缓存的表单")
public class RemoveLocationCacheForm {

   @Schema(description = "司机ID")
   private Long driverId;
   
}

@PostMapping("/driver/location/removeLocationCache")
R removeLocationCache(RemoveLocationCacheForm form);

@PostMapping("/driver/location/updateLocationCache")
R updateLocationCache(UpdateLocationCacheForm form);

void updateLocationCache(UpdateLocationCacheForm form);

void removeLocationCache(RemoveLocationCacheForm form);

@Override
public void updateLocationCache(UpdateLocationCacheForm form) {
   mpsServiceApi.updateLocationCache(form);
}

@Override
public void removeLocationCache(RemoveLocationCacheForm form) {
   mpsServiceApi.removeLocationCache(form);
}

@PostMapping("/updateLocationCache")
@Operation(summary = "更新司机缓存GPS定位")
@SaCheckLogin
public R updateLocationCache(@RequestBody @Valid UpdateLocationCacheForm form) {
   long driverId = StpUtil.getLoginIdAsLong();
   form.setDriverId(driverId);
   driverLocationService.updateLocationCache(form);
   return R.ok();
}
```
4. 先删除Redis中的缓存信息，然后运行hxds-tm、hxds-mps、bff-driver子系统启动成功，最后用FastRequest插件测试Web方法
5. 写 hxds-driver-wx/App.vue#onLaunch，先开启实时GPS定位，然后用Ajax提交GPS定位给后端Java程序
```javascript
 onLaunch: function() {
     let gps = [];
     wx.setKeepScreenOn({
         // 保持屏幕常亮
         keepScreenOn: true
     });
     //TODO 每隔3分钟触发自定义事件，接受系统消息
     wx.startLocationUpdate({
         success(resp) {
             console.log('开启定位成功');
         },
         fail(resp) {
             console.log('开启定位失败');
             uni.$emit('updateLocation', null);
         }
     });

     wx.onLocationChange(function(resp) {
         let latitude = resp.latitude;
         let longitude = resp.longitude;
         let speed = resp.speed;

         let location = {
             latitude: latitude,
             longitude: longitude
         };
         let workStatus = uni.getStorageSync('workStatus');
         let baseUrl = 'http://电脑IP:8080/hxds-driver';
         if (workStatus == '开始接单') {
             // TODO 只在每分钟的前10秒上报定位信息，减小服务器压力
             // let current = new Date();
             // if (current.getSeconds() > 10) {
             // 	return;
             // }
             let settings = uni.getStorageSync('settings');
             settings = {
                 orderDistance: 0,
                 rangeDistance: 5,
                 orientation: ''
             };
             let orderDistance = settings.orderDistance;
             let rangeDistance = settings.rangeDistance;
             let orientation = settings.orientation;
             uni.request({
                 url: `${baseUrl}/driver/location/updateLocationCache`,
                 method: 'POST',
                 header: {
                     token: uni.getStorageSync('token')
                 },
                 data: {
                     latitude: latitude,
                     longitude: longitude,
                     orderDistance: orderDistance,
                     rangeDistance: rangeDistance,
                     orientateLongitude: orientation != '' ? orientation.longitude : null,
                     orientateLatitude: orientation != '' ? orientation.latitude : null
                 },
                 success: function(resp) {
                     if (resp.statusCode == 401) {
                         uni.redirectTo({
                             url: 'pages/login/login'
                         });
                     } else if (resp.statusCode == 200 && resp.data.code == 200) {
                         let data = resp.data;
                         if (data.hasOwnProperty('token')) {
                             let token = data.token;
                             uni.setStorageSync('token', token);
                         }
                         console.log('定位更新成功');
                     } else {
                         console.error('更新GPS定位信息失败', resp.data);
                     }
                 },
                 fail: function(error) {
                     console.error('更新GPS定位信息失败', error);
                 }
             });
         } 
         else if(workStatus == '接客户'){
             let executeOrder = uni.getStorageSync('executeOrder');
             let orderId=executeOrder.id
             let data={
                 orderId:orderId,
                 latitude: latitude,
                 longitude: longitude
             }
             uni.request({
                 url: `${baseUrl}/driver/location/updateOrderLocationCache`,
                 method:"POST",
                 header:{
                     token:uni.getStorageSync("token")
                 },
                 data:data,
                 success:function(resp){
                     if (resp.statusCode == 401) {
                         uni.redirectTo({
                             url: 'pages/login/login'
                         });
                     } else if (resp.statusCode == 200 && resp.data.code == 200) {
                         let data = resp.data;
                         if (data.hasOwnProperty('token')) {
                             let token = data.token;
                             uni.setStorageSync('token', token);
                         }
                         console.log('订单定位更新成功');
                     } else {
                         console.error('订单定位更新失败', resp.data);
                     }
                 },
                 fail:function(error){
                     console.error('订单定位更新失败', error);
                 }
             })
         }
         else if (workStatus == '开始代驾') {
             //每凑够20个定位就上传一次，减少服务器的压力
             let executeOrder=uni.getStorageSync("executeOrder")
             if(executeOrder!=null){
                 gps.push({
                     orderId: executeOrder.id,
                     customerId: executeOrder.customerId,
                     latitude: latitude,
                     longitude: longitude,
                     speed: speed
                 })
                 if(gps.length==5){
                     uni.request({
                         url:`${baseUrl}/order/gps/insertOrderGps`,
                         method: 'POST',
                         header: {
                             token: uni.getStorageSync('token')
                         },
                         data: {
                             list: gps
                         },
                         success: function(resp) {
                             if (resp.statusCode == 401) {
                                 uni.redirectTo({
                                     url: '/pages/login/login'
                                 });
                             }
                             else if (resp.statusCode == 200 && resp.data.code == 200) {
                                 let data = resp.data;
                                 console.log("上传GPS成功");
                             } 
                             else {
                                 console.error('保存GPS定位失败', resp.data);
                             }
                             gps.length = 0;
                         },
                         fail: function(error) {
                             console.error('保存GPS定位失败', error);
                         }
                     })
                 }
             }
         }
         uni.$emit('updateLocation', location);
     });
 },
```
6. 先把Redis中缓存的定位信息删除掉，然后把hxds-tm、hxds-mps、hxds-dr、hxds-odr、bff-driver、gateway子系统启动成功，
   然后必须登陆司机端小程序，我们等待半分钟，看看Redis上面是否缓存了司机的定位
### 地图微服务用GEO查找附近适合接单的司机
司机端的小程序可以实时上传定位坐标，并且Redis中保存了司机的GEO缓存和上线缓存。回到创建订单这个业务主线上来，创建订单的过程中，
要查找附近适合接单的司机。如果有这样的司机，代驾系统才会创建订单，否则就拒绝创建订单。
1. 写 hxds-mps/src/main/java/service/DriverLocationService#searchBefittingDriverAboutOrder 及其实现类
   写 controller/form/SearchBefittingDriverAboutOrderForm
   写 controller/DriverLocationController#searchBefittingDriverAboutOrder
   计算方圆几公里以内的司机，我们就得用上Redis的GEO计算
   【拓展】WGS84（World Geodetic System 1984）：是 GPS 全球定位系统建立的坐标系统，通过GPS定位拿到的原始经纬度。
   GCJ-02（国家测量局02号标准）：GCJ-02 是由中国国家测绘局（G表示Guojia国家，C表示Cehui测绘，J表示Ju局）制订的地理信息系统的坐标系统，是在WGS84经纬度的基础上执行加密算法而成。因为GPS得到的经纬度直接在 GCJ-02 坐标系下会定位到错误的地点，有种到了火星的感觉，因此在坊间也将 GCJ-02 戏称为火星坐标系。国内的高德地图就是用的 GCJ-02 坐标系
   BD-09（Baidu, BD）：是百度地图使用的地理坐标系，其在GCJ-02上多增加了一次变换，用来保护用户隐私。从百度产品中得到的坐标都是BD-09坐标系
```java
ArrayList searchBefittingDriverAboutOrder(double startPlaceLatitude,
        double startPlaceLongitude, double endPlaceLatitude,
        double endPlaceLongitude, double mileage);

    @Override
    public List searchBefittingDriverAboutOrder(double startPlaceLatitude, double startPlaceLongitude,
                                                double endPlaceLatitude, double endPlaceLongitude, double mileage) {
        // 获取方圆5公里的范围
        Point point = new Point(startPlaceLongitude, startPlaceLatitude);
        RedisGeoCommands.DistanceUnit metric = RedisGeoCommands.DistanceUnit.KILOMETERS;
        Distance distance = new Distance(5, metric);
        Circle circle = new Circle(point, distance);

        // includeDistance 包含距离 includeCoordinates 包含坐标 sortAscending 正序排序
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance()
                .includeCoordinates().sortAscending();
        // 返回键包含的位置元素当中，与中心的距离不超过给定最大距离的所有位置元素
        GeoResults radius = redisTemplate.opsForGeo().radius("driver_location", circle, args);

        // 筛选在这个范围内附近适合接单(满足接单范围&订单里程范围&定向接单)的司机且在线的的司机
        ArrayList list = Lists.newArrayList();
        if(!Objects.isNull(radius)){
            Iterator<GeoResult<RedisGeoCommands.GeoLocation<String>>> iterator = radius.iterator();
            while(iterator.hasNext()){
                // content就是符合条件的driver_location对应的GEO数据，里面存的Member就是driverId
                // result: distance(距起点坐标的距离)、content     content: point(经纬度)、name(Member)
                GeoResult<RedisGeoCommands.GeoLocation<String>> result = iterator.next();
                RedisGeoCommands.GeoLocation<String> content = result.getContent();
                String driverId = content.getName();
                // 司机和起点坐标之间的距离
                double dist = result.getDistance().getValue();
                if(!redisTemplate.hasKey("driver_online#" + driverId)){
                    continue;
                }
                Object obj = redisTemplate.opsForValue().get("driver_online#" + driverId);
                if(Objects.isNull(obj)){
                    continue;
                }
                String value = obj.toString();
                String[] temp = value.split("#");
                int rangeDistance = Integer.parseInt(temp[0]);
                int orderDistance = Integer.parseInt(temp[1]);
                String orientation = temp[2];

                // 判断是否满足接单范围
                boolean bool_1 = (dist <= rangeDistance);
                // 判断是否满足订单里程范围，由于是起点坐标方圆五里，所以还需要对mileage进行判断
                // 这里有两方面考虑：1、orderDistance过小，对mileage限制防止跑超限；2、orderDistance过大，防止有的司机想拉远程却派发短程单
                boolean bool_2 = false;
                if (orderDistance == 0) {
                    bool_2 = true;
                } else if (orderDistance == 5 && mileage > 0 && mileage <= 5) {
                    bool_2 = true;
                } else if (orderDistance == 10 && mileage > 5 && mileage <= 10) {
                    bool_2 = true;
                } else if (orderDistance == 15 && mileage > 10 && mileage <= 15) {
                    bool_2 = true;
                } else if (orderDistance == 30 && mileage > 15 && mileage <= 30) {
                    bool_2 = true;
                }
                // 判断定向接单是否符合：定向接单即是只接根据司机设置的point为中心一定范围内的单
                boolean bool_3 = false;
                if (!orientation.equals("none")) {
                    double orientationLatitude = Double.parseDouble(orientation.split(",")[0]);
                    double orientationLongitude = Double.parseDouble(orientation.split(",")[1]);
                    //把定向点的火星坐标转换成GPS坐标
                    double[] location = CoordinateTransform.transformGCJ02ToWGS84(orientationLongitude, orientationLatitude);
                    GlobalCoordinates point_1 = new GlobalCoordinates(location[1], location[0]);
                    //把订单终点的火星坐标转换成GPS坐标
                    location = CoordinateTransform.transformGCJ02ToWGS84(endPlaceLongitude, endPlaceLatitude);
                    GlobalCoordinates point_2 = new GlobalCoordinates(location[1], location[0]);
                    //这里不需要Redis的GEO计算，直接用封装函数计算两个GPS坐标之间的距离
                    GeodeticCurve geoCurve = new GeodeticCalculator()
                            .calculateGeodeticCurve(Ellipsoid.WGS84, point_1, point_2);
                    if (geoCurve.getEllipsoidalDistance() <= 3000) {
                        bool_3 = true;
                    }
                } else {
                    bool_3 = true;
                }
                if (bool_1 && bool_2 && bool_3) {
                    HashMap map = new HashMap() {{
                        put("driverId", driverId);
                        put("distance", dist);
                    }};
                    list.add(map);
                }
            }
        }
        return list;
    }

@Data
@Schema(description = "查询符合某个订单接单的司机列表的表单")
public class SearchBefittingDriverAboutOrderForm {
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

   @NotBlank(message = "mileage不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d+$|^0\\.\\d*[1-9]\\d*$|^[1-9]\\d*$", message = "mileage内容不正确")
   @Schema(description = "预估里程")
   private String mileage;
}

@PostMapping("/searchBefittingDriverAboutOrder")
@Operation(summary = "查询符合某个订单接单的司机列表")
public R searchBefittingDriverAboutOrder(@RequestBody @Valid SearchBefittingDriverAboutOrderForm form) {
   double startPlaceLatitude = Double.parseDouble(form.getStartPlaceLatitude());
   double startPlaceLongitude = Double.parseDouble(form.getStartPlaceLongitude());
   double endPlaceLatitude = Double.parseDouble(form.getEndPlaceLatitude());
   double endPlaceLongitude = Double.parseDouble(form.getEndPlaceLongitude());
   double mileage = Double.parseDouble(form.getMileage());
   ArrayList list = driverLocationService.searchBefittingDriverAboutOrder(startPlaceLatitude, startPlaceLongitude,
           endPlaceLatitude, endPlaceLongitude, mileage);
   return R.ok().put("result", list);
}
```
2. 写 bff-customer/src/main/controller/form/SearchBefittingDriverAboutOrderForm
   写 feign/MpsServiceApi#searchBefittingDriverAboutOrder
   继续补全 service/OrderServiceImpl#createNewOrder 实现查找符合条件的司机
```java
@Data
@Schema(description = "查询符合某个订单接单的司机列表的表单")
public class SearchBefittingDriverAboutOrderForm {
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

    @NotBlank(message = "mileage不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d+$|^0\\.\\d*[1-9]\\d*$|^[1-9]\\d*$", message = "mileage内容不正确")
    @Schema(description = "预估里程")
    private String mileage;
}

@PostMapping("/driver/location/searchBefittingDriverAboutOrder")
R searchBefittingDriverAboutOrder(SearchBefittingDriverAboutOrderForm form);

SearchBefittingDriverAboutOrderForm form_3 = new SearchBefittingDriverAboutOrderForm();
form_3.setStartPlaceLatitude(startPlaceLatitude);
form_3.setStartPlaceLongitude(startPlaceLongitude);
form_3.setEndPlaceLatitude(endPlaceLatitude);
form_3.setEndPlaceLongitude(endPlaceLongitude);
form_3.setMileage(mileage);
r = mpsServiceApi.searchBefittingDriverAboutOrder(form_3);
ArrayList<HashMap> list = (ArrayList<HashMap>) r.get("result");
```
3. 补充 hxds-customer-wx/pages/car_list/car_list.vue#choseOneHandle 传递 carType 到 create_order
   补充 hxds-customer-wx/pages/create_order/create_order.vue#chooseCarHandle 接收 carType
   写 hxds-customer-wx/pages/create_order/create_order.vue#createOrderHandle 实现小程序下单逻辑
```javascript
chooseOneHandle(one.id, one.carPlate, one.carType)
choseOneHandle: function (id, carPlate, carType) {
    uni.navigateTo({
        url: `../create_order/create_order?showCar=true&carId=${id}&carPlate=${carPlate}&carType=${carType}`
    });
}

if(options.hasOwnProperty('showCar')){
   that.showCar = options.showCar;
   that.carId = options.carId;
   that.carPlate = options.carPlate;
   that.carType = options.carType;
}

createOrderHandle: function() {
   let that = this;
   if (that.carType == null || that.carPlate == null) {
      uni.showToast({
         icon: 'error',
         title: '没有设置代驾车辆'
      });
      return;
   }
   uni.showLoading({
      title: '下单中请稍后'
   });
   setTimeout(function() {
      uni.hideLoading();
   }, 60000);
   let data = {
      startPlace: that.from.address,
      startPlaceLatitude: that.from.latitude,
      startPlaceLongitude: that.from.longitude,
      endPlace: that.to.address,
      endPlaceLatitude: that.to.latitude,
      endPlaceLongitude: that.to.longitude,
      favourFee: '0.0',
      carPlate: that.carPlate,
      carType: that.carType
   };
   that.ajax(that.url.createNewOrder, 'POST', data, function(resp) {
      uni.hideLoading();
      if (resp.data.result.count > 0) {
         uni.showToast({
            icon: 'success',
            title: '订单创建成功'
         });
         setTimeout(function() {
            that.orderId = resp.data.result.orderId;
            that.showPopup = true;
            //此处应该是15*60，但是测试中我们等不了15分钟,每隔5分钟发送一次查询接单情况的请求
            /*that.timestamp = 60;
            that.$refs.uCountDown.start();*/
         }, 2000);
      } else {
         uni.showToast({
            icon: 'none',
            title: '没有适合接单的司机'
         });
      }
   });
},
```
3. 把 hxds-tm、hxds-dr、hxds-mps、hxds-cst、hxds-rule、hxds-snm、hxds-odr、bff-driver、bff-customer、gateway 这些子系统都启动了，然后在司机小程序上面登陆，保持实时上传司机的GPS定位，
   最后用FastRequest插件测试Web方法
### 消息微服务收发新订单消息
1. 写 hxds/hxds-snm/src/main/resources/application.yml 连接 RabbitMQ
   写 hxds/hxds-snm/src/main/java/entity/NewOrderMessage 定义订单消息体
   写 hxds/hxds-snm/src/main/java/task/NewOrderMassageTask#sendNewOrderMessage、sendNewOrderMessageAsync 实现同/异步发送消息
   写 hxds/hxds-snm/src/main/java/task/NewOrderMassageTask#receiveNewOrderMessage、deleteNewOrderQueue、deleteNewOrderQueueAsync 实现接收消息、同/异步删除消息
   写 hxds/hxds-snm/src/main/java/task/NewOrderMassageTask#clearNewOrderQueue、clearNewOrderQueueAsync 实现同/异步清空消息队列
```java
@Data
public class NewOrderMessage {
    private String userId;
    private String orderId;
    private String from;
    private String to;
    private String expectsFee;
    private String mileage;
    private String minute;
    private String distance;
    private String favourFee;
}

/**
 * 1. 获取Connection、Channel
 * 2. 定义交换机，根据 routingKey 路由消息
 * 3. 定义MQ消息的属性对象
 * 4. 定义队列(持久化缓存消息，消息接收不加锁，消息全部接收完并不删除队列)并绑定
 * 5. 向交换机发送消息，并附带 routingKey
 *
 * @param list
 */
public void sendNewOrderMessage(List<NewOrderMessage> list) {
   int ttl = 1 * 60 * 1000;
   String exchangeName = "new_order_private";
   try (
           Connection connection = factory.newConnection();
           Channel channel = connection.createChannel()
   ) {
      // 定义交换机，根据 routingKey 路由消息
      channel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT);
      HashMap param = Maps.newHashMap();
      for (NewOrderMessage message : list) {
         // 定义MQ消息的属性信息
         HashMap map = new HashMap<>() {{
            put("orderId", message.getOrderId());
            put("from", message.getFrom());
            put("to", message.getTo());
            put("expectsFee", message.getExpectsFee());
            put("mileage", message.getMileage());
            put("minute", message.getMinute());
            put("distance", message.getDistance());
            put("favourFee", message.getFavourFee());
         }};
         // 定义MQ消息的属性对象
         AMQP.BasicProperties properties = new AMQP.BasicProperties().builder().contentEncoding("UTF-8")
                 .headers(map).expiration(ttl + "").build();
         String queueName = "queue_" + message.getUserId();
         String routingKey = message.getUserId();
         // 定义队列(持久化缓存消息，消息接收不加锁，消息全部接收完并不删除队列)
         channel.queueDeclare(queueName, true, false, false, param);
         channel.queueBind(queueName, exchangeName, routingKey);
         // 向交换机发送消息，并附带 routingKey
         channel.basicPublish(exchangeName, routingKey, properties, ("新订单" + message.getOrderId()).getBytes());
         log.debug(message.getUserId() + "的新订单消息发送成功");
      }
   } catch (Exception e) {
      log.error("执行异常", e);
      throw new HxdsException("新订单消息发送失败");
   }
}

@Async
public void sendNewOrderMessageAsync(ArrayList<NewOrderMessage> list) {
   this.sendNewOrderMessage(list);
}

/**
 * 1. 获取Connection、Channel
 * 2. 定义交换机，根据 routingKey 路由消息
 * 3. 定义队列(持久化缓存消息，消息接收不加锁，消息全部接收完并不删除队列)并绑定
 * 4. 避免一次接收过多消息，采用限流方式
 * 5. while循环接收消息：定义消息属性对象，然后将其封装到NewOrderMessage
 * 6. 确认接收到消息，让 MQ 删除该消息
 * 7. 消息倒叙，让新消息排在前面
 * 自测的时候注意ttl为1分钟
 * @param userId
 * @return
 */
public List<NewOrderMessage> receiveNewOrderMessage(long userId) {
   String exchangeName = "new_order_private";
   String queueName = "queue_" + userId;
   String routingKey = userId + "";
   List<NewOrderMessage> list = Lists.newArrayList();
   try (
           Connection connection = factory.newConnection();
           Channel channel = connection.createChannel();
   ) {
      channel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT);
      channel.queueDeclare(queueName, true, false, false, null);
      channel.queueBind(queueName, exchangeName, routingKey);
      // 每次接收10条消息然后循环接收
      channel.basicQos(0, 10, true);
      while (true) {
         GetResponse response = channel.basicGet(queueName, false);
         if (!Objects.isNull(response)) {
            AMQP.BasicProperties properties = response.getProps();
            Map<String, Object> map = properties.getHeaders();
            String orderId = MapUtil.getStr(map, "orderId");
            String from = MapUtil.getStr(map, "from");
            String to = MapUtil.getStr(map, "to");
            String expectsFee = MapUtil.getStr(map, "expectsFee");
            String mileage = MapUtil.getStr(map, "mileage");
            String minute = MapUtil.getStr(map, "minute");
            String distance = MapUtil.getStr(map, "distance");
            String favourFee = MapUtil.getStr(map, "favourFee");
            NewOrderMessage message = new NewOrderMessage();
            message.setOrderId(orderId);
            message.setFrom(from);
            message.setTo(to);
            message.setExpectsFee(expectsFee);
            message.setMileage(mileage);
            message.setMinute(minute);
            message.setDistance(distance);
            message.setFavourFee(favourFee);
            list.add(message);
            byte[] body = response.getBody();
            String msg = new String(body);
            log.debug("从RabbitMQ接收的订单消息：" + msg);
            long deliveryTag = response.getEnvelope().getDeliveryTag();
            channel.basicAck(deliveryTag, false);
         } else {
            break;
         }
      }
      ListUtil.reverse(list);
      return list;
   } catch (Exception e) {
      log.error("执行异常", e);
      throw new HxdsException("接收新订单消息失败");
   }
}

/**
 * 1. 获取Connection、Channel
 * 2. 定义交换机，根据 routingKey 路由消息
 * 3. 删除队列
 * @param userId
 */
public void deleteNewOrderQueue(long userId) {
   String exchangeName = "new_order_private"; //交换机名字
   String queueName = "queue_" + userId; //队列名字
   try (
           Connection connection = factory.newConnection();
           Channel channel = connection.createChannel();
   ) {
      channel.exchangeDeclare(exchangeName,BuiltinExchangeType.DIRECT);
      channel.queueDelete(queueName);
      log.debug(userId + "的新订单消息队列成功删除");
   } catch (Exception e) {
      log.error(userId + "的新订单队列删除失败", e);
      throw new HxdsException("新订单队列删除失败");
   }
}

@Async
public void deleteNewOrderQueueAsync(long userId){
   this.deleteNewOrderQueue(userId);
}

/**
 * 1. 获取Connection、Channel
 * 2. 定义交换机，根据 routingKey 路由消息
 * 3. 清空队列
 * @param userId
 */
public void clearNewOrderQueue(long userId){
   String exchangeName = "new_order_private"; //交换机名字
   String queueName = "queue_" + userId; //队列名字
   try (
           Connection connection = factory.newConnection();
           Channel channel = connection.createChannel();
   ) {
      channel.exchangeDeclare(exchangeName,BuiltinExchangeType.DIRECT);
      channel.queuePurge(queueName);
      log.debug(userId + "的新订单消息队列清空删除");
   } catch (Exception e) {
      log.error(userId + "的新订单队列清空失败", e);
      throw new HxdsException("新订单队列清空失败");
   }
}

@Async
public void clearNewOrderQueueAsync(long userId){
   this.clearNewOrderQueue(userId);
}
```
2. 写 hxds-snm/src/main/java/com/example/hxds/snm/controller/form/SendNewOrderMessageForm.java
   写 hxds-snm/src/main/java/com/example/hxds/snm/controller/form/ReceiveNewOrderMessageForm.java
   写 hxds-snm/src/main/java/com/example/hxds/snm/controller/form/DeleteNewOrderQueueForm.java
   写 hxds-snm/src/main/java/com/example/hxds/snm/controller/form/ClearNewOrderQueueForm.java
   写 hxds-snm/src/main/java/com/example/hxds/snm/controller/NewOrderMessageController.java
   实现 snm 微服务对新订单消息队列对发送/接收/删除/清空，并自测
```java
@RestController
@RequestMapping("/message/order/new")
@Tag(name = "NewOrderMessageController", description = "新订单消息Web接口")
public class NewOrderMessageController {

    @Resource
    private NewOrderMassageTask task;

    @PostMapping("/sendNewOrderMessage")
    @Operation(summary = "同步发送新订单消息")
    public R sendNewOrderMessage(@RequestBody @Valid SendNewOrderMessageForm form) {
        ArrayList<NewOrderMessage> list = new ArrayList();
        String[] driversContent = form.getDriversContent();
        for (String one : driversContent) {
            String[] temp = one.split("#");
            String userId = temp[0];
            String distance = temp[1];
            NewOrderMessage message = new NewOrderMessage();
            message.setUserId(userId);
            message.setOrderId(form.getOrderId().toString());
            message.setFrom(form.getFrom());
            message.setTo(form.getTo());
            message.setMileage(form.getMileage());
            message.setMinute(form.getMinute().toString());
            message.setDistance(distance);
            message.setExpectsFee(form.getExpectsFee());
            message.setFavourFee(form.getFavourFee());
            list.add(message);
        }
        task.sendNewOrderMessage(list);
        return R.ok();
    }

    @PostMapping("/sendNewOrderMessageAsync")
    @Operation(summary = "同步发送新订单消息")
    public R sendNewOrderMessageAsync(@RequestBody @Valid SendNewOrderMessageForm form) {
        ArrayList<NewOrderMessage> list = new ArrayList();
        String[] driversContent = form.getDriversContent();
        for (String one : driversContent) {
            String[] temp = one.split("#");
            String userId = temp[0];
            String distance = temp[1];
            NewOrderMessage message = new NewOrderMessage();
            message.setUserId(userId);
            message.setOrderId(form.getOrderId().toString());
            message.setFrom(form.getFrom());
            message.setTo(form.getTo());
            message.setMileage(form.getMileage());
            message.setMinute(form.getMinute().toString());
            message.setDistance(distance);
            message.setExpectsFee(form.getExpectsFee());
            message.setFavourFee(form.getFavourFee());
            list.add(message);
        }
        task.sendNewOrderMessageAsync(list);
        return R.ok();
    }

    @PostMapping("/receiveNewOrderMessage")
    @Operation(summary = "同步接收新订单消息")
    public R receiveNewOrderMessage(@RequestBody @Valid ReceiveNewOrderMessageForm form) {
        List<NewOrderMessage> list = task.receiveNewOrderMessage(form.getUserId());
        return R.ok().put("result", list);
    }

    @PostMapping("/deleteNewOrderQueue")
    @Operation(summary = "同步删除新订单消息队列")
    public R deleteNewOrderQueue(@RequestBody @Valid DeleteNewOrderQueueForm form) {
        task.deleteNewOrderQueue(form.getUserId());
        return R.ok();
    }

    @PostMapping("/deleteNewOrderQueueeAsync")
    @Operation(summary = "异步删除新订单消息队列")
    public R deleteNewOrderQueueeAsync(@RequestBody @Valid DeleteNewOrderQueueForm form) {
        task.deleteNewOrderQueueAsync(form.getUserId());
        return R.ok();
    }

    @PostMapping("/clearNewOrderQueue")
    @Operation(summary = "同步清空新订单消息队列")
    public R clearNewOrderQueue(@RequestBody @Valid ClearNewOrderQueueForm form) {
        task.clearNewOrderQueue(form.getUserId());
        return R.ok();
    }

    @PostMapping("/clearNewOrderQueueAsync")
    @Operation(summary = "异步清空新订单消息队列")
    public R clearNewOrderQueueAsync(@RequestBody @Valid ClearNewOrderQueueForm form) {
        task.clearNewOrderQueueAsync(form.getUserId());
        return R.ok();
    }
}
```
3. 写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/SendNewOrderMessageForm.java、ReceiveBillMessageForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/SnmServiceApi.java#sendNewOrderMessageAsync、receiveBillMessage
   补充 bff-customer/src/main/java/com/example/hxds/bff/customer/service/impl/OrderServiceImpl.java#createNewOrder
```java
@Data
@Schema(description = "发送新订单消息的表单")
public class SendNewOrderMessageForm {

    @NotEmpty(message = "driversContent不能为空")
    @Schema(description = "司机的相关信息（司机ID#接单距离）")
    private String[] driversContent;

    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @NotBlank(message = "from内容不正确")
    @Pattern(regexp = "[\\(\\)0-9A-Z#\\-_\\u4e00-\\u9fa5]{2,50}", message = "from内容不正确")
    @Schema(description = "订单起点")
    private String from;

    @NotBlank(message = "to内容不正确")
    @Pattern(regexp = "[\\(\\)0-9A-Z#\\-_\\u4e00-\\u9fa5]{2,50}", message = "to内容不正确")
    @Schema(description = "订单终点")
    private String to;

    @NotBlank(message = "expectsFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "expectsFee内容不正确")
    @Schema(description = "预估价格")
    private String expectsFee;

    @NotBlank(message = "mileage不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d+$|^0\\.\\d*[1-9]\\d*$|^[1-9]\\d*$", message = "mileage内容不正确")
    @Schema(description = "预估里程")
    private String mileage;

    @NotNull(message = "minute")
    @Min(value = 1, message = "minute不能小于1")
    @Schema(description = "预估时长")
    private Integer minute;

    @NotBlank(message = "favourFee不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "favourFee内容不正确")
    @Schema(description = "顾客好处费")
    private String favourFee;
}

@Data
@Schema(description = "接收新订单消息的表单")
public class ReceiveBillMessageForm {

   @Schema(description = "用户ID")
   private Long userId;

   @Schema(description = "用户身份")
   private String identity;
}

@FeignClient(value = "hxds-snm")
public interface SnmServiceApi {

   @PostMapping("/message/order/new/sendNewOrderMessageAsync")
   R sendNewOrderMessageAsync(SendNewOrderMessageForm form);

   @PostMapping("/message/receiveBillMessage")
   R receiveBillMessage(ReceiveBillMessageForm form);
}

   // 发送通知给符合条件的司机抢单
SendNewOrderMessageForm form_5 = new SendNewOrderMessageForm();
String[] driverContent = new String[list.size()];
for (int i = 0; i < list.size(); i++) {
   HashMap one = list.get(i);
   String driverId = MapUtil.getStr(one, "driverId");
   String distance = MapUtil.getStr(one, "distance");
   distance = new BigDecimal(distance).setScale(1, RoundingMode.CEILING).toString();
   driverContent[i] = driverId + "#" + distance;
}
form_5.setDriversContent(driverContent);
form_5.setDriversContent(driverContent);
form_5.setOrderId(Long.parseLong(orderId));
form_5.setFrom(startPlace);
form_5.setTo(endPlace);
form_5.setExpectsFee(expectsFee);
//里程转化成保留小数点后一位
mileage = new BigDecimal(mileage).setScale(1, RoundingMode.CEILING).toString();
form_5.setMileage(mileage);
form_5.setMinute(minute);
form_5.setFavourFee(favourFee);
snmServiceApi.sendNewOrderMessageAsync(form_5);
```
4. 把 hxds-tm、hxds-dr、hxds-cst、hxds-mps、bff-customer、bff-driver、hxds-odr、hxds-rule、hxds-snm、gateway 这些子系统都运行起来。
   用FastRequest插件提交司机实时定位，然后手机端运行乘客端小程序进行下单，如果下单没有问题，说明抢单消息已经发送给适合接单的司机了。
### 司机子系统开始与停止接单
1. 之前写过 bff-driver/src/main/controller/form/RemoveLocationCacheForm，略
   之前写过 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/MpsService#removeLocationCache，略
   之前写过 bff-driver/src/main/service/DriverLocationService#removeLocationCache 及其实现类，略。实现清空司机定位/上线缓存
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/ClearNewOrderQueueForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/SnmServiceApi.java#clearNewOrderQueue
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/NewOrderMessageService.java 及其实现类,实现清空消息队列
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/DriverController.java#startWork、stopWork
```java
@Data
@Schema(description = "清空新订单消息队列的表单")
public class ClearNewOrderQueueForm {

   @Schema(description = "用户ID")
   private Long userId;
   
}

@PostMapping("/message/order/new/clearNewOrderQueue")
public R clearNewOrderQueue(ClearNewOrderQueueForm form);

void clearNewOrderQueue(ClearNewOrderQueueForm form);

@Override
public void clearNewOrderQueue(ClearNewOrderQueueForm form) {
   snmServiceApi.clearNewOrderQueue(form);
}

@PostMapping("/startWork")
@Operation(summary = "开始接单")
@SaCheckLogin
public R startWork() {
   long driverId = StpUtil.getLoginIdAsLong();
   RemoveLocationCacheForm removeCacheForm = new RemoveLocationCacheForm();
   removeCacheForm.setDriverId(driverId);
   driverLocationService.removeLocationCache(removeCacheForm);
   ClearNewOrderQueueForm clearQueueForm = new ClearNewOrderQueueForm();
   clearQueueForm.setUserId(driverId);
   newOrderMessageService.clearNewOrderQueue(clearQueueForm);
   return R.ok();
}
@PostMapping("/stopWork")
@Operation(summary = "停止接单")
@SaCheckLogin
public R stopWork() {
   long driverId = StpUtil.getLoginIdAsLong();
   RemoveLocationCacheForm removeCacheForm = new RemoveLocationCacheForm();
   removeCacheForm.setDriverId(driverId);
   driverLocationService.removeLocationCache(removeCacheForm);
   ClearNewOrderQueueForm clearQueueForm = new ClearNewOrderQueueForm();
   clearQueueForm.setUserId(driverId);
   newOrderMessageService.clearNewOrderQueue(clearQueueForm);
   return R.ok();
}
```
2. 把 hxds-tm、hxds-dr、hxds-odr、hxds-mps、hxds-snm、bff-driver 子系统运行起来，然后测试 Web 方法
3. 写 hxds-driver-wx/pages/workbench/workbench.vue#onLoad 填充地图控件
   写 hxds-driver-wx/pages/workbench/workbench.vue#onShow 修改地图控件为实时位置
   写 hxds-driver-wx/pages/workbench/workbench.vue#onHide 关闭实时定位
   写 hxds-driver-wx/pages/workbench/workbench.vue#returnLocationHandle 实现地图复位
   写 hxds-driver-wx/pages/workbench/main.js 定义全局变量
   写 hxds-driver-wx/pages/workbench/workbench.vue#startWorkHandle 实现开始接单
   写 hxds-driver-wx/pages/workbench/workbench.vue#stopWorkHandle 实现停止接单
   【拓展】[uni.$off](https://uniapp.dcloud.net.cn/tutorial/page.html#%E9%A1%B5%E9%9D%A2%E9%80%9A%E8%AE%AF)
```javascript
onLoad: function() {
  let that = this;
  if(!that.reviewAuth){
      let windowHeight = uni.getSystemInfoSync().windowHeight;
      that.windowHeight = windowHeight;
  }
  // TODO 查询正在执行的订单
  
  // 初始化地图
  that.map = uni.createMapContext('map');
},

// 实时获取定位
uni.$on('updateLocation', function(location) {
   // console.log(location)
   if(location != null){
      that.service.locationIcon = '../../static/workbench/service-icon-1.png';
      that.service.locationText = '定位正常';
      that.service.locationStyle = 'color:#46B68F';
      that.latitude = location.latitude;
      that.longitude = location.longitude;
   }else{
      that.service.locationIcon = '../../static/workbench/service-icon-5.png';
      that.service.locationText = '定位失败';
      that.service.locationStyle = 'color:#FF4D4D';
   }
});
let workStatus = uni.getStorageSync('workStatus');
// TODO：判断工作状态，是否实时轮训接单

// 初始化控件高度
if(['接客户', '到达代驾点', '开始代驾'].includes(workStatus)){
   that.contentStyle = `width: 750rpx;height:${that.windowHeight - 200 - 0}px;`;
} else {
   that.contentStyle = `width: 750rpx;height:${that.windowHeight - 200 - 70}px;`;
}

onHide: function() {
   uni.$off('updateLocation');
}

returnLocationHandle: function(){
   this.map.moveToLocation();
}

startWork: `${baseUrl}/driver/startWork`,
stopWork: `${baseUrl}/driver/stopWork`,
receiveNewOrderMessage: `${baseUrl}/message/order/new/receiveNewOrderMessage`,
        
startWorkHandle: function() {
   let that = this;
   /*
    * TODO 检查司机是否可以接单
    * 1.没有被禁止接单
    * 2.没有未缴纳的罚款
    * 3.当天已经做了人脸验证
    */
   //设置司机当天通过身份验证
   uni.setStorageSync('verification', { result: true, date: dayjs().format('YYYY-MM-DD') });

   uni.showModal({
      title: '提示消息',
      content: '你要开始接收代驾订单信息？',
      success: function(resp) {
         if (resp.confirm) {
            uni.vibrateShort({});
            let audio = uni.createInnerAudioContext();
            that.audio = audio;
            audio.src = '/static/voice/voice_1.mp3';
            audio.play();
            that.$refs.uToast.show({
               title: '开始接单了',
               type: 'success',
               callback: function() {
                  that.ajax(that.url.startWork, 'POST', null, function(resp) {});
                  uni.setStorageSync('workStatus', '开始接单');
                  that.workStatus = '开始接单';
                  /*//初始化新订单和列表变量
                  that.newOrder = null;
                  that.newOrderList.length = 0;
                  that.executeOrder = {};
                  //创建接收新订单消息的定时器，每隔5秒钟接收一次新订单消息
                  if (that.reciveNewOrderTimer == null) {
                     that.reciveNewOrderTimer = that.createTimer(that);
                  }*/
               }
            });
         }
      }
   });
},
stopWorkHandle: function() {
   let that = this;
   uni.showModal({
      title: '提示消息',
      content: '你要停止接收代驾订单信息？',
      success: function(resp) {
         if (resp.confirm) {
            uni.vibrateShort({});
            if (that.audio != null) {
               that.audio.stop();
            }
            let audio = uni.createInnerAudioContext();
            that.audio = audio;
            audio.src = '/static/voice/voice_2.mp3';
            audio.play();
            that.$refs.uToast.show({
               title: '停止接单了',
               type: 'default',
               callback: function() {
                  that.ajax(that.url.stopWork, 'POST', null, function(resp) {});
                  that.workStatus = '停止接单';
                  uni.setStorageSync('workStatus', '停止接单');
                  /*//初始化新订单和列表变量
                  that.newOrder = null;
                  that.newOrderList.length = 0;
                  that.executeOrder = {};
                  //销毁接收新订单消息的定时器
                  clearInterval(that.reciveNewOrderTimer);
                  that.reciveNewOrderTimer = null;
                  that.playFlag = false;*/
               }
            });
         }
      }
   });
},
```
### 司机端RR方式接收新订单，语音引擎智能播报
现在已经能开启司机接单模式了，司机端小程序发起轮询请求查询订单信息，需要先把后端代码给实现了，然后才能在移动端用语音播报出来
1. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/ReceiveNewOrderMessageForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/SnmServiceApi.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/NewOrderMessageService 及其实现类
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/NewOrderMessageController.java
```java
@Data
@Schema(description = "接收新订单消息的表单")
public class ReceiveNewOrderMessageForm {
    @Schema(description = "用户ID")
    private Long userId;
}

@PostMapping("/message/order/new/receiveNewOrderMessage")
R receiveNewOrderMessage(ReceiveNewOrderMessageForm form);

List receiveNewOrderMessage(ReceiveNewOrderMessageForm form);

@Override
public List receiveNewOrderMessage(ReceiveNewOrderMessageForm form) {
   R r = snmServiceApi.receiveNewOrderMessage(form);
   List list = (List) r.get("result");
   return list;
}

@PostMapping("/receiveNewOrderMessage")
@Operation(summary = "同步接收新订单消息")
@SaCheckLogin
public R receiveNewOrderMessage(){
   long driverId = StpUtil.getLoginIdAsLong();
   ReceiveNewOrderMessageForm form=new ReceiveNewOrderMessageForm();
   form.setUserId(driverId);
   List list = newOrderMessageService.receiveNewOrderMessage(form);
   return R.ok().put("result",list);
}
```
2. 写 hxds-driver-wx/pages/workbench/workbench.vue#createTimer
   轮询接收新订单，这种情况限于开始接单后，从工作台页面进入到其他页面，然后又退回到工作台页面
   补充 hxds-driver-wx/pages/workbench/workbench.vue#onShow
   司机开始接单之后，我们要调用createTimer()函数，开始轮询新订单。停止接单之后，我们要销毁定时器，不在轮询新订单
   补充 hxds-driver-wx/pages/workbench/workbench.vue#startWorkHandle，上面写过把注释去掉即可
   补充 hxds-driver-wx/pages/workbench/workbench.vue#stopWorkHandle，上面写过把注释去掉即可
```javascript
// 在工作台页面，先把创建轮询定时器的代码给封装起来。因为是自定义函数，所以需要外部传入VUE对象
createTimer: function(ref) {
   let timer = setInterval(function() {
      ref.ajax(
              ref.url.receiveNewOrderMessage,
              'POST',
              null,
              function(resp) {
                 let result = resp.data.result;
                 if (result.length > 0) {
                    // console.log(result);
                    // 用新订单数组和原有订单数组拼接成新数组，新订单就放在数组开头
                    ref.newOrderList = result.concat(ref.newOrderList);
                    // 如果当前没有播放订单，就显示新订单
                    if (ref.playFlag == false) {
                       // TODO:调用封装函数
                       // ref.showNewOrder(ref);
                    }
                 }
              },
              false
      );
   }, 5000);
   return timer;
},

// 判断工作状态，是否实时轮训接单
that.reciveNewOrderTimer = that.createTimer(that);
```
3. 把 hxds-tm、hxds-dr、hxds-cst、hxds-mps、bff-customer、bff-driver、hxds-odr、hxds-rule、hxds-snm、gateway 这些子系统都运行起来。
   然后在手机端运行司机端小程序，开始接单之后，我们利用 Web 接口向 bff-customer 子系统发出创建订单的请求
   
4. 新订单显示在工作台页面，然后用语音引擎播报新订单的详情。司机端小程序项目引用了微信官方的同声传译插件，既可以把文本转换成语音，也可以把语音转换成文字。
   这里我们用前者功能，把新订单播报出来。大家可以参看官方提供的[详细文档](https://mp.weixin.qq.com/wxopen/plugindevdoc?appid=wx069ba97219f66d99&token=1202914355&lang=zh_CN)
   写 hxds-driver-wx/pages/workbench/workbench.vue#showNewOrder 实现显示和语音播报订单
   补充 hxds-driver-wx/pages/workbench/workbench.vue#createTimer
```javascript
showNewOrder: function(ref) {
   ref.playFlag = true;
   ref.canAcceptOrder = false;
   let order = ref.newOrderList.shift();
   let orderId = order.orderId;
   let distance = order.distance;
   let expectsFee = order.expectsFee;
   let favourFee = order.favourFee;
   let from = order.from;
   let to = order.to;
   let mileage = order.mileage;
   let minute = order.minute;
   ref.newOrder = {
       orderId: orderId,
       distance: distance,
       expectsFee: expectsFee,
       favourFee: favourFee,
       from: from,
       to: to,
       mileage: mileage,
       minute: minute
   };
   if (ref.settings.listenService) {
       let audio = uni.createInnerAudioContext();
       ref.audio = audio;
       plugin.textToSpeech({
           lang: 'zh_CN',
           tts: true,
           content: `即时订单，距离您${distance}公里，从${from}，到${to}，全程约${mileage}公里，预估代驾费${expectsFee}元`,
           success: function(resp) {
               audio.src = resp.filename;
               audio.play();
               audio.onEnded(function() {
                   ref.canAcceptOrder = true;
                   let verification = uni.getStorageSync('verification');
                   /*
                    * TODO 判断人脸识别凭证是否过期，如果过期就要重新人脸验证。
                    * 这是以防司机今天开始接单人脸验证通过，但是一直没有停止接单，
                    * 到了次日依然要重新做人脸验证。
                    *
                    */
                   if (false) {
                   } else {
                       if (ref.settings.autoAccept) {
                           let data = {
                               orderId: orderId
                           };
                           ref.ajax(
                               ref.url.acceptNewOrder,
                               'POST',
                               data,
                               function(resp) {
                                   let result = resp.data.result;
                                   if (result == '接单成功') {
                                       uni.showToast({
                                           title: '接单成功'
                                       });
                                       audio = uni.createInnerAudioContext();
                                       ref.audio = audio;
                                       audio.src = '/static/voice/voice_3.mp3';
                                       audio.play();
                                       audio.onEnded(function() {
                                           ref.ajax(ref.url.stopWork, 'POST', null, function(resp) {});
                                           //初始化新订单和列表变量
                                           ref.newOrder = null;
                                           ref.newOrderList.length = 0;
                                           ref.executeOrder.id = orderId;
                                           clearInterval(ref.reciveNewOrderTimer);
                                           ref.reciveNewOrderTimer = null;
                                           ref.playFlag = false;
                                           //隐藏了工作台页面底部操作条之后，需要重新计算订单执行View的高度
                                           ref.contentStyle = `width: 750rpx;height:${ref.windowHeight - 200 - 0}px;`;
                                           //加载订单执行数据
                                           ref.loadExecuteOrder(ref);
                                       });
                                   } else {
                                       //自动抢单失败
                                       audio = uni.createInnerAudioContext();
                                       ref.audio = audio;
                                       audio.src = '/static/voice/voice_4.mp3';
                                       audio.play();
                                       audio.onEnded(function() {
                                           ref.playFlag = false;
                                           if (ref.newOrderList.length > 0) {
                                               ref.showNewOrder(ref);
                                           } else {
                                               ref.newOrder = null;
                                           }
                                       });
                                   }
                               },
                               false
                           );
                       } else {
                           ref.playFlag = false;
                           setTimeout(function() {
                               //如果用户不是正在手动抢单中，就播放下一个新订单
                               if (!ref.accepting) {
                                   ref.canAcceptOrder = false;
                                   if (ref.newOrderList.length > 0) {
                                       ref.showNewOrder(ref); //递归调用
                                   } else {
                                       ref.newOrder = null;
                                   }
                               }
                           }, 3000);
                       }
                   }
               });
           },
           fail: function(resp) {
               console.error('语音订单播报失败', resp);
               ref.playFlag = false;
               ref.canAcceptOrder = true;
               ref.acception = false;
           }
       });
   }
},

// 调用封装函数
ref.showNewOrder(ref);
```
5. 执行第3步再次进行自测
### 订单微服务的智能抢单
1. 写 hxds-odr/src/main/resources/mapper/OrderDao.xml#acceptNewOrder 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/impl/OrderService.java#acceptNewOrder 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/AcceptNewOrderForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#acceptNewOrder
   【拓展】tb_order#status:1等待接单，2已接单，3司机已到达，4开始代驾，5结束代驾，6未付款，7已付款，8订单已结束，9顾客撤单，10司机撤单，11事故关闭，12其他
```java
int acceptNewOrder(Map param);

<update id="acceptNewOrder" parameterType="Map">
        UPDATE tb_order
        SET driver_id   = #{driverId},
        accept_time = NOW(),
        `status`    = 2
        WHERE id = #{orderId}
</update>

String acceptNewOrder(long driverId, long orderId);

@Override
@Transactional
@LcnTransaction
public String acceptNewOrder(long driverId, long orderId) {
    if (!redisTemplate.hasKey("order#" + orderId)) {
        return "抢单失败";
    }
    // 执行redis事务
    redisTemplate.execute(new SessionCallback() {
        @Override
        public Object execute(RedisOperations operations) throws DataAccessException {
            // 获取新订单记录的version
            operations.watch("order#" + orderId);
            // 本地缓存redis操作
            operations.multi();
            // 把新订单缓存的Value设置成抢单司机的id
            operations.opsForValue().set("order#" + orderId, driverId);
            // 执行redis事务，如果事务提交失败会自动抛出异常
            return operations.exec();
        }
    });
    // 抢单成功后，删除redis中的新订单，避免让其他司机参与抢单
    redisTemplate.delete("order#" + orderId);
    // 更新订单操作，添加上抢单司机id和接单时间
    HashMap param = new HashMap() {{
        put("orderId", orderId);
        put("driverId", driverId);
    }};
    int rows = orderDao.acceptNewOrder(param);
    if (rows != 1) {
        throw new HxdsException("接单失败，无法更新订单记录");
    }
    return "接单成功";
}

@Data
@Schema(description = "司机接单的表单")
public class AcceptNewOrderForm {
   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@PostMapping("/acceptNewOrder")
@Operation(summary = "司机接单")
public R acceptNewOrder(@RequestBody @Valid AcceptNewOrderForm form) {
   String result = orderService.acceptNewOrder(form.getDriverId(), form.getOrderId());
   return R.ok().put("result", result);
}
```
2. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/AcceptNewOrderForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/OdrServiceApi.java#acceptNewOrder
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/OrderService.java#acceptNewOrder 及其实现类
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/OrderController.java
```java
@Data
@Schema(description = "司机接单的表单")
public class AcceptNewOrderForm {

   @Schema(description = "司机ID")
   private Long driverId;

   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

}

@PostMapping("/order/acceptNewOrder")
R acceptNewOrder(AcceptNewOrderForm form);

@PostMapping("/order/acceptNewOrder")
R acceptNewOrder(AcceptNewOrderForm form);

@Override
@Transactional
@LcnTransaction
public String acceptNewOrder(AcceptNewOrderForm form) {
   R r = odrServiceApi.acceptNewOrder(form);
   String result = MapUtil.getStr(r, "result");
   return result;
}

@PostMapping("/acceptNewOrder")
@SaCheckLogin
@Operation(summary = "司机接单")
public R acceptNewOrder(@RequestBody @Valid AcceptNewOrderForm form) {
   long driverId = StpUtil.getLoginIdAsLong();
   form.setDriverId(driverId);
   String result = orderService.acceptNewOrder(form);
   return R.ok().put("result", result);
}
```
3. 补充 hxds-driver-wx/main.js 声明全局URL
   写 hxds-driver-wx/pages/workbench/workbench.vue#acceptHandle 实现手动抢单
```javascript
acceptNewOrder: `${baseUrl}/driver/acceptNewOrder`,

acceptHandle: function() {
   let that = this;
   if (!that.canAcceptOrder || that.accepting) {
      return;
   }
   that.accepting = true;
   uni.vibrateShort({});
   that.ajax(that.url.acceptNewOrder, 'POST', { orderId: that.newOrder.orderId }, function(resp) {
      let audio = uni.createInnerAudioContext();
      let result = resp.data.result;
      //手动抢单成功
      if (result == '接单成功') {
         uni.showToast({
            title: '接单成功'
         });
         that.audio = audio;
         audio.src = '/static/voice/voice_3.mp3';
         audio.play();
         audio.onEnded(function() {
            //停止接单
            that.audio = null;
            that.ajax(that.url.stopWork, 'POST', null, function(resp) {});
            //初始化新订单和列表变量
            that.executeOrder.id = that.newOrder.orderId;
            that.newOrder = null;
            that.newOrderList.length = 0;
            clearInterval(that.reciveNewOrderTimer);
            that.reciveNewOrderTimer = null;
            that.playFlag = false;
            that.accepting = false;
            that.canAcceptOrder = false;
            //隐藏了工作台页面底部操作条之后，需要重新计算订单执行View的高度
            that.contentStyle = `width: 750rpx;height:${that.windowHeight - 200 - 0}px;`;
            //加载订单执行数据
            that.loadExecuteOrder(that);
         });
      } else {
         that.audio = audio;
         audio.src = '/static/voice/voice_4.mp3';
         audio.play();
         that.playFlag = false;
         setTimeout(function() {
            that.accepting = false;
            that.canAcceptOrder = false;
            if (that.newOrderList.length > 0) {
               that.showNewOrder(that); //递归调用
            } else {
               that.newOrder = null;
            }
         }, 3000);
      }
   });
},
```
### 订单微服务加载执行订单

1. 写 hxds-odr/src/main/java/com/example/hxds/odr/db/dao/OrderDao.java#searchDriverExecuteOrder 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/impl/OrderService.java#searchDriverExecuteOrder 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/SearchDriverExecuteOrderForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#searchDriverExecuteOrder
```java
HashMap searchDriverExecuteOrder(Map param);

<select id="searchDriverExecuteOrder" parameterType="Map" resultType="HashMap">
   SELECT CAST(id AS CHAR)                              AS id,
      customer_id                                   AS customerId,
      start_place                                   AS startPlace,
      start_place_location                          AS startPlaceLocation,
      end_place                                     AS endPlace,
      end_place_location                            AS endPlaceLocation,
      CAST(favour_fee AS CHAR)                      AS favourFee,
      car_plate                                     AS carPlate,
      car_type                                      AS carType,
      DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
   FROM tb_order
   WHERE id = #{orderId}
     AND driver_id = #{driverId}
</select>

HashMap searchDriverExecuteOrder(Map param);

@Override
public HashMap searchDriverExecuteOrder(Map param) {
     HashMap map = orderDao.searchDriverExecuteOrder(param);
     return map;
}

@Data
@Schema(description = "查询司机正在执行的订单")
public class SearchDriverExecuteOrderForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
}

@PostMapping("/searchDriverExecuteOrder")
@Operation(summary = "查询司机正在执行的订单记录")
public R searchDriveExecutorOrder(@RequestBody @Valid SearchDriverExecuteOrderForm form) {
   Map param = BeanUtil.beanToMap(form);
   HashMap map = orderService.searchDriverExecuteOrder(param);
   return R.ok().put("result", map);
}
```
2. 写 hxds-cst/src/main/resources/mapper/CustomerDao.xml#searchCustomerInfoInOrder 及其对应接口
   写 hxds-cst/src/main/java/com/example/hxds/cst/service/CustomerService.java 及其实现类
   写 hxds-cst/src/main/java/com/example/hxds/cst/controller/form/SearchCustomerInfoInOrderForm.java
   写 hxds-cst/src/main/java/com/example/hxds/cst/controller/CustomerController.java
```java
HashMap searchCustomerInfoInOrder(long customerId);

<select id="searchCustomerInfoInOrder" parameterType="long" resultType="HashMap">
  SELECT IF(sex = '男', '先生', '女士') AS title,
         tel,
         photo
  FROM tb_customer
  WHERE id = #{customerId}
</select>

HashMap searchCustomerInfoInOrder(long customerId);

@Override
public HashMap searchCustomerInfoInOrder(long customerId) {
     HashMap map = customerDao.searchCustomerInfoInOrder(customerId);
     return map;
}

@Data
@Schema(description = "查询订单中的客户信息")
public class SearchCustomerInfoInOrderForm {

   @NotNull(message = "customerId不能为空")
   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;
}

@PostMapping("/searchCustomerInfoInOrder")
@Operation(summary = "查询订单中的客户信息")
public R searchCustomerInfoInOrder(@RequestBody @Valid SearchCustomerInfoInOrderForm form) {
   HashMap map = customerService.searchCustomerInfoInOrder(form.getCustomerId());
   return R.ok().put("result", map);
}
```
2. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/SearchDriverExecuteOrderForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/OdrServiceApi.java#searchDriverExecuteOrder
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/SearchCustomerInfoInOrderForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/CstServiceApi.java#searchCustomerInfoInOrder
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/OrderService.java 及其实现类
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/OrderController.java#searchDriverExecuteOrder
```java
@Data
@Schema(description = "查询司机正在执行的订单")
public class SearchDriverExecuteOrderForm {
    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "司机ID")
    private Long driverId;
}

@PostMapping("/order/searchDriverExecuteOrder")
R searchDriverExecuteOrder(SearchDriverExecuteOrderForm form);

@Data
@Schema(description = "查询订单中的客户信息")
public class SearchCustomerInfoInOrderForm {

   @NotNull(message = "customerId不能为空")
   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;
}

@PostMapping("/customer/searchCustomerInfoInOrder")
R searchCustomerInfoInOrder(SearchCustomerInfoInOrderForm form);

HashMap searchDriverExecuteOrder(SearchDriverExecuteOrderForm form);

@Override
public HashMap searchDriverExecuteOrder(SearchDriverExecuteOrderForm form) {
   // 查询订单信息
   R r = odrServiceApi.searchDriverExecuteOrder(form);
   HashMap orderMap = (HashMap) r.get("result");

   // 查询代驾客户信息
   Long customerId = MapUtil.getLong(orderMap, "customerId");
   SearchCustomerInfoInOrderForm customerInfoInOrderForm = new SearchCustomerInfoInOrderForm();
   customerInfoInOrderForm.setCustomerId(customerId);
   r = cstServiceApi.searchCustomerInfoInOrder(customerInfoInOrderForm);
   HashMap cstMap = (HashMap) r.get("result");

   HashMap map = new HashMap();
   map.putAll(orderMap);
   map.putAll(cstMap);
   return map;
}

@PostMapping("/searchDriverExecuteOrder")
@SaCheckLogin
@Operation(summary = "查询司机正在执行的订单记录")
public R searchDriverExecuteOrder(@RequestBody @Valid SearchDriverExecuteOrderForm form) {
   long driverId = StpUtil.getLoginIdAsLong();
   form.setDriverId(driverId);
   HashMap map = orderService.searchDriverExecuteOrder(form);
   return R.ok().put("result", map);
}
```
3. 写 hxds-driver-wx/main.js
   写 hxds-driver-wx/pages/workbench/workbench.vue#loadExecuteOrder 在自动抢单和手动抢单的中调用该函数，只要抢单成功就加载正在执行订单的数据
   写 hxds-driver-wx/pages/workbench/workbench.vue#callCustomerHandle、callServiceHandle 实现拨打代驾客户和客服手机
```javascript
searchDriverExecuteOrder: `${baseUrl}/driver/searchDriverExecuteOrder`,

loadExecuteOrder: function(ref) {
   let data = { orderId: ref.executeOrder.id };
   this.ajax(ref.url.searchDriverExecuteOrder, 'POST', data, function(resp){
      let result = resp.data.result;
      ref.executeOrder = {
         id: ref.executeOrder.id,
         photo: result.photo,
         title: result.title,
         tel: result.tel,
         customerId: result.customerId,
         startPlace: result.startPlace,
         startPlaceLocation: JSON.parse(result.startPlaceLocation),
         endPlace: result.endPlace,
         endPlaceLocation: JSON.parse(result.endPlaceLocation),
         favourFee: result.favourFee,
         carPlate: result.carPlate,
         carType: result.carType,
         createTime: result.createTime
      };
      ref.workStatus = '接客户';
      uni.setStorageSync('workStatus', '接客户');
      uni.setStorageSync('executeOrder', ref.executeOrder);
   })
},
callCustomerHandle: function() {
   let that = this;
   uni.makePhoneCall({
      phoneNumber: that.executeOrder.tel
   });
},
callServiceHandle: function() {
   let that = this;
   uni.makePhoneCall({
      phoneNumber: '10086'
   });
},
```
### 乘客端RR轮询司机接单
1. 写 hxds-odr/src/main/java/com/example/hxds/odr/db/dao/OrderDao.java 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java#searchOrderStatus、deleteUnAcceptOrder 及其实现类
   修改 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java#insertOrder
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/SearchOrderStatusForm.java、DeleteUnAcceptOrderForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#searchOrderStatus、deleteUnAcceptOrder
   【说明】因为乘客端的等待司机接单的倒计时为15分钟，现在后端Redis里面的抢单缓存也是15分钟。如果移动端倒计时到15分钟恰好结束，这时候发起Ajax请求取消订单，那么就会给后端程序带来困难，因为在Redis里面已经不存在抢单缓存了，后端程序无法判断到底是司机抢单成功后删除了抢单缓存，还是因为没有司机抢单，缓存过期自动删除了。

有的同学觉得我们查询数据库的订单状态不就知道有没有人接单了么？这是不行的。因为恰好司机抢单成功之后，修改了订单的状态为2，但是事务还没来的及提交，这时候关闭订单的Ajax请求发过来了。Java程序通过查询数据库，发现订单的状态是1，以为没有司机接单，实际上已经有司机接单了。

为了避免以上的情况，我们把Redis抢单缓存延长到16分钟。如果关闭订单的Ajax请求发送给后端，这时候抢单缓存还存在，说明没有司机抢单，那么我们就删除抢单缓存和订单记录就可以了。如果抢单缓存不存在，说明已经有司机抢单成功了，这时候我们返回给乘客端关闭订单失败，已有司机抢单成功即可。
```java
Integer searchOrderStatus(Map param);

int deleteUnAcceptOrder(Map param);

<select id="searchOrderStatus" parameterType="Map" resultType="Integer">
        SELECT status
        FROM tb_order
        WHERE id = #{orderId}
   <if test="driverId!=null">
           AND driver_id = #{driverId}
   </if>
   <if test="customerId!=null">
           AND customer_id = #{customerId}
   </if>
</select>

<delete id="deleteUnAcceptOrder" parameterType="Map">
        DELETE FROM tb_order
        WHERE id = #{orderId}
        AND `status` = 1
   <if test="driverId!=null">
           AND driver_id = #{driverId}
   </if>
   <if test="customerId!=null">
           AND customer_id = #{customerId}
   </if>
</delete>

Integer searchOrderStatus(Map param);

String deleteUnAcceptOrder(Map param);

@Override
public Integer searchOrderStatus(Map param) {
   Integer status = orderDao.searchOrderStatus(param);
   if (status == null)
       throw new HxdsException("没有查询到数据，请核对查询条件");
   return status;
}

@Override
@Transactional
@LcnTransaction
public String deleteUnAcceptOrder(Map param) {
     Long orderId = MapUtil.getLong(param, "orderId");
     if (!redisTemplate.hasKey("order#" + orderId)) {
        return "订单取消失败";
     }
     redisTemplate.execute(new SessionCallback() {
        @Override
        public Object execute(RedisOperations operations) throws DataAccessException {
           operations.watch("order#" + orderId);
           operations.multi();
           operations.opsForValue().set("order#" + orderId, "none");
           return operations.exec();
        }
     });
     redisTemplate.delete("order#" + orderId);
     int rows = orderDao.deleteUnAcceptOrder(param);
     if(rows != 1){
        return "订单取消失败";
     }
     return "订单取消成功";
}

redisTemplate.expire("order#" + id, 16, TimeUnit.MINUTES);

@Data
@Schema(description = "查询订单状态的表单")
public class SearchOrderStatusForm {
   @NotNull(message = "orderId不能为空")
   @Schema(description = "订单ID")
   private Long orderId;

   @Min(value = 0, message = "driverId不能小于0")
   @Schema(description = "司机ID")
   private Long driverId;

   @Min(value = 0, message = "customerId不能小于0")
   @Schema(description = "乘客ID")
   private Long customerId;
}

@Data
@Schema(description = "更新订单状态的表单")
public class DeleteUnAcceptOrderForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1,message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @Min(value = 0, message = "driverId不能小于0")
   @Schema(description = "司机ID")
   private Long driverId;

   @Min(value = 0, message = "customerId不能小于0")
   @Schema(description = "乘客ID")
   private Long customerId;

}

@PostMapping("/searchOrderStatus")
@Operation(summary = "查询订单状态")
public R searchOrderStatus(@RequestBody @Valid SearchOrderStatusForm form) {
   Map param = BeanUtil.beanToMap(form);
   Integer status = orderService.searchOrderStatus(param);
   return R.ok().put("result", status);
}

@PostMapping("/deleteUnAcceptOrder")
@Operation(summary = "删除没有司机接单的订单")
public R deleteUnAcceptOrder(@RequestBody @Valid DeleteUnAcceptOrderForm form) {
   Map param = BeanUtil.beanToMap(form);
   String result = orderService.deleteUnAcceptOrder(param);
   return R.ok().put("result", result);
}
```
2. 写 bff-customer/src/main/java/com/example/hxds/bff/driver/controller/form/SearchOrderStatusForm.java、DeleteUnAcceptOrderForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/driver/service/OrderService.java#searchOrderStatus、deleteUnAcceptOrder 及其实现类
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/OrderController.java#searchOrderStatus、deleteUnAcceptOrder
```java
@Data
@Schema(description = "查询订单状态的表单")
public class SearchOrderStatusForm {
   @NotNull(message = "orderId不能为空")
   @Schema(description = "订单ID")
   private Long orderId;

   @Min(value = 0, message = "customerId不能小于0")
   @Schema(description = "乘客ID")
   private Long customerId;
}

@Data
@Schema(description = "更新订单状态的表单")
public class DeleteUnAcceptOrderForm {
   @NotNull(message = "orderId不能为空")
   @Schema(description = "订单ID")
   private Long orderId;

   @Min(value = 0, message = "customerId不能小于0")
   @Schema(description = "乘客ID")
   private Long customerId;
}

@PostMapping("/order/searchOrderStatus")
R searchOrderStatus(SearchOrderStatusForm form);

@PostMapping("/order/deleteUnAcceptOrder")
R DeleteUnAcceptOrder(DeleteUnAcceptOrderForm form);

Integer searchOrderStatus(SearchOrderStatusForm form);

String deleteUnAcceptOrder(DeleteUnAcceptOrderForm form);

@Override
public Integer searchOrderStatus(SearchOrderStatusForm form) {
   R r = odrServiceApi.searchOrderStatus(form);
   Integer result = MapUtil.getInt(r, "result");
   return result;
}

@Override
@Transactional
@LcnTransaction
public String deleteUnAcceptOrder(DeleteUnAcceptOrderForm form) {
   R r = odrServiceApi.deleteUnAcceptOrder(form);
   String result = MapUtil.getStr(r, "result");
   return result;
}

@PostMapping("/searchOrderStatus")
@Operation(summary = "查询订单状态")
@SaCheckLogin
public R searchOrderStatus(@RequestBody @Valid SearchOrderStatusForm form) {
   long customerId = StpUtil.getLoginIdAsLong();
   form.setCustomerId(customerId);
   Integer status = orderService.searchOrderStatus(form);
   return R.ok().put("result", status);
}

@PostMapping("/deleteUnAcceptOrder")
@Operation(summary = "关闭没有司机接单的订单")
@SaCheckLogin
public R deleteUnAcceptOrder(@RequestBody @Valid DeleteUnAcceptOrderForm form) {
   long customerId = StpUtil.getLoginIdAsLong();
   form.setCustomerId(customerId);
   String result = orderService.deleteUnAcceptOrder(form);
   return R.ok().put("result", result);
}
```
3. 写 hxds-customer-wx/main.js
   补充 hxds-customer-wx/pages/create_order/create_order.vue#createOrderHandle
   写 hxds-customer-wx/pages/create_order/create_order.vue#searchOrderStatus、deleteUnAcceptOrder 把查询订单状态的代码封装起来，这样每隔5秒钟轮询的时候，以及倒计时结束取消订单失败后，再次查询订单状态时候，都要调用这个封装函数。同理，也对关闭没有司机接单的订单函数进行封装
   写 hxds-customer-wx/pages/create_order/create_order.vue#countChangeHandle、countEndHandle、cancelHandle
```javascript
searchOrderStatus: `${baseUrl}/order/searchOrderStatus`,
deleteUnAcceptOrder: `${baseUrl}/order/deleteUnAcceptOrder`,

// 此处应该是15*60，但是测试中我们等不了15分钟,每隔5分钟发送一次查询接单情况的请求
that.timestamp = 60;
that.$refs.uCountDown.start();

// 修改模型层
timestamp: 60,

searchOrderStatus: function(ref) {
   let data = {
      orderId: ref.orderId
   };
   ref.ajax(
           ref.url.searchOrderStatus,
           'POST',
           data,
           function(resp) {
              if (resp.data.result == 2) {
                 ref.showPopup = false;
                 ref.timestamp = null;
                 uni.showToast({
                    icon: 'success',
                    title: '司机已接单'
                 });
                 uni.vibrateShort({});
                 setTimeout(function() {
                    uni.redirectTo({
                       url: '../move/move?orderId=' + ref.orderId
                    });
                 }, 3000);
              }
           },
           false
   );
},
deleteUnAcceptOrder: function(ref) {
   ref.showPopup = false;
   ref.timestamp = null;
   let data = {
      orderId: ref.orderId
   };
   ref.ajax(ref.url.deleteUnAcceptOrder, 'POST', data, function(resp) {
      let result = resp.data.result;
      console.log(result);
      if (result == '订单取消成功') {
         uni.showToast({
            icon: 'success',
            title: '订单取消成功'
         });
         setTimeout(function() {
            uni.redirectTo({
               url: '../workbench/workbench'
            });
         }, 3000);
      } else {
         ref.searchOrderStatus(ref);
      }
   });
},
countChangeHandle: function(s) {
   let that = this;
   if (s != 0 && s % 5 == 0) {
      that.searchOrderStatus(that);
   }
},
countEndHandle: function() {
   let that = this;
   that.deleteUnAcceptOrder(that);
},
cancelHandle: function() {
   let that = this;
   that.deleteUnAcceptOrder(that);
}
```
4. 把 hxds-tm、hxds-dr、hxds-mps、hxds-cst、hxds-rule、hxds-snm、hxds-odr、bff-driver、bff-customer、gateway 这些子系统都启动了
   记得调用 http://127.0.0.1:8101/driver/location/updateLocationCache 更新近距离的司机地理位置以防提示`没有适合接单的司机`
   乘客端下单，然后没有司机接单，我们等待倒计时结束，看看能否自动关闭订单。然后重新创建订单，没有司机接单的情况下，我们手动取消订单。
   最后我们再创建订单，然后有司机接单，我们看看轮询的结果能不能弹出订单已被接单的提示消息