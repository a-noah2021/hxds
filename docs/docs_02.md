## 订单执行与安全监控

乘客下单后，司机端和乘客端都会有司乘同显功能。司机赶往代驾点和代驾线路都会实时显示，偏航后自动重新生成线路。代驾过程中，司机端使用同声传译技术，把录制的音频转换成对话本文，然后将音频和文本分时上传服务端。对话文本被保存到HBase大数据平台，录音被保存到私有云空间

### 订单微服务，司机端加载执行的订单
1. 写 hxds-odr/src/main/resources/mapper/OrderDao.xml#searchDriverCurrentOrder 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java#searchDriverCurrentOrder 及其对应实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/SearchDriverCurrentOrderForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#searchDriverCurrentOrder
```java
<select id="searchDriverCurrentOrder" parameterType="long" resultType="HashMap">
    SELECT CAST(id AS CHAR)                              AS id,
           customer_id                                   AS customerId,
           start_place                                   AS startPlace,
           start_place_location                          AS startPlaceLocation,
           end_place                                     AS endPlace,
           end_place_location                            AS endPlaceLocation,
           CAST(favour_fee AS CHAR)                      AS favourFee,
           car_plate                                     AS carPlate,
           car_type                                      AS carType,
           DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime,
           `status`
    FROM tb_order
    WHERE driver_id = #{driverId}
      AND `status` IN (2, 3, 4) LIMIT 1
</select>

HashMap searchDriverCurrentOrder(long driverId);

@Override
public HashMap searchDriverCurrentOrder(long driverId) {
     HashMap map = orderDao.searchDriverCurrentOrder(driverId);
     return map;
}

@Data
@Schema(description = "查询司机当前订单的表单")
public class SearchDriverCurrentOrderForm {
   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
}

@PostMapping("/searchDriverCurrentOrder")
@Operation(summary = "查询司机当前订单")
public R searchDriverCurrentOrder(@RequestBody @Valid SearchDriverCurrentOrderForm form) {
   HashMap map = orderService.searchDriverCurrentOrder(form.getDriverId());
   return R.ok().put("result", map);
}
```
2. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/SearchDriverCurrentOrderForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/OdrServiceApi.java#searchDriverCurrentOrder
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/OrderService.java#searchDriverCurrentOrder 及其实现类
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/OrderController.java#searchDriverCurrentOrder
```java
@Data
@Schema(description = "查询司机当前订单的表单")
public class SearchDriverCurrentOrderForm {

    @Schema(description = "司机ID")
    private Long driverId;
}

@PostMapping("/order/searchDriverCurrentOrder")
R searchDriverCurrentOrder(SearchDriverCurrentOrderForm form);

HashMap searchDriverCurrentOrder(SearchDriverCurrentOrderForm form);

@PostMapping("/searchDriverCurrentOrder")
@SaCheckLogin
@Operation(summary = "查询司机当前订单")
public R searchDriverCurrentOrder(@RequestBody @Valid SearchDriverCurrentOrderForm form) {
   long driverId = StpUtil.getLoginIdAsLong();
   form.setDriverId(driverId);
   HashMap map = orderService.searchDriverCurrentOrder(form);
   return R.ok().put("result", map);
}
```
3. 补充 hxds-driver-wx/pages/workbench/workbench.vue#onLoad
   补充 hxds-driver-wx/pages/workbench/workbench.vue#onHide
```javascript
// 查询正在执行的订单
that.ajax(
        that.url.searchDriverCurrentOrder,
        'POST',
        null,
        function(resp) {
           if (resp.data.hasOwnProperty('result')) {
              let result = resp.data.result;
              that.executeOrder = {
                 id: result.id,
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
              let map = {
                 '2': '接客户',
                 '3': '到达代驾点',
                 '4': '开始代驾'
              };
              that.contentStyle = `width: 750rpx;height:${that.windowHeight - 200 - 0}px;`;
              that.workStatus = map[result.status + ''];
              uni.setStorageSync('workStatus', that.workStatus);
              uni.setStorageSync('executeOrder', that.executeOrder);
              // console.log(that.workStatus);
              if (that.workStatus == '开始代驾') {
                 that.recordManager.start({ duration: 20 * 1000, lang: 'zh_CN' });
              }
           }
        },
        false
);

 onHide: function() {
     uni.$off('updateLocation');
     // 当小程序挂起或者离开工作台页面，都要停止轮询定时器，清理变量
     this.newOrder = null;
     this.newOrderList.length = 0;
     if(this.audio != null){
         this.audio.stop();
         this.audio = null;
     }
     clearInterval(this.reciveNewOrderTimer);
     this.reciveNewOrderTimer = null;
     this.playFlag = false;
 }
```
4. 把 hxds-tm、hxds-dr、hxds-cst、hxds-odr、bff-driver 子系统启动起来，然后在司机端小程序上进入到工作台页面，看看能不能加载出来正在执行的订单
### 订单微服务，乘客端加载执行的订单
1. 写 hxds-odr/src/main/resources/mapper/OrderBillDao.xml#deleteUnAcceptOrderBill 及其对应接口
   补充 hxds-odr/src/main/java/com/example/hxds/odr/service/impl/OrderServiceImpl.java#deleteUnAcceptOrder
   写 hxds-odr/src/main/java/com/example/hxds/odr/config/RedisConfiguration.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/config/KeyExpiredListener.java
   补充 hxds-odr/src/main/java/com/example/hxds/odr/service/impl/OrderServiceImpl.java#searchOrderStatus
   补充 hxds-customer-wx/pages/create_order/create_order.vue#searchOrderStatus
   

【说明】为了不干扰我们测试程序，首先我们把订单表和账单表所有的记录都给清空。我们把抢单缓存设置成超时30秒，然后像之前一样，把各子系统运行起来。乘客端小程序下单之后，就立即关闭小程序，然后等待30秒钟，看看订单和账单记录能不能都删除掉。测试完程序，你要把抢单缓存改回16分钟。

现在还有一种情况需要我们动脑子认真想想，比如说乘客下单成功之后，等待了5分钟，微信就闪退了。过了5分钟之后，他重新登录小程序。因为抢单缓存还没有被销毁，而且订单和账单记录也都在，小程序跳转到create_order.vue页面，重新从15分钟开始倒计时，但是倒计时过程中，抢单缓存会超时被销毁，同时订单和账单记录也都删除了。这时候乘客端小程序发来轮询请求，业务层发现倒计时还没结束，但是抢单缓存就没有了，说明有司机抢单了，于是就跳转到司乘同显页面，这明显是不对的。

于是我们要改造OrderServiceImpl类中的代码，把抛出异常改成返回状态码为0，在移动端轮询的时候如果发现状态码是0，说明订单已经被关闭了。所以就弹出提示消息即可
```java
int deleteUnAcceptOrderBill(long orderId);

<delete id="deleteUnAcceptOrderBill" parameterType="long">
        DELETE FROM tb_order_bill WHERE order_id = #{orderId}
</delete>

rows = orderBillDao.deleteUnAcceptOrderBill(orderId);
if(rows != 1){
     return "订单取消失败";
}

@Configuration
public class RedisConfiguration {

   @Resource
   private RedisConnectionFactory redisConnectionFactory;

   /**
    * 自定义Redis队列的名字，如果有缓存销毁，就自动往这个队列中发消息
    * 每个子系统有各自的Redis逻辑库，订单子系统不会监听到其他子系统缓存数据销毁
    */
   @Bean
   public ChannelTopic expiredTopic() {
      return new ChannelTopic("__keyevent@5__:expired");  // 选择5号数据库
   }

   @Bean
   public RedisMessageListenerContainer redisMessageListenerContainer() {
      RedisMessageListenerContainer redisMessageListenerContainer = new RedisMessageListenerContainer();
      redisMessageListenerContainer.setConnectionFactory(redisConnectionFactory);
      return redisMessageListenerContainer;
   }
}

@Slf4j
@Component
public class KeyExpiredListener extends KeyExpirationEventMessageListener {

   @Resource
   private OrderDao orderDao;

   @Resource
   private OrderBillDao orderBillDao;

   public KeyExpiredListener(RedisMessageListenerContainer listenerContainer) {
      super(listenerContainer);
   }

   @Override
   @Transactional
   public void onMessage(Message message, byte[] pattern) {
      // 从消息队列中接收消息
      if(new String(message.getChannel()).equals("__keyevent@5__:expired")){
         // 反系列化Key，否则出现乱码
         JdkSerializationRedisSerializer serializer=new JdkSerializationRedisSerializer();
         String key = serializer.deserialize(message.getBody()).toString();
         if(key.contains("order#")){
            long orderId = Long.parseLong(key.split("#")[1]);
            HashMap param=new HashMap(){{
               put("orderId",orderId);
            }};
            int rows = orderDao.deleteUnAcceptOrder(param);
            if(rows==1){
               log.info("删除了无人接单的订单：" + orderId);
            }
            rows = orderBillDao.deleteUnAcceptOrderBill(orderId);
            if(rows==1){
               log.info("删除了无人接单的账单：" + orderId);
            }
         }
      }
      super.onMessage(message, pattern);
   }
}

if (status == null) {
    // throw new HxdsException("没有查询到数据，请核对查询条件");
    status = 0;
}

else if (resp.data.result == 0) {
   ref.showPopup = false;
   ref.timestamp = null;
   uni.showToast({
      icon: 'success',
      title: '订单已经关闭'
   });
}
```
2. 写 hxds-odr/src/main/resources/mapper/OrderDao.xml#hasCustomerUnFinishedOrder、hasCustomerUnAcceptOrder 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/impl/OrderService.java#hasCustomerCurrentOrder 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/HasCustomerCurrentOrderForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#hasCustomerCurrentOrder
   【说明】在OrderDao.xml文件中，定义两个SQL语句，分别查询没有司机接单的订单和没有完成的订单。因为在小程序上面要根据订单的状态，决定跳转到什么页面。
   如果存在没有司机接单的订单，小程序就跳转到create_order.vue页面，重新开始倒计时。如果存在未完成的订单，就跳转到司乘同显页面
```java
long hasCustomerUnFinishedOrder(long customerId);

HashMap hasCustomerUnAcceptOrder(long customerId);

<select id="hasCustomerUnFinishedOrder" parameterType="long" resultType="Long">
        SELECT CAST(id AS CHAR) AS id
        FROM tb_order
        WHERE customer_id = #{customerId}
        AND `status` IN (2, 3, 4) LIMIT 1;
</select>

<select id="hasCustomerUnAcceptOrder" parameterType="long" resultType="HashMap">
        SELECT CAST(id AS CHAR)     AS id,
        start_place          AS startPlace,
        start_place_location AS startPlaceLocation,
        end_place            AS endPlace,
        end_place_location   AS endPlaceLocation,
        car_plate            AS carPlate,
        car_type             AS carType
        FROM tb_order
        WHERE customer_id = #{customerId}
        AND `status` = 1 LIMIT 1;
</select>

HashMap hasCustomerCurrentOrder(long customerId);

@Override
public HashMap hasCustomerCurrentOrder(long customerId) {
     HashMap result = new HashMap();
     HashMap map = orderDao.hasCustomerUnAcceptOrder(customerId);
     result.put("hasCustomerUnAcceptOrder", map != null);
     result.put("unAcceptOrder", map);
     Long id = orderDao.hasCustomerUnFinishedOrder(customerId);
     result.put("hasCustomerUnFinishedOrder", id != null);
     result.put("unFinishedOrder", id);
     return result;
}

@Data
@Schema(description = "查询乘客是否存在当前的订单的表单")
public class HasCustomerCurrentOrderForm {
   @NotNull(message = "customerId不能为空")
   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;
}

@PostMapping("/hasCustomerCurrentOrder")
@Operation(summary = "查询乘客是否存在当前的订单")
public R hasCustomerCurrentOrder(@RequestBody @Valid HasCustomerCurrentOrderForm form) {
   HashMap map = orderService.hasCustomerCurrentOrder(form.getCustomerId());
   return R.ok().put("result", map);
}
```
3. 写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/HasCustomerCurrentOrderForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/OdrServiceApi.java#hasCustomerCurrentOrder
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/service/OrderService.java#hasCustomerCurrentOrder 及其实现类
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/OrderController.java#hasCustomerCurrentOrder
```java
@Data
@Schema(description = "查询乘客是否存在当前的订单的表单")
public class HasCustomerCurrentOrderForm {

   @Schema(description = "客户ID")
   private Long customerId;
}

@PostMapping("/order/hasCustomerCurrentOrder")
R hasCustomerCurrentOrder(HasCustomerCurrentOrderForm form);

HashMap hasCustomerCurrentOrder(HasCustomerCurrentOrderForm form);

@Override
public HashMap hasCustomerCurrentOrder(HasCustomerCurrentOrderForm form) {
   R r = odrServiceApi.hasCustomerCurrentOrder(form);
   HashMap map = (HashMap) r.get("result");
   return map;
}

@PostMapping("/hasCustomerCurrentOrder")
@SaCheckLogin
@Operation(summary = "查询乘客是否存在当前的订单")
public R hasCustomerCurrentOrder() {
   long customerId = StpUtil.getLoginIdAsLong();
   HasCustomerCurrentOrderForm form = new HasCustomerCurrentOrderForm();
   form.setCustomerId(customerId);
   HashMap map = orderService.hasCustomerCurrentOrder(form);
   return R.ok().put("result", map);
}
```
4. 写 hxds-customer-wx/main.js
   补充 hxds-customer-wx/pages/workbench/workbench.vue#onLoad
   补充 hxds-customer-wx/pages/create_order/create_order.vue#onLoad
   【说明】发起Ajax请求，查询乘客的当前的订单。如果有未接单的订单，就跳转到create_order.vue页面；如果有未完成的订单，就跳转到move.vue页面
```javascript
hasCustomerCurrentOrder: `${baseUrl}/order/hasCustomerCurrentOrder`,

// 查询乘客当前订单
that.ajax(that.url.hasCustomerCurrentOrder,"POST",{},function(resp){
   let result=resp.data.result
   let hasCustomerUnAcceptOrder = result.hasCustomerUnAcceptOrder;
   let hasCustomerUnFinishedOrder = result.hasCustomerUnFinishedOrder;
   if(hasCustomerUnAcceptOrder){
      let json=result.unAcceptOrder
      let carType = json.carType;
      let carPlate = json.carPlate;
      let startPlaceLocation = JSON.parse(json.startPlaceLocation);
      let endPlaceLocation = JSON.parse(json.endPlaceLocation);
      let from = {
         address: json.startPlace,
         latitude: startPlaceLocation.latitude,
         longitude: startPlaceLocation.longitude
      };
      let to = {
         address: json.endPlace,
         latitude: endPlaceLocation.latitude,
         longitude: endPlaceLocation.longitude
      };
      uni.setStorageSync("from",from)
      uni.setStorageSync("to",to)
      uni.showModal({
         title: '提示消息',
         content: '您有一个订单等待司机接单，现在将跳转到等待接单页面',
         showCancel: false,
         success: function(resp) {
            if (resp.confirm) {
               uni.navigateTo({
                  url: `../create_order/create_order?showPopup=true&orderId=${json.id}&showCar=true&carType=${carType}&carPlate=${carPlate}`
               });
            }
         }
      });
   }
   else if(hasCustomerUnFinishedOrder){
      uni.showModal({
         title: '提示消息',
         content: '您有一个正在执行的定订单，现在将跳转到司乘同显画面',
         showCancel: false,
         success: function(resp) {
            if (resp.confirm) {
               uni.navigateTo({
                  url: '../move/move?orderId=' + result.unFinishedOrder
               });
            }
         }
      });
   }
},false)

if(options.hasOwnProperty("showPopup")){
   that.timestamp=60
   that.showPopup=true
   that.orderId=options.orderId
   that.$refs.uCountDown.start();
}
```
5. 把抢单缓存设定成1分钟，然后乘客创建订单成功 ，等待10秒左右，关闭微信。立即重新打开微信登陆乘客端小程序，看看能不能跳转到create_order.vue页面，并且重新倒计时，最佳线路有没有生成，车辆有没有选中。在倒计时过程中，抢单缓存超时被销毁，订单和账单记录也被删除了。我们看看小程序能不能根据status为0，判断出订单已经关闭，然后弹出提示信息。

抢单缓存依然为1分钟，然后乘客创建订单成功就立即关闭微信。过两分钟之后再重新登录微信，因为抢单缓存超时被销毁，订单和账单记录也被删除了。我们看看小程序应该停留在工作台页面，等待乘客创建新的订单。

抢单缓存依然为1分钟，然后乘客创建订单成功就立即关闭微信。等待10秒钟，重新打开微信登陆小程序，看看页面跳转到create_order.vue页面之后，我们看看能不能手动关闭订单。

把抢单缓存恢复成16分钟，然后乘客正常创建订单，倒计时结束之后，看看订单能不能成功关闭
### 地图微服务，司机端的司乘同显
1. 写 hxds-odr/src/main/resources/mapper/OrderDao.xml#searchOrderForMoveById 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/impl/OrderService.java#searchOrderForMoveById 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/SearchOrderByIdForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#searchOrderForMoveById
```java
 <select id="searchOrderForMoveById" parameterType="Map" resultType="HashMap">
     SELECT
     start_place AS startPlace,
     start_place_location AS startPlaceLocation,
     end_place AS endPlace,
     end_place_location AS endPlaceLocation,
     `status`
     FROM tb_order
     WHERE id = #{orderId}
     <if test="customerId!=null">
         AND customer_id = #{customerId}
     </if>
     <if test="driverId!=null">
         AND driver_id = #{driverId}
     </if>
     LIMIT 1;
 </select>

HashMap searchOrderForMoveById(Map param);
 
HashMap searchOrderForMoveById(Map param);

@Override
public HashMap searchOrderForMoveById(Map param) {
     HashMap map = orderDao.searchOrderForMoveById(param);
     return map;
}

@Data
@Schema(description = "根据ID查询订单信息的表单")
public class SearchOrderByIdForm {
   @NotNull
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;

   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
}

@PostMapping("/searchOrderForMoveById")
@Operation(summary = "查询订单信息用于司乘同显功能")
public R searchOrderForMoveById(@RequestBody @Valid SearchOrderForMoveByIdForm form) {
   Map param = BeanUtil.beanToMap(form);
   HashMap map = orderService.searchOrderForMoveById(param);
   return R.ok().put("result", map);
}
```
2. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/SearchOrderForMoveByIdForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/OdrServiceApi.java#searchOrderForMoveById
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/OrderService.java#searchOrderForMoveById 及其实现类
   同理，给乘客端也来一套一样的，记得把 driverId 改成 customerId
```java
@Data
@Schema(description = "查询订单信息用于司乘同显功能的表单")
public class SearchOrderForMoveByIdForm {

   @NotNull(message = "orderId不能为空")
   @Schema(description = "订单ID")
   private Long orderId;

   @Schema(description = "司机ID")
   private Long driverId;

}

@PostMapping("/order/searchOrderForMoveById")
R searchOrderForMoveById(SearchOrderForMoveByIdForm form);

HashMap searchOrderForMoveById(SearchOrderForMoveByIdForm form);

@Override
public HashMap searchOrderForMoveById(SearchOrderForMoveByIdForm form) {
   R r = odrServiceApi.searchOrderForMoveById(form);
   HashMap map = (HashMap) r.get("result");
   return map;
}

@PostMapping("/searchOrderForMoveById")
@SaCheckLogin
@Operation(summary = "查询订单信息用于司乘同显功能")
public R searchOrderForMoveById(@RequestBody @Valid SearchOrderForMoveByIdForm form) {
   long driverId = StpUtil.getLoginIdAsLong();
   form.setDriverId(driverId);
   HashMap map = orderService.searchOrderForMoveById(form);
   return R.ok().put("result", map);
}
```
3. 启动 hxds-tm、hxds-odr、hxds-dr 和 bff-driver 子系统，用FastRequest插件测试Web方法
4. 写 hxds-driver-wx/pages/workbench/workbench.vue#showMoveHandle
   补充 hxds-driver-wx/main.js
   补充 hxds-driver-wx/execution/move/move.vue 模型层
   写 hxds-driver-wx/execution/move/move.vue#formatPolyline 实现把腾讯位置服务查询到的导航坐标解压缩
   写 hxds-driver-wx/execution/move/move.vue#calculateLine
   写 hxds-driver-wx/execution/move/move.vue#onLoad
   写 hxds-driver-wx/execution/move/move.vue#onShow
   写 hxds-driver-wx/execution/move/move.vue#onHide
   写 hxds-driver-wx/execution/move/move.vue#hideHandle
   写 hxds-driver-wx/execution/move/move.vue#showHandle
   同理，给乘客端也来一套一样的
   【说明】在地图组件上长按，触发长按事件，我们编写回调函数把状态条显示出来。如果点击状态条的关闭图标，就隐藏状态条
```javascript
showMoveHandle: function() {
   let that = this;
   uni.navigateTo({
       url: '../../execution/move/move?orderId=' + that.executeOrder.id
   });
},

searchOrderForMoveById: `${baseUrl}/order/searchOrderForMoveById`,

orderId: null,
status: null,
mode: null,
map: null,
mapStyle: '',
startLatitude: 0,
startLongitude: 0,
endLatitude: 0,
endLongitude: 0,
latitude: 0,
longitude: 0,
targetLatitude: 0,
targetLongitude: 0,
distance: 0,
duration: 0,
polyline: [],
markers: [],
timer: null,
infoStatus: true

formatPolyline(polyline) {
   let coors = polyline;
   let pl = [];
   //坐标解压（返回的点串坐标，通过前向差分进行压缩）
   const kr = 1000000;
   for (let i = 2; i < coors.length; i++) {
      coors[i] = Number(coors[i - 2]) + Number(coors[i]) / kr;
   }
   //将解压后的坐标放入点串数组pl中
   for (let i = 0; i < coors.length; i += 2) {
      pl.push({
         longitude: coors[i + 1],
         latitude: coors[i]
      });
   }
   return pl;
},

calculateLine: function(ref) {
   if (ref.latitude == 0 || ref.longitude == 0) {
      return;
   }
   qqmapsdk.direction({
      mode: ref.mode,
      from: {
         latitude: ref.latitude,
         longitude: ref.longitude
      },
      to: {
         latitude: ref.targetLatitude,
         longitude: ref.targetLongitude
      },
      success: function(resp) {
         let route = resp.result.routes[0];
         let distance = route.distance;
         let duration = route.duration;
         let polyline = route.polyline;
         ref.distance = Math.ceil((distance / 1000) * 10) / 10;
         ref.duration = duration;

         let points = ref.formatPolyline(polyline);

         ref.polyline = [
            {
               points: points,
               width: 6,
               color: '#05B473',
               arrowLine: true
            }
         ];
         ref.markers = [
            {
               id: 1,
               latitude: ref.latitude,
               longitude: ref.longitude,
               width: 35,
               height: 35,
               anchor: {
                  x: 0.5,
                  y: 0.5
               },
               iconPath: '../static/move/driver-icon.png'
            }
         ];
      },
      fail: function(error) {
         console.log(error);
      }
   });
},

onLoad: function(options) {
   let that = this;
   that.orderId = options.orderId;
   qqmapsdk = new QQMapWX({
      key: that.tencent.map.key
   });
   let windowHeight = uni.getSystemInfoSync().windowHeight;
   that.mapStyle = `height:${windowHeight}px`;
},
onShow: function() {
   let that = this;
   uni.$on('updateLocation', function(location) {
      if (location != null) {
         that.latitude = location.latitude;
         that.longitude = location.longitude;
      }
   });

   let data = {
      orderId: that.orderId
   };
   that.ajax(that.url.searchOrderForMoveById, 'POST', data, function(resp) {
      let result = resp.data.result;

      let startPlaceLocation = JSON.parse(result.startPlaceLocation);
      that.startLatitude = startPlaceLocation.latitude;
      that.startLongitude = startPlaceLocation.longitude;

      let endPlaceLocation = JSON.parse(result.endPlaceLocation);
      that.endLatitude = endPlaceLocation.latitude;
      that.endLongitude = endPlaceLocation.longitude;

      let status = result.status;
      if (status == 2) {
         that.targetLatitude = that.startLatitude;
         that.targetLongitude = that.startLongitude;
         that.mode = 'bicycling';
      } else if (status == 3 || status == 4) {
         that.targetLatitude = that.endLatitude;
         that.targetLongitude = that.endLongitude;
         that.mode = 'driving';
      }
      that.calculateLine(that);
      that.timer = setInterval(function() {
         that.calculateLine(that);
      }, 6000);
   });
},
onHide: function() {
   let that = this;
   uni.$off('updateLocation');
   clearInterval(that.timer);
   that.timer = null;
}

hideHandle: function() {
   this.infoStatus = false;
},
showHandle: function() {
   this.infoStatus = true;
}

// 乘客端
methods: {
   formatPolyline(polyline) {
      let coors = polyline;
      let pl = [];
      //坐标解压（返回的点串坐标，通过前向差分进行压缩）
      const kr = 1000000;
      for (let i = 2; i < coors.length; i++) {
         coors[i] = Number(coors[i - 2]) + Number(coors[i]) / kr;
      }
      //将解压后的坐标放入点串数组pl中
      for (let i = 0; i < coors.length; i += 2) {
         pl.push({
            longitude: coors[i + 1],
            latitude: coors[i]
         });
      }
      return pl;
   },
   calculateLine: function(ref) {
      if (ref.latitude == 0 || ref.longitude == 0) {
         return;
      }
      qqmapsdk.direction({
         mode: ref.mode,
         from: {
            latitude: ref.latitude,
            longitude: ref.longitude
         },
         to: {
            latitude: ref.targetLatitude,
            longitude: ref.targetLongitude
         },
         success: function(resp) {
            let route = resp.result.routes[0];
            let distance = route.distance;
            let duration = route.duration;
            let polyline = route.polyline;
            ref.distance = Math.ceil((distance / 1000) * 10) / 10;
            ref.duration = duration;

            let points = ref.formatPolyline(polyline);

            ref.polyline = [
               {
                  points: points,
                  width: 6,
                  color: '#05B473',
                  arrowLine: true
               }
            ];
            ref.markers = [
               {
                  id: 1,
                  latitude: ref.latitude,
                  longitude: ref.longitude,
                  width: 35,
                  height: 35,
                  anchor: {
                     x: 0.5,
                     y: 0.5
                  },
                  iconPath: '../../static/move/driver-icon.png'
               }
            ];
         },
         fail: function(error) {
            console.log(error);
         }
      });
   },
   analyse: function(ref) {
      if (ref.status == 2) {
         let data = {
            orderId: ref.orderId
         };
         ref.ajax(
                 ref.url.searchOrderLocationCache,
                 'POST',
                 data,
                 function(resp) {
                    let result = resp.data.result;
                    if (result.hasOwnProperty('latitude') && result.hasOwnProperty('longitude')) {
                       let latitude = result.latitude;
                       let longitude = result.longitude;
                       ref.latitude = latitude;
                       ref.longitude = longitude;
                       ref.calculateLine(ref);
                    }
                 },
                 false
         );
      } else {
         ref.calculateLine(ref);
      }
   },
},
onLoad: function(options) {
   let that = this;
   that.orderId = options.orderId;
   qqmapsdk = new QQMapWX({
      key: that.tencent.map.key
   });
   let windowHeight = uni.getSystemInfoSync().windowHeight;
   that.mapStyle = `height:${windowHeight}px`;
},
onShow: function() {
   let that = this;
   uni.$on('updateLocation', function(location) {
      if (location != null && that.status != 2) {
         that.latitude = location.latitude;
         that.longitude = location.longitude;
      }
   });

   let data = {
      orderId: that.orderId
   };
   that.ajax(that.url.searchOrderForMoveById, 'POST', data, function(resp) {
      let result = resp.data.result;

      let startPlaceLocation = JSON.parse(result.startPlaceLocation);
      that.startLatitude = startPlaceLocation.latitude;
      that.startLongitude = startPlaceLocation.longitude;

      let endPlaceLocation = JSON.parse(result.endPlaceLocation);
      that.endLatitude = endPlaceLocation.latitude;
      that.endLongitude = endPlaceLocation.longitude;

      let status = result.status;

      that.status = status;
      if (status == 2) {
         that.targetLatitude = that.startLatitude;
         that.targetLongitude = that.startLongitude;
         that.mode = 'bicycling';
      } else if (status == 3 || status == 4) {
         that.targetLatitude = that.endLatitude;
         that.targetLongitude = that.endLongitude;
         that.mode = 'driving';
      }

      that.analyse(that);
      that.timer = setInterval(function() {
         that.analyse(that);
      }, 6000);

      if(status == 4 || status == 5){
         that.messageTimer=setInterval(function(){
            that.ajax(that.url.receiveBillMessage,"POST",{},function(resp){
               if(resp.data.result=="您有代驾订单待支付"){
                  uni.redirectTo({
                     url:"../order/order?orderId="+that.orderId
                  })
               }
            },false)
         },5000)
      }
   });
},
onHide: function() {
```
5. 修改 main.js/App.vue 文件的 URL 进行自测
### 地图微服务，乘客端的司乘同显
1. 写 hxds-mps/src/main/java/com/example/hxds/mps/service/DriverLocationService.java#updateOrderLocationCache、searchOrderLocationCache 及其实现类
   写 hxds-mps/src/main/java/com/example/hxds/mps/controller/form/UpdateOrderLocationCacheForm.java
   写 hxds-mps/src/main/java/com/example/hxds/mps/controller/form/SearchOrderLocationCacheForm.java
   写 hxds-mps/src/main/java/com/example/hxds/mps/controller/DriverLocationController.java#updateOrderLocationCache、searchOrderLocationCache
```java
void updateOrderLocationCache(Map param);

HashMap searchOrderLocationCache(long orderId);

@Override
public void updateOrderLocationCache(Map param) {
     Long orderId = MapUtil.getLong(param, "orderId");
     String latitude = MapUtil.getStr(param, "latitude");
     String longitude = MapUtil.getStr(param, "longitude");
     String location = latitude + "#" + longitude;
     redisTemplate.opsForValue().set("order_location#" + orderId, location, 10, TimeUnit.MINUTES);
}

@Override
public HashMap searchOrderLocationCache(long orderId) {
     Object obj = redisTemplate.opsForValue().get("order_location#" + orderId);
     if(obj != null){
        String[] temp = obj.toString().split("#");
        String latitude = temp[0];
        String longitude = temp[1];
        HashMap map = new HashMap() {{
           put("latitude", latitude);
           put("longitude", longitude);
        }};
        return map;
     }
     return null;
}

@Data
@Schema(description = "更新订单定位缓存的表单")
public class UpdateOrderLocationCacheForm {

   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private String orderId;

   @NotBlank(message = "latitude不能为空")
   @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "latitude内容不正确")
   @Schema(description = "纬度")
   private String latitude;

   @NotBlank(message = "longitude不能为空")
   @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "longitude内容不正确")
   @Schema(description = "经度")
   private String longitude;
}

@Data
@Schema(description = "查询订单定位缓存的表单")
public class SearchOrderLocationCacheForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@PostMapping("/updateOrderLocationCache")
@Operation(summary = "更新订单定位缓存")
public R updateOrderLocationCache(@RequestBody @Valid UpdateOrderLocationCacheForm form){
   Map param = BeanUtil.beanToMap(form);
   driverLocationService.updateOrderLocationCache(param);
   return R.ok();
}

@PostMapping("/searchOrderLocationCache")
@Operation(summary = "查询订单定位缓存")
public R searchOrderLocationCache(@RequestBody @Valid SearchOrderLocationCacheForm form){
   HashMap map = driverLocationService.searchOrderLocationCache(form.getOrderId());
   return R.ok().put("result",map);
}
```
2. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/UpdateOrderLocationCacheForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/SearchOrderLocationCacheForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/MpsServiceApi.java#updateOrderLocationCache
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/MpsServiceApi.java#searchOrderLocationCache
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/DriverLocationService.java#updateOrderLocationCache 及其实现类
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/service/OrderLocationService.java#searchOrderLocationCache 及其实现类
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/DriverLocationController.java#updateOrderLocationCache
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/OrderLocationController.java#searchOrderLocationCache
```java
@Data
@Schema(description = "更新订单定位缓存的表单")
public class UpdateOrderLocationCacheForm {

    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private String orderId;

    @NotBlank(message = "latitude不能为空")
    @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "latitude内容不正确")
    @Schema(description = "纬度")
    private String latitude;

    @NotBlank(message = "longitude不能为空")
    @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "longitude内容不正确")
    @Schema(description = "经度")
    private String longitude;
}

@Data
@Schema(description = "查询订单定位缓存的表单")
public class SearchOrderLocationCacheForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@PostMapping("/driver/location/updateOrderLocationCache")
R updateOrderLocationCache(UpdateOrderLocationCacheForm form);

@PostMapping("/driver/location/searchOrderLocationCache")
R searchOrderLocationCache(SearchOrderLocationCacheForm form);

void updateOrderLocationCache(UpdateOrderLocationCacheForm form)
   
@Override
public void updateOrderLocationCache(UpdateOrderLocationCacheForm form) {
   mpsServiceApi.updateOrderLocationCache(form);
}

HashMap searchOrderLocationCache(SearchOrderLocationCacheForm form);

@Override
public HashMap searchOrderLocationCache(SearchOrderLocationCacheForm form) {
   R r = mpsServiceApi.searchOrderLocationCache(form);
   HashMap map = (HashMap) r.get("result");
   return map;
}

@PostMapping("/updateOrderLocationCache")
@Operation(summary = "更新订单定位缓存")
@SaCheckLogin
public R updateOrderLocationCache(@RequestBody @Valid UpdateOrderLocationCacheForm form){
   driverLocationService.updateOrderLocationCache(form);
   return R.ok();
}

@PostMapping("/searchOrderLocationCache")
@Operation(summary = "查询订单定位缓存")
@SaCheckLogin
public R searchOrderLocationCache(@RequestBody @Valid SearchOrderLocationCacheForm form){
   HashMap map = orderLocationService.searchOrderLocationCache(form);
   return R.ok().put("result",map);
}
```
3. 补充 hxds-driver-wx/App.vue
```javascript
 let executeOrder = uni.getStorageSync('executeOrder');
 let orderId = executeOrder.id;
 let data = {
     orderId: orderId,
     latitude: latitude,
     longitude: longitude
 };
 uni.request({
     url: `${baseUrl}/driver/location/updateOrderLocationCache`,
     method: 'POST',
     header: {
         token: uni.getStorageSync('token')
     },
     data: data,
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
             console.log('订单定位更新成功');
         } else {
             console.error('订单定位更新失败', resp.data);
         }
     },
     fail: function(error) {
         console.error('订单定位更新失败', error);
     }
 });
```
4. 把后端各个子系统都运行起来，然后通过FastRequest插件调用Web方法，手动上传司机定位信息，然后打开乘客端小程序，自动跳转到司乘同显页面并且显示司机
   赶往上车点的最佳线路、里程和时间。这时候我们用FastRequest插件调用Web方法，更新司机定位之后，乘客端的司乘同显页面也会随之更新
### 订单微服务司机到达起始点，更新订单状态
1. 写 hxds-odr/src/main/resources/mapper/OrderDao.xml 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java#arriveStartPlace 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/ArriveStartPlaceForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#arriveStartPlace
```java
 <update id="updateOrderStatus" parameterType="Map">
     UPDATE tb_order
     SET
     <if test="status==3">
         arrive_time = NOW(),
     </if>
     <if test="status==4">
         start_time = NOW(),
         waiting_minute = CEIL(TIMESTAMPDIFF(SECOND, arrive_time, NOW())/60),
     </if>
     <if test="status==5">
         end_time = NOW(),
     </if>
     `status` = #{status}
     WHERE id = #{orderId}
     <if test="customerId!=null">
         AND customer_id = #{customerId}
     </if>
     <if test="driverId!=null">
         AND driver_id = #{driverId}
     </if>
 </update>

int arriveStartPlace(Map param);

@Override
public int arriveStartPlace(Map param) {
     // 添加到达上车点标志位
     Long orderId = MapUtil.getLong(param, "orderId");
     redisTemplate.opsForValue().set("order_driver_arrived#" + orderId, "1");
     int rows = orderDao.updateOrderStatus(param);
     if (rows != 1) {
        throw new HxdsException("更新订单状态失败");
     }
     return rows;
}

@Data
@Schema(description = "更新订单状态的表单")
public class ArriveStartPlaceForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

}

@PostMapping("/arriveStartPlace")
@Operation(summary = "司机到达上车点")
public R arriveStartPlace(@RequestBody @Valid ArriveStartPlaceForm form) {
     Map param = BeanUtil.beanToMap(form);
     param.put("status", 3);
     int rows = orderService.arriveStartPlace(param);
     return R.ok().put("rows", rows);
}
```
2. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/ArriveStartPlaceForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/OdrServiceApi.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/OrderService.java#arriveStartPlace 及其实现类
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/OrderController.java#arriveStartPlace
```java
@Data
@Schema(description = "更新订单状态的表单")
public class ArriveStartPlaceForm {
    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "司机ID")
    private Long driverId;

    @NotNull(message = "customerId不能为空")
    @Min(value = 1, message = "customerId不能小于1")
    @Schema(description = "客户ID")
    private Long customerId;
}

@PostMapping("/order/arriveStartPlace")
R arriveStartPlace(ArriveStartPlaceForm form);

int arriveStartPlace(ArriveStartPlaceForm form);

@Override
@Transactional
@LcnTransaction
public int arriveStartPlace(ArriveStartPlaceForm form) {
   R r = odrServiceApi.arriveStartPlace(form);
   int rows = MapUtil.getInt(r, "rows");
   if (rows == 1) {
      //TODO 发送通知消息
   }
   return rows;
}

@PostMapping("/arriveStartPlace")
@Operation(summary = "司机到达上车点")
@SaCheckLogin
public R arriveStartPlace(@RequestBody @Valid ArriveStartPlaceForm form) {
   long driverId = StpUtil.getLoginIdAsLong();
   form.setDriverId(driverId);
   int rows = orderService.arriveStartPlace(form);
   return R.ok().put("rows", rows);
}
```
3. 写 hxds-driver-wx/main.js
   写 hxds-driver-wx/pages/workbench/workbench.vue#arriveStartPlaceHandle
```javascript
arriveStartPlace: `${baseUrl}/order/arriveStartPlace`,

arriveStartPlaceHandle: function() {
   let that = this;
   uni.showModal({
       title: '消息通知',
       content: '确认已经到达了代驾点？',
       success: function(resp) {
           if (resp.confirm) {
               let data = {
                   orderId: that.executeOrder.id,
                   customerId: that.executeOrder.customerId
               };
               that.ajax(that.url.arriveStartPlace, 'POST', data, function(resp) {
                   if (resp.data.rows == 1) {
                       uni.showToast({
                           icon: 'success',
                           title: '订单状态更新成功'
                       });
                       that.workStatus = '到达代驾点';
                       uni.setStorageSync('workStatus', '到达代驾点');
                   }
               });
           }
       }
   });
},
```
### 乘客端手动确认司机到达，并开始代驾模式
1. 写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java#confirmArriveStartPlace 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/ConfirmArriveStartPlaceForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#confirmArriveStartPlace
   【说明】有一点需要大家必须清楚，乘客端点击了司机已到达，并不更改订单状态，因为上节课司机已到达的时候就已经把订单改成了3状态，为什么不是乘客确认司机已到达之后，再把订单改成3状态呢？这是因为司机到达上车点之后，有10分钟的免费等时。10分钟之后，乘客还没有到达上车点，代驾账单中就会出现等时费（1分钟1元钱）。有时候明明司机已经到达了上车点，但是乘客拖拖拉拉半个小时才到上车点，然后才点击司机已到达，于是等待的半个小时就成了免费的，因为没有确认司机已到达上车点，那就不算等时。

乘客端点击确认司机已到达之后，并不会修改订单状态，修改Redis里面的标志位缓存。等到司机端点击开始代驾的时候，要确定Redis里面标志位的值为2，然后才能把订单更新成4状态。
```java
boolean confirmArriveStartPlace(long orderId);

@Override
public boolean confirmArriveStartPlace(long orderId) {
   String key = "order_driver_arrivied#" + orderId;
   if (redisTemplate.hasKey(key) && redisTemplate.opsForValue().get(key).toString().equals("1")) {
      redisTemplate.opsForValue().set(key, "2");
      return true;
   }
   return false;
}

@PostMapping("/confirmArriveStartPlace")
@Operation(summary = "乘客确认司机到达上车点")
public R confirmArriveStartPlace(@RequestBody @Valid ConfirmArriveStartPlaceForm form) {
     boolean result = orderService.confirmArriveStartPlace(form.getOrderId());
     return R.ok().put("result", result);
}
```
2. 写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/ConfirmArriveStartPlaceForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/OdrServiceApi.java#confirmArriveStartPlace
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/service/OrderService.java#confirmArriveStartPlace 及其实现类
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/OrderController.java#confirmArriveStartPlace
```java
@Data
@Schema(description = "更新订单状态的表单")
public class ConfirmArriveStartPlaceForm {
    
    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

}

@PostMapping("/order/confirmArriveStartPlace")
R confirmArriveStartPlace(ConfirmArriveStartPlaceForm form);

boolean confirmArriveStartPlace(ConfirmArriveStartPlaceForm form);
   
@Override
public boolean confirmArriveStartPlace(ConfirmArriveStartPlaceForm form) {
   R r = odrServiceApi.confirmArriveStartPlace(form);
   boolean result = MapUtil.getBool(r, "result");
   return result;
}

@PostMapping("/confirmArriveStartPlace")
@SaCheckLogin
@Operation(summary = "确定司机已经到达")
public R confirmArriveStartPlace(@RequestBody @Valid ConfirmArriveStartPlaceForm form) {
   boolean result = orderService.confirmArriveStartPlace(form);
   return R.ok().put("result", result);
}
```
3. 写 hxds-customer-wx/main.js
   写 hxds-customer-wx/pages/move/move.vue#driverArriviedHandle
```javascript
confirmArriveStartPlace: `${baseUrl}/order/confirmArriveStartPlace`,

driverArriviedHandle: function() {
   let that = this;
   uni.showModal({
      title: '提示消息',
      content: '确定司机已经到达代驾点？',
      success: function(resp) {
         if (resp.confirm) {
            let data = {
               orderId: that.orderId
            };
            that.ajax(that.url.confirmArriveStartPlace, 'POST', data, function(resp) {
               if (resp.data.result) {
                  uni.showToast({
                     icon: 'success',
                     title: '状态更新成功'
                  });
                  that.status = 4;
                  that.mode = 'driving';
                  that.targetLatitude = that.endLatitude;
                  that.targetLongitude = that.endLongitude;
               }
            });
         }
      }
   });
}
```
4. 把后端各个子系统运行起来，然后运行乘客端小程序，在司乘同显页面点击“司机到达”按钮，看看页面上是不是变成根据当前乘客定位计算最佳线路
5. 写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java#startDriving 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/StartDrivingForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#startDriving
```java
int startDriving(Map param);

@Override
@Transactional
@LcnTransaction
public int startDriving(Map param) {
   long orderId = MapUtil.getLong(param, "orderId");
   String key = "order_driver_arrivied#" + orderId;
   if (redisTemplate.hasKey(key) && redisTemplate.opsForValue().get(key).toString().equals("2")) {
      redisTemplate.delete(key);
      int rows = orderDao.updateOrderStatus(param);
      if (rows != 1) {
            throw new HxdsException("更新订单状态失败");
      }
      return rows;
   }
   return 0;
}

@Data
@Schema(description = "开始代驾的表单")
public class StartDrivingForm {
    
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

}

@PostMapping("/startDriving")
@Operation(summary = "开始代驾")
public R startDriving(@RequestBody @Valid StartDrivingForm form) {
   Map param = BeanUtil.beanToMap(form);
   param.put("status", 4);
   int rows = orderService.startDriving(param);
   return R.ok().put("rows", rows);
}
```
6. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/StartDrivingForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/OdrServiceApi.java#startDriving
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/OrderService.java#startDriving 及其实现类
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/OrderController.java#startDriving
```java
@Data
@Schema(description = "开始代驾的表单")
public class StartDrivingForm {
    
    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @Min(value = 1, message = "driverId不能小于1")
    @Schema(description = "司机ID")
    private Long driverId;

    @NotNull(message = "customerId不能为空")
    @Min(value = 1, message = "customerId不能小于1")
    @Schema(description = "客户ID")
    private Long customerId;

}

@PostMapping("/order/startDriving")
R startDriving(StartDrivingForm form);

int startDriving(StartDrivingForm form)
   
@Override
@Transactional
@LcnTransaction
public int startDriving(StartDrivingForm form) {
   R r = odrServiceApi.startDriving(form);
   int rows = MapUtil.getInt(r, "rows");
   // TODO 发送通知消息
   return rows;
}

@PostMapping("/startDriving")
@Operation(summary = "开始代驾")
@SaCheckLogin
public R startDriving(@RequestBody @Valid StartDrivingForm form) {
   long driverId = StpUtil.getLoginIdAsLong();
   form.setDriverId(driverId);
   int rows = orderService.startDriving(form);
   return R.ok().put("rows", rows);
}
```
7. 写 hxds-driver-wx/main.js
   写 hxds-driver-wx/pages/workbench/workbench.vue#startDrivingHandle
```javascript
startDriving: `${baseUrl}/order/startDriving`,

startDrivingHandle: function() {
   let that = this;
   uni.showModal({
      title: '消息通知',
      content: '您已经接到客户，现在开始代驾？',
      success: function(resp) {
         if (resp.confirm) {
            // TODO:设置录音标志位
            // that.stopRecord = false;
            let data = {
               orderId: that.executeOrder.id,
               customerId: that.executeOrder.customerId
            };
            that.ajax(that.url.startDriving, 'POST', data, function(resp) {
               if (resp.data.rows == 1) {
                  uni.showToast({
                     icon: 'success',
                     title: '订单状态更新成功'
                  });
                  that.workStatus = '开始代驾';
                  uni.setStorageSync('workStatus', '开始代驾');
                  // TODO:开始录音
               }
            });
         }
      }
   });
},
```
8. 把后端各个子系统开启，手机运行司机端小程序，进入工作台页面之后，点击“开始代驾”按钮，看看订单状态是否更新
### 司机端利用地图APP实现驾驶导航
到目前为止，我们完成了司机到达上车点，经过乘客确认，司机可以开始代驾了。如果开车光盯着司乘同显的最佳线路并不方便，所以我们应该把地图导航功能给实现了。首先说清楚，微信小程序的分包加载之后的总体积不能超过32M，所以我们根本不可能把地图导航功能塞到小程序里面。为此我们只能用小程序调用手机上面的地图APP，实现驾驶导航功能
小程序地图组件的openMapApp()函数可以打开手机默认的地图APP，然后进行导航。比如苹果手机内置了地图APP，我又额外安装了腾讯地图APP，所以弹窗出现了选项，让我选择使用哪个APP软件
```javascript
showNavigationHandle: function() {
   let that = this;
   let latitude = null;
   let longitude = null;
   let destination = null;
   if (that.workStatus == '接客户') {
       latitude = Number(that.executeOrder.startPlaceLocation.latitude);
       longitude = Number(that.executeOrder.startPlaceLocation.longitude);
       destination = that.executeOrder.startPlace;
   } else {
       latitude = Number(that.executeOrder.endPlaceLocation.latitude);
       longitude = Number(that.executeOrder.endPlaceLocation.longitude);
       destination = that.executeOrder.endPlace;
   }
   //打开手机导航软件
   that.map.openMapApp({
       latitude: latitude,
       longitude: longitude,
       destination: destination
   });
},
```
### 搭建HBase+Phoenix大数据平台
1. 在云服务器上搭建环境
```bash
# 导入镜像文件
docker load < phoenix.tar.gz
# 创建容器
docker run -it -d -p 2181:2181 -p 8765:8765 -p 15165:15165 \
-p 16000:16000 -p 16010:16010 -p 16020:16020 \
-v /www/evmt/hbase/data:/tmp/hbase-root/hbase/data \
--name phoenix --net mynet --ip 172.18.0.14 \
boostport/hbase-phoenix-all-in-one:2.0-5.0
# 开放端口
把Linux的2181、8765、15165、16000、16010、16020端口，映射到Windows的相应端口上面
# 初始化Phoenix
docker exec -it phoenix bash
export HBASE_CONF_DIR=/opt/hbase/conf/
/opt/phoenix-server/bin/sqlline.py localhost
# 创建逻辑库
0: jdbc:phoenix:localhost> CREATE SCHEMA hxds;
No rows affected (0.23 seconds)
0: jdbc:phoenix:localhost> USE hxds;
No rows affected (0.017 seconds)
# 创建数据表：创建order_voice_text、order_monitoring和order_gps数据表。其中order_voice_text表用于存放司乘对话内容的文字内容
CREATE TABLE hxds.order_voice_text
(
   "id" BIGINT NOT NULL PRIMARY KEY,
   "uuid" VARCHAR,
   "order_id" BIGINT,
   "record_file" VARCHAR,
   "text" VARCHAR,
   "label" VARCHAR,
   "suggestion" VARCHAR,
   "keyWords"VARCHAR,
   "create_time" DATE
);
CREATE SEQUENCE hxds.ovt_sequence START wITH 1 INCREMENT BY 1;
CREATE INDEX ovt_index_1 ON hxds.order_voice_text("uuid");
CREATE INDEX ovt_index_2 ON hxds.order_voice_text("order_id");
CREATE INDEX ovt_index_3 ON hxds.order_voice_text("label");
CREATE INDEX ovt_index_4 ON hxds.order_voice_text("suggestion");
CREATE INDEX ovt_index_5 ON hxds.order_voice_text("create_time");

# 创建数据表：order_monitoring表存储AI分析对话内容的安全评级结果
CREATE TABLE hxds.order_monitoring
(
      "id"              BIGINT NOT NULL PRIMARY KEY,
      "order_id"        BIGINT,
      "status"          TINYINT,
      "records"         INTEGER,
      "safety"          VARCHAR,
      "reviews"         INTEGER,
      "alarm"           TINYINT,
      "create_time"     DATE
);
CREATE INDEX om_index_1 ON hxds.order_monitoring("order_id");
CREATE INDEX om_index_2 ON hxds.order_monitoring("status");
CREATE INDEX om_index_3 ON hxds.order_monitoring("safety");
CREATE INDEX om_index_4 ON hxds.order_monitoring("reviews");
CREATE INDEX om_index_5 ON hxds.order_monitoring("alarm");
CREATE INDEX om_index_6 ON hxds.order_monitoring("create_time");
CREATE SEQUENCE hXds.om_Sequence START WITH 1 INCREMENT BY 1;

# 创建数据表：order_gps表保存的时候代驾过程中的GPS定位
CREATE TABLE hxds.order_gps(
      "id"              BIGINT NOT NULL PRIMARY KEY,
      "order_id"        BIGINT,
      "driver_id"       BIGINT,
      "customer_id"     BIGINT,
      "latitude"        VARCHAR,
      "longitude"       VARCHAR,
      "speed"           VARCHAR,
      "create_time"     DATE
);
CREATE SEQUENCE og_Sequence START WITH 1 INCREMENT BY 1;
CREATE INDEX og_index_1 ON hxds.order_gps("order_id");
CREATE INDEX og_index_2 ON hxds.order_gps("driver_id");
CREATE INDEX og_index_3 ON hxds.order_gps("customer_id");
CREATE INDEX og_index_4 ON hxds.order_gps("create_time");
```
2. 写 hxds-nebula/src/main/java/com/example/hxds/nebula/db/pojo/OrderGpsEntity.java、OrderMonitoringEntity.java、OrderVoiceTextEntity.java
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/db/dao/OrderGpsDao.java、OrderMonitoringDao.java、OrderVoiceTextDao.java
   写 hxds-nebula/src/main/resources/mapper/OrderGpsDao.xml、OrderMonitoringDao.xml、OrderVoiceTextDao.xml
```java
@Data
public class OrderVoiceTextEntity {
    private Long id;
    private String uuid;
    private Long orderId;
    private String recordFile;
    private String text;
    private String label;
    private String suggestion;
    private String keywords;
    private String createTime;
}

@Data
public class OrderMonitoringEntity {
   private Long id;
   private Long orderId;
   private Byte status;
   private Integer records;
   private String safety;
   private Integer reviews;
   private Byte alarm;
   private String createTime;
}

@Data
public class OrderGpsEntity {
   private Long id;
   private Long orderId;
   private Long driverId;
   private Long customerId;
   private String latitude;
   private String longitude;
   private String speed;
   private String createTime;
}

public interface OrderGpsDao {

}

public interface OrderMonitoringDao {

}

public interface OrderVoiceTextDao {

}

<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.hxds.nebula.db.dao.OrderGpsDao">

</mapper>

<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.hxds.nebula.db.dao.OrderMonitoringDao">

</mapper>

<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.hxds.nebula.db.dao.OrderVoiceTextDao">

</mapper>
```
### 将录音监控保存到私有云，对话文本保存到大数据平台
1. 写 hxds-nebula/src/main/resources/mapper/OrderVoiceTextDao.xml#insert 及其对应接口
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/service/MonitoringService.java#monitoring 及其对应实现类
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/controller/MonitoringController.java#monitoring
```java
 <insert id="insert" parameterType="com.example.hxds.nebula.db.pojo.OrderVoiceTextEntity">
     UPSERT INTO hxds.order_voice_text("id", "uuid", "order_id", "record_file", "text", "create_time")
     VALUES(NEXT VALUE FOR hxds.ovt_sequence, '${uuid}', #{orderId}, '${recordFile}', '${text}', NOW())
 </insert>

int insert(OrderVoiceTextEntity entity);

void monitoring(MultipartFile file, String name, String text);

@Override
@Transactional
public void monitoring(MultipartFile file, String name, String text) {
     // 把录音文件上传到minio
     try {
        MinioClient client = new MinioClient.Builder().endpoint(endpoint)
                                    .credentials(accessKey, secretKey).build();
        client.putObject(PutObjectArgs.builder().bucket("hxds-record")
              .object(name).stream(file.getInputStream(), -1, 20971520)
              .contentType("audio/x-mpeg").build());
     } catch (Exception e) {
        log.error("上传代驾录音文件失败", e);
           throw new HxdsException("上传代驾录音文件失败");
     }
     OrderVoiceTextEntity entity = new OrderVoiceTextEntity();
     // 文件名格式例如:2156356656617-1.mp3，解析出订单号
     String[] temp = name.substring(0, name.indexOf(".mp3")).split("-");
     Long orderId = Long.parseLong(temp[0]);
     String uuid = IdUtil.simpleUUID();
     entity.setOrderId(orderId);
     entity.setUuid(uuid);
     entity.setRecordFile(name);
     entity.setText(text);
     // 把文稿保持到HBase
     int rows = orderVoiceTextDao.insert(entity);
     if (rows != 1) {
        throw new HxdsException("保存录音文稿失败");
     }
     // TODO:执行文稿内容审查
}

@PostMapping(value = "/uploadRecordFile")
@Operation(summary = "上传代驾录音文件")
public R uploadRecordFile(@RequestPart("file") MultipartFile file,
@RequestPart("name") String name,
@RequestPart(value = "text", required = false) String text) {
     if (file.isEmpty()) {
        throw new HxdsException("录音文件不能为空");
     }
     monitoringService.monitoring(file, name, text);
     return R.ok();
}
```
2. 写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/NebulaServiceApi.java#uploadRecordFile
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/MonitoringController.java#uploadRecordFile
   【说明】在com.example.hxds.bff.driver.controller包中，创建MonitoringController.java类，声明Web方法。这里之所以让Web方法直接调用Feign接口，是因为要把接收到的文件直接传递给hxds-nebula子系统。如果通过业务层去调用Feign接口，业务方法的参数用上了@RequestPart("file")就非常不适合
```java
@PostMapping(value = "/monitoring/uploadRecordFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
R uploadRecordFile(@RequestPart(value = "file") MultipartFile file,
                           @RequestPart("name") String name,
                           @RequestPart(value = "text", required = false) String text);

@PostMapping(value = "/uploadRecordFile")
public R uploadRecordFile(@RequestPart("file") MultipartFile file,
                           @RequestPart("name") String name,
                           @RequestPart(value = "text", required = false) String text) {
     if (file.isEmpty()) {
        throw new HxdsException("上传文件不能为空");
     }
     nebulaServiceApi.uploadRecordFile(file, name, text);
     return R.ok();
}
```
3. 写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java#updateOrderStatus 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/UpdateOrderStatusForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#updateOrderStatus
```java
int updateOrderStatus(Map param);

 @Override
 @Transactional
 @LcnTransaction
 public int updateOrderStatus(Map param) {
     int rows = orderDao.updateOrderStatus(param);
     if (rows != 1) {
         throw new HxdsException("更新取消订单记录失败");
     }
     return rows;
 }

@Data
@Schema(description = "更新订单状态的表单")
public class UpdateOrderStatusForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @NotNull(message = "status不能为空")
   @Range(min = 1, max = 12, message = "status内容不正确")
   @Schema(description = "订单状态")
   private Byte status;
}

@PostMapping("/updateOrderStatus")
@Operation(summary = "更新订单状态")
public R updateOrderStatus(@RequestBody @Valid UpdateOrderStatusForm form) {
   Map param = BeanUtil.beanToMap(form);
   int rows = orderService.updateOrderStatus(param);
   return R.ok().put("rows", rows);
}
```
4. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/UpdateOrderStatusForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/OdrServiceApi.java#updateOrderStatus
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/OrderService.java#updateOrderStatus 及其实现类
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/OrderController.java#updateOrderStatus
```java
@Data
@Schema(description = "更新订单状态的表单")
public class UpdateOrderStatusForm {
    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @NotNull(message = "status不能为空")
    @Range(min = 1, max = 12, message = "status内容不正确")
    @Schema(description = "订单状态")
    private Byte status;

    @NotNull(message = "customerId不能为空")
    @Min(value = 1, message = "customerId不能小于1")
    @Schema(description = "客户ID")
    private Long customerId;
}

@PostMapping("/order/updateOrderStatus")
R updateOrderStatus(UpdateOrderStatusForm form);

int updateOrderStatus(UpdateOrderStatusForm form)
   
@Override
@Transactional
@LcnTransaction
public int updateOrderStatus(UpdateOrderStatusForm form) {
   R r = odrServiceApi.updateOrderStatus(form);
   int rows = MapUtil.getInt(r, "rows");
   // TODO:判断订单的状态，然后实现后续业务
   return rows;
}

@PostMapping("/updateOrderStatus")
@SaCheckLogin
@Operation(summary = "更新订单状态")
public R updateOrderStatus(@RequestBody @Valid UpdateOrderStatusForm form) {
   int rows = orderService.updateOrderStatus(form);
   return R.ok().put("rows", rows);
}
```
5. 写 hxds-driver-wx/main.js
   补充 hxds-driver-wx/pages/workbench/workbench.vue#onLoad
   补充 hxds-driver-wx/pages/workbench/workbench.vue#startDrivingHandle
   写 hxds-driver-wx/pages/workbench/workbench.vue#endDrivingHandle
   【说明】同声传译插件可以实现录音，并且把录音中的语音部分转换成文本。我们先来看一下官方提供的API接口案例，[官方文档](https://mp.weixin.qq.com/wxopen/plugindevdoc?appid=wx069ba97219f66d99&token=61191740&lang=zh_CN)
   在测试的过程中，我们的小程序上传文件的请求会被积压到队列中，直到我们点击结束代驾按钮之后，订单状态变更成5，然后页面跳转。这时队列中的上传任务才会执行，然后你等上5分钟，去Minio的数据目录中看一眼，就能找到小程序上传的音频文件了。如果按照以往，真的是几秒钟音频文件就上传好了。唉，现在只能等待微信APP解决这个BUG了
```javascript
// index.js 同声传译示例
var plugin = requirePlugin("WechatSI")
let manager = plugin.getRecordRecognitionManager()
// 从语音中识别出文字，会执行该回调函数
manager.onRecognize = function(res) {
    console.log("current result", res.result)
}
// 录音结束的回调函数
manager.onStop = function(res) {
    console.log("record file path", res.tempFilePath)
    console.log("result", res.result)
}
// 开始录音的回调函数
manager.onStart = function(res) {
    console.log("成功开始录音识别", res)
}
// 出现异常的回调函数
manager.onError = function(res) {
    console.error("error msg", res.msg)
}
// 开始录音，并且识别中文语音内容
manager.start({duration:30000, lang: "zh_CN"})
```
```javascript
uploadRecordFile: `${baseUrl}/monitoring/uploadRecordFile`,
updateOrderStatus: `${baseUrl}/order/updateOrderStatus`,

let recordManager = plugin.getRecordRecognitionManager(); //初始化录音管理器
recordManager.onStop = function(resp) {
   if (that.workStatus == '开始代驾' && that.stopRecord == false) {
      that.recordManager.start({ duration: 20 * 1000, lang: 'zh_CN' });
   }
   let tempFilePath = resp.tempFilePath;
   //上传录音
   that.recordNum += 1;
   let data = {
      name: `${that.executeOrder.id}-${that.recordNum}.mp3`,
      text: resp.result
   };
   // console.log(data);
   that.upload(that.url.uploadRecordFile, tempFilePath, data, function(resp) {
      console.log('录音上传成功');
   });
};
recordManager.onStart = function(resp) {
   console.log('成功开始录音识别');
   if (that.recordNum == 0) {
      uni.vibrateLong({
         complete: function() {}
      });
      uni.showToast({
         icon: 'none',
         title: '请提示客户系上安全带！'
      });
   }
};
recordManager.onError = function(resp) {
   console.error('录音识别故障', resp.msg);
};
that.recordManager = recordManager;

startDrivingHandle: function() {
   let that = this;
   uni.showModal({
      title: '消息通知',
      content: '您已经接到客户，现在开始代驾？',
      success: function(resp) {
         if (resp.confirm) {
            //设置录音标志位
            that.stopRecord = false;
            let data = {
               orderId: that.executeOrder.id,
               customerId: that.executeOrder.customerId
            };
            that.ajax(that.url.startDriving, 'POST', data, function(resp) {
               if (resp.data.rows == 1) {
                  uni.showToast({
                     icon: 'success',
                     title: '订单状态更新成功'
                  });
                  that.workStatus = '开始代驾';
                  uni.setStorageSync('workStatus', '开始代驾');
                  //开始录音
                  that.recordManager.start({ duration: 20 * 1000, lang: 'zh_CN' });
               }
            });
         }
      }
   });
},

endDrivingHandle: function() {
   let that = this;
   uni.showModal({
      title: '消息通知',
      content: '已经到达终点，现在结束代驾？',
      success: function(resp) {
         if (resp.confirm) {
            let data = {
               orderId: that.executeOrder.id,
               customerId: that.executeOrder.customerId,
               status: 5
            };
            that.ajax(that.url.updateOrderStatus, 'POST', data, function(resp) {
               that.stopRecord = true;
               try {
                  that.recordManager.stop();
                  that.recordNum = 0;
                  that.stopRecord = false;
                  that.workStatus = '结束代驾';
                  uni.setStorageSync('workStatus', '结束代驾');
                  uni.navigateTo({
                     url: '../../order/enter_fee/enter_fee?orderId=' + that.executeOrder.id + '&customerId=' + that.executeOrder.customerId
                  });
               } catch (e) {
                  console.error(e);
               }
            });
         }
      }
   });
},
```
### 司机微服务打击刷单，禁止其他手机卡登陆司机小程序
上次测试司机端小程序，开始代驾之后确实能实时录制音频，并且同声传译插件可以把司乘对话内容提取出文字内容，最后音频文件保存在Minio中，对话内容保存在HBase里面。这一章我们还有个非常重要的功能要完成，那就是打击司机刷单行为。有同学可能会问：为什么只打击司机刷单，不去管管乘客的刷单呢？这很简单，因为司机有实名认证，而且还跟代驾平台签了合同，所以代驾平台可以处罚司机。但是乘客没有做实名认证，我们想处罚乘客也没有办法，所以还是把司机监管好吧

因为后续我们要写程序实现对刷单的监管，司机接单之后，只有到达上车点一公里以内的时候才可以点击到达上车点按钮。司机想要结束订单的时候，必须距离订单终点在两公里以内，才可以点击结束代驾按钮。这一公里和两公里，给乘客上车和下车留足了变通的余地。比如说乘客发现小区北门关闭了，那就只能绕道南门进入小区，这时候距离订单终点的距离在两公里以内是允许的。

如果司机想要在现有规则之下刷单，需要两个人相互配合，比如说A手机充当乘客下单，然后司机用B手机接单。A手机下单的上车点正好距离司机的位置在1公里以内，于是司机接单然后点击到达上车点。接下来，另一个人距离代驾终点在两公里以内，然后用C手机登陆司机小程序，点击代驾结束。你看看，两个人用3部手机，足不出户就可以刷单，这多简单啊。

为了避免上述情况，司机登陆小程序的时候，咱们必须要获取手机卡的手机号，然后跟实名认证的手机号做对比，如果手机号一致，就允许司机登陆。如果不一致，那就不允许登陆。这就能避免两部手机交替登陆司机账号的情况出现，刷单的漏洞也就堵死了。

大家目前的微信开发者账号是个人类型的，所以不能获取到用户的手机号，所以这两集的视频大家看一个流程即可。等到你有企业身份的账号之后，再去实现以下的功能也不迟。

个人开发账号不支持获取登陆小程序的手机号[传送门](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/getPhoneNumber.html)，本节略

### 利用地图服务，智能判断司机刷单行为 
本节实现实现排查司机刷单的行为。判断司机刷单的办法也很简单，司机点击到达上车点按钮的时候，司机端小程序通过腾讯地图服务的API，计算当前定位到上车点的距离。如果超过1公里，那就不可以。司机必须距离上车点在1公里以内，点击到达上车点才有效。当司机想要结束代驾的时候，距离代驾终点必须在两公里以内才可以，否则就无法结束代驾
1. 写 hxds-driver-wx/pages/workbench/workbench.vue#onLoad
   补充 hxds-driver-wx/pages/workbench/workbench.vue#arriveStartPlaceHandle
   补充 hxds-driver-wx/pages/workbench/workbench.vue#endDrivingHandle
```javascript
let QQMapWX = require('../../lib/qqmap-wx-jssdk.min.js');
let qqmapsdk
// 补充 onLoad
qqmapsdk = new QQMapWX({
   key: that.tencent.map.key
});
// 打注释即为补充部分
arriveStartPlaceHandle: function() {
   let that = this;
   uni.showModal({
      title: '消息通知',
      content: '确认已经到达了代驾点？',
      success: function(resp) {
         if (resp.confirm) {
            // qqmapsdk.calculateDistance({
            //     mode: 'straight',
            //     from: {
            //         latitude: that.latitude,
            //         longitude: that.longitude
            //     },
            //     to: [
            //         {
            //             latitude: that.executeOrder.startPlaceLocation.latitude,
            //             longitude: that.executeOrder.startPlaceLocation.longitude
            //         }
            //     ],
            //     success: function(resp) {
            //         let distance = resp.result.elements[0].distance;
            //         if (distance <= 1000) {
            let data = {
               orderId: that.executeOrder.id,
               customerId: that.executeOrder.customerId
            };
            that.ajax(that.url.arriveStartPlace, 'POST', data, function(resp) {
               if (resp.data.rows == 1) {
                  uni.showToast({
                     icon: 'success',
                     title: '订单状态更新成功'
                  });
                  that.workStatus = '到达代驾点';
                  uni.setStorageSync('workStatus', '到达代驾点');
               }
            });
            //         } else {
            //             uni.showToast({
            //                 icon: 'none',
            //                 title: '请移动到距离代驾起点1公里以内'
            //             });
            //         }
            //     },
            //     fail: function(error) {
            //         console.log(error);
            //     }
            // });
         }
      }
   });
},
// 打注释即为补充部分
endDrivingHandle: function() {
   let that = this;
   uni.showModal({
      title: '消息通知',
      content: '已经到达终点，现在结束代驾？',
      success: function(resp) {
         if (resp.confirm) {
            // qqmapsdk.calculateDistance({
            //     mode: 'straight',
            //     from: {
            //         latitude: that.latitude,
            //         longitude: that.longitude
            //     },
            //     to: [
            //         {
            //             latitude: that.executeOrder.endPlaceLocation.latitude,
            //             longitude: that.executeOrder.endPlaceLocation.longitude
            //         }
            //     ],
            //     success: function(resp) {
            //         let distance = resp.result.elements[0].distance;
            //         if (distance <= 2000) {
            let data = {
               orderId: that.executeOrder.id,
               customerId: that.executeOrder.customerId,
               status: 5
            };
            that.ajax(that.url.updateOrderStatus, 'POST', data, function(resp) {
               that.stopRecord = true;
               try {
                  that.recordManager.stop();
                  that.recordNum = 0;
                  that.stopRecord = false;
                  that.workStatus = '结束代驾';
                  uni.setStorageSync('workStatus', '结束代驾');
                  uni.navigateTo({
                     url: '../../order/enter_fee/enter_fee?orderId=' + that.executeOrder.id + '&customerId=' + that.executeOrder.customerId
                  });
               } catch (e) {
                  console.error(e);
               }
            });
            //         } else {
            //             uni.showToast({
            //                 icon: 'none',
            //                 title: '请移动到距离代驾终点2公里以内'
            //             });
            //         }
            //     },
            //     fail: function(error) {
            //         console.log(error);
            //     }
            // });
         }
      }
   });
},
```
## AI分析与订单监控（AI智能分析司乘对话内容，如有危害自动告警）

本章围绕订单监控来展开。代驾系统利用AI技术，分析司乘对话内容，如果存在暴力或者色情，系统自动告警或者转交人工处理。代驾系统的后台管理者，可以在Web端查验每笔订单的司乘对话内容，也可以收听具体的录音。无论后台报警还是移动端报警，Web系统会立即锁定司乘GPS定位，实时跟踪行进线路，并且把数据提交给警方

### 利用AI对司乘对话内容安全评级
1. 写 hxds-nebula/src/main/resources/mapper/OrderMonitoringDao.xml#insert 及其对应接口
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/service/MonitoringService.java#insertOrderMonitoring 及其实现类
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/db/pojo/InsertOrderMonitoringForm.java
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/controller/MonitoringController.java#insertOrderMonitoring
```java
 <insert id="insert" parameterType="long">
     UPSERT INTO hxds.order_monitoring("id","order_id","status","records","safety","reviews","alarm","create_time")
     VALUES(NEXT VALUE FOR hxds.om_sequence, #{orderId}, 1, 0, 'common', 0, 1, NOW())
 </insert>

int insertOrderMonitoring(long orderId);

@Override
@Transactional
public int insertOrderMonitoring(long orderId) {
     int rows = orderMonitoringDao.insert(orderId);
     if (rows != 1) {
        throw new HxdsException("添加订单监控摘要记录失败");
     }
     return rows;
}

@Data
@Schema(description = "添加订单监控摘要记录的表单")
public class InsertOrderMonitoringForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@PostMapping(value = "/insertOrderMonitoring")
@Operation(summary = "添加订单监控摘要记录")
public R insertOrderMonitoring(@RequestBody @Valid InsertOrderMonitoringForm form) {
     int rows = monitoringService.insertOrderMonitoring(form.getOrderId());
     return R.ok().put("rows", rows);
}
```
2. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/InsertOrderMonitoringForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/NebulaServiceApi.java#insertOrderMonitoring
   补充 bff-driver/src/main/java/com/example/hxds/bff/driver/service/impl/OrderServiceImpl.java#startDriving
```java
@Data
@Schema(description = "添加订单监控摘要记录的表单")
public class InsertOrderMonitoringForm {
    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;
}

@PostMapping(value = "/monitoring/insertOrderMonitoring")
R insertOrderMonitoring(InsertOrderMonitoringForm form);

@Override
@Transactional
@LcnTransaction
public int startDriving(StartDrivingForm form) {
   R r = odrServiceApi.startDriving(form);
   int rows = MapUtil.getInt(r, "rows");
   if (rows == 1){
      InsertOrderMonitoringForm monitoringForm = new InsertOrderMonitoringForm();
      monitoringForm.setOrderId(form.getOrderId());
      nebulaServiceApi.insertOrderMonitoring(monitoringForm);
      // TODO 发送通知消息
   }
   return rows;
}
```
2. 写 hxds-nebula/src/main/resources/mapper/OrderVoiceTextDao.xml 及其对应接口
   写 hxds-nebula/src/main/resources/mapper/OrderMonitoringDao.xml 及其对应接口
   补充 hxds-nebula/pom.xml
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/task/VoiceTextCheckTask.java#checkText
   补充 hxds-nebula/src/main/java/com/example/hxds/nebula/service/impl/MonitoringServiceImpl.java#monitoring
   【说明】因为调用数据万象接口审核文字内容，然后还要更新order_monitoring表中的记录，根据数据万象审核的结果更新订单的安全等级。这些操作都是需要消耗时间的，所以我们应该交给异步线程任务去做。业务层调用DAO，向order_voice_text插入记录，至于说调用数据万象审核文本内容，以及更新记录，这些就都交给异步线程去做。我们应该简单看一下数据万象的API文档（https://cloud.tencent.com/document/product/460/61607）。
```java
 <select id="searchIdByUuid" parameterType="String" resultType="Long">
     SELECT "id" FROM hxds.order_voice_text WHERE "uuid" = '${uuid}'
 </select>
 <update id="updateCheckResult" parameterType="Map">
     UPSERT INTO hxds.order_voice_text("id","label","suggestion","keywords")
     VALUES(#{id},'${label}','${suggestion}',
     <if test="keywords!=null">
         '${keywords}'
     </if>
     <if test="keywords==null">
         NULL
     </if>
     )
 </update>

Long searchIdByUuid(String uuid);

int updateCheckResult(Map param);

<select id="searchOrderRecordsAndReviews" parameterType="long" resultType="HashMap">
        SELECT "id",
        "records",
        "reviews"
        FROM hxds.order_monitoring
        WHERE "order_id" = #{orderId}
</select>
<update id="updateOrderMonitoring" parameterType="com.example.hxds.nebula.db.pojo.OrderMonitoringEntity">
        UPSERT INTO hxds.order_monitoring("id","order_id",
         <if test="status!=null">
                 "status",
         </if>
         <if test="safety!=null">
                 "safety",
         </if>
         <if test="reviews!=null">
                 "reviews",
         </if>
                 "records"
                 )
        VALUES(#{id}, #{orderId},
         <if test="status!=null">
                 #{status},
         </if>
         <if test="safety!=null">
                 #{safety},
         </if>
         <if test="reviews!=null">
                 #{reviews},
         </if>
                 #{records}
        )
</update>

HashMap searchOrderRecordsAndReviews(long orderId);

int updateOrderMonitoring(OrderMonitoringEntity entity);

<!--数据万象文本审查-->
<dependency>
   <groupId>com.qcloud</groupId>
   <artifactId>cos_api</artifactId>
   <version>5.6.74</version>
</dependency>

 @Async
 @Transactional
 public void checkText(long orderId, String content, String uuid) {
     String label = "Normal"; // 审核结果
     String suggestion = "Pass"; // 后续建议

     // 后续建议模板
     Map<String, String> template = new HashMap<>() {{
         put("0", "Pass");
         put("1", "Block");
         put("2", "Review");
     }};
     if (StrUtil.isNotBlank(content)) {
         BasicCOSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
         Region region = new Region("ap-beijing");
         ClientConfig config = new ClientConfig(region);
         COSClient client = new COSClient(credentials, config);
         TextAuditingRequest request = new TextAuditingRequest();
         request.setBucketName(bucketPublic);
         request.getInput().setContent(Base64.encode(content));
         request.getConf().setDetectType("all");
         TextAuditingResponse response = client.createAuditingTextJobs(request);
         AuditingJobsDetail detail = response.getJobsDetail();
         String state = detail.getState();
         ArrayList keywords = Lists.newArrayList();
         if ("Success".equals(state)) {
             label = detail.getLabel(); // 审核结果
             String result = detail.getResult(); // 检测结果
             suggestion = template.get(result); // 后续建议
             List<SectionInfo> list = detail.getSectionList();
             for (SectionInfo info : list) {
                 String keywords1 = info.getPornInfo().getKeywords();
                 String keywords2 = info.getIllegalInfo().getKeywords();
                 String keywords3 = info.getAbuseInfo().getKeywords();
                 if (keywords1.length() > 0) {
                     List<String> temp = Arrays.asList(keywords1.split(","));
                     keywords.addAll(temp);
                 } else if (keywords2.length() > 0) {
                     List<String> temp = Arrays.asList(keywords2.split(","));
                     keywords.addAll(temp);
                 } else if (keywords3.length() > 0) {
                     List<String> temp = Arrays.asList(keywords3.split(","));
                     keywords.addAll(temp);
                 }
             }
         }
         Long id = orderVoiceTextDao.searchIdByUuid(uuid);
         if (id == null) {
             throw new HxdsException("没有找到代驾语音文本记录");
         }
         HashMap param = new HashMap();
         param.put("id", id);
         param.put("label", label);
         param.put("suggestion", suggestion);
         param.put("keywords", ArrayUtil.join(keywords.toArray(), ","));
         int rows = orderVoiceTextDao.updateCheckResult(param);
         if (rows != 1) {
             throw new HxdsException("更新内容检查结果失败");
         }
         // 查询该订单中有多少个审核录音和需要人工审核的文本
         HashMap map = orderMonitoringDao.searchOrderRecordsAndReviews(orderId);
         id = MapUtil.getLong(map, "id");
         Integer records = MapUtil.getInt(map, "records");
         Integer reviews = MapUtil.getInt(map, "reviews");
         OrderMonitoringEntity entity = new OrderMonitoringEntity();
         entity.setId(id);
         entity.setOrderId(orderId);
         entity.setRecords(records + 1);
         if (suggestion.equals("Review")) {
             entity.setReviews(reviews + 1);
         }
         if (suggestion.equals("Block")) {
             entity.setSafety("danger");
         }
         // 更新 order_monitoring 表中的记录
         rows = orderMonitoringDao.updateOrderMonitoring(entity);
         if (rows != 1) {
             throw new HxdsException("更新订单监控记录失败");
         }
     }
 }

// 执行文稿内容审查
task.checkText(orderId, text, uuid);
```
3.【测试】为了更清晰的查看HBase中的数据和数据万象的审查结果，执行下面的SQL语句删除数据表中的记录。
```sql
delete from hxds.order_voice_text
delete from hxds.order_monitoring
```
把后端各个子系统都运行起来，司机端小程序点击开始代驾按钮，你对着手机正常说话聊天，持续一分钟之后，结束代驾。最后去看一下order_voice_text表和order_monitoring表的记录
```sql
select "order_id", "records", "safety", "reviews" from hxds.order_monitoring
select "order_id", "record_files", "label", "suggestion" from hxds.order_voice_text
```
### 大数据服务记录代驾途中GPS定位信息
1. 写 hxds-nebula/src/main/resources/mapper/OrderGpsDao.xml 及其对应接口
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/controller/vo/InsertOrderGpsVO.java
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/service/OrderGpsService.java 及其实现类
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/controller/form/InsertOrderGpsForm.java
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/controller/OrderGpsController.java
```java
 <insert id="insert" parameterType="com.example.hxds.nebula.db.pojo.OrderGpsEntity">
     UPSERT INTO hxds.order_gps("id", "order_id", "driver_id", "customer_id", "latitude", "longitude", "speed", "create_time")
     VALUES(NEXT VALUE FOR hxds.og_sequence, ${orderId}, ${driverId}, ${customerId}, '${latitude}', '${longitude}', '${speed}', NOW())
 </insert>

int insert(OrderGpsEntity orderGpsEntity);

@Data
public class InsertOrderGpsVO extends OrderGpsEntity {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

   @NotNull(message = "customerId不能为空")
   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;


   @NotBlank(message = "latitude不能为空")
   @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "latitude内容不正确")
   @Schema(description = "纬度")
   private String latitude;

   @NotBlank(message = "longitude不能为空")
   @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "longitude内容不正确")
   @Schema(description = "经度")
   private String longitude;

   @Schema(description = "速度")
   private String speed;
}

int insertOrderGps(List<InsertOrderGpsVO> list);

@Override
@Transactional
public int insertOrderGps(List<InsertOrderGpsVO> list) {
   int rows = 0;
   for (OrderGpsEntity entity : list) {
      rows += orderGpsDao.insert(entity);
   }
   return rows;
}

@Data
@Schema(description = "添加订单GPS记录的表单")
public class InsertOrderGpsForm {
   @NotEmpty(message = "list不能为空")
   @Schema(description = "GPS数据")
   private List<@Valid InsertOrderGpsVO> list;
}

@PostMapping("/insertOrderGps")
@Operation(summary = "添加订单GPS记录")
public R insertOrderGps(@RequestBody @Valid InsertOrderGpsForm form) {
   int rows = orderGpsService.insertOrderGps(form.getList());
   return R.ok().put("rows", rows);
}
```
2. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/vo/InsertOrderGpsVO.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/InsertOrderGpsForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/NebulaServiceApi.java#insertOrderGps
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/OrderGpsService.java 及其实现类
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/OrderGpsController.java
```java
@Data
public class InsertOrderGpsVO {
    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "司机ID")
    private Long driverId;

    @NotNull(message = "customerId不能为空")
    @Min(value = 1, message = "customerId不能小于1")
    @Schema(description = "客户ID")
    private Long customerId;

    @NotBlank(message = "latitude不能为空")
    @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "latitude内容不正确")
    @Schema(description = "纬度")
    private String latitude;

    @NotBlank(message = "longitude不能为空")
    @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "longitude内容不正确")
    @Schema(description = "经度")
    private String longitude;

    @Schema(description = "速度")
    private String speed;
}

@Data
@Schema(description = "添加订单GPS记录的表单")
public class InsertOrderGpsForm {
   @NotEmpty(message = "list不能为空")
   @Schema(description = "GPS数据")
   private List<@Valid InsertOrderGpsVO> list;
}

@PostMapping("/order/gps/insertOrderGps")
R insertOrderGps(InsertOrderGpsForm form);

int insertOrderGps(InsertOrderGpsForm form);

@Override
public int insertOrderGps(InsertOrderGpsForm form) {
   R r = nebulaServiceApi.insertOrderGps(form);
   int rows = MapUtil.getInt(r, "rows");
   return rows;
}

@PostMapping("/insertOrderGps")
@SaCheckLogin
@Operation(summary = "添加订单GPS记录")
public R insertOrderGps(@RequestBody @Valid InsertOrderGpsForm form){
   long driverId = StpUtil.getLoginIdAsLong();
   form.getList().forEach(one->{
      one.setDriverId(driverId);
   });
   int rows = orderGpsService.insertOrderGps(form);
   return R.ok().put("rows",rows);
}
```
3. 写 hxds-driver-wx/App.vue#onLaunch
【说明】开始代驾之后，司机端实时上传GPS定位，以前我们做过赶往上车点途中上传定位的功能，那可是实时上传。但是开始代驾之后，不需要实时上传，我们要把网络资源腾出来给同声传译插件和上传录音的Ajax请求。所以我打算在本地先缓存GPS定位，然后积累多了，再批量上传
```javascript
} else if (workStatus == '开始代驾') {
    //每凑够20个定位就上传一次，减少服务器的压力
    let executeOrder = uni.getStorageSync('executeOrder');
    if (executeOrder != null) {
        gps.push({
            orderId: executeOrder.id,
            customerId: executeOrder.customerId,
            latitude: latitude,
            longitude: longitude,
            speed: speed
        });
        if (gps.length == 5) {
            uni.request({
                url: `${baseUrl}/order/gps/insertOrderGps`,
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
                    } else if (resp.statusCode == 200 && resp.data.code == 200) {
                        let data = resp.data;
                        console.log('上传GPS成功');
                    } else {
                        console.error('保存GPS定位失败', resp.data);
                    }
                    gps.length = 0;
                },
                fail: function(error) {
                    console.error('保存GPS定位失败', error);
                }
            });
        }
    }
}
```
### 订单微服务中查询执行中订单信息
1. 写 hxds-odr/src/main/resources/mapper/OrderDao.xml 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java#searchOrderByPage 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/SearchOrderByPageForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#searchOrderByPage
```java
 <select id="searchOrderByPage" parameterType="Map" resultType="HashMap">
     SELECT CAST(id AS CHAR) AS id,
     CAST(start_place AS CHAR) AS startPlace,
     CAST(end_place AS CHAR) AS endPlace,
     CAST(real_mileage AS CHAR) AS realMileage,
     CAST(real_fee AS CHAR) AS realFee,
     `status`,
     DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime
     FROM tb_order
     WHERE 1 = 1
     <if test="orderId!=null">
         AND id = #{orderId}
     </if>
     <if test="customerId!=null">
         AND customer_id = #{customerId}
     </if>
     <if test="driverId!=null">
         AND driver_id = #{driverId}
     </if>
     <if test="startDate!=null and endDate!=null">
         AND date BETWEEN #{startDate} AND #{endDate}
     </if>
     <if test="status!=null">
         AND `status` = #{status}
     </if>
     ORDER BY id DESC
     LIMIT #{start},#{length}
 </select>
 <select id="searchOrderCount" parameterType="Map" resultType="long">
     SELECT COUNT(*)
     FROM tb_order
     WHERE 1 = 1
     <if test="orderId!=null">
         AND id = #{orderId}
     </if>
     <if test="customerId!=null">
         AND customer_id = #{customerId}
     </if>
     <if test="driverId!=null">
         AND driver_id = #{driverId}
     </if>
     <if test="startDate!=null and endDate!=null">
         AND date BETWEEN #{startDate} AND #{endDate}
     </if>
     <if test="status!=null">
         AND `status` = #{status}
     </if>
 </select>

long searchOrderCount(Map param);

List<HashMap> searchOrderByPage(Map param);

PageUtils searchOrderByPage(Map param);

@Override
public PageUtils searchOrderByPage(Map param) {
     long count = orderDao.searchOrderCount(param);
     List<HashMap> list = null;
     if (count == 0) {
        list = Lists.newArrayList();
     } else {
        list = orderDao.searchOrderByPage(param);
     }
     Integer start = (Integer) param.get("start");
     Integer length = (Integer) param.get("length");
     PageUtils pageUtils = new PageUtils(list, count, start, length);
     return pageUtils;
}

@Data
@Schema(description = "查询订单分页记录的表单")
public class SearchOrderByPageForm {
   @NotNull(message = "page不能为空")
   @Min(value = 1, message = "page不能小于1")
   @Schema(description = "页数")
   private Integer page;

   @NotNull(message = "length不能为空")
   @Range(min = 10, max = 50, message = "length必须在10~50之间")
   @Schema(description = "每页记录数")
   private Integer length;

   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;

   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

   @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$",
           message = "startDate内容不正确")
   @Schema(description = "开始日期")
   private String startDate;

   @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$",
           message = "endDate内容不正确")
   @Schema(description = "结束日期")
   private String endDate;

   @Range(min = 1, max = 12, message = "status范围不正确")
   @Schema(description = "状态")
   private Byte status;
}

@PostMapping("/searchOrderByPage")
@Operation(summary = "查询订单分页记录")
public R searchOrderByPage(@RequestBody @Valid SearchOrderByPageForm form) {
   Map param = BeanUtil.beanToMap(form);
   int page = form.getPage();
   int length = form.getLength();
   int start = (page - 1) * length;
   param.put("start", start);
   PageUtils pageUtils = orderService.searchOrderByPage(param);
   return R.ok().put("result", pageUtils);
}
```
2. 写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchOrderByPageForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/feign/OdrServiceApi.java#searchOrderByPage
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/service/OrderService.java#searchOrderByPage 及其实现类
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/OrderController.java#searchOrderByPage
```java
@Data
@Schema(description = "查询订单分页记录的表单")
public class SearchOrderByPageForm {
    @NotNull(message = "page不能为空")
    @Min(value = 1, message = "page不能小于1")
    @Schema(description = "页数")
    private Integer page;

    @NotNull(message = "length不能为空")
    @Range(min = 10, max = 50, message = "length必须在10~50之间")
    @Schema(description = "每页记录数")
    private Integer length;

    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @Min(value = 1, message = "customerId不能小于1")
    @Schema(description = "客户ID")
    private Long customerId;

    @Min(value = 1, message = "driverId不能小于1")
    @Schema(description = "司机ID")
    private Long driverId;

    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$",
            message = "startDate内容不正确")
    @Schema(description = "开始日期")
    private String startDate;

    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$",
            message = "endDate内容不正确")
    @Schema(description = "结束日期")
    private String endDate;

    @Range(min = 1, max = 12, message = "status范围不正确")
    @Schema(description = "状态")
    private Byte status;
}

@PostMapping("/order/searchOrderByPage")
R searchOrderByPage(SearchOrderByPageForm form);

PageUtils searchOrderByPage(SearchOrderByPageForm form);

@Override
public PageUtils searchOrderByPage(SearchOrderByPageForm form) {
   R r = odrServiceApi.searchOrderByPage(form);
   PageUtils pageUtils = BeanUtil.toBean(r.get("result"), PageUtils.class);
   return pageUtils;
}

@PostMapping("/searchOrderByPage")
@SaCheckPermission(value = {"ROOT", "ORDER:SELECT"}, mode = SaMode.OR)
@Operation(summary = "查询订单分页记录")
public R searchOrderByPage(@RequestBody @Valid SearchOrderByPageForm form){
   PageUtils pageUtils = orderService.searchOrderByPage(form);
   return R.ok().put("result",pageUtils);
}
```
3. 写 hxds-mis-vue/src/views/order.vue
```javascript
 loadDataList(){
   let that = this;
   let data = {
     page: that.pageIndex,
     length: that.pageSize,
     orderId: that.dataForm.orderId == ''? null:that.dataForm.orderId,
     driverId: that.dataForm.driverId == ''? null:that.dataForm.driverId,
     customId: that.dataForm.customId == ''? null:that.dataForm.customId,
     status: that.dataForm.status == '' ? null : that.dataForm.status
   }
   if (that.dataForm.date != null && that.dataForm.date.length == 2) {
     let startDate = that.dataForm.date[0];
     let endDate = that.dataForm.date[1];
     data.startDate = dayjs(startDate).format('YYYY-MM-DD');
     data.endDate = dayjs(endDate).format('YYYY-MM-DD');
   }
   that.$http('order/searchOrderByPage', 'POST', data, true, function(resp) {
     let result = resp.result;
     let list = result.list;
     for (let one of list) {
       one.status = status[one.status + ''];
       if (!one.hasOwnProperty('realMileage')) {
         one.realMileage = '--';
       }
       if (!one.hasOwnProperty('realFee')) {
         one.realFee = '--';
       }
     }
     that.dataList = list;
     that.totalCount = Number(result.totalCount);
     that.dataListLoading = false;
   });
 },
 sizeChangeHandle(val) {
   this.pageSize = val;
   this.pageIndex = 1;
   this.loadDataList();
 },
 currentChangeHandle(val) {
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
 loadPanelData: function(ref, row) {
   let data = {
     orderId: row.id
   };
   ref.$http('order/searchOrderComprehensiveInfo', 'POST', data, true, function(resp) {
     let result = resp.result;
     let content = result.content;

     let customerInfo = result.customerInfo;
     ref.panel.customer.id = customerInfo.id;
     ref.panel.customer.sex = customerInfo.sex;
     ref.panel.customer.tel = customerInfo.tel;

     let driverInfo = result.driverInfo;
     ref.panel.driver.id = driverInfo.id;
     ref.panel.driver.name = driverInfo.name;
     ref.panel.driver.tel = driverInfo.tel;

     ref.panel.order.carPlate = content.carPlate;
     ref.panel.order.carType = content.carType;
     let city = calculateCarPlateCity(content.carPlate);
     ref.panel.order.city = city;

     if (content.hasOwnProperty('acceptTime')) {
       ref.panel.order.acceptTime = content.acceptTime;
     } else {
       ref.panel.order.acceptTime = '--';
     }
     if (content.hasOwnProperty('arriveTime')) {
       ref.panel.order.arriveTime = content.arriveTime;
     } else {
       ref.panel.order.arriveTime = '--';
     }
     if (content.hasOwnProperty('startTime')) {
       ref.panel.order.startTime = content.startTime;
     } else {
       ref.panel.order.startTime = '--';
     }
     if (content.hasOwnProperty('endTime')) {
       ref.panel.order.endTime = content.endTime;
     } else {
       ref.panel.order.endTime = '--';
     }
     if (content.hasOwnProperty('waitingMinute')) {
       ref.panel.order.waitingMinute = content.waitingMinute + '分钟';
     } else {
       ref.panel.order.waitingMinute = '--';
     }
     if (content.hasOwnProperty('driveMinute')) {
       ref.panel.order.driveMinute = content.driveMinute + '分钟';
     } else {
       ref.panel.order.driveMinute = '--';
     }
     if (content.hasOwnProperty('realMileage')) {
       ref.panel.order.realMileage = content.realMileage + '公里';
       row.realMileage = content.realMileage;
     } else {
       ref.panel.order.realMileage = '--';
       row.realMileage = '--';
     }
     if (content.hasOwnProperty('realFee')) {
       ref.panel.order.realFee = content.realFee + '元';
       row.realFee = content.realFee;
     } else {
       ref.panel.order.realFee = '--';
       row.realFee = '--';
     }
     ref.panel.order.status = status[content.status + ''];
     row.status = status[content.status + ''];
     if (result.hasOwnProperty('chargeRule')) {
       ref.panel.order.chargeRule = result.chargeRule.code;
     } else {
       ref.panel.order.chargeRule = '--';
     }
     if (result.hasOwnProperty('cancelRule')) {
       ref.panel.order.cancelRule = result.cancelRule.code;
     } else {
       ref.panel.order.cancelRule = '--';
     }
     if (result.hasOwnProperty('profitsharingRule')) {
       ref.panel.order.profitsharingRule = result.profitsharingRule.code;
     } else {
       ref.panel.order.profitsharingRule = '--';
     }

     ref.$nextTick(function() {
       let startPlaceLocation = content.startPlaceLocation;
       let endPlaceLocation = content.endPlaceLocation;
       let mapCenter = new TMap.LatLng(startPlaceLocation.latitude, startPlaceLocation.longitude);
       let map = new TMap.Map($(`.el-table__expanded-cell #order_${row.id}`)[0], {
         center: mapCenter, //地图显示中心点
         zoom: 13,
         viewMode: '2D',
         baseMap: {
           type: 'vector',
           features: ['base', 'label']
         }
       });

       let driveLine = result.driveLine;
       let coors = driveLine.routes[0].polyline;
       let pl = [];
       //坐标解压（返回的点串坐标，通过前向差分进行压缩，因此需要解压）
       let kr = 1000000;
       for (let i = 2; i < coors.length; i++) {
         coors[i] = Number(coors[i - 2]) + Number(coors[i]) / kr;
       }
       //将解压后的坐标生成LatLng数组
       for (let i = 0; i < coors.length; i += 2) {
         pl.push(new TMap.LatLng(coors[i], coors[i + 1]));
       }

       let polylineLayer = new TMap.MultiPolyline({
         id: 'polyline-layer', //图层唯一标识
         map: map, //绘制到目标地图
         styles: {
           style_blue: new TMap.PolylineStyle({
             color: 'rgba(190,188,188,1)',
             width: 6,
             lineCap: 'round' //线端头方式
           })
         },
         geometries: [
           {
             id: 'pl_1',
             styleId: 'style_blue',
             paths: pl
           }
         ]
       });

       let markerLayer = new TMap.MultiMarker({
         map: map,
         styles: {
           startStyle: new TMap.MarkerStyle({
             width: 24,
             height: 36,
             anchor: { x: 16, y: 32 },
             src: 'https://mapapi.qq.com/web/lbs/javascriptGL/demo/img/start.png'
           }),
           endStyle: new TMap.MarkerStyle({
             width: 24,
             height: 36,
             anchor: { x: 16, y: 32 },
             src: 'https://mapapi.qq.com/web/lbs/javascriptGL/demo/img/end.png'
           }),
           carStyle: new TMap.MarkerStyle({
             width: 30,
             height: 30,
             src: '../order/driver-icon.png',
             anchor: { x: 16, y: 32 }
           })
         },
         geometries: [
           //起点标记
           {
             id: '1',
             styleId: 'startStyle',
             position: new TMap.LatLng(startPlaceLocation.latitude, startPlaceLocation.longitude)
           },
           //终点标记
           {
             id: '2',
             styleId: 'endStyle',
             position: new TMap.LatLng(endPlaceLocation.latitude, endPlaceLocation.longitude)
           }
         ]
       });
       if (content.status == 4) {
         let lastGps = result.lastGps;
         markerLayer.add([
           {
             id: '3',
             styleId: 'carStyle', //指定样式id
             position: new TMap.LatLng(lastGps.latitude, lastGps.longitude)
           }
         ]);
         ref.gpsTimer = setInterval(function() {
           let data = {
             orderId: row.id
           };
           ref.$http('order/searchOrderLastGps', 'POST', data, true, function(resp) {
             if (resp.hasOwnProperty('result')) {
               let lastGps = resp.result;
               markerLayer.updateGeometries([
                 {
                   id: '3',
                   styleId: 'carStyle',
                   position: new TMap.LatLng(lastGps.latitude, lastGps.longitude)
                 }
               ]);
             } else {
               //重新加载面板数据
               $(`.el-table__expanded-cell #order_${row.id}`).empty();
               ref.loadPanelData(ref, row);
               clearInterval(ref.gpsTimer);
             }
           });
         }, 15 * 1000);
       } else if (content.status >= 5 && content.status <= 8) {
         let orderGps = result.orderGps;
         let paths = [];
         for (let one of orderGps) {
           let temp = new TMap.LatLng(one.latitude, one.longitude);
           paths.push(temp);
         }
         let polylineLayer = new TMap.MultiPolyline({
           id: 'drive-polyline-layer', //图层唯一标识
           map: map,
           //折线样式定义
           styles: {
             style_blue: new TMap.PolylineStyle({
               color: '#3777FF', //线填充色
               width: 6 //折线宽度
             })
           },
           //折线数据定义
           geometries: [
             {
               id: 'pl_1',
               styleId: 'style_blue',
               paths: paths
             }
           ]
         });
       }
     });
   });
 },
 expand: function(row, expandedRows) {
   let that = this;
   if (expandedRows.length > 0) {
     that.expands = [];
     if (row) {
       that.expands.push(row.id);
       that.panel.id = `order_${row.id}`;
       that.loadPanelData(that, row);
     } else {
       that.expands = [];
     }
   }
 }
```
### 展示详情最佳线路与实际线路
1. 写 hxds-odr/src/main/java/com/example/hxds/odr/db/dao/OrderDao.xml 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/mis/api/service/OrderService.java#searchOrderContent 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/SearchOrderContentForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController#searchOrderContent
```java
HashMap searchOrderContent(long orderId);

<select id="searchOrderContent" parameterType="long" resultType="HashMap">
        SELECT CAST(o.driver_id AS CHAR)                      AS driverId,
        CAST(o.customer_id AS CHAR)                    AS customerId,
        o.car_plate                                    AS carPlate,
        o.car_type                                     AS carType,
        DATE_FORMAT(o.accept_time, '%Y-%m-%d %H:%i')   AS acceptTime,
        DATE_FORMAT(o.arrive_time, '%Y-%m-%d %H:%i')   AS arriveTime,
        DATE_FORMAT(o.start_time, '%Y-%m-%d %H:%i')    AS startTime,
        DATE_FORMAT(o.end_time, '%Y-%m-%d %H:%i')      AS endTime,
        o.waiting_minute                               AS waitingMinute,
        TIMESTAMPDIFF(MINUTE,o.start_time, o.end_time) AS `driveMinute`,
        CAST(o.real_mileage AS CHAR)                   AS realMileage,
        CAST(o.real_fee AS CHAR)                       AS realFee,
        o.`status`,
        CAST(o.charge_rule_id AS CHAR)                 AS chargeRuleId,
        CAST(o.cancel_rule_id AS CHAR)                 AS cancelRuleId,
        CAST(p.rule_id AS CHAR)                        AS profitsharingRuleId,
        o.start_place_location                         AS startPlaceLocation,
        o.end_place_location                           AS endPlaceLocation
        FROM tb_order o
        LEFT JOIN tb_order_profitsharing p ON o.id = p.order_id
        WHERE o.id = #{orderId}
</select>
        
HashMap searchOrderContent(long orderId);

@Override
public HashMap searchOrderContent(long orderId) {
     HashMap map = orderDao.searchOrderContent(orderId);
     JSONObject startPlaceLocation = JSONUtil.parseObj(MapUtil.getStr(map, "startPlaceLocation"));
     JSONObject endPlaceLocation = JSONUtil.parseObj(MapUtil.getStr(map, "endPlaceLocation"));
     map.put("startPlaceLocation", startPlaceLocation);
     map.put("endPlaceLocation", endPlaceLocation);
     return map;
}

@Data
@Schema(description = "查询订单详情的表单")
public class SearchOrderContentForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@PostMapping("/searchOrderContent")
@Operation(summary = "查询订单详情")
public R searchOrderContent(@RequestBody @Valid SearchOrderContentForm form) {
   HashMap map = orderService.searchOrderContent(form.getOrderId());
   return R.ok().put("result", map);
}
```
2. 写 hxds-cst/src/main/resources/mapper/CustomerDao.xml 及其对应接口
   写 hxds-cst/src/main/java/com/example/hxds/cst/service/CustomerService.java#searchCustomerBriefInfo 及其实现类
   写 hxds-cst/src/main/java/com/example/hxds/cst/controller/form/SearchCustomerBriefInfoForm.java
   写 hxds-cst/src/main/java/com/example/hxds/cst/controller/CustomerController.java#searchCustomerBriefInfo
```java
HashMap searchCustomerBriefInfo(long customerId);

<select id="searchCustomerBriefInfo" parameterType="long" resultType="HashMap">
     SELECT CAST(id AS CHAR) AS id,
     sex,
     tel
     FROM tb_customer
     WHERE id = #{customerId};
</select>

HashMap searchCustomerBriefInfo(long customerId);

@Override
public HashMap searchCustomerBriefInfo(long customerId) {
     HashMap map = customerDao.searchCustomerBriefInfo(customerId);
     return map;
}

@Data
@Schema(description = "查询客户简明信息的表单")
public class SearchCustomerBriefInfoForm {
   @NotNull(message = "customerId不能为空")
   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;
}

@PostMapping("/searchCustomerBriefInfo")
@Operation(summary = "查询客户简明信息")
public R searchCustomerBriefInfo(@RequestBody @Valid SearchCustomerBriefInfoForm form) {
     HashMap map = customerService.searchCustomerBriefInfo(form.getCustomerId());
     return R.ok().put("result", map);
}
```
3. 写 hxds-dr/src/main/resources/mapper/DriverDao.xml#searchDriverBriefInfo 及其对应接口
   写 hxds-dr/src/main/java/com/example/hxds/dr/service/CustomerService.java#searchDriverBriefInfo 及其实现类
   写 hxds-dr/src/main/java/com/example/hxds/dr/controller/form/SearchDriverBriefInfoForm.java
   写 hxds-dr/src/main/java/com/example/hxds/dr/controller/DriverController.java#searchDriverBriefInfo
```java
<select id="searchDriverBriefInfo" parameterType="long" resultType="HashMap">
     SELECT CAST(id AS CHAR) AS id,
     `name`,
     tel,
     FROM tb_driver
     WHERE id = #{driverId}
</select>

HashMap searchDriverBriefInfo(long driverId);

HashMap searchDriverBriefInfo(long driverId);

@Override
public HashMap searchDriverBriefInfo(long driverId) {
     HashMap map = driverDao.searchDriverBriefInfo(driverId);
     return map;
}

@Data
@Schema(description = "查询司机简明信息的表单")
public class SearchDriverBriefInfoForm {
   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
}

@PostMapping("/searchDriverBriefInfo")
@Operation(summary = "查询司机简明信息")
public R searchDriverBriefInfo(@RequestBody @Valid SearchDriverBriefInfoForm form) {
     HashMap map = driverService.searchDriverBriefInfo(form.getDriverId());
     return R.ok().put("result", map);
}
```
4. 写 hxds-nebula/src/main/resources/mapper/OrderGpsDao.xml 及其对应接口
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/service/OrderGpsService.java 及其实现类
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/controller/form/SearchOrderGpsForm.java
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/controller/form/SearchOrderLastGpsForm.java
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/controller/OrderGpsController.java
```java
List<HashMap> searchOrderGps(long orderId);

HashMap searchOrderLastGps(long orderId);

<select id="searchOrderGps" parameterType="long" resultType="HashMap">
        SELECT "id",
        "latitude",
        "longitude",
        TO_CHAR("create_time",'yyyy-MM-dd HH:mm:ss') AS "createTime"
        FROM hxds.order_gps
        WHERE "order_id" = #{orderId}
</select>
<select id="searchOrderLastGps" parameterType="long" resultType="HashMap">
        SELECT "id",
        "latitude",
        "longitude",
        TO_CHAR("create_time",'yyyy-MM-dd HH:mm:ss') AS "createTime"
        FROM hxds.order_gps
        WHERE "order_id" = #{orderId}
        ORDER BY "id" DESC
        LIMIT 1
</select>

List<HashMap> searchOrderGps(long orderId);

HashMap searchOrderLastGps(long orderId);

@Override
public List<HashMap> searchOrderGps(long orderId) {
    List<HashMap> list = orderGpsDao.searchOrderGps(orderId);
     return list;
}

@Override
public HashMap searchOrderLastGps(long orderId) {
     HashMap map = orderGpsDao.searchOrderLastGps(orderId);
     return map;
}

@Data
@Schema(description = "获取某个订单的GPS定位的表单")
public class SearchOrderGpsForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@Data
@Schema(description = "获取某个订单最后的GPS定位的表单")
public class SearchOrderLastGpsForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@PostMapping("/searchOrderGps")
@Operation(summary = "获取某个订单所有的GPS定位")
public R searchOrderGps(@RequestBody @Valid SearchOrderGpsForm form){
   ArrayList<HashMap> list = orderGpsService.searchOrderGps(form.getOrderId());
   return R.ok().put("result",list);
}

@PostMapping("/searchOrderLastGps")
@Operation(summary = "获取某个订单最后的GPS定位")
public R searchOrderLastGps(@RequestBody @Valid SearchOrderLastGpsForm form){
   HashMap map = orderGpsService.searchOrderLastGps(form.getOrderId());
   return R.ok().put("result",map);
}
```
5. 写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchOrderContentForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/feign/OdrServiceApi.java#searchOrderContent
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchCustomerBriefInfoForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/feign/CstServiceApi.java#searchCustomerBriefInfo
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchDriverBriefInfoForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/feign/DrServiceApi.java#searchDriverBriefInfo
```java
@Data
@Schema(description = "查询订单详情的表单")
public class SearchOrderContentForm {
    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;
}

@PostMapping("/order/searchOrderContent")
R searchOrderContent(SearchOrderContentForm form);

@Data
@Schema(description = "查询客户简明信息的表单")
public class SearchCustomerBriefInfoForm {
   @NotNull(message = "customerId不能为空")
   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;
}

@PostMapping("/customer/searchCustomerBriefInfo")
R searchCustomerBriefInfo(SearchCustomerBriefInfoForm form);

@Data
@Schema(description = "查询司机简明信息的表单")
public class SearchDriverBriefInfoForm {
   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
}

@PostMapping("/driver/searchDriverBriefInfo")
R searchDriverBriefInfo(SearchDriverBriefInfoForm form);
```
6. 写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchChargeRuleByIdForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchCancelRuleByIdForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchProfitsharingRuleByIdForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/feign/RuleServiceApi.java
```java
@Data
@Schema(description = "根据ID查询费用规则的表单")
public class SearchChargeRuleByIdForm {
    @NotNull(message = "ruleId不为空")
    @Min(value = 1, message = "ruleId不能小于1")
    @Schema(description = "规则ID")
    private Long ruleId;
}

@Data
@Schema(description = "根据ID查询取消规则的表单")
public class SearchCancelRuleByIdForm {
   @NotNull(message = "ruleId不为空")
   @Min(value = 1, message = "ruleId不能小于1")
   @Schema(description = "规则ID")
   private Long ruleId;
}

@Data
@Schema(description = "根据ID查询分账规则的表单")
public class SearchProfitsharingRuleByIdForm {
   @NotNull(message = "ruleId不为空")
   @Min(value = 1, message = "ruleId不能小于1")
   @Schema(description = "规则ID")
   private Long ruleId;
}

@FeignClient(value = "hxds-rule")
public interface RuleServiceApi {
   @PostMapping("/charge/searchChargeRuleById")
   R searchChargeRuleById(SearchChargeRuleByIdForm form);

   @PostMapping("/cancel/searchCancelRuleById")
   R searchCancelRuleById(SearchCancelRuleByIdForm form);

   @PostMapping("/profitsharing/searchProfitsharingRuleById")
   R searchProfitsharingRuleById(SearchProfitsharingRuleByIdForm form);
}
```
7. 写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/CalculateDriveLineForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/feign/MpsServiceApi.java
```java
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

@FeignClient(value = "hxds-mps")
public interface MpsServiceApi {
   @PostMapping("/map/calculateDriveLine")
   R calculateDriveLine(CalculateDriveLineForm form);
}
```
8. 写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchOrderGpsForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchOrderLastGpsForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/feign/NebulaServiceApi.java
```java
@Data
@Schema(description = "获取某个订单的GPS定位的表单")
public class SearchOrderGpsForm {
    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;
}

@Data
@Schema(description = "获取某个订单最后的GPS定位的表单")
public class SearchOrderLastGpsForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@FeignClient(value = "hxds-nebula")
public interface NebulaServiceApi {
   @PostMapping("/order/gps/searchOrderGps")
   R searchOrderGps(SearchOrderGpsForm form);

   @PostMapping("/order/gps/searchOrderLastGps")
   R searchOrderLastGps(SearchOrderLastGpsForm form);
}
```
9. 写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/service/OrderService.java#searchOrderComprehensiveInfo 及其实现类
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchOrderComprehensiveInfoForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/OrderController.java#searchOrderComprehensiveInfo
```java
Map searchOrderComprehensiveInfo(long orderId);

@Override
public Map searchOrderComprehensiveInfo(long orderId) {
    Map map = new HashMap();

    SearchOrderContentForm form_1 = new SearchOrderContentForm();
    form_1.setOrderId(orderId);
    R r = odrServiceApi.searchOrderContent(form_1);
    if (!r.containsKey("result")) {
        throw new HxdsException("不存在订单记录");
    }
    HashMap content = (HashMap) r.get("result");
    map.put("content", content);

    long customerId = MapUtil.getLong(content, "customerId");
    SearchCustomerBriefInfoForm form_2 = new SearchCustomerBriefInfoForm();
    form_2.setCustomerId(customerId);
    r = cstServiceApi.searchCustomerBriefInfo(form_2);
    HashMap customerInfo = (HashMap) r.get("result");
    map.put("customerInfo", customerInfo);

    long driverId = MapUtil.getLong(content, "driverId");
    SearchDriverBriefInfoForm form_3 = new SearchDriverBriefInfoForm();
    form_3.setDriverId(driverId);
    r = drServiceApi.searchDriverBriefInfo(form_3);
    HashMap driverInfo = (HashMap) r.get("result");
    map.put("driverInfo", driverInfo);

    if (content.containsKey("chargeRuleId")) {
        long chargeRuleId = MapUtil.getLong(content, "chargeRuleId");
        SearchChargeRuleByIdForm form_4 = new SearchChargeRuleByIdForm();
        form_4.setRuleId(chargeRuleId);
        r = ruleServiceApi.searchChargeRuleById(form_4);
        HashMap chargeRule = (HashMap) r.get("result");
        map.put("chargeRule", chargeRule);
    }

    if (content.containsKey("cancelRuleId")) {
        long cancelRuleId = MapUtil.getLong(content, "cancelRuleId");
        SearchCancelRuleByIdForm form_5 = new SearchCancelRuleByIdForm();
        form_5.setRuleId(cancelRuleId);
        r = ruleServiceApi.searchCancelRuleById(form_5);
        HashMap cancelRule = (HashMap) r.get("result");
        map.put("cancelRule", cancelRule);
    }

    if (content.containsKey("profitsharingRuleId")) {
        long profitsharingRuleId = MapUtil.getLong(content, "profitsharingRuleId");
        SearchProfitsharingRuleByIdForm form_6 = new SearchProfitsharingRuleByIdForm();
        form_6.setRuleId(profitsharingRuleId);
        r = ruleServiceApi.searchProfitsharingRuleById(form_6);
        HashMap profitsharingRule = (HashMap) r.get("result");
        map.put("profitsharingRule", profitsharingRule);
    }

    CalculateDriveLineForm form_7 = new CalculateDriveLineForm();
    HashMap startPlaceLocation = (HashMap) content.get("startPlaceLocation");
    HashMap endPlaceLocation = (HashMap) content.get("endPlaceLocation");
    form_7.setStartPlaceLatitude(MapUtil.getStr(startPlaceLocation, "latitude"));
    form_7.setStartPlaceLongitude(MapUtil.getStr(startPlaceLocation, "longitude"));
    form_7.setEndPlaceLatitude(MapUtil.getStr(endPlaceLocation, "latitude"));
    form_7.setEndPlaceLongitude(MapUtil.getStr(endPlaceLocation, "longitude"));
    r = mpsServiceApi.calculateDriveLine(form_7);
    HashMap driveLine = (HashMap) r.get("result");
    map.put("driveLine", driveLine);

    int status = MapUtil.getInt(content, "status");
    if (status >= 5 && status <= 8) {
        SearchOrderGpsForm form_8 = new SearchOrderGpsForm();
        form_8.setOrderId(orderId);
        r = nebulaServiceApi.searchOrderGps(form_8);
        ArrayList<HashMap> orderGps = (ArrayList<HashMap>) r.get("result");
        map.put("orderGps", orderGps);
    } else if (status == 4) {
        SearchOrderLastGpsForm form_9 = new SearchOrderLastGpsForm();
        form_9.setOrderId(orderId);
        r = nebulaServiceApi.searchOrderLastGps(form_9);
        HashMap lastGps = (HashMap) r.get("result");
        map.put("lastGps", lastGps);
    }
    return map;
}

@Data
@Schema(description = "查询订单综合信息")
public class SearchOrderComprehensiveInfoForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@PostMapping("/searchOrderComprehensiveInfo")
@SaCheckPermission(value = {"ROOT", "ORDER:SELECT"}, mode = SaMode.OR)
@Operation(summary = "查询订单")
public R searchOrderComprehensiveInfo(@RequestBody @Valid SearchOrderComprehensiveInfoForm form){
   Map map = orderService.searchOrderComprehensiveInfo(form.getOrderId());
   return R.ok().put("result",map);
}
```
10. 写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchOrderStatusForm.java
    写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/feign/OdrServiceApi.java#searchOrderStatus
    写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/service/OrderService.java#searchOrderLastGps 及其实现类
    写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/OrderController.java#searchOrderLastGps
```java
@Data
@Schema(description = "查询订单状态的表单")
public class SearchOrderStatusForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;
}

@PostMapping("/order/searchOrderStatus")
R searchOrderStatus(SearchOrderStatusForm form);

Map searchOrderLastGps(SearchOrderLastGpsForm form);

@Override
public Map searchOrderLastGps(SearchOrderLastGpsForm form) {
   SearchOrderStatusForm statusForm=new SearchOrderStatusForm();
   statusForm.setOrderId(form.getOrderId());
   R r = odrServiceApi.searchOrderStatus(statusForm);
   if (!r.containsKey("result")) {
      throw new HxdsException("没有对应的订单记录");
   }
   int status = MapUtil.getInt(r, "result");
   if(status==4){
      r=nebulaServiceApi.searchOrderLastGps(form);
      HashMap lastGps = (HashMap) r.get("result");
      return lastGps;
   }
   return null;
}

@PostMapping("/searchOrderLastGps")
@SaCheckPermission(value = {"ROOT", "ORDER:SELECT"}, mode = SaMode.OR)
@Operation(summary = "获取某个订单最后的GPS定位")
public R searchOrderLastGps(@RequestBody @Valid SearchOrderLastGpsForm form){
   Map map = orderService.searchOrderLastGps(form);
   return R.ok().put("result",map);
}
```
11. 写 hxds-mis-vue/src/views/order.vue#loadPanelData
    写 hxds-mis-vue/src/views/order.vue#expand
```vue
 loadPanelData: function(ref, row) {
   let data = {
     orderId: row.id
   };
   ref.$http('order/searchOrderComprehensiveInfo', 'POST', data, true, function(resp) {
     let result = resp.result;
     let content = result.content;

     let customerInfo = result.customerInfo;
     ref.panel.customer.id = customerInfo.id;
     ref.panel.customer.sex = customerInfo.sex;
     ref.panel.customer.tel = customerInfo.tel;

     let driverInfo = result.driverInfo;
     ref.panel.driver.id = driverInfo.id;
     ref.panel.driver.name = driverInfo.name;
     ref.panel.driver.tel = driverInfo.tel;

     ref.panel.order.carPlate = content.carPlate;
     ref.panel.order.carType = content.carType;
     let city = calculateCarPlateCity(content.carPlate);
     ref.panel.order.city = city;

     if (content.hasOwnProperty('acceptTime')) {
       ref.panel.order.acceptTime = content.acceptTime;
     } else {
       ref.panel.order.acceptTime = '--';
     }
     if (content.hasOwnProperty('arriveTime')) {
       ref.panel.order.arriveTime = content.arriveTime;
     } else {
       ref.panel.order.arriveTime = '--';
     }
     if (content.hasOwnProperty('startTime')) {
       ref.panel.order.startTime = content.startTime;
     } else {
       ref.panel.order.startTime = '--';
     }
     if (content.hasOwnProperty('endTime')) {
       ref.panel.order.endTime = content.endTime;
     } else {
       ref.panel.order.endTime = '--';
     }
     if (content.hasOwnProperty('waitingMinute')) {
       ref.panel.order.waitingMinute = content.waitingMinute + '分钟';
     } else {
       ref.panel.order.waitingMinute = '--';
     }
     if (content.hasOwnProperty('driveMinute')) {
       ref.panel.order.driveMinute = content.driveMinute + '分钟';
     } else {
       ref.panel.order.driveMinute = '--';
     }
     if (content.hasOwnProperty('realMileage')) {
       ref.panel.order.realMileage = content.realMileage + '公里';
       row.realMileage = content.realMileage;
     } else {
       ref.panel.order.realMileage = '--';
       row.realMileage = '--';
     }
     if (content.hasOwnProperty('realFee')) {
       ref.panel.order.realFee = content.realFee + '元';
       row.realFee = content.realFee;
     } else {
       ref.panel.order.realFee = '--';
       row.realFee = '--';
     }
     ref.panel.order.status = status[content.status + ''];
     row.status = status[content.status + ''];
     if (result.hasOwnProperty('chargeRule')) {
       ref.panel.order.chargeRule = result.chargeRule.code;
     } else {
       ref.panel.order.chargeRule = '--';
     }
     if (result.hasOwnProperty('cancelRule')) {
       ref.panel.order.cancelRule = result.cancelRule.code;
     } else {
       ref.panel.order.cancelRule = '--';
     }
     if (result.hasOwnProperty('profitsharingRule')) {
       ref.panel.order.profitsharingRule = result.profitsharingRule.code;
     } else {
       ref.panel.order.profitsharingRule = '--';
     }

     ref.$nextTick(function() {
       let startPlaceLocation = content.startPlaceLocation;
       let endPlaceLocation = content.endPlaceLocation;
       let mapCenter = new TMap.LatLng(startPlaceLocation.latitude, startPlaceLocation.longitude);
       let map = new TMap.Map($(`.el-table__expanded-cell #order_${row.id}`)[0], {
         center: mapCenter, //地图显示中心点
         zoom: 13,
         viewMode: '2D',
         baseMap: {
           type: 'vector',
           features: ['base', 'label']
         }
       });

       let driveLine = result.driveLine;
       let coors = driveLine.routes[0].polyline;
       let pl = [];
       //坐标解压（返回的点串坐标，通过前向差分进行压缩，因此需要解压）
       let kr = 1000000;
       for (let i = 2; i < coors.length; i++) {
         coors[i] = Number(coors[i - 2]) + Number(coors[i]) / kr;
       }
       //将解压后的坐标生成LatLng数组
       for (let i = 0; i < coors.length; i += 2) {
         pl.push(new TMap.LatLng(coors[i], coors[i + 1]));
       }

       let polylineLayer = new TMap.MultiPolyline({
         id: 'polyline-layer', //图层唯一标识
         map: map, //绘制到目标地图
         styles: {
           style_blue: new TMap.PolylineStyle({
             color: 'rgba(190,188,188,1)',
             width: 6,
             lineCap: 'round' //线端头方式
           })
         },
         geometries: [
           {
             id: 'pl_1',
             styleId: 'style_blue',
             paths: pl
           }
         ]
       });

       let markerLayer = new TMap.MultiMarker({
         map: map,
         styles: {
           startStyle: new TMap.MarkerStyle({
             width: 24,
             height: 36,
             anchor: { x: 16, y: 32 },
             src: 'https://mapapi.qq.com/web/lbs/javascriptGL/demo/img/start.png'
           }),
           endStyle: new TMap.MarkerStyle({
             width: 24,
             height: 36,
             anchor: { x: 16, y: 32 },
             src: 'https://mapapi.qq.com/web/lbs/javascriptGL/demo/img/end.png'
           }),
           carStyle: new TMap.MarkerStyle({
             width: 30,
             height: 30,
             src: '../order/driver-icon.png',
             anchor: { x: 16, y: 32 }
           })
         },
         geometries: [
           //起点标记
           {
             id: '1',
             styleId: 'startStyle',
             position: new TMap.LatLng(startPlaceLocation.latitude, startPlaceLocation.longitude)
           },
           //终点标记
           {
             id: '2',
             styleId: 'endStyle',
             position: new TMap.LatLng(endPlaceLocation.latitude, endPlaceLocation.longitude)
           }
         ]
       });
       if (content.status == 4) {
         let lastGps = result.lastGps;
         markerLayer.add([
           {
             id: '3',
             styleId: 'carStyle', //指定样式id
             position: new TMap.LatLng(lastGps.latitude, lastGps.longitude)
           }
         ]);
         ref.gpsTimer = setInterval(function() {
           let data = {
             orderId: row.id
           };
           ref.$http('order/searchOrderLastGps', 'POST', data, true, function(resp) {
             if (resp.hasOwnProperty('result')) {
               let lastGps = resp.result;
               markerLayer.updateGeometries([
                 {
                   id: '3',
                   styleId: 'carStyle',
                   position: new TMap.LatLng(lastGps.latitude, lastGps.longitude)
                 }
               ]);
             } else {
               //重新加载面板数据
               $(`.el-table__expanded-cell #order_${row.id}`).empty();
               ref.loadPanelData(ref, row);
               clearInterval(ref.gpsTimer);
             }
           });
         }, 15 * 1000);
       } else if (content.status >= 5 && content.status <= 8) {
         let orderGps = result.orderGps;
         let paths = [];
         for (let one of orderGps) {
           let temp = new TMap.LatLng(one.latitude, one.longitude);
           paths.push(temp);
         }
         let polylineLayer = new TMap.MultiPolyline({
           id: 'drive-polyline-layer', //图层唯一标识
           map: map,
           //折线样式定义
           styles: {
             style_blue: new TMap.PolylineStyle({
               color: '#3777FF', //线填充色
               width: 6 //折线宽度
             })
           },
           //折线数据定义
           geometries: [
             {
               id: 'pl_1',
               styleId: 'style_blue',
               paths: paths
             }
           ]
         });
       }
     });
   });
 },

expand: function(row, expandedRows) {
   let that = this;
   if (expandedRows.length > 0) {
      that.expands = [];
      if (row) {
         that.expands.push(row.id);
         that.panel.id = `order_${row.id}`;
         that.loadPanelData(that, row);
      } else {
         that.expands = [];
      }
   }
}
```
### 分析订单执行的热点地区
1. 写 hxds-odr/src/main/java/com/example/hxds/odr/db/dao/OrderDao.xml 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/mis/api/service/OrderService.java#searchOrderStartLocationIn30Days 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController#searchOrderStartLocationIn30Days
```java
List<String> searchOrderStartLocationIn30Days();

<select id="searchOrderStartLocationIn30Days" resultType="String">
        SELECT start_place_location
        FROM tb_order
        WHERE start_time IS NOT NULL
        AND start_time BETWEEN TIMESTAMPADD(DAY, -30, NOW()) AND NOW();
</select>

List<Map> searchOrderStartLocationIn30Days();

@Override
public List<Map> searchOrderStartLocationIn30Days() {
     List<String> list = orderDao.searchOrderStartLocationIn30Days();
     List<Map> result = Lists.newArrayList();
     list.forEach(location -> {
        JSONObject json = JSONUtil.parseObj(location);
        String latitude = json.getStr("latitude");
        String longitude = json.getStr("longitude");
        latitude = latitude.substring(0, latitude.length() - 4);
        latitude += "0001";
        longitude = longitude.substring(0, longitude.length() - 4);
        longitude += "0001";
        Map map = Maps.newHashMap();
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        result.add(map);
     });
     return result;
}

@PostMapping("/searchOrderStartLocationIn30Days")
@Operation(summary = "查询30天以内订单上车定点位")
public R searchOrderStartLocationIn30Days() {
     List<Map> result = orderService.searchOrderStartLocationIn30Days();
     return R.ok().put("result", result);
}
```
【说明】在com.example.hxds.odr.service.impl 包 OrderServiceImpl.java 接口中，实现抽象方法。为了能把相邻的代驾上车点坐标合并到一起,于是我们要
抹掉上车点坐标的后四位小数。因为腾讯位置服务要求经纬度坐标必须精确到小数点后6位,于是有人就想给经纬度坐标补上0000,这是不行的。由于生成热力图的时候,我们
需要先把数据导出成Excel文件,在Excel文件中1233.790000这样的数字就自动被转换成123.79了,并不能满足腾讯位置服务的要求。所以我们给经纬度坐标补上的是0001,
形成的数字例如123.790001,这样导出到Excel文件就不会丢失数据了
2. 写 hxds-mis-api/pom.xml
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/feign/OdrServiceApi.java#searchOrderStartLocationIn30Days
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/service/OrderService.java 及其实现类
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/OrderController.java#downloadOrderStartLocationIn30Days
```java
<dependency>
   <groupId>org.apache.poi</groupId>
   <artifactId>poi-ooxml</artifactId>
   <version>4.1.2</version>
</dependency>

@PostMapping("/order/searchOrderStartLocationIn30Days")
R searchOrderStartLocationIn30Days();

List<Map> searchOrderStartLocationIn30Days();

public List<Map> searchOrderStartLocationIn30Days() {
     R r = odrServiceApi.searchOrderStartLocationIn30Days();
     // 此时result中的元素是Map,每个Map中有latitude,longitude两个元素
     List<Map> list = (List<Map>) r.get("result");
     List<Map> result = Lists.newArrayList();
     // 调用Collectionutil.countMap()函数就能得到结果,返回值是HashMap对象,Kev是原来的元素,Value是数量
     Map<Map, Integer> map = CollectionUtil.countMap(list);
     map.forEach((keyMap, value) -> {
        keyMap.replace("latitude", MapUtil.getDouble(keyMap, "latitude"));
        keyMap.replace("longitude", MapUtil.getDouble(keyMap, "longitude"));
        keyMap.put("count", value);
        result.add(keyMap);
     });
     // 此时result中的元素是Map,每个Map中有latitude,longitude,count三个元素
     return result;
}

@PostMapping("/downloadOrderStartLocationIn30Days")
@SaCheckPermission(value = {"ROOT", "ORDER:SELECT"}, mode = SaMode.OR)
@Operation(summary = "查询最近30天内订单的上车点定位记录")
public void downloadOrderStartLocationIn30Days(HttpServletResponse response){
     List<Map> result = orderService.searchOrderStartLocationIn30Days();
     response.setCharacterEncoding("UTF-8");
     response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
     response.setHeader("Content-Disposition","attachment;filename=heat_data.xls");
     try (ServletOutputStream out = response.getOutputStream();
        BufferedOutputStream bff = new BufferedOutputStream(out);) {
        ExcelWriter writer = ExcelUtil.getWriter();
        writer.write(result, true);
        writer.flush(bff);
        writer.close();
     } catch (IOException e) {
        throw new HxdsException("Excel文件下载失败");
     }
}
```
3. 创建 hxds-driver-wx/pages/heat_chart/heat_chart.vue
   写 hxds-driver-wx/pages.json
   写 hxds-driver-wx/main/main.vue
```vue
"path": "pages/heat_chart/heat_chart",
"style": {
  "navigationBarTitleText": "接单热点地区",
  "enablePullDownRefresh": false
}

 <view>
     <map id="map" :style="contentStyle" 
         subkey="FGRBZ-GS266-44VSO-ER7OG-IW5S7-ANB47" scale="12" 
         :latitude="latitude" :longitude="longitude">
     </map>
 </view>

 export default {
     data() {
         return {
             windowHeight: 0,
             contentStyle: '',
             latitude: 39.908823,
             longitude: 116.39747,
         }
     },
     methods: {
         
     },
     onShow: function() {
         let that = this;
         uni.$on('updateLocation', function(location) {
             if (location != null) {
                 that.latitude = location.latitude;
                 that.longitude = location.longitude;
             }
         });
     },
     onHide: function() {
         uni.$off('updateLocation');
     },
     onLoad:function(){
         let that=this
         let windowHeight=uni.getSystemInfoSync().windowHeight
         that.windowHeight=windowHeight
         that.contentStyle = `width: 750rpx;height:${that.windowHeight}px;`;
         
         let map = wx.createMapContext('map');
         map.addVisualLayer({
             layerId: '10f611245811',
             interval: 5,
             zIndex: 999,
             success: function(resp) {
                 console.log(resp);
             },
             fail: function(error) {
                 console.log(error);
             }
         })
     }
 }

<u-cell-item icon="eye-fill" :icon-style="icon" title="接单热点地区" @click="this.toPage('../heat_chart/heat_chart')" />
```
## 订单支付与分账（规则引擎自动计算分配比例，执行实时分账）

当代驾结束后，大数据系统根据GPS定位计算行进里程，规则引擎计算出账单各项金额，系统把账单推送给乘客。乘客付款之后，后端系统和移动端系统分别核验支付结果，规则引擎自动计算给司机的分账比例和奖励，QuartZ定时器等待微信平台准备好分账状态后，调用API执行给司机实时分账

### 订单微服务更新订单、账单和分账记录

1. 写 hxds-odr/src/main/java/com/example/hxds/odr/db/dao/OrderBillDao.xml#updateBillFee 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/db/dao/OrderDao.xml#updateOrderMileageAndFee 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/db/dao/OrderProfitsharingDao.xml#updateOrderMileageAndFee 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderBillService.java#updateBillFee 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/UpdateBillFeeForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderBillController.java#updateBillFee
```java
 <update id="updateBillFee" parameterType="Map">
     UPDATE tb_order_bill
     SET total = #{total},
         mileage_fee = #{mileageFee},
         waiting_fee = #{waitingFee},
         other_fee = #{otherFee},
         return_fee = #{returnFee},
         incentive_fee = #{incentiveFee},
     WHERE order_id = #{orderId}
 </update>

int updateBillFee(Map param);

<update id="updateOrderMileageAndFee" parameterType="Map">
        UPDATE tb_order
        SET real_mileage   = #{realMileage},
        return_mileage = #{returnMileage},
        incentive_fee  = #{incentiveFee},
        real_fee       = #{total}
        WHERE id = #{orderId}
</update>

int updateOrderMileageAndFee(Map param);

<insert id="insert" parameterType="com.example.hxds.odr.db.pojo.OrderProfitsharingEntity">
        INSERT INTO tb_order_profitsharing
        SET order_id = #{orderId},
        rule_id = #{ruleId},
        amount_fee = #{amountFee},
        payment_rate = #{paymentRate},
        payment_fee = #{paymentFee},
        tax_rate = #{taxRate},
        tax_fee = #{taxFee},
        system_income = #{systemIncome},
        driver_income = #{driverIncome},
        `status` = 1
</insert>

int updateBillFee(Map param);

@Override
@Transactional
@LcnTransaction
public int updateBillFee(Map param) {
     int rows = orderBillDao.updateBillFee(param);
     if (rows != 1) {
        throw new HxdsException("更新账单费用详情失败");
     }
     rows = orderDao.updateOrderMileageAndFee(param);
     if (rows != 1) {
        throw new HxdsException("更新订单费用详情失败");
     }
     OrderProfitsharingEntity entity = new OrderProfitsharingEntity();
     entity.setOrderId(MapUtil.getLong(param, "orderId"));
     entity.setRuleId(MapUtil.getLong(param, "ruleId"));
     entity.setAmountFee(new BigDecimal((String) param.get("total")));
     entity.setPaymentRate(new BigDecimal((String) param.get("paymentRate")));
     entity.setPaymentFee(new BigDecimal((String) param.get("paymentFee")));
     entity.setTaxRate(new BigDecimal((String) param.get("taxRate")));
     entity.setTaxFee(new BigDecimal((String) param.get("taxFee")));
     entity.setSystemIncome(new BigDecimal((String) param.get("systemIncome")));
     entity.setDriverIncome(new BigDecimal((String) param.get("driverIncome")));

     rows = orderProfitsharingDao.insert(entity);
     if (rows != 1) {
        throw new HxdsException("添加分账记录失败");
     }
     return rows;
}

@Data
@Schema(description = "更新账单的表单")
public class UpdateBillFeeForm {

   @NotBlank(message = "total不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "total内容不正确")
   @Schema(description = "总金额")
   private String total;

   @NotBlank(message = "mileageFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "mileageFee内容不正确")
   @Schema(description = "里程费")
   private String mileageFee;

   @NotBlank(message = "waitingFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "waitingFee内容不正确")
   @Schema(description = "等时费")
   private String waitingFee;

   @NotBlank(message = "tollFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "tollFee内容不正确")
   @Schema(description = "路桥费")
   private String tollFee;

   @NotBlank(message = "parkingFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "parkingFee内容不正确")
   @Schema(description = "路桥费")
   private String parkingFee;

   @NotBlank(message = "otherFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "otherFee内容不正确")
   @Schema(description = "其他费用")
   private String otherFee;

   @NotBlank(message = "returnFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "returnFee内容不正确")
   @Schema(description = "返程费用")
   private String returnFee;

   @NotBlank(message = "incentiveFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "incentiveFee内容不正确")
   @Schema(description = "系统奖励费用")
   private String incentiveFee;

   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @NotBlank(message = "realMileage不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d+$|^0\\.\\d+$|^[1-9]\\d*$", message = "realMileage内容不正确")
   @Schema(description = "代驾公里数")
   private String realMileage;

   @NotBlank(message = "returnMileage不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d+$|^0\\.\\d+$|^[1-9]\\d*$", message = "returnMileage内容不正确")
   @Schema(description = "返程公里数")
   private String returnMileage;

   @NotNull(message = "ruleId不能为空")
   @Min(value = 1, message = "ruleId不能小于1")
   @Schema(description = "规则ID")
   private Long ruleId;

   @NotBlank(message = "paymentRate不能为空")
   @Pattern(regexp = "^0\\.\\d+$|^[1-9]\\d*$|^0$", message = "paymentRate内容不正确")
   @Schema(description = "支付手续费率")
   private String paymentRate;

   @NotBlank(message = "paymentFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "paymentFee内容不正确")
   @Schema(description = "支付手续费")
   private String paymentFee;

   @NotBlank(message = "taxRate不能为空")
   @Pattern(regexp = "^0\\.\\d+$|^[1-9]\\d*$|^0$", message = "taxRate内容不正确")
   @Schema(description = "代缴个税费率")
   private String taxRate;

   @NotBlank(message = "taxFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "taxFee内容不正确")
   @Schema(description = "代缴个税")
   private String taxFee;

   @NotBlank(message = "systemIncome不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "systemIncome内容不正确")
   @Schema(description = "代驾系统分账收入")
   private String systemIncome;

   @NotBlank(message = "driverIncome不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "driverIncome内容不正确")
   @Schema(description = "司机分账收入")
   private String driverIncome;

}

@Operation(summary = "更新订单账单费用")
public R updateBillFee(@RequestBody @Valid UpdateBillFeeForm form) {
   Map param = BeanUtil.beanToMap(form);
   int rows = orderBillService.updateBillFee(param);
   return R.ok().put("rows", rows);
}
```
### 大数据微服务计算实际代驾里程
1. 写 hxds-odr/src/main/java/com/example/hxds/odr/db/dao/OrderDao.xml#validDriverOwnOrder 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/db/dao/OrderDao.xml#searchSettlementNeedData 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java/validDriverOwnOrder 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java/searchSettlementNeedData 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/ValidDriverOwnOrderForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/SearchSettlementNeedDataForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java
```java
 <select id="validDriverOwnOrder" resultType="long" parameterType="Map">
     SELECT COUNT(*) FROM tb_order
     WHERE id = #{orderId} AND driver_id = #{driverId}
 </select>
 <select id="searchSettlementNeedData" resultType="Map" parameterType="long">
     SELECT DATE_FORMAT(accept_time, '%Y-%m-%d %H:%i:%s') AS acctptTime,
            DATE_FORMAT(start_time, '%Y-%m-%d %H:%i:%s')  AS startTime,
            waiting_minute                                AS waitingMinute,
            CAST(favour_fee AS CHAR)                      AS favourFee
     FROM tb_order
     WHERE id = #{orderId}
 </select>

boolean validDriverOwnOrder(Map param);

Map searchSettlementNeedData(long orderId);

@Override
public boolean validDriverOwnOrder(Map param) {
     long count = orderDao.validDriverOwnOrder(param);
     return count == 1? true:false;
}

@Override
public Map searchSettlementNeedData(long orderId) {
     Map map = orderDao.searchSettlementNeedData(orderId);
     return map;
}

@Data
@Schema(description = "查询司机是否关联某订单的表单")
public class ValidDriverOwnOrderForm {
   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@Data
@Schema(description = "查询订单的开始和等时的表单")
public class SearchSettlementNeedDataForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@PostMapping("/validDriverOwnOrder")
@Operation(summary = "查询司机是否关联某订单")
public R validDriverOwnOrder(@RequestBody @Valid ValidDriverOwnOrderForm form) {
   Map param = BeanUtil.beanToMap(form);
   boolean bool = orderService.validDriverOwnOrder(param);
   return R.ok().put("result", bool);
}

@PostMapping("/searchSettlementNeedData")
@Operation(summary = "查询订单的开始和等时")
public R searchSettlementNeedData(@RequestBody @Valid SearchSettlementNeedDataForm form) {
   Map map = orderService.searchSettlementNeedData(form.getOrderId());
   return R.ok().put("result", map);
}
```
2. 写 hxds-nebula/src/main/resources/mapper/OrderGpsDao.xml/searchOrderAllGps 及其对应接口
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/util/LocationUtil.java
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/service/OrderGpsService.java#calculateOrderMileage 及其实现类
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/controller/form/CalculateOrderMileageForm.java
   写 hxds-nebula/src/main/java/com/example/hxds/nebula/controller/OrderGpsController.java#calculateOrderMileage
```java
 <select id="searchOrderAllGps" parameterType="long" resultType="HashMap">
     SELECT "latitude", "longitude"
     FROM hxds.order_gps
     WHERE "order_id" = #{orderId}
     ORDER BY "id"
 </select>

List<HashMap> searchOrderAllGps(long orderId);

public class LocationUtil {

   //地球半径
   private final static double EARTH_RADIUS = 6378.137;

   private static double rad(double d) {
      return d * Math.PI / 180.0;
   }

   /**
    * 计算两点间距离
    *
    * @return double 距离 单位公里,精确到米
    */
   public static double getDistance(double lat1, double lng1, double lat2, double lng2) {
      double radLat1 = rad(lat1);
      double radLat2 = rad(lat2);
      double a = radLat1 - radLat2;
      double b = rad(lng1) - rad(lng2);
      double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2) +
              Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
      s = s * EARTH_RADIUS;
      s = new BigDecimal(s).setScale(3, RoundingMode.HALF_UP).doubleValue();
      return s;
   }
}

String calculateOrderMileage(long orderId);

@Override
public String calculateOrderMileage(long orderId) {
   List<HashMap> list = orderGpsDao.searchOrderAllGps(orderId);
   double mileage = 0;
   for (int i = 0; i < list.size(); i++) {
      if (i != list.size() - 1) {
         HashMap map_1 = list.get(i);
         HashMap map_2 = list.get(i + 1);
         double latitude_1 = MapUtil.getDouble(map_1, "latitude");
         double longitude_1 = MapUtil.getDouble(map_1, "longitude");
         double latitude_2 = MapUtil.getDouble(map_2, "latitude");
         double longitude_2 = MapUtil.getDouble(map_2, "longitude");
         double distance = LocationUtil.getDistance(latitude_1, longitude_1, latitude_2, longitude_2);
         mileage += distance;
      }
   }
   return mileage + "";
}

@Data
@Schema(description = "计算订单里程的表单")
public class CalculateOrderMileageForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@PostMapping("/calculateOrderMileage")
@Operation(summary = "计算订单里程")
public R calculateOrderMileage(@RequestBody @Valid CalculateOrderMileageForm form){
   String mileage = orderGpsService.calculateOrderMileage(form.getOrderId());
   return R.ok().put("result",mileage);
}
```
### 规则微服务计算代驾费和系统奖励费
1. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/ValidDriverOwnOrderForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/SearchSettlementNeedDataForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/UpdateBillFeeForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/CalculateOrderMileageForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/CalculateOrderChargeForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/CalculateIncentiveFeeForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/CalculateProfitsharingForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/OdrServiceApi.java#validDriverOwnOrder
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/OdrServiceApi.java#searchSettlementNeedData
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/NebulaServiceApi.java#calculateOrderMileage
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/RuleServiceApi.java#calculateOrderCharge
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/RuleServiceApi.java#calculateIncentiveFee
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/RuleServiceApi.java#calculateProfitsharing
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/OrderService.java#updateOrderBill 及其实现类
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/OrderController.java#updateOrderBill
```java
@Data
@Schema(description = "查询司机是否关联某订单的表单")
public class ValidDriverOwnOrderForm {

    @Schema(description = "司机ID")
    private Long driverId;

    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;
}

@Data
@Schema(description = "查询订单的开始和等时的表单")
public class SearchSettlementNeedDataForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@Data
@Schema(description = "更新订单账单费用的表单")
public class UpdateBillFeeForm {

   @Schema(description = "总金额")
   private String total;

   @Schema(description = "里程费")
   private String mileageFee;

   @Schema(description = "等时费")
   private String waitingFee;

   @NotBlank(message = "tollFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "tollFee内容不正确")
   @Schema(description = "路桥费")
   private String tollFee;

   @NotBlank(message = "parkingFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "parkingFee内容不正确")
   @Schema(description = "路桥费")
   private String parkingFee;

   @NotBlank(message = "otherFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "otherFee内容不正确")
   @Schema(description = "其他费用")
   private String otherFee;

   @Schema(description = "返程费用")
   private String returnFee;

   @Schema(description = "系统奖励费用")
   private String incentiveFee;

   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @Schema(description = "司机ID")
   private Long driverId;

   @Schema(description = "代驾公里数")
   private String realMileage;

   @Schema(description = "返程公里数")
   private String returnMileage;

   @Schema(description = "规则ID")
   private Long ruleId;

   @Schema(description = "支付手续费率")
   private String paymentRate;

   @Schema(description = "支付手续费")
   private String paymentFee;

   @Schema(description = "代缴个税费率")
   private String taxRate;

   @Schema(description = "代缴个税")
   private String taxFee;

   @Schema(description = "代驾系统分账收入")
   private String systemIncome;

   @Schema(description = "司机分账收入")
   private String driverIncome;

}

@Data
@Schema(description = "查询某订单的全部GPS数据的表单")
public class CalculateOrderMileageForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@Data
@Schema(description = "计算代驾费用的表单")
public class CalculateOrderChargeForm {

   @Schema(description = "代驾公里数")
   private String mileage;

   @Schema(description = "代驾开始时间")
   private String time;

   @Schema(description = "等时分钟")
   private Integer minute;
}

@Data
@Schema(description = "计算系统奖励的表单")
public class CalculateIncentiveFeeForm {

   @Schema(description = "司机ID")
   private long driverId;

   @NotBlank(message = "acceptTime不能为空")
   @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))\\s(20|21|22|23|[0-1]\\d):[0-5]\\d:[0-5]\\d$",
           message = "acceptTime内容不正确")
   @Schema(description = "接单时间")
   private String acceptTime;
}

@Data
@Schema(description = "计算订单分账的表单")
public class CalculateProfitsharingForm {

   @Schema(description = "订单ID")
   private Long orderId;

   @Schema(description = "待分账费用")
   private String amount;
}

@PostMapping("/order/validDriverOwnOrder")
R validDriverOwnOrder(ValidDriverOwnOrderForm form);

@PostMapping("/order/searchSettlementNeedData")
R searchSettlementNeedData(SearchSettlementNeedDataForm form);

@PostMapping("/bill/updateBillFee")
R updateBillFee(UpdateBillFeeForm form);
   
@PostMapping("/order/gps/calculateOrderMileage")
R calculateOrderMileage(CalculateOrderMileageForm form);

@PostMapping("/charge/calculateOrderCharge")
R calculateOrderCharge(CalculateOrderChargeForm form);

@PostMapping("/award/calculateIncentiveFee")
R calculateIncentiveFee(CalculateIncentiveFeeForm form);

@PostMapping("/profitsharing/calculateProfitsharing")
R calculateProfitsharing(CalculateProfitsharingForm form);

int updateOrderBill(UpdateBillFeeForm form);

@Override
public int updateOrderBill(UpdateBillFeeForm form) {
   // 1. 判断司机是否关联该订单
   ValidDriverOwnOrderForm form_1 = new ValidDriverOwnOrderForm();
   form_1.setOrderId(form.getOrderId());
   form_1.setDriverId(form.getDriverId());
   R r = odrServiceApi.validDriverOwnOrder(form_1);
   Boolean bool = MapUtil.getBool(r, "result");
   if (!bool) {
      throw new HxdsException("司机未关联该订单");
   }
   // 2. 计算订单里程数据
   CalculateOrderMileageForm form_2 = new CalculateOrderMileageForm();
   form_2.setOrderId(form.getOrderId());
   r = nebulaServiceApi.calculateOrderMileage(form_2);
   String mileage = (String) r.get("result");
   mileage = NumberUtil.div(mileage, "1000", 1, RoundingMode.CEILING).toString();
   // 3. 查询订单消息
   SearchSettlementNeedDataForm form_3 = new SearchSettlementNeedDataForm();
   form_3.setOrderId(form.getOrderId());
   r = odrServiceApi.searchSettlementNeedData(form_3);
   HashMap map = (HashMap) r.get("result");
   String acceptTime = MapUtil.getStr(map, "acceptTime");
   String startTime = MapUtil.getStr(map, "startTime");
   int waitingMinute = MapUtil.getInt(map, "waitingMinute");
   String favourFee = MapUtil.getStr(map, "favourFee");
   // 4. 计算代驾费
   CalculateOrderChargeForm form_4 = new CalculateOrderChargeForm();
   form_4.setMileage(mileage);
   form_4.setTime(startTime.split(" ")[1]);
   form_4.setMinute(waitingMinute);
   r = ruleServiceApi.calculateOrderCharge(form_4);
   map = (HashMap) r.get("result");
   String mileageFee = MapUtil.getStr(map, "mileageFee");
   String returnFee = MapUtil.getStr(map, "returnFee");
   String waitingFee = MapUtil.getStr(map, "waitingFee");
   String amount = MapUtil.getStr(map, "amount");
   String returnMileage = MapUtil.getStr(map, "returnMileage");
   // 5. 计算系统奖励费用
   CalculateIncentiveFeeForm form_5 = new CalculateIncentiveFeeForm();
   form_5.setDriverId(form.getDriverId());
   form_5.setAcceptTime(acceptTime);
   r = ruleServiceApi.calculateIncentiveFee(form_5);
   String incentiveFee = (String) r.get("result");

   form.setMileageFee(mileageFee);
   form.setReturnFee(returnFee);
   form.setWaitingFee(waitingFee);
   form.setIncentiveFee(incentiveFee);
   form.setRealMileage(mileage);
   form.setReturnMileage(returnMileage);
   //计算总费用
   String total = NumberUtil.add(amount, form.getTollFee(), form.getParkingFee(), form.getOtherFee(), favourFee).toString();
   form.setTotal(total);
   // 6. 计算分账数据
   CalculateProfitsharingForm form_6 = new CalculateProfitsharingForm();
   form_6.setOrderId(form.getOrderId());
   form_6.setAmount(total);
   r = ruleServiceApi.calculateProfitsharing(form_6);
   map = (HashMap) r.get("result");
   long ruleId = MapUtil.getLong(map, "ruleId");
   String systemIncome = MapUtil.getStr(map, "systemIncome");
   String driverIncome = MapUtil.getStr(map, "driverIncome");
   String paymentRate = MapUtil.getStr(map, "paymentRate");
   String paymentFee = MapUtil.getStr(map, "paymentFee");
   String taxRate = MapUtil.getStr(map, "taxRate");
   String taxFee = MapUtil.getStr(map, "taxFee");

   form.setRuleId(ruleId);
   form.setPaymentRate(paymentRate);
   form.setPaymentFee(paymentFee);
   form.setTaxRate(taxRate);
   form.setTaxFee(taxFee);
   form.setSystemIncome(systemIncome);
   form.setDriverIncome(driverIncome);
   // 7. 更新代驾费账单数据
   r = odrServiceApi.updateBillFee(form);
   int rows = MapUtil.getInt(r, "rows");
   return rows;
}

@PostMapping("/updateBillFee")
@SaCheckLogin
@Operation(summary = "更新订单账单费用")
public R updateBillFee(@RequestBody @Valid UpdateBillFeeForm form) {
   long driverId = StpUtil.getLoginIdAsLong();
   form.setDriverId(driverId);
   int rows = orderService.updateOrderBill(form);
   return R.ok().put("rows", rows);
}
```
### 司机端手动添加路桥费等相关费用
1. 写 hxds-driver-wx/main.js#Vue.prototype.url
   写 hxds-driver-wx/order/enter_fee/enter_fee.vue
```java
updateBillFee: `${baseUrl}/order/updateBillFee`,
  
methods: {
  enterFee: function (type) {
    let that = this;
    if (type == 'toll') {
      uni.showModel({
        title: '路桥费',
        content: that.tollFee,
        editable: true,
        placeholderText: '输入路桥费',
        showCancel: false,
        success: function (resp) {
          if (that.checkValidFee(resp.content, '路桥费')) {
            that.tollFee = resp.content;
          }
        }
      });
    } else if (type == 'parking') {
      uni.showModel({
        title: '停车费',
        content: that.tollFee,
        editable: true,
        placeholderText: '输入停车费',
        showCancel: false,
        success: function (resp) {
          if (that.checkValidFee(resp.content, '停车费')) {
            that.parkingFee = resp.content;
          }
        }
      });
    } else if (type == 'other') {
      uni.showModel({
        title: '其他费用',
        content: that.tollFee,
        editable: true,
        placeholderText: '输入其他费用',
        showCancel: false,
        success: function (resp) {
          if (that.checkValidFee(resp.content, '其他费用')) {
            that.otherFee = resp.content;
          }
        }
      });
    }
  },
  submit: function () {
    let that = this;
    if (that.checkValidFee(that.tollFee, '路桥费')
        && that.checkValidFee(that.parkingFee, '停车费')
        && that.checkValidFee(that.otherFee, '其他费用')) {
      uni.showModal({
        title: '提示消息',
        content: '您确认要提交以上相关费用？',
        success: function(resp) {
          if (resp.confirm) {
            let data = {
              tollFee: that.tollFee,
              parkingFee: that.parkingFee,
              otherFee: that.otherFee,
              orderId: that.orderId
            };
            that.ajax(that.url.updateBillFee, 'POST', data, function(resp) {
              uni.navigateTo({
                url: `/order/order_bill/order_bill?orderId=${that.orderId}&customerId=${that.customerId}`
              });
            });
          }
        }
      });
    }
  }
},
// 携带orderId和customerId，从workbench页面跳转过来
onLoad: function (options) {
  this.orderId = options.orderId;
  this.customerId = options.customerId;
},
```
2. 【测试】首先测试 Web 方法，向 tb_order_profitsharing 表添加的分账记录给删除掉，把订单的 status 改成4状态。接下来启动后端各个子系统，运行司机端小程序。
    结束代驾，然后输入相关费用，测试一下程序运行的效果。
### 司机端预览代驾账单
1. 写 hxds-odr/src/main/resources/mapper/OrderBillDao.xml#searchReviewDriverOrderBill 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java#searchDriverCurrentOrder 及其对应实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/SearchReviewDriverOrderBillForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderBillController.java#searchReviewDriverOrderBill
```java
 <select id="searchReviewDriverOrderBill" parameterType="Map" resultType="HashMap">
     SELECT CAST(b.total AS CHAR)                AS total,
            CAST(b.real_pay AS CHAR)             AS realPay,
            CAST(b.mileage_fee AS CHAR)          AS mileageFee,
            CAST(o.favour_fee AS CHAR)           AS favourFee,
            CAST(o.incentive_fee AS CHAR)        AS incentiveFee,
            CAST(b.waiting_fee AS CHAR)          AS waitingFee,
            CAST(o.real_mileage AS CHAR)         AS realMileage,
            CAST(b.return_fee AS CHAR)           AS returnFee,
            CAST(b.parking_fee AS CHAR)          AS parkingFee,
            CAST(b.toll_fee AS CHAR)             AS tollFee,
            CAST(b.other_fee AS CHAR)           AS otherFee,
            CAST(b.voucher_fee AS CHAR)          AS voucherFee,
            o.waiting_minute                     AS waitingMinute,
            b.base_mileage                       AS baseMileage,
            CAST(b.base_mileage_price AS CHAR)   AS baseMileagePrice,
            CAST(b.exceed_mileage_price AS CHAR) AS exceedMileagePrice,
            b.base_minute                        AS baseMinute,
            CAST(b.exceed_minute_price AS CHAR)  AS exceedMinutePrice,
            b.base_return_mileage                AS baseReturnMileage,
            CAST(b.exceed_return_price AS CHAR)  AS exceedReturnPrice,
            CAST(o.return_mileage AS CHAR)       AS returnMileage,
            CAST(p.tax_fee AS CHAR)              AS taxFee,
            CAST(p.driver_income AS CHAR)        AS driverIncome
     FROM tb_order o
              JOIN tb_order_bill b ON o.id = b.order_id
              JOIN tb_order_profitsharing p ON o.id = p.order_id
     WHERE o.id = #{orderId}
       AND o.driver_id = #{driverId}
       AND o.`status` = 5
 </select>

HashMap searchReviewDriverOrderBill(Map param);

HashMap searchReviewDriverOrderBill(Map param);

@Override
public HashMap searchReviewDriverOrderBill(Map param) {
     HashMap map = orderBillDao.searchReviewDriverOrderBill(param);
     return map;
}

@Data
@Schema(description = "查询司机预览账单的表单")
public class SearchReviewDriverOrderBillForm {

   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

}

@PostMapping("/searchReviewDriverOrderBill")
@Operation(summary = "查询司机预览账单")
public R searchReviewDriverOrderBill(@RequestBody @Valid SearchReviewDriverOrderBillForm form){
   Map param = BeanUtil.beanToMap(form);
   HashMap map = orderBillService.searchReviewDriverOrderBill(param);
   return R.ok().put("result",map);
}
```
2. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/SearchReviewDriverOrderBillForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/OdrServiceApi.java#searchReviewDriverOrderBill
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/OrderService.java#searchReviewDriverOrderBill 及其实现类
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/OrderController.java#searchReviewDriverOrderBill
```java
@Data
@Schema(description = "查询司机预览账单的表单")
public class SearchReviewDriverOrderBillForm {

    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "司机ID")
    private Long driverId;

}

@PostMapping("/bill/searchReviewDriverOrderBill")
R searchReviewDriverOrderBill(SearchReviewDriverOrderBillForm form);

HashMap searchReviewDriverOrderBill(SearchReviewDriverOrderBillForm form);

@Override
public HashMap searchReviewDriverOrderBill(SearchReviewDriverOrderBillForm form) {
   R r = odrServiceApi.searchReviewDriverOrderBill(form);
   HashMap map = (HashMap) r.get("result");
   return map;
}

@PostMapping("/searchReviewDriverOrderBill")
@SaCheckLogin
@Operation(summary = "查询司机预览订单")
public R searchReviewDriverOrderBill(@RequestBody @Valid SearchReviewDriverOrderBillForm form) {
   long driverId = StpUtil.getLoginIdAsLong();
   form.setDriverId(driverId);
   HashMap map = orderService.searchReviewDriverOrderBill(form);
   return R.ok().put("result", map);
}
```
3. 写 hxds-driver-wx/main.js#Vue.prototype.url
   写 hxds-driver-wx/order/order_bill/order_bill.vue#onLoad
```java
searchReviewDriverOrderBill: `${baseUrl}/order/searchReviewDriverOrderBill`,

onLoad: function(options) {
  let that = this;
  that.orderId = options.orderId;
  that.customerId = options.customerId;
  let data = {
    orderId: that.orderId
  };
  that.ajax(that.url.searchReviewDriverOrderBill, 'POST', data, function(resp) {
    let result = resp.data.result;
    that.favourFee = result.favourFee;
    that.incentiveFee = result.incentiveFee;
    that.realMileage = result.realMileage;
    that.mileageFee = result.mileageFee;
    that.baseMileagePrice = result.baseMileagePrice;
    that.baseMileage = result.baseMileage;
    that.exceedMileagePrice = result.exceedMileagePrice;
    that.waitingFee = result.waitingFee;
    that.baseMinute = result.baseMinute;
    that.waitingMinute = result.waitingMinute;
    that.exceedMinutePrice = result.exceedMinutePrice;
    that.returnFee = result.returnFee;
    that.baseReturnMileage = result.baseReturnMileage;
    that.exceedReturnPrice = result.exceedReturnPrice;
    that.returnMileage = result.returnMileage;
    that.parkingFee = result.parkingFee;
    that.tollFee = result.tollFee;
    that.otherFee = result.otherFee;
    that.total = result.total;
    that.driverIncome = result.driverIncome;
    that.taxFee = result.taxFee;
  });
},
```
### 系统消息模块的设计原理
【说明】之前我们做司机抢单功能的时候,用到了RabbitMQ消息队列。但是抢单消息被司机接收之后,消息就被队列给删除了,并不会持久化存储。毕竟抢单信息都是临时性的消息,
并不需要持久化存储。但是其他的一些系统通知,无论司机或者乘客是否收到该消息,都要持久化存储。比如说钱包充值成功的通知,这样的消息就应该持久存储,除非司机手动删除消息。
持久化存储系统消息,数据量很大,而且又不是高价值的数据,好像用MongoDB和HBase都可以,到底应该选用哪一个呢?如果数据的检索需要用上复杂的查询条件和表达式,那么用HBase
更加的适合,毕竟SQL语句写起来很容易。反之,就可以用MongoDB。恰好系统消息无非就是分页查询、按照主键值查询,没有什么复杂的条件,所以我们用MongoDB就可以了。MongoDB
中没有数据表的概念,而是采用集合(Collectioon)存储数据,每条数据就是一个文档(Document)。文档结构很好理解,其实就是我们常用的JSOIN,一个JSON就是一条记录。

创建message集合：集合相当于MySQL中的数据表，但是没有固定的表结构。集合有什么字段,取决于保存在其中的数据。
创建message_ref集合：虽然message集合记录的是消息，里面有接收者ID，但是如果是群发消息，那么接受者ID是空值。这时候
就需要用上message_ ref 集合来记录接收人和已读状态。
比如说小程序每隔5分钟轮询是否有新的消息，如果积压的消息太多，Java系统没有接收完消息，这时候新的轮询到来，就会产生两个消费者共同接收同一个消息的情况，
这会造成数据库中添加了重复的记录, 如果每条MQ消息都有唯一的UUID值， 第一个消费者把消息保存到数据库，那么第二个消费者就无法再 把这条消息保存到数据库，
解决了消息的重复消费问题。
很多时候系统消息是群发的，也就是存在大量的接收人。假设网站有100万注册用户，我们要发送广播消息，理论上向message集合插入1条记录，然后向message_ref
集合添加100万条记录。如果有80万用户常年不登录，我们在 message_ref 表中一直保存他们的通知消息，有天太浪费存储空间了，最好能给消息 记录设置个过期时间，
自动销毁。很遗憾MongoDB没有过期时间这个机制，于是我们想到了RabbitMQ, 消息队列里面的消息是有过期时间的。当系统要发送消息了，先把消息记录保存到
message集合里面，然后向收件人的队列中发送消息。假设过期时间是1个月。这期间如果用户没有登陆系统查收消息，那么队列中的消息就自动销毁了。

如果用户在消息过期之前登陆了系统，Java程序会从RabbitMQ的队列中接收消息，然后把消息保存到message_ref集合中。你看用消息队列，我们就可以为活跃用户
持久化存储消息通知了。对于不活跃的用 户，为了节省存储空间，我们不会为他们存储消息通知。
### 消息微服务封装收发系统消息的接口
1. 写 hxds-snm/src/main/java/com/example/hxds/snm/service/MessageService.java
   写 hxds-snm/src/main/java/com/example/hxds/snm/service/impl/MessageServiceImpl.java
   写 hxds-snm/src/main/java/com/example/hxds/snm/task/MessageTask.java
```java
public interface MessageService {
    String insertMessage(MessageEntity entity);

    HashMap searchMessageById(String id);

    String insertRef(MessageRefEntity entity);

    long searchUnreadCount(long userId, String identity);

    long searchLastCount(long userId, String identity);

    long updateUnreadMessage(String id);

    long deleteMessageRefById(String id);

    long deleteUserMessageRef(long userId, String identity);
}

@Service
public class MessageServiceImpl implements MessageService {
   @Resource
   private MessageDao messageDao;

   @Resource
   private MessageRefDao messageRefDao;

   @Override
   public String insertMessage(MessageEntity entity) {
      String id = messageDao.insert(entity);
      return id;
   }

   @Override
   public HashMap searchMessageById(String id) {
      HashMap map = messageDao.searchMessageById(id);
      return map;
   }

   @Override
   public String insertRef(MessageRefEntity entity) {
      String id = messageRefDao.insert(entity);
      return id;
   }

   @Override
   public long searchUnreadCount(long userId, String identity) {
      long count = messageRefDao.searchUnreadCount(userId, identity);
      return count;
   }

   @Override
   public long searchLastCount(long userId, String identity) {
      long count = messageRefDao.searchLastCount(userId, identity);
      return count;
   }

   @Override
   public long updateUnreadMessage(String id) {
      long rows = messageRefDao.updateUnreadMessage(id);
      return rows;
   }

   @Override
   public long deleteMessageRefById(String id) {
      long rows = messageRefDao.deleteMessageRefById(id);
      return rows;
   }

   @Override
   public long deleteUserMessageRef(long userId, String identity) {
      long rows = messageRefDao.deleteUserMessageRef(userId, identity);
      return rows;
   }
}

@Component
@Slf4j
public class MessageTask {

   @Resource
   private ConnectionFactory factory;

   @Resource
   private MessageService messageService;

   /**
    * 同步发送私有消息
    */
   public void sendPrivateMessage(String identify, long userId, Integer ttl, MessageEntity entity) {
      String id = messageService.insertMessage(entity);
      String exchangeName = identify + "_private";
      String queueName = "queue_" + userId;
      String routingKey = userId + "";
      try (Connection connection = factory.newConnection();
           Channel channel = connection.createChannel();
      ) {
         channel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT);
         HashMap param = Maps.newHashMap();
         channel.queueDeclare(queueName, true, false, false, param);
         HashMap map = Maps.newHashMap();
         map.put("messageId", id);
         AMQP.BasicProperties properties;
         if (ttl != null && ttl > 0) {
            properties = new AMQP.BasicProperties().builder().contentEncoding("UTF-8")
                    .headers(map).expiration(ttl + "").build();
         } else {
            properties = new AMQP.BasicProperties().builder().contentEncoding("UTF-8")
                    .headers(map).build();
         }
         channel.basicPublish(exchangeName, routingKey, properties, entity.getMsg().getBytes());
         log.debug("消息发送成功");
      } catch (Exception e) {
         log.error("执行异常", e);
         throw new HxdsException("向MQ发送消息失败");
      }
   }

   /**
    * 异步发送私有消息
    */
   @Async
   public void sendPrivateMessageAsync(String identity, long userId, Integer ttl, MessageEntity entity) {
      sendPrivateMessage(identity, userId, ttl, entity);
   }

   public void sendBroadcastMessage(String identity, Integer ttl, MessageEntity entity) {
      String id = messageService.insertMessage(entity);
      String exchangeName = identity + "_broadcast";

      try (Connection connection = factory.newConnection();
           Channel channel = connection.createChannel();
      ) {
         HashMap map = new HashMap();
         map.put("messageId", id);
         AMQP.BasicProperties.Builder builder = new AMQP.BasicProperties().builder();
         builder.deliveryMode(MessageDeliveryMode.toInt(MessageDeliveryMode.PERSISTENT));//消息持久化存储
         builder.expiration(ttl.toString());//设置消息有效期
         AMQP.BasicProperties properties = builder.headers(map).build();

         channel.exchangeDeclare(exchangeName, BuiltinExchangeType.FANOUT);
         channel.basicPublish(exchangeName, "", properties, entity.getMsg().getBytes());
         log.debug("消息发送成功");
      } catch (Exception e) {
         log.error("执行异常", e);
         throw new HxdsException("向MQ发送消息失败");
      }
   }

   @Async
   public void sendBroadcastMessageAsync(String identity, Integer ttl, MessageEntity entity) {
      this.sendBroadcastMessage(identity, ttl, entity);
   }

   /**
    * 同步接收消息
    */
   public int receive(String identity, long userId) {
      String privateExchangeName = identity + "_private";
      String broadcastExchangeName = identity + "_broadcast";
      String queueName = "queue_" + userId;
      String routingKey = userId + "";

      int i = 0;
      try (Connection connection = factory.newConnection();
           Channel privateChannel = connection.createChannel();
           Channel broadcastChannel = connection.createChannel();
      ) {
         //接收私有消息
         privateChannel.exchangeDeclare(privateExchangeName, BuiltinExchangeType.DIRECT);
         privateChannel.queueDeclare(queueName, true, false, false, null);
         privateChannel.queueBind(queueName, privateExchangeName, routingKey);
         while (true) {
            GetResponse response = privateChannel.basicGet(queueName, false);
            if (response != null) {
               AMQP.BasicProperties properties = response.getProps();
               Map<String, Object> map = properties.getHeaders();
               String messageId = map.get("messageId").toString();
               byte[] body = response.getBody();
               String message = new String(body);
               log.debug("从RabbitMQ接收的私有消息：" + message);

               MessageRefEntity entity = new MessageRefEntity();
               entity.setMessageId(messageId);
               entity.setReceiverId(userId);
               entity.setReceiverIdentity(identity);
               entity.setReadFlag(false);
               entity.setLastFlag(true);
               messageService.insertRef(entity);
               long deliveryTag = response.getEnvelope().getDeliveryTag();
               privateChannel.basicAck(deliveryTag, false);
               i++;
            } else {
               break;
            }
         }
         //接收公有消息
         broadcastChannel.exchangeDeclare(broadcastExchangeName, BuiltinExchangeType.FANOUT);
         broadcastChannel.queueDeclare(queueName, true, false, false, null);
         broadcastChannel.queueBind(queueName, broadcastExchangeName, "");
         while (true) {
            GetResponse response = broadcastChannel.basicGet(queueName, false);
            if (response != null) {
               AMQP.BasicProperties properties = response.getProps();
               Map<String, Object> map = properties.getHeaders();
               String messageId = map.get("messageId").toString();
               byte[] body = response.getBody();
               String message = new String(body);
               log.debug("从RabbitMQ接收的广播消息：" + message);
               MessageRefEntity entity = new MessageRefEntity();
               entity.setMessageId(messageId);
               entity.setReceiverId(userId);
               entity.setReceiverIdentity(identity);
               entity.setReadFlag(false);
               entity.setLastFlag(true);
               messageService.insertRef(entity);
               long deliveryTag = response.getEnvelope().getDeliveryTag();
               broadcastChannel.basicAck(deliveryTag, false);
               i++;
            } else {
               break;
            }
         }
      } catch (Exception e) {
         log.error("执行异常", e);
         throw new HxdsException("接收消息失败");
      }
      return i;

   }

   /**
    * 异步接收消息
    */
   @Async
   public void receiveAsync(String identity, long userId) {
      receiveAsync(identity, userId);
   }

   /**
    * 同步删除消息队列
    */
   public void deleteQueue(String identity, long userId) {
      String privateExchangeName = identity + "_private";
      String broadcastExchangeName = identity + "_broadcast";
      String queueName = "queue_" + userId;
      try (Connection connection = factory.newConnection();
           Channel privateChannel = connection.createChannel();
           Channel broadcastChannel = connection.createChannel();
      ) {
         privateChannel.exchangeDeclare(privateExchangeName, BuiltinExchangeType.DIRECT);
         privateChannel.queueDelete(queueName);

         broadcastChannel.exchangeDeclare(broadcastExchangeName, BuiltinExchangeType.FANOUT);
         broadcastChannel.queueDelete(queueName);

         log.debug("消息队列成功删除");
      } catch (Exception e) {
         log.error("删除队列失败", e);
         throw new HxdsException("删除队列失败");
      }
   }

   /**
    * 异步删除消息队列
    */
   @Async
   public void deleteQueueAsync(String identity, long userId) {
      deleteQueue(identity, userId);
   }

}
```
2. 写 hxds-snm/src/main/java/com/example/hxds/snm/controller/form/RefreshMessageForm.java
   写 hxds-snm/src/main/java/com/example/hxds/snm/controller/form/DeleteMessageRefByIdForm.java
   写 hxds-snm/src/main/java/com/example/hxds/snm/controller/form/UpdateUnreadMessageForm.java
   写 hxds-snm/src/main/java/com/example/hxds/snm/controller/form/SearchMessageByIdForm.java
   写 hxds-snm/src/main/java/com/example/hxds/snm/controller/form/SendPrivateMessageForm.java
   写 hxds-snm/src/main/java/com/example/hxds/snm/controller/MessageController.java
```java
@Data
@Schema(description = "刷新用户消息的表单")
public class RefreshMessageForm {
    
    @NotNull(message = "userId不能为空")
    @Min(value = 1, message = "userId不能小于1")
    @Schema(description = "用户ID")
    private Long userId;

    @NotBlank(message = "identity不能为空")
    @Pattern(regexp = "^driver$|^mis$|^customer$",message = "identity内容不正确")
    @Schema(description = "用户身份")
    private String identity;

}

@Data
@Schema(description = "根据消息ID删除消息的表单")
public class DeleteMessageRefByIdForm {
   @NotBlank(message = "id不能为空")
   @Schema(description = "Ref消息的ID")
   private String id;
}

@Data
@Schema(description = "把未读消息更新成已读的表单")
public class UpdateUnreadMessageForm {
   @NotBlank(message = "id不能为空")
   @Schema(description = "ref消息ID")
   private String id;
}

@Data
@Schema(description = "根据消息ID查询消息的表单")
public class SearchMessageByIdForm {
   @NotBlank(message = "id不能为空")
   @Schema(description = "消息的ID")
   private String id;
}

@Data
@Schema(description = "同步发送私有消息的表单")
public class SendPrivateMessageForm {

   @NotBlank(message = "receiverIdentity不能为空")
   @Schema(description = "接收人exchange")
   private String receiverIdentity;

   @NotNull(message = "receiverId不能为空")
   @Min(value = 1, message = "receiverId不能小于1")
   @Schema(description = "接收人ID")
   private Long receiverId;

   @Min(value = 1, message = "ttl不能小于1")
   @Schema(description = "消息过期时间")
   private Integer ttl;

   @Min(value = 0, message = "senderId不能小于0")
   @Schema(description = "发送人ID")
   private Long senderId;

   @NotBlank(message = "senderIdentity不能为空")
   @Schema(description = "发送人exchange")
   private String senderIdentity;

   @Schema(description = "发送人头像")
   private String senderPhoto = "http://static-1258386385.cos.ap-beijing.myqcloud.com/img/System.jpg";

   @NotNull(message = "senderName不能为空")
   @Schema(description = "发送人名称")
   private String senderName;

   @NotNull(message = "消息不能为空")
   @Schema(description = "消息内容")
   private String msg;
}

@PostMapping("/searchMessageById")
@Operation(summary = "根据ID查询消息")
public R searchMessageById(@Valid @RequestBody SearchMessageByIdForm form) {
   HashMap map = messageService.searchMessageById(form.getId());
   return R.ok().put("result", map);
}

@PostMapping("/updateUnreadMessage")
@Operation(summary = "未读消息更新成已读消息")
public R updateUnreadMessage(@Valid @RequestBody UpdateUnreadMessageForm form) {
   long rows = messageService.updateUnreadMessage(form.getId());
   return R.ok().put("rows", rows);
}

@PostMapping("/deleteMessageRefById")
@Operation(summary = "删除消息")
public R deleteMessageRefById(@Valid @RequestBody DeleteMessageRefByIdForm form) {
   long rows = messageService.deleteMessageRefById(form.getId());
   return R.ok().put("rows", rows);
}

@PostMapping("/refreshMessage")
@Operation(summary = "刷新用户消息")
public R refreshMessage(@Valid @RequestBody RefreshMessageForm form) {
   messageTask.receive(form.getIdentity(), form.getUserId());
   long lastRows = messageService.searchLastCount(form.getUserId(), form.getIdentity());
   long unreadRows = messageService.searchUnreadCount(form.getUserId(), form.getIdentity());
   HashMap map = new HashMap() {{
      put("lastRows", lastRows + "");
      put("unreadRows", unreadRows + "");
   }};
   return R.ok().put("result", map);
}

@PostMapping("/sendPrivateMessage")
@Operation(summary = "同步发送私有消息")
public R sendPrivateMessage(@RequestBody @Valid SendPrivateMessageForm form) {
   MessageEntity entity = new MessageEntity();
   entity.setSenderId(form.getSenderId());
   entity.setSenderIdentity(form.getSenderIdentity());
   entity.setSenderPhoto(form.getSenderPhoto());
   entity.setSenderName(form.getSenderName());
   entity.setMsg(form.getMsg());
   entity.setSendTime(new Date());
   messageTask.sendPrivateMessage(form.getReceiverIdentity(), form.getReceiverId(), form.getTtl(), entity);
   return R.ok();
}

@PostMapping("/sendPrivateMessageAsync")
@Operation(summary = "异步发送私有消息")
public R sendPrivateMessageAsync(@RequestBody @Valid SendPrivateMessageForm form) {
   MessageEntity entity = new MessageEntity();
   entity.setSenderId(form.getSenderId());
   entity.setSenderIdentity(form.getSenderIdentity());
   entity.setSenderPhoto(form.getSenderPhoto());
   entity.setSenderName(form.getSenderName());
   entity.setMsg(form.getMsg());
   entity.setSendTime(new Date());
   messageTask.sendPrivateMessageAsync(form.getReceiverIdentity(), form.getReceiverId(), form.getTtl(), entity);
   return R.ok();
}
```
### 司机确认账单推送给乘客
1. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/SendPrivateMessageForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/SnmServiceApi.java
   补全 bff-driver/src/main/java/com/example/hxds/bff/driver/service/impl/OrderServiceImpl.java#updateOrderStatus
```java
@Data
@Schema(description = "同步发送私有消息的表单")
public class SendPrivateMessageForm {

    @NotBlank(message = "receiverIdentity不能为空")
    @Schema(description = "接收人exchange")
    private String receiverIdentity;

    @NotNull(message = "receiverId不能为空")
    @Min(value = 1, message = "receiverId不能小于1")
    @Schema(description = "接收人ID")
    private Long receiverId;

    @Min(value = 1, message = "ttl不能小于1")
    @Schema(description = "消息过期时间")
    private Integer ttl;

    @Min(value = 0, message = "senderId不能小于0")
    @Schema(description = "发送人ID")
    private Long senderId;

    @NotBlank(message = "senderIdentity不能为空")
    @Schema(description = "发送人exchange")
    private String senderIdentity;

    @Schema(description = "发送人头像")
    private String senderPhoto = "http://static-1258386385.cos.ap-beijing.myqcloud.com/img/System.jpg";

    @NotNull(message = "senderName不能为空")
    @Schema(description = "发送人名称")
    private String senderName;

    @NotNull(message = "消息不能为空")
    @Schema(description = "消息内容")
    private String msg;
}

@PostMapping("/message/sendPrivateMessage")
@Operation(summary = "同步发送私有消息")
R sendPrivateMessage(SendPrivateMessageForm form);

@PostMapping("/message/sendPrivateMessageSync")
@Operation(summary = "异步发送私有消息")
R sendPrivateMessageSync(SendPrivateMessageForm form);

@Override
@Transactional
@LcnTransaction
public int updateOrderStatus(UpdateOrderStatusForm form) {
   R r = odrServiceApi.updateOrderStatus(form);
   int rows = MapUtil.getInt(r, "rows");
   // TODO:判断订单的状态，然后实现后续业务
   if (rows != 1) {
      throw new HxdsException("订单状态修改失败");
   }
   if (form.getStatus() == 6) {
      SendPrivateMessageForm messageForm = new SendPrivateMessageForm();
      messageForm.setReceiverIdentity("customer_bill");
      messageForm.setReceiverId(form.getCustomerId());
      messageForm.setTtl(3 * 24 * 3600 * 1000);
      // 如果这条消息是系统发出的，那么senderId为0
      messageForm.setSenderId(0L);
      messageForm.setSenderIdentity("system");
      messageForm.setSenderName("华夏代驾");
      messageForm.setMsg("您有代驾订单待支付");
      snmServiceApi.sendPrivateMessage(messageForm);
   }
   return rows;
}
```
2. 写 hxds-driver-wx/order/order_bill/order_bill.vue#updateOrderBill
```vue
sendOrderBill: function() {
	let that = this;
	uni.showModal({
		title: '提示消息',
		content: '是否发送代驾账单给客户？',
		success: function(resp) {
			if (resp.confirm) {
				let data = {
					orderId: that.orderId,
					customerId: that.customerId,
					status: 6
				};
				that.ajax(that.url.updateOrderStatus, 'POST', data, function(resp) {
					that.workStatus = '等待付款';
					uni.setStorageSync('workStatus', '等待付款');
					uni.navigateTo({
						url: '../waiting_payment/waiting_payment?orderId=' + that.orderId
					});
				});
			}
		}
	});
}
```
### 乘客接收订单消息
1. 写 hxds-snm/src/main/java/com/example/hxds/snm/task/MessageTask.java#receiveBillMessage
   写 hxds-snm/src/main/java/com/example/hxds/snm/controller/form/ReceiveBillMessageForm.java
   写 hxds-snm/src/main/java/com/example/hxds/snm/controller/MessageController.java#receiveBillMessage
```java
 public String receiveBillMessage(String identity, long userId) {
     String exchangeName = identity + "_private"; //交换机名字
     String queueName = "queue_" + userId; //队列名字
     String routingKey = userId + ""; //routing key
     try (
             Connection connection = factory.newConnection();
             Channel channel = connection.createChannel();
     ) {
         channel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT);
         channel.queueDeclare(queueName, true, false, false, null);
         channel.queueBind(queueName, exchangeName, routingKey);
         channel.basicQos(0, 1, true);
         GetResponse response = channel.basicGet(queueName, false);
         if (response != null) {
             AMQP.BasicProperties properties = response.getProps();
             Map<String, Object> map = properties.getHeaders();
             String messageId = map.get("messageId").toString();
             byte[] body = response.getBody();
             String msg = new String(body);
             log.debug("从RabbitMQ接收的订单消息：" + msg);

             MessageRefEntity entity = new MessageRefEntity();
             entity.setMessageId(messageId);
             entity.setReceiverId(userId);
             entity.setReceiverIdentity(identity);
             entity.setReadFlag(true);
             entity.setLastFlag(true);
             messageService.insertRef(entity);

             long deliveryTag = response.getEnvelope().getDeliveryTag();
             channel.basicAck(deliveryTag, false);

             return msg;
         }
         return "";
     } catch (Exception e) {
         log.error("执行异常", e);
         throw new HxdsException("接收新订单失败");
     }
 }

@Data
@Schema(description = "接收新订单消息的表单")
public class ReceiveBillMessageForm {
   @NotNull(message = "userId不能为空")
   @Min(value = 1, message = "userId不能小于1")
   @Schema(description = "用户ID")
   private Long userId;

   @NotBlank(message = "identity不能为空")
   @Pattern(regexp = "^driver$|^mis$|^customer$|^customer_bill$",message = "identity内容不正确")
   @Schema(description = "用户身份")
   private String identity;
}

@PostMapping("/receiveBillMessage")
@Operation(summary = "同步接收新订单消息")
public R receiveBillMessage(@RequestBody @Valid ReceiveBillMessageForm form){
   String msg = messageTask.receiveBillMessage(form.getIdentity(), form.getUserId());
   return R.ok().put("result",msg);
}
```
2. 写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/ReceiveBillMessageForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/SnmServiceApi.java#receiveBillMessage
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/service/MessageService.java#receiveBillMessage 及其实现类
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/MessageController.java#receiveBillMessage
```java
@Data
@Schema(description = "接收新订单消息的表单")
public class ReceiveBillMessageForm {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户身份")
    private String identity;
}

@PostMapping("/message/receiveBillMessage")
R receiveBillMessage(ReceiveBillMessageForm form);

String receiveBillMessage(ReceiveBillMessageForm form);

@Override
public String receiveBillMessage(ReceiveBillMessageForm form) {
   R r = snmServiceApi.receiveBillMessage(form);
   String map = MapUtil.getStr(r, "result");
   return map;
}

@PostMapping("/receiveBillMessage")
@SaCheckLogin
@Operation(summary = "同步接收新订单消息")
public R receiveBillMessage(@RequestBody @Valid ReceiveBillMessageForm form){
   long customerId = StpUtil.getLoginIdAsLong();
   form.setUserId(customerId);
   form.setIdentity("customer_bill");
   String msg = messageService.receiveBillMessage(form);
   return R.ok().put("result",msg);
}
```
3. 写 hxds-customer-wx/main.js#Vue.prototype.url
   写 hxds-customer-wx/execution/move/move.vue
```vue
receiveBillMessage: `${baseUrl}/order/receiveBillMessage`,

data() {
  return {
  // ...
  messageTimer: null
}

onShow: function() {
   // ...
   if(status == 4 || status == 5){
      that.messageTimer=setInterval(function(){
         that.ajax(that.url.receiveBillMessage,"POST",{},function(resp){
               if(resp.data.result=="您有代驾订单待支付"){
                  uni.redirectTo({
                      url:"../order/order?orderId="+that.orderId
                  })
               }
         },false)
      },5000)
   }
}

onHide: function() {
   // ...
   clearInterval(that.messageTimer)
   that.messageTimer = null
}
```
4. 【测试】1.到Navicat上面把订单记录的status改成4状态，然后把后端各个子系统运行起来。
   2.在手机上面运行乘客端的小程序，因为有正在执行的订单，所以小程序会跳转到move.vue页面。
   3.用Navicat把订单的status改成5状态。或许有同学说想说为什么不调用updateOrderStatus()函数, 把订单修改成5状态?这是因为我们之前运行程序的时候，
   在司机端小程序上面测试过结束代驾，所以订单表和账单表的记录都更新过了，并且分账表也有记录。故此，我们不用再重新执行结束代驾流程，只改订单状态即可。
   4.接下来用FastRequest插件,调用bff-driver子系统的updateOrderStatus()函数,把订单修改成6状态。观察乘客端小程序的效果，应该是收到通知消息之后，
   立即跳转到order.vue页面
### 乘客端显示待付款账单信息
1. 写 hxds-odr/src/main/resources/mapper/OrderDao.xml#searchOrderById 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java#searchOrderById 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/SearchOrderByIdForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#searchOrderById
```java
<select id="searchOrderById" parameterType="Map" resultType="HashMap">
     SELECT CAST(o.id AS CHAR) AS id,
     CAST(o.driver_id AS CHAR) AS driverId,
     CAST(o.customer_id AS CHAR) AS customerId,
     o.start_place AS startPlace,
     o.start_place_location AS startPlaceLocation,
     o.end_place AS endPlace,
     o.end_place_location AS endPlaceLocation,
     CAST(b.total AS CHAR) AS total,
     CAST(b.real_pay AS CHAR) AS realPay,
     CAST(b.mileage_fee AS CHAR) AS mileageFee,
     CAST(o.favour_fee AS CHAR) AS favourFee,
     CAST(o.incentive_fee AS CHAR) AS incentiveFee,
     CAST(b.waiting_fee AS CHAR) AS waitingFee,
     CAST(b.return_fee AS CHAR) AS returnFee,
     CAST(b.parking_fee AS CHAR) AS parkingFee,
     CAST(b.toll_fee AS CHAR) AS tollFee,
     CAST(b.other_fee AS CHAR) AS otherFee,
     CAST(b.voucher_fee AS CHAR) AS voucherFee,
     CAST(o.real_mileage AS CHAR) AS realMileage,
     o.waiting_minute AS waitingMinute,
     b.base_mileage AS baseMileage,
     CAST(b.base_mileage_price AS CHAR) AS baseMileagePrice,
     CAST(b.exceed_mileage_price AS CHAR) AS exceedMileagePrice,
     b.base_minute AS baseMinute,
     CAST(b.exceed_minute_price AS CHAR) AS exceedMinutePrice,
     b.base_return_mileage AS baseReturnMileage,
     CAST(b.exceed_return_price AS CHAR) AS exceedReturnPrice,
     CAST(o.return_mileage AS CHAR) AS returnMileage,
     o.car_plate AS carPlate,
     o.car_type AS carType,
     o.status,
     DATE_FORMAT(o.create_time, '%Y-%m-%d %H:%i:%s') AS createTime
     FROM tb_order o
     JOIN tb_order_bill b ON o.id = b.order_id
     WHERE o.id = #{orderId}
     <if test="driverId!=null">
         AND driver_id = #{driverId}
     </if>
     <if test="customerId!=null">
         AND customer_id = #{customerId}
     </if>
</select>

HashMap searchOrderById(Map param);

HashMap searchOrderById(Map param);

@Override
public HashMap searchOrderById(Map param) {
     HashMap map = orderDao.searchOrderById(param);
     String startPlaceLocation = MapUtil.getStr(map, "startPlaceLocation");
     String endPlaceLocation = MapUtil.getStr(map, "endPlaceLocation");
     map.replace("startPlaceLocation", JSONUtil.parse(startPlaceLocation));
     map.replace("endPlaceLocation", JSONUtil.parse(endPlaceLocation));
     return map;
}

@Data
@Schema(description = "根据ID查询订单信息的表单")
public class SearchOrderByIdForm {
   @NotNull
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;

   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
}

@PostMapping("/searchOrderById")
@Operation(summary = "根据id查询订单信息")
public R searchOrderById(@RequestBody @Valid SearchOrderByIdForm form) {
   Map param = BeanUtil.beanToMap(form);
   Map map = orderService.searchOrderById(param);
   return R.ok().put("result", map);
}
```
2. 修改 hxds-dr/src/main/resources/mapper/DriverDao.xml#searchDriverBriefInfo
```xml
 <select id="searchDriverBriefInfo" parameterType="long" resultType="HashMap">
     SELECT CAST(id AS CHAR) AS id,
            `name`,
            tel,
            photo
     FROM tb_driver
     WHERE id = #{driverId}
 </select>
```
3. 写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/SearchDriverBriefInfoForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/DrServiceApi.java#searchDriverBriefInfo
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/SearchOrderByIdForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/OdrServiceApi.java#searchOrderById
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/service/OrderService.java#searchOrderById 及其实现类
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/service/impl/OrderServiceImpl.java#searchOrderById
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/OrderController.java#searchOrderById
```java
@Data
@Schema(description = "查询司机简明信息的表单")
public class SearchDriverBriefInfoForm {
    @NotNull(message = "driverId不能为空")
    @Min(value = 1, message = "driverId不能小于1")
    @Schema(description = "司机ID")
    private Long driverId;
}

@PostMapping("/driver/searchDriverBriefInfo")
R searchDriverBriefInfo(SearchDriverBriefInfoForm form);

@Data
@Schema(description = "根据ID查询订单信息的表单")
public class SearchOrderByIdForm {
   @NotNull
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @Schema(description = "客户ID")
   private Long customerId;

}

@PostMapping("/order/searchOrderById")
R searchOrderById(SearchOrderByIdForm form);

HashMap searchOrderById(SearchOrderByIdForm form);

@Override
public HashMap searchOrderById(SearchOrderByIdForm form) {
   R r = odrServiceApi.searchOrderById(form);
   HashMap map = (HashMap) r.get("result");
   Long driverId = MapUtil.getLong(map, "driverId");
   if (!Objects.isNull(driverId)) {
      SearchDriverBriefInfoForm infoForm = new SearchDriverBriefInfoForm();
      infoForm.setDriverId(driverId);
      r = drServiceApi.searchDriverBriefInfo(infoForm);
      HashMap temp = (HashMap) r.get("result");
      map.putAll(temp);
      return map;
   }
   return null;
}

@PostMapping("/searchOrderById")
@SaCheckLogin
@Operation(summary = "根据ID查询订单信息")
public R searchOrderById(@RequestBody @Valid SearchOrderByIdForm form) {
   long customerId = StpUtil.getLoginIdAsLong();
   form.setCustomerId(customerId);
   HashMap map = orderService.searchOrderById(form);
   return R.ok().put("result", map);
}
```
4. 写 hxds-customer-wx/main.js#Vue.prototype.url
   写 hxds-customer-wx/execution/order/order.vue#onLoad
   【说明】`status` tinyint NOT NULL DEFAULT '1' COMMENT '1等待接单，2已接单，3司机已到达，4开始代驾，5结束代驾，6未付款，7已付款，8订单已结束，9顾客撤单，10司机撤单，11事故关闭，12其他'
```vue
searchOrderById: `${baseUrl}/order/searchOrderById`,

onLoad: function(options) {
  let that = this;
  let orderId = options.orderId;
  that.orderId = orderId;
  let data = {
      orderId: orderId
  };
  that.ajax(that.url.searchOrderById, 'POST', data, function(resp) {
      let result = resp.data.result;
      // console.log(result);
      that.driverId = result.driverId;
      that.name = result.name;
      that.photo = result.photo;
      that.title = result.title;
      that.tel = result.tel;
      that.startPlace = result.startPlace;
      that.endPlace = result.endPlace;
      that.createTime = result.createTime;
      that.favourFee = result.favourFee;
      that.incentiveFee = result.incentiveFee;
      that.carPlate = result.carPlate;
      that.carType = result.carType;
      let status = result.status;
      that.status = status;
      if ([5, 6, 7, 8].includes(status)) {
          that.realMileage = result.realMileage;
          that.mileageFee = result.mileageFee;
          that.waitingFee = result.waitingFee;
          that.waitingMinute = result.waitingMinute;
          that.returnFee = result.returnFee;
          that.returnMileage = result.returnMileage;
          that.parkingFee = result.parkingFee;
          that.tollFee = result.tollFee;
          that.otherFee = result.otherFee;
          that.total = result.total;
          that.voucherFee = result.voucherFee;
      }
      that.baseMileagePrice = result.baseMileagePrice;
      that.baseMileage = result.baseMileage;
      that.exceedMileagePrice = result.exceedMileagePrice;
      that.base_minute = result.baseMinute;
      that.exceedMinutePrice = result.exceedMinutePrice;
      that.baseReturnMileage = result.baseReturnMileage;
      that.exceedReturnPrice = result.exceedReturnPrice;
      that.realPay = '--';
      if ([2, 3].includes(status)) {
          that.img = '../../static/order/icon-1.png';
      } else if ([4].includes(status)) {
          that.img = '../../static/order/icon-2.png';
      } else if ([5, 6].includes(status)) {
          that.img = '../../static/order/icon-3.png';
      } else if ([7].includes(status)) {
          that.img = '../../static/order/icon-4.png';
          that.realPay = result.realPay;
      } else if ([8, 9, 10, 11, 12].includes(status)) {
          that.img = '../../static/order/icon-5.png';
          that.realPay = result.realPay;
      }
  }
}
```
### 微信支付分账前，先查询司机和乘客OpenId
1. 写 hxds-cst/src/main/resources/mapper/CustomerDao.xml#searchCustomerOpenId 及其对应接口
   写 hxds-cst/src/main/java/com/example/hxds/cst/service/CustomerService.java#searchCustomerOpenId 及其实现类
   写 hxds-cst/src/main/java/com/example/hxds/cst/controller/form/SearchCustomerOpenIdForm.java
   写 hxds-cst/src/main/java/com/example/hxds/cst/controller/CustomerController.java#searchCustomerOpenId
```java
<select id="searchCustomerOpenId" parameterType="long" resultType="String">
   SELECT open_id AS openId
   FROM tb_customer
   WHERE id = #{customerId}
</select>

String searchCustomerOpenId(long customerId);

String searchCustomerOpenId(long customerId);

@Override
public String searchCustomerOpenId(long customerId) {
     String openId = customerDao.searchCustomerOpenId(customerId);
     return openId;
}

@Data
@Schema(description = "查询客户OpenId的表单")
public class SearchCustomerOpenIdForm {
   @NotNull(message = "customerId不能为空")
   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;
}

@PostMapping("/searchCustomerOpenId")
@Operation(summary = "查询客户的OpenId")
public R searchCustomerOpenId(@RequestBody @Valid SearchCustomerOpenIdForm form){
   String openId = customerService.searchCustomerOpenId(form.getCustomerId());
   return R.ok().put("result",openId);
}
```
2. 写 hxds-dr/src/main/resources/mapper/DriverDao.xml#searchDriverOpenId 及其对应接口
   写 hxds-dr/src/main/java/com/example/hxds/dr/service/DriverService.java#searchDriverOpenId 及其实现类
   写 hxds-dr/src/main/java/com/example/hxds/dr/controller/form/SearchDriverOpenIdForm.java
   写 hxds-dr/src/main/java/com/example/hxds/dr/controller/DriverController.java#searchDriverOpenId
```java
<select id="searchDriverOpenId" parameterType="long" resultType="String">
   SELECT open_id AS openId
   FROM tb_driver
   WHERE id = #{driverId}
</select>

String searchDriverOpenId(long driverId);

String searchDriverOpenId(long driverId);

@Override
public String searchDriverOpenId(long driverId) {
     String openId = driverDao.searchDriverOpenId(driverId);
     return openId;
}

@Data
@Schema(description = "查询司机OpenId的表单")
public class SearchDriverOpenIdForm {
   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;
}

@PostMapping("/searchDriverOpenId")
@Operation(summary = "查询司机的OpenId")
public R searchDriverOpenId(@RequestBody @Valid SearchDriverOpenIdForm form){
   String openId = driverService.searchDriverOpenId(form.getDriverId());
   return R.ok().put("result", openId);
}
```
3. 写 hxds-odr/src/main/resources/mapper/OrderDao.xml 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/db/dao/OrderBillDao.java 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderBillService.java 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/ValidCanPayOrderForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/UpdateOrderPrepayIdForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/UpdateBillPaymentForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderBillController.java
```java
<select id="validCanPayOrder" parameterType="Map" resultType="HashMap">
     SELECT CAST(real_fee AS CHAR)  AS realFee,
     CAST(driver_id AS CHAR) AS driverId,
     uuid
     FROM tb_order
     WHERE id = #{orderId}
     AND customer_id = #{customerId}
     AND `status` = 6
</select>
<update id="updateOrderPrepayId" parameterType="Map">
        UPDATE tb_order
        SET prepay_id = #{prepayId}
        WHERE id = #{orderId}
</update>

HashMap validCanPayOrder(Map param);

int updateOrderPrepayId(Map param);

<update id="updateBillPayment" parameterType="Map">
     UPDATE tb_order_bill
     SET real_pay = #{realPay},
     <if test="voucherFee==null">
        voucherFee="0.00"
     </if>
     <if test="voucherFee!=null">
        voucher_fee = #{voucherFee}
     </if>
     WHERE order_id = #{orderId}
</update>

int updateBillPayment(Map param);

HashMap validCanPayOrder(Map param);

int updateOrderPrepayId(Map param);

@Override
public HashMap validCanPayOrder(Map param) {
     HashMap map = orderDao.validCanPayOrder(param);
     if (Objects.isNull(map) || map.size() == 0) {
        throw new HxdsException("订单无法支付");
     }
     return map;
}

@Override
public int updateOrderPrepayId(Map param) {
     int rows = orderDao.updateOrderPrepayId(param);
     if (rows != 1) {
        throw new HxdsException("更新预支付订单ID失败");
     }
     return rows;
}

int updateBillPayment(Map param);

@Override
public int updateBillPayment(Map param) {
     int rows = orderBillDao.updateBillPayment(param);
     if (rows != 1) {
        throw new HxdsException("更新账单实际支付费用失败");
     }
     return rows;
}

@Data
@Schema(description = "检查订单是否可以支付的表单")
public class ValidCanPayOrderForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @NotNull(message = "customerId不能为空")
   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;
}

@Data
@Schema(description = "更新预支付订单ID的表单")
public class UpdateOrderPrepayIdForm {
   @NotBlank(message = "prepayId不能为空")
   @Schema(description = "预支付订单ID")
   private String prepayId;

   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@PostMapping("/validCanPayOrder")
@Operation(summary = "检查订单是否可以支付")
public R validCanPayOrder(@RequestBody @Valid ValidCanPayOrderForm form) {
   Map param = BeanUtil.beanToMap(form);
   HashMap map = orderService.validCanPayOrder(param);
   return R.ok().put("result", map);
}

@PostMapping("/updateOrderPrepayId")
@Operation(summary = "更新预支付订单ID")
public R updateOrderPrepayId(@RequestBody @Valid UpdateOrderPrepayIdForm form) {
   Map param = BeanUtil.beanToMap(form);
   int rows = orderService.updateOrderPrepayId(param);
   return R.ok().put("rows", rows);
}

@Data
@Schema(description = "更新账单实际支付费用的表单")
public class UpdateBillPaymentForm {
   @NotBlank(message = "realPay不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "realPay内容不正确")
   @Schema(description = "实际支付金额")
   private String realPay;

   @NotBlank(message = "voucherFee不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "voucherFee内容不正确")
   @Schema(description = "代金券面额")
   private String voucherFee;

   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@PostMapping("/updateBillPayment")
@Operation(summary = "更新账单实际支付费用")
public R updateBillPayment(@RequestBody @Valid UpdateBillPaymentForm form){
   Map param = BeanUtil.beanToMap(form);
   int rows = orderBillService.updateBillPayment(param);
   return R.ok().put("rows",rows);
}
```
4. 写 hxds-vhr/src/main/resources/mapper/VoucherCustomerDao.xml#validCanUseVoucher/bindVoucher 及其对应接口
   写 hxds-vhr/src/main/java/com/example/hxds/vhr/service/VoucherCustomerService.java#useVoucher 及其实现类
   写 hxds-vhr/src/main/java/com/example/hxds/vhr/controller/form/UseVoucherForm.java
   写 hxds-vhr/src/main/java/com/example/hxds/vhr/controller/VoucherCustomerController.java#useVoucher
```java
 <select id="validCanUseVoucher" parameterType="Map" resultType="String">
     SELECT CAST(v.discount AS CHAR) AS discount
     FROM tb_voucher_customer vc
              JOIN tb_voucher v ON vc.voucher_id = v.id
     WHERE vc.voucher_id = #{voucherId}
       AND vc.customer_id = #{customerId}
       AND v.with_amount &lt;= #{amount}
       AND vc.`status` = 1
       AND ((CURRENT_DATE BETWEEN vc.start_time AND vc.end_time)
         OR (vc.start_time IS NULL AND vc.end_time IS NULL));
 </select>
 <update id="bindVoucher" parameterType="Map">
     UPDATE tb_voucher_customer
     SET order_id = #{orderId},
         `status` = 2
     WHERE voucher_id = #{voucherId}
 </update>

String validCanUseVoucher(Map param);

int bindVoucher(Map param);

String useVoucher(Map param);

@Override
@Transactional
@LcnTransaction
public String useVoucher(Map param) {
     String discount = voucherCustomerDao.validCanUseVoucher(param);
     if (!Objects.isNull(discount)) {
        int rows = voucherCustomerDao.bindVoucher(param);
        if (rows != 1) {
            throw new HxdsException("代金券不可用");
        }
        return discount;
     }
     throw new HxdsException("代金券不可用");
}

@Data
@Schema(description = "使用代金券的表单")
public class UseVoucherForm {
   @NotNull(message = "id不能为空")
   @Min(value = 1, message = "id不能小于1")
   @Schema(description = "领取代金券的ID")
   private Long id;

   @NotNull(message = "voucherId不能为空")
   @Min(value = 1, message = "voucherId不能小于1")
   @Schema(description = "代金券的ID")
   private Long voucherId;

   @NotNull(message = "customerId不能为空")
   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;

   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "客户ID")
   private Long orderId;

   @NotBlank(message = "amount不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "amount内容不正确")
   @Schema(description = "订单金额")
   private String amount;

}

@PostMapping("/useVoucher")
@Operation(summary = "检查可否使用代金券")
public R useVoucher(@RequestBody @Valid UseVoucherForm form){
   Map param = BeanUtil.beanToMap(form);
   String discount = voucherCustomerService.useVoucher(param);
   return R.ok().put("result",discount);
}
```
5. 写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/ValidCanPayOrderForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/UpdateBillPaymentForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/UpdateOrderPrepayIdForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/OdrServiceApi.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/UseVoucherForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/VhrServiceApi.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/SearchCustomerOpenIdForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/CstServiceApi.java#searchCustomerOpenId
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/SearchDriverOpenIdForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/DrServiceApi.java#searchDriverOpenId
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/service/OrderService.java#createWxPayment 及其实现类
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/CreateWxPaymentForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/OrderController.java#createWxPayment
```java
@Data
@Schema(description = "检查订单是否可以支付的表单")
public class ValidCanPayOrderForm {

   @Schema(description = "订单ID")
   private Long orderId;

   @Schema(description = "客户ID")
   private Long customerId;
}

@Data
@Schema(description = "更新账单实际支付费用的表单")
public class UpdateBillPaymentForm {

   @Schema(description = "实际支付金额")
   private String realPay;

   @Schema(description = "代金券面额")
   private String voucherFee;

   @Schema(description = "订单ID")
   private Long orderId;
}

@Data
@Schema(description = "更新支付订单ID的表单")
public class UpdateOrderPrepayIdForm {

    @Schema(description = "预支付订单ID")
    private String prepayId;

    @Schema(description = "订单ID")
    private Long orderId;
}

@PostMapping("/order/validCanPayOrder")
R validCanPayOrder(ValidCanPayOrderForm form);

@PostMapping("/bill/updateBillPayment")
R updateBillPayment(UpdateBillPaymentForm form);

@PostMapping("/order/updateOrderPrepayId")
R updateOrderPrepayId(UpdateOrderPrepayIdForm form);

@Data
@Schema(description = "使用代金券的表单")
public class UseVoucherForm {
   @NotNull(message = "id不能为空")
   @Min(value = 1, message = "id不能小于1")
   @Schema(description = "领取的代金券ID")
   private Long id;

   @NotNull(message = "voucherId不能为空")
   @Min(value = 1, message = "voucherId不能小于1")
   @Schema(description = "代金券的ID")
   private Long voucherId;

   @NotNull(message = "customerId不能为空")
   @Min(value = 1, message = "customerId不能小于1")
   @Schema(description = "客户ID")
   private Long customerId;

   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "客户ID")
   private Long orderId;

   @NotBlank(message = "amount不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "amount内容不正确")
   @Schema(description = "订单金额")
   private String amount;
}
   
@PostMapping("/voucher/customer/useVoucher")
R useVoucher(UseVoucherForm form);

@Data
@Schema(description = "查询客户OpenId的表单")
public class SearchCustomerOpenIdForm {

   @Schema(description = "客户ID")
   private Long customerId;
   
}

@PostMapping("/customer/searchCustomerOpenId")
public R searchCustomerOpenId(SearchCustomerOpenIdForm form);

@Data
@Schema(description = "查询司机OpenId的表单")
public class SearchDriverOpenIdForm {

   @Schema(description = "司机ID")
   private Long driverId;
   
}

@PostMapping("/driver/searchDriverOpenId")
public R searchDriverOpenId(SearchDriverOpenIdForm form);

HashMap createWxPayment(long orderId, long customerId, Long customerVoucherId, Long voucherId);

@Override
@Transactional
@LcnTransaction
public HashMap createWxPayment(long orderId, long customerId, Long customerVoucherId, Long voucherId) {
   // 先查询订单是否为6状态，其余状态都不可以生成支付订单
   ValidCanPayOrderForm form_1 = new ValidCanPayOrderForm();
   form_1.setOrderId(orderId);
   form_1.setCustomerId(customerId);
   R r = odrServiceApi.validCanPayOrder(form_1);
   HashMap map = (HashMap) r.get("result");
   String amount = MapUtil.getStr(map, "realFee");
   String uuid = MapUtil.getStr(map, "uuid");
   long driverId = MapUtil.getLong(map, "driverId");
   String discount = "0.00";
   if (!Objects.isNull(voucherId) && !Objects.isNull(customerVoucherId)) {
      // 查询代金券是否可以使用并绑定
      UseVoucherForm form_2 = new UseVoucherForm();
      form_2.setCustomerId(customerId);
      form_2.setVoucherId(voucherId);
      form_2.setOrderId(orderId);
      form_2.setAmount(amount);
      r = vhrServiceApi.useVoucher(form_2);
      discount = MapUtil.getStr(r, "result");
   }
   if (new BigDecimal(amount).compareTo(new BigDecimal(discount)) == -1) {
      throw new HxdsException("总金额不能小于优惠劵面额");
   }
   // 修改实付金额
   amount = NumberUtil.sub(amount, discount).toString();
   UpdateBillPaymentForm form_3 = new UpdateBillPaymentForm();
   form_3.setOrderId(orderId);
   form_3.setRealPay(amount);
   form_3.setVoucherFee(discount);
   odrServiceApi.updateBillPayment(form_3);
   // 查询用户的OpenId字符串
   SearchCustomerOpenIdForm form_4 = new SearchCustomerOpenIdForm();
   form_4.setCustomerId(customerId);
   r = cstServiceApi.searchCustomerOpenId(form_4);
   String customerOpenId = MapUtil.getStr(r, "result");
   // 查询司机的OpenId字符串
   SearchDriverOpenIdForm form_5 = new SearchDriverOpenIdForm();
   form_5.setDriverId(driverId);
   r = drServiceApi.searchDriverOpenId(form_5);
   String driverOpenId = MapUtil.getStr(r, "result");
   // 创建支付订单
   try {
      WXPay wxPay = new WXPay(myWXPayConfig);
      HashMap param = Maps.newHashMap();
      // 生成随机字符串
      param.put("nonce_str", WXPayUtil.generateNonceStr());
      param.put("body", "代驾费");
      param.put("out_trade_no", uuid);
      // 充值金额转换成分为单位，并且让BigDecimal取整数
      // amount = "1.00"
      param.put("total_fee", NumberUtil.mul(amount, "100").setScale(0, RoundingMode.FLOOR).toString());
      param.put("total_fee", "4");
      param.put("spbill_create_ip", "127.0.0.1");
      // 这里要修改成内网穿透的公网URL
      param.put("notify_url", "http://s2.nsloop.com:8955/hxds-odr/order/recieveMessage");
      param.put("trade_type", "JSAPI");
      param.put("openid", customerOpenId);
      param.put("attach", driverOpenId);
      // 支付需要分账
      param.put("profit_sharing", "Y");
      // 创建支付订单
      Map<String, String> result = wxPay.unifiedOrder(param);
      System.out.println(result);
      // 预支付交易会话标识id
      String prepayId = result.get("prepay_id");
      if (Objects.isNull(prepayId)) {
         // 更新订单记录中的prepay_id字段值
         UpdateOrderPrepayIdForm form_6 = new UpdateOrderPrepayIdForm();
         form_6.setOrderId(orderId);
         form_6.setPrepayId(prepayId);
         odrServiceApi.updateOrderPrepayId(form_6);
         // 准备生成数字签名用的数据
         map.clear();
         map.put("appId", myWXPayConfig.getAppID());
         String timeStamp = new Date().getTime() + "";
         map.put("timeStamp", timeStamp);
         String nonceStr = WXPayUtil.generateNonceStr();
         map.put("nonceStr", nonceStr);
         map.put("package", "prepay_id=" + prepayId);
         map.put("signType", "MD5");
         // 生成数字签名
         String paySign = WXPayUtil.generateSignature(map, myWXPayConfig.getKey());
         // 清理HashMap，放入结果
         map.clear();
         map.put("package", "prepay_id=" + prepayId);
         map.put("timeStamp", timeStamp);
         map.put("nonceStr", nonceStr);
         map.put("paySign", paySign);
         // uuid用户付款成功后，移动端主动请求更新充值状态
         map.put("uuid", uuid);
         return map;
      } else {
         log.error("创建支付订单失败");
         throw new HxdsException("创建支付订单失败");
      }
   } catch (Exception e) {
      log.error("创建支付订单失败", e);
      throw new HxdsException("创建支付订单失败");
   }
}

@Data
@Schema(description = "创建支付订单")
public class CreateWxPaymentForm {
   @NotNull(message = "orderId不为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;

   @Schema(description = "客户ID")
   private Long customerId;


   @Min(value = 1, message = "customerVoucherId不能小于1")
   @Schema(description = "领取的代金券ID")
   private Long customerVoucherId;

   @Min(value = 1, message = "customerVoucherId不能小于1")
   @Schema(description = "代金券ID")
   private Long voucherId;
}

@PostMapping("/createWxPayment")
@Operation(summary = "创建支付订单")
@SaCheckLogin
public R createWxPayment(@RequestBody @Valid CreateWxPaymentForm form) {
   long customerId = StpUtil.getLoginIdAsLong();
   form.setCustomerId(customerId);
   HashMap map = orderService.createWxPayment(form.getOrderId(), form.getCustomerId(), form.getCustomerVoucherId(), form.getVoucherId());
   return R.ok().put("result", map);
}
```
### 乘客端小程序唤起付款窗口
1. 写 hxds-customer-wx/main.js#Vue.prototype.url
   写 hxds-customer-wx/pages/order/order.vue#payHandle
```vue
createWxPayment: `${baseUrl}/order/createWxPayment`,

payHandle: function() {
   let that = this;
   uni.showModal({
       title: '提示消息',
       content: '您确定支付该订单？',
       success: function(resp) {
           if (resp.confirm) {
               let data = {
                   orderId: that.orderId,
                   customerVoucherId: that.voucher.id,
                   voucherId: that.voucher.voucherId
               };
               that.ajax(that.url.createWxPayment, 'POST', data, function(resp) {
                   let result = resp.data.result;
                   let pk = result.package;
                   let timeStamp = result.timeStamp;
                   let nonceStr = result.nonceStr;
                   let paySign = result.paySign;
                   let uuid = result.uuid;
                   uni.requestPayment({
                       timeStamp: timeStamp,
                       nonceStr: nonceStr,
                       package: pk,
                       paySign: paySign,
                       signType: 'MD5',
                       success: function() {
                           console.log('付款成功');
                           // TODO: 主动发起查询请求
                       },
                       fail: function(error) {
                           console.error(error);
                           uni.showToast({
                               icon: 'error',
                               title: '付款失败'
                           });
                       }
                   });
               });
           }
       }
   });
},
```
2. 写 gateway/src/main/resources/bootstrap.yml
```properties
- id: hxds-odr
 uri: lb://hxds-odr
 predicates:
   - Path=/hxds-odr/**
 filters:
   - StripPrefix=1
```
3. 写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/OrderController.java#receiveMessage
```java
@RequestMapping("/receiveMessage")
@Operation(summary = "接收代驾费消息通知")
public void receiveMessage(HttpServletRequest request, HttpServletResponse response) throws Exception {
   request.setCharacterEncoding("UTF-8");
   Reader reader = request.getReader();
   BufferedReader buffer = new BufferedReader(reader);
   String line = buffer.readLine();
   StringBuffer temp = new StringBuffer();
   while (line != null) {
      temp.append(line);
      line = buffer.readLine();
   }
   buffer.close();
   reader.close();
   String xml = temp.toString();
   if (WXPayUtil.isSignatureValid(xml, myWXPayConfig.getKey())) {
      Map<String, String> map = WXPayUtil.xmlToMap(xml);
      String resultCode = map.get("result_code");
      String returnCode = map.get("return_code");
      if ("SUCCESS".equals(resultCode) && "SUCCESS".equals(returnCode)) {
          response.setCharacterEncoding("UTF-8");
          response.setContentType("application/xml");
          Writer writer = response.getWriter();
          BufferedWriter bufferedWriter = new BufferedWriter(writer);
          bufferedWriter.write("<xml><return_code><![CDATA[SUCCESS]]></return_code> <return_msg><![CDATA[OK]]></return_msg></xml>");
          bufferedWriter.close();
          writer.close();
          String uuid = map.get("out_trade_no");
          String payId = map.get("transaction_id");
          String driverOpenId = map.get("attach");
          String payTime = DateUtil.parse(map.get("time_end"), "yyyyMMddHHmmss").toString("yyyy-MM-dd HH:mm:ss");
          // TODO: 修改订单状态、执行分账、发放系统奖励
      } else {
          response.sendError(500, "数字签名异常");
      }
   }
}
```