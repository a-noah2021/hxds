### 订单更新为已付款，向代驾司机发放奖励
1. 写 hxds-dr/src/main/resources/mapper/WalletIncomeDao.xml#insert 及其对应接口
   写 hxds-dr/src/main/resources/mapper/WalletDao.xml#updateWalletBalance 及其对应接口
   写 hxds-dr/src/main/java/com/example/hxds/dr/service/WalletIncomeService.java#transfer 及其实现类
   写 hxds-dr/src/main/java/com/example/hxds/dr/controller/form/TransferForm.java
   写 hxds-dr/src/main/java/com/example/hxds/dr/controller/WalletIncomeController.java#transfer
```java
<insert id="insert" parameterType="com.example.hxds.dr.db.pojo.WalletIncomeEntity">
    INSERT INTO tb_wallet_income
    SET uuid=#{uuid},
        driver_id=#{driverId},
        amount=#{amount},
        `type` =#{type},
        `status`=#{status},
        remark=#{remark}
</insert>

int insert(WalletIncomeEntity entity);

<update id="updateWalletBalance" parameterType="map">
    UPDATE tb_wallet
    SET balance=balance + #{amount}
    WHERE driver_id = #{driverId}
    <if test="amount &lt; 0 and password!=null">
        AND balance >= ABS(#{amount})
        AND password = MD5(CONCAT(MD5(driver_id),#{password}))
    </if>
</update>

int updateWalletBalance(Map param);

int transfer(WalletIncomeEntity entity);
        
@Override
@Transactional
@LcnTransaction
public int transfer(WalletIncomeEntity entity) {
   // 添加转账记录
   int rows = walletIncomeDao.insert(entity);
   if (rows != 1) {
       throw new HxdsException("添加转账记录失败");
   }
   HashMap param = new HashMap() {{
       put("driverId", entity.getDriverId());
       put("amount", entity.getAmount());
   }};
   // 更新账户余额
   rows = walletDao.updateWalletBalance(param);
   if (rows != 1) {
       throw new HxdsException("更新钱包余额失败");
   }
   return rows;
}

@Data
@Schema(description = "转账的表单")
public class TransferForm {
   @NotBlank(message = "uuid不能为空")
   @Schema(description = "uuid")
   private String uuid;

   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

   @NotBlank(message = "amount不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "amount内容不正确")
   @Schema(description = "转账金额")
   private String amount;

   @NotNull(message = "type不能为空")
   @Range(min = 1, max = 3, message = "type内容不正确")
   @Schema(description = "充值类型")
   private Byte type;

   @NotBlank(message = "remark不能为空")
   @Schema(description = "备注信息")
   private String remark;
}

@PostMapping("/transfer")
@Operation(summary = "转账")
public R transfer(@RequestBody @Valid TransferForm form) {
   WalletIncomeEntity entity = BeanUtil.toBean(form, WalletIncomeEntity.class);
   entity.setStatus((byte) 3);
   int rows = walletIncomeService.transfer(entity);
   return R.ok().put("rows", rows);
}
```
2. 写 hxds-odr/src/main/resources/mapper/OrderDao.xml 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/TransferForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/feigin/DrServiceApi.java#transfer
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java#handlePayment 及其实现类
   补充 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#receiveMessage
```java
 <select id="searchOrderIdAndStatus" parameterType="String" resultType="HashMap">
     SELECT CAST(id AS CHAR) AS id,
            `status`
     FROM tb_order
     WHERE uuid = #{uuid}
 </select>
 <select id="searchDriverIdAndIncentiveFee" parameterType="String" resultType="HashMap">
     SELECT CAST(driver_id AS CHAR)     AS driverId,
            CAST(incentive_fee AS CHAR) AS incentiveFee
     FROM tb_order
     WHERE uuid = #{uuid}
 </select>
 <update id="updateOrderPayIdAndStatus" parameterType="Map">
     UPDATE tb_order
     SET pay_id   = #{payId},
         `status` = 7,
         pay_time = #{payTime}
     WHERE uuid = #{uuid}
 </update>

HashMap searchOrderIdAndStatus(String uuid);

HashMap searchDriverIdAndIncentiveFee(String uuid);

int updateOrderPayIdAndStatus(Map param);

@Data
@Schema(description = "转账的表单")
public class TransferForm {
   @NotBlank(message = "uuid不能为空")
   @Schema(description = "uuid")
   private String uuid;

   @NotNull(message = "driverId不能为空")
   @Min(value = 1, message = "driverId不能小于1")
   @Schema(description = "司机ID")
   private Long driverId;

   @NotBlank(message = "amount不能为空")
   @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "amount内容不正确")
   @Schema(description = "转账金额")
   private String amount;

   @NotNull(message = "type不能为空")
   @Range(min = 1, max = 3, message = "type内容不正确")
   @Schema(description = "充值类型")
   private Byte type;

   @NotBlank(message = "remark不能为空")
   @Schema(description = "备注信息")
   private String remark;

}

@PostMapping("/wallet/income/transfer")
R transfer(TransferForm form);

void handlePayment(String uuid, String payId, String driverOpenId, String payTime);

@Override
@Transactional
@LcnTransaction
public void handlePayment(String uuid, String payId, String driverOpenId, String payTime) {
   // 更新订单状态之前，先查询订单的状态
   // 因为乘客端付款成功之后会主动发起Ajax请求，要求更新订单状态
   // 所以后端接收到付款通知消息之后，不要着急修改订单状态，先看一下订单是否是7状态(已付款)
   HashMap map = orderDao.searchOrderIdAndStatus(uuid);
   int status = MapUtil.getInt(map, "status");
   if (status == 7) {
      return;
   }
   HashMap param = new HashMap() {{
      put("uuid", uuid);
      put("payId", payId);
      put("payTime", payTime);
   }};
   // 更新订单记录的PayId、状态和付款时间
   int rows = orderDao.updateOrderPayIdAndStatus(param);
   if (rows != 1) {
      throw new HxdsException("更新支付订单ID失败");
   }
   // 查询系统奖励
   map = orderDao.searchDriverIdAndIncentiveFee(uuid);
   String incentiveFee = MapUtil.getStr(map, "incentiveFee");
   long driverId = MapUtil.getLong(map, "driverId");
   // 判断系统奖励费是否大于0
   if (new BigDecimal(incentiveFee).compareTo(new BigDecimal("0.00")) == 1) {
      TransferForm form = new TransferForm();
      form.setUuid(IdUtil.simpleUUID());
      form.setAmount(incentiveFee);
      form.setDriverId(driverId);
      form.setType((byte) 2);
      form.setRemark("系统奖励费");
      // 给司机钱包转账奖励费
      drServiceApi.transfer(form);
   }
   // TODO: 执行分账
}

// 修改订单状态、执行分账、发放系统奖励
orderService.handlePayment(uuid, payId, driverOpenId, payTime);
```
### 订单子系统执行账单分账
普通商户的分账比例默认上限是30%，也就是说商户自己留存70%，给员工或者其他人分账最高比例是
30%，在普通业务中也还凑合，因为员工拿的提成很少有高于30%的。但是在代驾业务中，代驾司机的
劳务费占了订单总额的大头,所以给司机30%的分账比例根本不够。
微信商户平台之所以给商户默认设置这么低的分账上限，主要是为了防止商户逃税或者洗钱。比如公司按
照营业利润交税，但是公司老板想要少缴税，这需要通过某些手段减少企业盈利。于是把订单分账比例调
高到99%，然后公司客户每笔货款都被分账到了老板指定的微信上面，揣到老板自己腰包里面，而公司的
营业利润大幅降低，缴纳的税款也就变少了。微信平台为了避免有些公司通过分账来逃税，所以就给分账
比例设置了30%的上线。不过也不是申请更高的分账比例，在设置分账比例上线的页面中，我们可以按照
申请流程的指引，申请更高的分账比例。这就需要企业提交更多的证明材料,然后微信平台还要聘请专业
的审计公司对审核的单位加以评估。即便你们公司申请下来高比例的分账，但是微信平台会把你们公司的
分账记录和税务局的金税系统联网，随时监控你们公司是否伪造交易虚设分账，企图逃税

假设用户付款成功，商户系统至少要2分钟之后才可以申请分账。很多同学第一次做分账,不知道这个细
节，导致Web方法刚收到付款成功的通知消息，就立即发起分账，然后就收到订单暂时不可以分账的异
常消息。既然我们要等待两分钟以上才可以申请分账，所以我们就应该用上Quartz定时器技术
1. 写 hxds-odr/src/main/resources/mapper/OrderProfitsharingDao.xml 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/quartz/job/HandleProfitsharingJob.java#executeInternal
```java
 <select id="searchDriverIncome" parameterType="String" resultType="HashMap">
     SELECT p.id                        AS profitsharingId,
            CAST(driver_income AS CHAR) AS driverIncome
     FROM tb_order_profitsharing p
              JOIN tb_order o ON p.order_id = o.id
     WHERE o.uuid = #{uuid};
 </select>

 <update id="updateProfitsharingStatus" parameterType="long">
     UPDATE tb_order_profitsharing
     SET `status`= 2
     WHERE id = #{profitsharingId}
 </update>

HashMap searchDriverIncome(String uuid);

int updateProfitsharingStatus(long profitsharingId);

@Override
@Transactional
protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
   // 获取传递给定时器的业务数据
   Map map = context.getJobDetail().getJobDataMap();
   String uuid = MapUtil.getStr(map, "uuid");
   String driverOpenId = MapUtil.getStr(map, "driverOpenId");
   String payId = MapUtil.getStr(map, "payId");
   // 查询分账记录ID、分账金额
   map = profitsharingDao.searchDriverIncome(uuid);
   if (map == null || map.size() == 0) {
      log.error("没有查询到分账记录");
      return;
   }
   String driverIncome = MapUtil.getStr(map, "driverIncome");
   long profitsharingId = MapUtil.getLong(map, "profitsharingId");
   try {
      WXPay wxPay = new WXPay(myWXPayConfig);
      // 分账请求必要的参数
      HashMap param = new HashMap() {{
         put("appid", myWXPayConfig.getAppID());
         put("mch_id", myWXPayConfig.getMchID());
         put("nonce_str", WXPayUtil.generateNonceStr());
         put("out_order_no", uuid);
         put("transaction_id", payId);
      }};
      // 分账收款人数组
      JSONArray receivers = new JSONArray();
      // 分账收款人（司机）信息
      JSONObject json = new JSONObject();
      json.set("type", "PERSONAL_OPENID");
      json.set("account", driverOpenId);
      //分账金额从元转换成分
      int amount = Integer.parseInt(NumberUtil.mul(driverIncome, "100").setScale(0, RoundingMode.FLOOR).toString());
      json.set("amount", amount);
      // json.set("amount", 1); //设置分账金额为1分钱（测试阶段）
      json.set("description", "给司机的分账");
      receivers.add(json);
      // 添加分账收款人JSON数组
      param.put("receivers", receivers.toString());
      // 生成数字签名
      String sign = WXPayUtil.generateSignature(param, myWXPayConfig.getKey(), WXPayConstants.SignType.HMACSHA256);
      param.put("sign", sign);
      String url = "/secapi/pay/profitsharing";
      // 执行分账请求
      String response = wxPay.requestWithCert(url, param, 3000, 3000);
      log.debug(response);
      // 验证响应的数字签名
      if (WXPayUtil.isSignatureValid(response, myWXPayConfig.getKey(), WXPayConstants.SignType.HMACSHA256)) {
         // 从响应中提取数据
         Map<String, String> data = wxPay.processResponseXml(response, WXPayConstants.SignType.HMACSHA256);
         String returnCode = data.get("return_code");
         String resultCode = data.get("result_code");
         // 验证通信状态码和业务状态码
         if ("SUCCESS".equals(resultCode) && "SUCCESS".equals(returnCode)) {
            String status = data.get("status");
            // 把分账记录更改为2状态
            if ("FINISHED".equals(status)) {
               // 更新分账状态
               int rows = profitsharingDao.updateProfitsharingStatus(profitsharingId);
               if (rows != 1) {
                  log.error("更新分账状态失败", new HxdsException("更新分账状态失败"));
               }
            }
            // 判断正在分账中
            else if ("PROCESSING".equals(status)) {
               // TODO: 创建查询分账定时器
            }
         } else {
            log.error("执行分账失败", new HxdsException("执行分账失败"));
         }
      } else {
         log.error("验证数字签名失败", new HxdsException("验证数字签名失败"));
      }
   } catch (Exception e) {
      log.error("执行分账失败", e);
   }
}
```
2. 写 hxds-odr/src/main/resources/mapper/OrderDao.xml#finishOrder 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/quartz/QuartzUtil.java
   补全 hxds-odr/src/main/java/com/example/hxds/odr/service/impl/OrderServiceImpl.java#handlePayment
```java
<update id="finishOrder" parameterType="String">
        UPDATE tb_order
        SET `status` = 8
        WHERE uuid = #{uuid}
</update>

int finishOrder(String uuid);

@Component
@Slf4j
public class QuartzUtil {

   @Resource
   private Scheduler scheduler;

   /**
    * 添加定时器
    */
   public void addJob(JobDetail jobDetail, String jobName, String jobGroupName, Date start) {
      try {
         Trigger trigger = TriggerBuilder.newTrigger().withIdentity(jobName, jobGroupName)
                 .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                         .withMisfireHandlingInstructionFireNow())
                 .startAt(start).build();
         scheduler.scheduleJob(jobDetail, trigger);
      } catch (SchedulerException e) {
         log.error("定时器添加失败", e);
      }
   }

   /**
    * 查询是否存在定时器
    */
   public boolean checkExists(String jobName, String jobGroupName) {
      TriggerKey key = new TriggerKey(jobName, jobGroupName);
      try {
         boolean bool = scheduler.checkExists(key);
         return bool;
      } catch (Exception e) {
         log.error("定时器查询失败", e);
         return false;
      }
   }

   /**
    * 删除定时器
    */
   public void deleteJob(String jobName, String jobGroupName) {
      TriggerKey key = new TriggerKey(jobName, jobGroupName);
      try {
         scheduler.resumeTrigger(key);
         scheduler.unscheduleJob(key);
         scheduler.deleteJob(JobKey.jobKey(jobName, jobGroupName));
         log.debug("成功删除" + jobName + "定时器");
      } catch (SchedulerException e) {
         log.error("定时器删除失败", e);
      }
   }

}

// 先判断是否有分账定时器
if (quartzUtil.checkExists(uuid, "代驾单分账任务组") || quartzUtil.checkExists(uuid, "查询代驾单分账任务组")){
    // 存在分账定时器就不需要再继续分账
    return;
}
// 执行分账
JobDetail jobDetail = JobBuilder.newJob(HandleProfitsharingJob.class).build();
Map dataMap = jobDetail.getJobDataMap();
dataMap.put("uuid", uuid);
dataMap.put("driverOpenId", driverOpenId);
dataMap.put("payId", payId);
// 2分钟之后执行分账定时器
Date executeDate = new DateTime().offset(DateField.MINUTE, 2);
quartzUtil.addJob(jobDetail, uuid, "代驾单分账任务组", executeDate);
// 更新订单状态为已完成状态(8)
rows = orderDao.finishOrder(uuid);
if (rows != 1) {
    throw new HxdsException("更新订单结束状态失败");
}
```
3. 【测试】1.在课程QQ群中，把分账收款人的OPENID和真实姓名(收款人微信APP. 上面开通支付时候备案的真
   实姓名)发送给课程讲师。因为分账收款人必须要添加到微信商户平台，才可以分账。
   2.必须运行量子互联程序，否则付款成功之后，无法收到付款结果通知消息。
   3.修改创建支付账单的Java代码，设置接收通知消息的URL路径
   4.测试阶段，我们可以手动把订单付款金额调低，比如付款金额为4分钱，给司机分账1分钱(不超过
   30%分账比例上限)
   5.每个代驾订单的UUID只能创建一个微信 支付账单。如果遇上无法创建支付账单，很可能之间你用这
   个UUID创建过支付账单，所以就无法重复创建了。这种情况很简单，重新生成- -个UUID字符串更新
   到订单记录的uuid字段上面即可(UUID字符串不能带有横线，可以在线生成,
   https://uuid.bmcx.com/)。
   6.运行各个子系统，在乘客端小程序上面支付代驾订单。
   7.订单子系统成功接收付款通知消息之后，查看订单状态是否变成了8状态。
   8.等待2分钟以上的时间，分账定时器开始执行后，查看分账的结果(执行中状态)
   9.查司机手机.上面的微信是否收到了分账的转账款(大概20分钟之后)
4. 写 hxds-odr/src/main/java/com/example/hxds/odr/quartz/job/SearchProfitsharingJob.java#executeInternal
   补充 hxds-odr/src/main/java/com/example/hxds/odr/quartz/job/HandleProfitsharingJob.java#executeInternal
```java
 @Override
 protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
     Map map = context.getJobDetail().getJobDataMap();
     String uuid = MapUtil.getStr(map, "uuid");
     long profitsharingId = MapUtil.getLong(map, "profitsharingId");
     String payId = MapUtil.getStr(map, "payId");
     try {
         WXPay wxPay = new WXPay(myWXPayConfig);
         String url = "/pay/profitsharingquery";
         HashMap param = new HashMap() {{
             put("mch_id", myWXPayConfig.getMchID());
             put("transaction_id", payId);
             put("out_order_no", uuid);
             put("nonce_str", WXPayUtil.generateNonceStr());
         }};
         // 生成数字签名
         String sign = WXPayUtil.generateSignature(param, myWXPayConfig.getKey(), WXPayConstants.SignType.HMACSHA256);
         param.put("sign", sign);
         // 查询分账结果
         String response = wxPay.requestWithCert(url, param, 3000, 3000);
         log.debug(response);
         // 验证响应的数字签名
         if (WXPayUtil.isSignatureValid(response, myWXPayConfig.getKey(), WXPayConstants.SignType.HMACSHA256)) {
             Map<String, String> data = wxPay.processResponseXml(response, WXPayConstants.SignType.HMACSHA256);
             String returnCode = data.get("return_code");
             String resultCode = data.get("result_code");
             if ("SUCCESS".equals(resultCode) && "SUCCESS".equals(returnCode)) {
                 String status = data.get("status");
                 if ("FINISHED".equals(status)) {
                     // 把分账记录更改为2状态
                     int rows = profitsharingDao.updateProfitsharingStatus(profitsharingId);
                     if (rows != 1) {
                         log.error("更新分账状态失败", new HxdsException("更新分账状态失败"));
                     }
                 }
             } else {
                 log.error("查询分账失败", new HxdsException("查询分账失败"));
             }
         } else {
             log.error("验证数字签名失败", new HxdsException("验证数字签名失败"));
         }
     } catch (Exception e) {
         e.printStackTrace();
     }
 }

// 判断正在分账中
else if ("PROCESSING".equals(status)) {
   // 如果状态是分账中，等待几分钟再查询分账结果
   JobDetail jobDetail = JobBuilder.newJob(SearchProfitsharingJob.class).build();
   Map dataMap = jobDetail.getJobDataMap();
   dataMap.put("uuid", uuid);
   dataMap.put("profitsharingId", profitsharingId);
   dataMap.put("payId", payId);
   DateTime executeDate = new DateTime().offset(DateField.MINUTE, 20);
   quartzUtil.addJob(jobDetail, uuid, "查询代驾单分账任务组", executeDate);
}
```
5. 【测试】1.把订单记录status改成6状态,然后重新生成uuid字符串，更新到订单记录的uuid字段上面
   2.运行各个子系统，在乘客端小程序上面支付代驾订单
   3.如果付款成功，等待2分钟，然后查看订单状态是否修改成了8状态
   4.等待20分钟(各个子系统都不能停止)，然后查看分账记录的status是否变成了2状态
### 订单微服务主动查询付款结果
1. 写 hxds-odr/src/main/resources/mapper/OrderDao.xml 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderService.java#updateOrderAboutPayment 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/UpdateOrderAboutPaymentForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderController.java#updateOrderAboutPayment
```java
<select id="searchUuidAndStatus" parameterType="long" resultType="HashMap">
     SELECT uuid,
            `status`
     FROM tb_order
     WHERE id = #{orderId}
</select>
<update id="updateOrderAboutPayment" parameterType="Map">
     UPDATE tb_order
     SET status   = 7,
     pay_id   = #{payId},
     pay_time = #{payTime}
     WHERE id = #{orderId}
</update>

HashMap searchUuidAndStatus(long orderId);

int updateOrderAboutPayment(Map param);

String updateOrderAboutPayment(Map param);

@Override
@Transactional
@LcnTransaction
public String updateOrderAboutPayment(Map param) {
    long orderId = MapUtil.getLong(param, "orderId");
    // 查询订单状态
    // 因为有可能Web方法先收到了付款结果通知消息，把订单状态改成了7、8状态
    // 所以先进行订单状态判断
    HashMap map = orderDao.searchUuidAndStatus(orderId);
    String uuid = MapUtil.getStr(map, "uuid");
    int status = MapUtil.getInt(map, "status");
    // 如果订单状态已经是已付款，就退出当前方法
    if (status == 7 || status == 8) {
        return "付款成功";
    }
    // 查询支付结果的参数
    map.clear();
    map.put("appid", myWXPayConfig.getAppID());
    map.put("mch_id", myWXPayConfig.getMchID());
    map.put("out_trade_no", uuid);
    map.put("nonce_str", WXPayUtil.generateNonceStr());
    try {
        // 生成数字签名
        String sign = WXPayUtil.generateSignature(map, myWXPayConfig.getKey());
        map.put("sign", sign);
        WXPay wxPay = new WXPay(myWXPayConfig);
        // 查询支付结果
        Map<String, String> result = wxPay.orderQuery(map);
        Object returnCode = result.get("return_code");
        Object resultCode = result.get("result_code");
        if ("SUCCESS".equals(returnCode) && "SUCCESS".equals(resultCode)) {
            String tradeState = result.get("trade_state");
            if ("SUCCESS".equals(tradeState)) {
                String driverOpenId = result.get("attach");
                String payId = result.get("transaction_id");
                String payTime = new DateTime(result.get("time_end"), "yyyyMMddHHmmss").toString("yyyy-MM-dd HH:mm:ss");
                // 更新订单相关付款信息和状态
                param.put("payId", payId);
                param.put("payTime", payTime);
                // 把订单更新为7状态
                int rows = orderDao.updateOrderAboutPayment(param);
                if (rows != 1) {
                    throw new HxdsException("更新订单相关付款信息失败");
                }
                // 查询系统奖励
                map = orderDao.searchDriverIdAndIncentiveFee(uuid);
                String incentiveFee = MapUtil.getStr(map, "incentiveFee");
                long driverId = MapUtil.getLong(map, "driverId");
                // 判断系统奖励费是否大于0
                if (new BigDecimal(incentiveFee).compareTo(new BigDecimal("0.00")) == 1) {
                    TransferForm form = new TransferForm();
                    form.setUuid(IdUtil.simpleUUID());
                    form.setAmount(incentiveFee);
                    form.setDriverId(driverId);
                    form.setType((byte) 2);
                    form.setRemark("系统奖励费");
                    // 给司机钱包转账奖励费
                    drServiceApi.transfer(form);
                }
                // 先判断是否有分账定时器
                if (quartzUtil.checkExists(uuid, "代驾单分账任务组")
                        || quartzUtil.checkExists(uuid, "查询代驾单分账任务组")) {
                    // 存在分账计时器就不需要再执行分账
                    return "付款成功";
                }
                // 执行分账
                JobDetail jobDetail = JobBuilder.newJob(HandleProfitsharingJob.class).build();
                Map dataMap = jobDetail.getJobDataMap();
                dataMap.put("uuid", uuid);
                dataMap.put("driverOpenId", driverOpenId);
                dataMap.put("payId", payId);
                Date executeDate = new DateTime().offset(DateField.MINUTE, 2);
                quartzUtil.addJob(jobDetail, uuid, "代驾单分账任务组", executeDate);
                rows = orderDao.finishOrder(uuid);
                if (rows != 1) {
                    throw new HxdsException("更新订单结束状态失败");
                }
                return "付款成功";
            } else {
                return "付款异常";
            }
        } else {
            return "付款异常";
        }
    } catch (Exception e) {
        e.printStackTrace();
        throw new HxdsException("更新订单相关付款信息失败");
    }
}

@Data
@Schema(description = "更新代驾订单支付信息的表单")
public class UpdateOrderAboutPaymentForm {

   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
   
}

@PostMapping("/updateOrderAboutPayment")
@Operation(summary = "更新代驾订单支付信息")
public R updateOrderAboutPayment(@RequestBody @Valid UpdateOrderAboutPaymentForm form) {
   Map param = BeanUtil.beanToMap(form);
   String result = orderService.updateOrderAboutPayment(param);
   return R.ok().put("result", result);
}
```
2. 写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/UpdateOrderAboutPaymentForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/OdrServiceApi.java#updateOrderAboutPayment
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/service/OrderService.java#updateOrderAboutPayment 及其实现类
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/OrderController.java#updateOrderAboutPayment
```java
@Data
@Schema(description = "更新代驾订单支付信息的表单")
public class UpdateOrderAboutPaymentForm {

    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;
}

@PostMapping("/order/updateOrderAboutPayment")
R updateOrderAboutPayment(UpdateOrderAboutPaymentForm form);

String updateOrderAboutPayment(UpdateOrderAboutPaymentForm form);

@Override
@Transactional
@LcnTransaction
public String updateOrderAboutPayment(UpdateOrderAboutPaymentForm form) {
   R r = odrServiceApi.updateOrderAboutPayment(form);
   String result = MapUtil.getStr(r, "result");
   return result;
}

@PostMapping("/updateOrderAboutPayment")
@Operation(summary = "更新代驾订单支付信息")
@SaCheckLogin
public R updateOrderAboutPayment(@RequestBody @Valid UpdateOrderAboutPaymentForm form) {
   String result = orderService.updateOrderAboutPayment(form);
   return R.ok().put("result", result);
}
```
3. 写 hxds-customer-wx/main.js#Vue.prototype.url
   补全 hxds-customer-wx/execution/order/order.vue#payHandle
```vue
updateOrderAboutPayment: `${baseUrl}/order/updateOrderAboutPayment`,

// 主动发起查询请求
that.ajax(that.url.updateOrderAboutPayment, 'POST', data, function(resp) {
	let result = resp.data.result;
	if (result == '付款成功') {
		uni.showToast({
			icon: 'success',
			title: '付款成功'
		});
		setTimeout(function() {
            url: '../workbench/workbench'
		}, 2000);
	} else {
		uni.showToast({
			icon: 'success',
			title: '付款异常，如有疑问可以拨打客服电话'
		});
	}
});
```
4. 【测试】1.我们把量子互联程序关闭，这样后端Web方法就无法接收到付款结果通知消息了
   2.重新生成UUID字符串，更新到订单记录的uuid字段，然后把订单修改成6状态
   3.把分账记录修改成1状态
   4.启动各个子系统，然后在乘客端小程序上面支付订单
   5.支付成功之后，查看小程序是否跳转到工作台页面
   6.等待2分钟，查看订单记录是否修改成了8状态
   7.等待20分钟，查看分账记录是否修改成了2状态
### 司机端小程序轮询付款结果
1. 写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/SearchOrderStatusForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/form/UpdateOrderAboutPaymentForm.java
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/feign/OdrServiceApi.java#searchOrderStatus/updateOrderAboutPayment
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/service/OrderService.java#searchOrderStatus/updateOrderAboutPayment 及其实现类
   写 bff-driver/src/main/java/com/example/hxds/bff/driver/controller/OrderController.java#searchOrderStatus/updateOrderAboutPayment
```java
@Data
@Schema(description = "查询订单状态的表单")
public class SearchOrderStatusForm {
    @NotNull(message = "orderId不能为空")
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "司机ID")
    private Long driverId;
}

@Data
@Schema(description = "更新代驾订单支付信息的表单")
public class UpdateOrderAboutPaymentForm {
   @NotNull(message = "orderId不能为空")
   @Min(value = 1, message = "orderId不能小于1")
   @Schema(description = "订单ID")
   private Long orderId;
}

@PostMapping("/order/searchOrderStatus")
R searchOrderStatus(SearchOrderStatusForm form);

@PostMapping("/order/updateOrderAboutPayment")
R updateOrderAboutPayment(UpdateOrderAboutPaymentForm form);

Integer searchOrderStatus(SearchOrderStatusForm form);

String updateOrderAboutPayment(long driverId, UpdateOrderAboutPaymentForm form);

@Override
public Integer searchOrderStatus(SearchOrderStatusForm form) {
   R r = odrServiceApi.searchOrderStatus(form);
   Integer result = MapUtil.getInt(r, "result");
   return result;
}

@Override
@Transactional
@LcnTransaction
public String updateOrderAboutPayment(long driverId, UpdateOrderAboutPaymentForm form) {
   ValidDriverOwnOrderForm validForm = new ValidDriverOwnOrderForm();
   validForm.setDriverId(driverId);
   validForm.setOrderId(form.getOrderId());
   R r = odrServiceApi.validDriverOwnOrder(validForm);
   boolean bool = MapUtil.getBool(r, "result");
   if (!bool) {
      throw new HxdsException("司机未关联该订单");
   }
   r = odrServiceApi.updateOrderAboutPayment(form);
   String result = MapUtil.getStr(r, "result");
   return result;
}

@PostMapping("/searchOrderStatus")
@SaCheckLogin
@Operation(summary = "查询订单状态")
public R searchOrderStatus(@RequestBody @Valid SearchOrderStatusForm form) {
   long driverId = StpUtil.getLoginIdAsLong();
   form.setDriverId(driverId);
   Integer status = orderService.searchOrderStatus(form);
   return R.ok().put("result", status);
}

@PostMapping("/updateOrderAboutPayment")
@SaCheckLogin
@Operation(summary = "更新订单相关的付款信息")
public R updateOrderAboutPayment(@RequestBody @Valid UpdateOrderAboutPaymentForm form) {
   long driverId = StpUtil.getLoginIdAsLong();
   String result = orderService.updateOrderAboutPayment(driverId, form);
   return R.ok().put("result", result);
}
```
2. 写 hxds-driver-wx/main.js#Vue.prototype.url
   写 hxds-driver-wx/order/waiting_payment/waiting_payment.vue#onLoad
   写 hxds-driver-wx/order/waiting_payment/waiting_payment.vue#checkPaymentHandle
```vue
searchOrderStatus: `${baseUrl}/order/searchOrderStatus`,
updateOrderAboutPayment: `${baseUrl}/order/updateOrderAboutPayment`,

onLoad: function(options) {
		let that = this;
		that.orderId = options.orderId;
		that.timer = setInterval(function() {
			that.i++;
			if (that.i % 2 == 0) {
				let data = {
					orderId: that.orderId
				};
				that.ajax(
					that.url.searchOrderStatus,
					'POST',
					data,
					function(resp) {
						if (!resp.data.hasOwnProperty('result')) {
							uni.showToast({
								icon: 'none',
								title: '没有找到订单'
							});
							clearInterval(that.timer);
							that.i = 0;
						} else {
							let result = resp.data.result;
							if (result == 7 || result == 8) {
								uni.showToast({
									title: '客户已付款'
								});
								uni.setStorageSync('workStatus', '停止接单');
								clearInterval(that.timer);
								that.i = 0;
								setTimeout(function() {
									uni.switchTab({
										url: '../../pages/workbench/workbench'
									});
								}, 2500);
							}
						}
					},
					false
				);
			}
		}, 1000);
},

checkPaymentHandle: function() {
	let that = this;
	let data = {
		orderId: that.orderId
	};
	that.ajax(that.url.updateOrderAboutPayment, 'POST', data, function(resp) {
		let result = resp.data.result;
		if (result == '付款成功') {
			uni.showToast({
				title: '客户已付款'
			});
			uni.setStorageSync('workStatus', '停止接单');
			clearInterval(that.timer);
			that.i = 0;
			setTimeout(function() {
				uni.switchTab({
					url: '../../pages/workbench/workbench'
				});
			}, 2500);
		} else {
			uni.showToast({
				icon: '未检测到成功付款'
			});
		}
	});
}
```
5. 【测试】测试司机端小程序轮询订单支付结果的操作步骤如下:
   1.运行量子互联程序，把后端各个子系统运行起来。
   2.生成新的UUID字符串，更新到订单记录的uuid字段上面，订单status字段修改成6状态。
   3.分账记录修改成1状态。
   4.用小程序模拟器运行司机端小程序，直接运行waiting_payment.vue页面，然后URL传入orderId参数.
   5.【补】订单status字段修改成7或8状态
   6.观察司机端小程序是否检测到付款成功,然后跳转到工作台页面。
   测试司机端小程序强制查询付款结果步骤如下:
   1.关闭运行量子互联程序，把后端各个子系统运行起来。
   2.生成新的UUID字符串，更新到订单记录的uuid字段上面，订单status字段修改成6状态。
   3.分账记录修改成1状态。
   4.注释乘客端付款成功之后自动发起Ajax查询付款结果的代码。
   5.用小程序模拟器运行司机端小程序，直接运行waiting_payment.vue页面，然后URL传入orderId参数
   6.乘客端小程序为代驾订单付款。即便付款成功，订单依旧是6状态。
   7.在司机端小程序上面点击按钮，强制后端查询付款结果
   8.测试完毕之后,把注释掉的程序恢复
## 订单评价与申诉（如遇恶意差评，司机可以申诉）
由于乘客的评价关乎系统限制司机接单，所以一旦遇到乘客的恶意差评，代驾系统允许司机执行申诉，经过大数据审查与人工核验，可以给司机撤销恶意差评。如果差评属实，则系统自动限制司机接单，并且降低司机分账比例和接单奖励
### 订单子系统保存订单评价，并过滤内容
1. 写 hxds-odr/pom.xml
   写 hxds-odr/src/main/resources/mapper/OrderDao.xml#validDriverAndCustomerOwnOrder 及其对应接口
   写 hxds-odr/src/main/resources/mapper/OrderCommentDao.xml#insert 及其对应接口
   写 hxds-odr/src/main/java/com/example/hxds/odr/service/OrderCommentService.java#insert 及其实现类
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/form/InsertCommentForm.java
   写 hxds-odr/src/main/java/com/example/hxds/odr/controller/OrderCommentController.java#insertComment
```java
<!--数据万象依赖库-->
<dependency>
   <groupId>com.qcloud</groupId>
   <artifactId>cos_api</artifactId>
   <version>5.6.74</version>
</dependency>

<select id="validDriverAndCustomerOwnOrder" parameterType="Map" resultType="long">
     SELECT COUNT(*)
     FROM tb_order
     WHERE id = #{orderId}
     AND driver_id = #{driverId}
     AND customer_id = #{customerId}
</select>

long validDriverAndCustomerOwnOrder(Map param);

<insert id="insert" parameterType="com.example.hxds.odr.db.pojo.OrderCommentEntity">
     INSERT INTO tb_order_comment
     SET order_id=#{orderId},
     driver_id=#{driverId},
     customer_id=#{customerId},
     rate=#{rate},
     remark=#{remark},
     status=#{status},
     instance_id=#{instanceId},
     create_time=#{createTime}
</insert>

int insert(OrderCommentEntity entity);

int insert(OrderCommentEntity entity);

@Override
@Transactional
@LcnTransaction
public int insert(OrderCommentEntity entity) {
    // 验证司机和乘客与该订单是否关联
    HashMap param = new HashMap() {{
      put("orderId", entity.getOrderId());
      put("driverId", entity.getDriverId());
      put("customerId", entity.getCustomerId());
    }};
    long count = orderDao.validDriverAndCustomerOwnOrder(param);
    if (count != 1) {
      throw new HxdsException("司机和乘客与该订单无关联");
    }
    // 审核评价内容
    COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
    Region region = new Region("ap-beijing");
    ClientConfig config = new ClientConfig(region);
    COSClient client = new COSClient(cred, config);
    TextAuditingRequest request = new TextAuditingRequest();
    request.setBucketName(bucketPublic);
    request.getInput().setContent(Base64.encode(entity.getRemark()));
    request.getConf().setDetectType("all");

    TextAuditingResponse response = client.createAuditingTextJobs(request);
    AuditingJobsDetail detail = response.getJobsDetail();
    String state = detail.getState();
    if ("Success".equals(state)) {
      String result = detail.getResult();
      // 内容审查不通过就设置评价内容为null
      if (!"0".equals(result)) {
        entity.setRemark(null);
      }
    }
    // 保存评价
    int rows = orderCommentDao.insert(entity);
    if (rows != 1) {
      throw new HxdsException("保存订单评价失败");
    }
    return rows;
}

@Data
@Schema(description = "保存订单评价的表单")
public class InsertCommentForm {

   @NotNull
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


   @NotNull(message = "rate不能为空")
   @Range(min = 1, max = 5, message = "rate范围不正确")
   @Schema(description = "评价分数")
   private Byte rate;

   @Schema(description = "评价")
   private String remark = "默认系统好评";

}

@PostMapping("/insertComment")
@Operation(summary = "保存订单评价")
public R insertComment(@RequestBody @Valid InsertCommentForm form) {
   OrderCommentEntity entity = BeanUtil.toBean(form, OrderCommentEntity.class);
   entity.setStatus((byte) 1);
   entity.setCreateTime(new Date());
   int rows = orderCommentService.insert(entity);
   return R.ok().put("rows", rows);
}
```
2. 写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/form/InsertCommentForm.java
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/feign/OdrServiceApi.java#insertComment
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/service/OrderCommentService.java#insertComment 及其实现类
   写 bff-customer/src/main/java/com/example/hxds/bff/customer/controller/OrderCommentController.java#insertComment
```java
@Data
@Schema(description = "保存订单评价的表单")
public class InsertCommentForm {

    @NotNull
    @Min(value = 1, message = "orderId不能小于1")
    @Schema(description = "订单ID")
    private Long orderId;

    @NotNull(message = "driverId不能为空")
    @Min(value = 1, message = "driverId不能小于1")
    @Schema(description = "司机ID")
    private Long driverId;

    @Schema(description = "客户ID")
    private Long customerId;


    @NotNull(message = "rate不能为空")
    @Range(min = 1, max = 5, message = "rate范围不正确")
    @Schema(description = "评价分数")
    private Byte rate;

    @Schema(description = "评价")
    private String remark = "默认系统好评";
}

@PostMapping("/comment/insertComment")
R insertComment(InsertCommentForm form);

int insertComment(InsertCommentForm form);

@Override
@Transactional
@LcnTransaction
public int insertComment(InsertCommentForm form) {
   R r = odrServiceApi.insertComment(form);
   int rows = MapUtil.getInt(r, "result");
   if (rows != 1) {
      throw new HxdsException("保存订单评价失败");
   }
   return rows;
}

@PostMapping("/insertComment")
@Operation(summary = "保存订单评价")
@SaCheckLogin
public R insertComment(@RequestBody @Valid InsertCommentForm form){
   long customerId = StpUtil.getLoginIdAsLong();
   form.setCustomerId(customerId);
   int rows = orderCommentService.insertComment(form);
   return R.ok().put("rows",rows);
}
```
### 乘客付款后对订单评价
1. 写 hxds-mis-vue/src/router/index.js
   写 hxds-mis-vue/src/views/main.vue
   写 hxds-mis-vue/src/views/comment.vue#loadDataList
   写 hxds-mis-vue/src/views/comment.vue#created
   写 hxds-mis-vue/src/views/comment.vue#searchHandle
   写 hxds-mis-vue/src/views/comment.vue#sizeChangeHandle
   写 hxds-mis-vue/src/views/comment.vue#currentChangeHandle
```java
import Comment from "../views/comment.vue"

{
   path: '/comment',
   name: 'Comment',
   component: Comment,
   meta: {
      title: '订单评价',
      isTab: true
   }
},

<el-menu-item
        index="order"
        v-if="isAuth(['ROOT', 'COMMENT:SELECT'])"
@click="$router.push({ name: 'Comment' })">
   <SvgIcon name="company_fill" class="icon-svg" />
   <span slot="title">订单评价</span>
</el-menu-item>

loadDataList: function() {
  let that = this;
  that.dataListLoading = true;
  let data = {
    page: that.pageIndex,
    length: that.pageSize,
    orderId: that.dataForm.orderId == '' ? null : that.dataForm.orderId,
    driverId: that.dataForm.driverId == '' ? null : that.dataForm.driverId,
    customerId: that.dataForm.customerId == '' ? null : that.dataForm.customerId,
    rate: that.dataForm.rate == '' ? null : that.dataForm.rate,
    status: that.dataForm.status == '' ? null : that.dataForm.status
  };
  if (that.dataForm.date != null && that.dataForm.date.length == 2) {
    let startDate = that.dataForm.date[0];
    let endDate = that.dataForm.date[1];
    data.startDate = dayjs(startDate).format('YYYY-MM-DD');
    data.endDate = dayjs(endDate).format('YYYY-MM-DD');
  }

  that.$http('comment/searchCommentByPage', 'POST', data, true, function(resp) {
    let result = resp.result;
    let list = result.list;
    let status = {
      '1': '未申诉',
      '2': '已申诉',
      '3': '申诉失败',
      '4': '申诉成功'
    };
    let rate = {
      '1': '差评',
      '2': '差评',
      '3': '中评',
      '4': '中评',
      '5': '好评'
    };
    for (let one of list) {
      one.status = status[one.status + ''];
      one.rate = rate[one.rate + ''];
    }
    that.dataList = list;
    that.totalCount = Number(result.totalCount);
    that.dataListLoading = false;
  });
},

created: function() {
  this.loadDataList();
}

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

sizeChangeHandle: function(val) {
  this.pageSize = val;
  this.pageIndex = 1;
  this.loadDataList();
},
currentChangeHandle: function(val) {
  this.pageIndex = val;
  this.loadDataList();
},
```
2. 写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/AcceptCommentAppealForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/feign/WorkflowServiceApi.java#acceptCommentAppeal
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/service/OrderCommentService.java#acceptCommentAppeal 及其实现类
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/OrderCommandController.java#acceptCommentAppeal
```java
@Data
@Schema(description = "受理订单评价申诉的表单")
public class AcceptCommentAppealForm {

    @Schema(description = "用户ID")
    private Integer userId;

    @Schema(description = "用户姓名")
    private String userName;

    @NotNull(message = "commentId不能为空")
    @Min(value = 1, message = "commentId不能小于1")
    @Schema(description = "评价ID")
    private Long commentId;
}

@PostMapping("/comment/acceptCommentAppeal")
R acceptCommentAppeal(AcceptCommentAppealForm form);

void acceptCommentAppeal(AcceptCommentAppealForm form);

@Override
@Transactional
@LcnTransaction
public void acceptCommentAppeal(AcceptCommentAppealForm form) {
   HashMap map = userDao.searchUserSummary(form.getUserId());
   String name = MapUtil.getStr(map, "name");
   form.setUserName(name);
   workflowServiceApi.acceptCommentAppeal(form);
}

@PostMapping("/acceptCommentAppeal")
@Operation(summary = "受理评价申诉")
public R acceptCommentAppeal(@RequestBody @Valid AcceptCommentAppealForm form){
   int userId = StpUtil.getLoginIdAsInt();
   form.setUserId(userId);
   orderCommentService.acceptCommentAppeal(form);
   return R.ok();
}
```
3. 写 hxds-mis-vue/src/views/comment.vue#showAcceptModel
   写 hxds-mis-vue/src/views/comment.vue#acceptHandle
```vue
showAcceptModel: function(id) {
   this.acceptVisible = true;
   this.commentId = id;
},
acceptHandle: function() {
   let that = this;
   let data = {
       commentId: that.commentId
   };
   that.$http('comment/acceptCommentAppeal', 'POST', data, true, function(resp) {
       that.acceptVisible = false;
       that.$message.success('受理成功');
       that.expands = [];
       that.loadDataList();
   });
},
```
4. 写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/HandleCommentAppealForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/feign/WorkflowServiceApi.java#handleCommentAppeal
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/service/OrderCommentService.java#handleCommentAppeal 及其实现类
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/OrderCommandController.java#handleCommentAppeal
```java
@Data
@Schema(description = "处理评价申诉的表单")
public class HandleCommentAppealForm {
    @NotNull(message = "customerId不能为空")
    @Min(value = 1,message = "commentId不能小于1")
    private Long commentId;

    @NotBlank(message = "result不能为空")
    @Pattern(regexp = "^同意$|^不同意$", message = "result内容不正确")
    @Schema(description = "处理结果")
    private String result;

    @Schema(description = "处理说明")
    private String note;

    @NotBlank(message = "instanceId不能为空")
    @Schema(description = "工作流实例ID")
    private String instanceId;

    @Schema(description = "用户ID")
    private Integer userId;
}

@PostMapping("/comment/handleCommentAppeal")
R handleCommentAppeal(HandleCommentAppealForm form);

void handleCommentAppeal(HandleCommentAppealForm form);

@Override
@Transactional
@LcnTransaction
public void handleCommentAppeal(HandleCommentAppealForm form) {
   workflowServiceApi.handleCommentAppeal(form);
}

@PostMapping("/handleCommentAppeal")
@Operation(summary = "处理评价申诉")
public R handleCommentAppeal(@RequestBody @Valid HandleCommentAppealForm form){
   int userId = StpUtil.getLoginIdAsInt();
   form.setUserId(userId);
   orderCommentService.handleCommentAppeal(form);
   return R.ok();
}
```
5. 写 hxds-mis-vue/src/views/comment.vue#handleAppeal
```vue
handleAppeal: function() {
  let that = this;
  let data = {
    commentId: that.commentId,
    result: that.handleMode == 'yes' ? '同意' : '不同意',
    note: that.note != null && that.note.length > 0 ? that.note : null,
    instanceId: that.instanceId
  };
  that.$http('comment/handleCommentAppeal', 'POST', data, true, function(resp) {
    that.handleVisible = false;
    that.$message.success('执行成功');
    that.expands = [];
    that.loadDataList();
  });
},
```
6. 写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/form/SearchAppealContentForm.java
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/feign/WorkflowServiceApi.java#searchAppealContent
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/service/OrderCommentService.java#searchAppealContent 及其实现类
   写 hxds-mis-api/src/main/java/com/example/hxds/mis/api/controller/OrderCommandController.java#searchAppealContent
```java
@Data
@Schema(description = "查询审批工作流内容的表单")
public class SearchAppealContentForm {
    @NotBlank(message = "instanceId不能为空")
    @Schema(description = "工作流实例ID")
    private String instanceId;

    @NotNull(message = "isEnd不能为空")
    @Schema(description = "审批是否结束")
    private Boolean isEnd;
}

@PostMapping("/comment/searchAppealContent") 
R searchAppealContent(SearchAppealContentForm form);

HashMap searchAppealContent(SearchAppealContentForm form);

@Override
public HashMap searchAppealContent(SearchAppealContentForm form) {
   R r = workflowServiceApi.searchAppealContent(form);
   HashMap map = (HashMap) r.get("result");
   return map;
}

@PostMapping("/searchAppealContent")
@SaCheckPermission(value = {"ROOT", "COMMENT:SELECT"}, mode = SaMode.OR)
@Operation(summary = "查询审批工作流内容")
public R searchAppealContent(@RequestBody @Valid SearchAppealContentForm form){
   HashMap map = orderCommentService.searchAppealContent(form);
   return R.ok().put("result",map);
}
```
7. 写 hxds-mis-vue/src/views/comment.vue#expand
```vue
expand: function(row, expandedRows) {
   let that = this;
   if (expandedRows.length > 0) {
       that.expands = [];
       if (row) {
           that.expands.push(row.orderId);
           if (row.status != '未申诉') {
               let data = {
                   instanceId: row.instanceId,
                   isEnd: row.status == '已申诉' ? false : true
               };
               that.$http('comment/searchAppealContent', 'POST', data, true, function(resp) {
                   row.reason = resp.result.reason;
                   row.note = resp.result.note;
               });
           }
       } else {
           that.expands = [];
       }
   }
}
```