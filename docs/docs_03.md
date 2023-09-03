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
1. 写 
```java

```