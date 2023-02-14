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
1. 写 