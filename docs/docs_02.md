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
3. 写
```javascript

```