## 订单执行与安全监控
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
        DELETE tb_order_bill WHERE order_id = #{orderId}
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
1. 写 