package com.rengu.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rengu.entity.RG_AdjustProcessEntity;
import com.rengu.entity.RG_OrderEntity;
import com.rengu.entity.RG_PlanEntity;
import com.rengu.entity.RG_ScheduleEntity;
import com.rengu.util.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class AutoRollingSchedulingAction extends SuperAction {
    //待删除订单
    private static List<RG_OrderEntity> deleteOrderList = new ArrayList<>();

    public static String createScheduleInfo() throws ParseException, JsonProcessingException {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat simpleDateFormat1WithTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (UserConfigTools.getLatestSchedule("1") != null) {
            RG_ScheduleEntity rg_scheduleEntity = DAOFactory.getScheduleDAOImplInstance().findAllById(UserConfigTools.getLatestSchedule("1"));
            if (rg_scheduleEntity != null) {
                ObjectMapper objectMapper = new ObjectMapper();
                //创建排程信息的根节点
                ObjectNode scheduleInfo = objectMapper.createObjectNode();
                //计算本次排程开始时间
                calendar.setTime(simpleDateFormat.parse(simpleDateFormat.format(rg_scheduleEntity.getScheduleTime())));
                calendar.add(Calendar.DAY_OF_YEAR, rg_scheduleEntity.getRollTime());
                Date beginScheduleDate = calendar.getTime();
                scheduleInfo.put("name", "排程-" + Tools.formatToStandardDate(beginScheduleDate));
                scheduleInfo.put("scheduleWindow", rg_scheduleEntity.getScheduleWindow());
                scheduleInfo.put("rollTime", rg_scheduleEntity.getRollTime());
                scheduleInfo.put("scheduleOption", rg_scheduleEntity.getScheduleOption());
                scheduleInfo.put("BeginScheduleDate", simpleDateFormat1WithTime.format(beginScheduleDate));
                //上次排程的结束时间
                calendar.setTime(simpleDateFormat.parse(simpleDateFormat.format(rg_scheduleEntity.getScheduleTime())));
                calendar.add(Calendar.DAY_OF_YEAR, rg_scheduleEntity.getScheduleWindow());
                Date lastScheduleEndDate = calendar.getTime();
                //本次排程的结束时间
                calendar.setTime(beginScheduleDate);
                calendar.add(Calendar.DAY_OF_YEAR, rg_scheduleEntity.getScheduleWindow());
                Date currentScheduleEndDate = calendar.getTime();
                //获取新增滚动进来的订单
                List<RG_OrderEntity> rg_orderEntityList = DAOFactory.getOrdersDAOInstance().findAllByisFinishedAndDate(lastScheduleEndDate, currentScheduleEndDate, false);
                //加入上次排程使用的订单
                rg_orderEntityList.addAll(rg_scheduleEntity.getOrders());
                // 除去以调用删除接口删除的订单
                rg_orderEntityList.removeAll(deleteOrderList);
                ArrayNode ordersNode = objectMapper.createArrayNode();
                for (RG_OrderEntity rg_OrderEntity : rg_orderEntityList) {
                    ObjectNode objectNode = objectMapper.createObjectNode();
                    objectNode.put("id", rg_OrderEntity.getId());
                    ordersNode.add(objectNode);
                }
                scheduleInfo.set("orders", ordersNode);
                //Aps优化参数节点
                ObjectNode apsConfigNode = objectMapper.createObjectNode();
                //找出最早开工时间和最晚完工时间
                Date apsStartDate = rg_orderEntityList.get(0).getT0();
                Date apsEndDate = rg_orderEntityList.get(0).getT2();
                for (RG_OrderEntity rg_orderEntity : rg_orderEntityList) {
                    if (rg_orderEntity.getT0().before(apsStartDate)) {
                        apsStartDate = rg_orderEntity.getT0();
                    }
                    if (rg_orderEntity.getT2().after(apsEndDate)) {
                        apsEndDate = rg_orderEntity.getT2();
                    }
                }
                //计算优化开始时间
                calendar.setTime(apsStartDate);
                calendar.add(Calendar.DAY_OF_YEAR, -2);
                apsStartDate = calendar.getTime();
                //计算优化结束时间
                calendar.setTime(apsEndDate);
                calendar.add(Calendar.DAY_OF_YEAR, rg_scheduleEntity.getScheduleOption());
                apsEndDate = calendar.getTime();
                apsConfigNode.put("t0", apsStartDate.getTime());
                apsConfigNode.put("t2", apsEndDate.getTime());
                apsConfigNode.put("modeScheduling", rg_scheduleEntity.getApsModel());
                scheduleInfo.set("APSConfig", apsConfigNode);
                //布局信息
                ObjectNode layoutNode = objectMapper.createObjectNode();
                layoutNode.put("id", rg_scheduleEntity.getLayout().getId());
                scheduleInfo.set("layout", layoutNode);

                //资源信息
                ArrayNode resourcesNode = objectMapper.createArrayNode();
                ObjectNode resourceNode = objectMapper.createObjectNode();
                resourceNode.put("id", 2);
                resourcesNode.add(resourceNode);
                scheduleInfo.set("resources", resourcesNode);

                //工组信息
                ArrayNode groupResourceNode = objectMapper.createArrayNode();
                ObjectNode groupNode = objectMapper.createObjectNode();
                groupNode.put("id", 2);
                groupResourceNode.add(groupNode);
                scheduleInfo.put("groupResource", groupResourceNode);

                //位置信息
                ArrayNode sitesNode = objectMapper.createArrayNode();
                ObjectNode siteNode = objectMapper.createObjectNode();
                siteNode.put("id", 2);
                sitesNode.add(siteNode);
                scheduleInfo.put("site", sitesNode);
                return objectMapper.writeValueAsString(scheduleInfo);
            } else {
                System.out.println("查找上次排程信息出错");
                return null;
            }
        } else {
            System.out.println("查找上次排程信息出错");
            return null;
        }
    }

    public void autoRollingScheduling() throws ParseException, JsonProcessingException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar calendar = Calendar.getInstance();
        //滚动排程时清除待删除列表
        System.out.println(deleteOrderList.toString());
        if (deleteOrderList.size() > 0) {
            deleteOrderList.clear();
        }
        if (UserConfigTools.getLatestSchedule("1") != null) {
            RG_ScheduleEntity rg_scheduleEntity = DAOFactory.getScheduleDAOImplInstance().findAllById(UserConfigTools.getLatestSchedule("1"));
            if (rg_scheduleEntity != null) {
                //获取到最后一次排程信息
                WebSocketNotification.broadcast(Tools.creatNotificationMessage("发现最后一次排程信息", "confirm"));
                //获取上次排程使用的订单
                Set<RG_OrderEntity> lastScheduleOrderSet = rg_scheduleEntity.getOrders();
                if (!lastScheduleOrderSet.isEmpty()) {
                    //计算上次计算的开始和结束时间
                    Date lastScheduleRollingStartDate = simpleDateFormat.parse(simpleDateFormat.format(rg_scheduleEntity.getScheduleTime()));
                    calendar.setTime(lastScheduleRollingStartDate);
                    calendar.add(Calendar.DAY_OF_YEAR, rg_scheduleEntity.getRollTime());
                    Date lastScheduleRollingEndDate = calendar.getTime();
                    //获取其中上个滚动周期内的订单
                    List<RG_OrderEntity> exceptionOrderList = new ArrayList<>();
                    for (RG_OrderEntity rg_orderEntity : lastScheduleOrderSet) {
                        //随机选择异常订单
                        if (rg_orderEntity.getT2().after(lastScheduleRollingStartDate) && rg_orderEntity.getT2().before(lastScheduleRollingEndDate) && random()) {
                            exceptionOrderList.add(rg_orderEntity);
                        }
                        //选择待删除订单(结束时间在上次滚动结束之前的订单)
                        if (rg_orderEntity.getT2().before(lastScheduleRollingEndDate)) {
                            deleteOrderList.add(rg_orderEntity);
                        }
                    }
                    //在删除订单中除去异常订单
                    deleteOrderList.removeAll(exceptionOrderList);
                    Tools.deleteAPSOrder(DatabaseInfo.ORACLE, DatabaseInfo.APS, deleteOrderList);
                    //异常列表
                    List<RG_AdjustProcessEntity> rg_adjustProcessEntityList = new ArrayList<>();
                    //异常模拟
                    for (RG_OrderEntity rg_orderEntity : exceptionOrderList) {
                        //查找当前订单对应的工序信息列表
                        List<RG_PlanEntity> rg_planEntityList = DAOFactory.getPlanDAOImplInstance().findAllByOrderId(rg_orderEntity.getId());
                        if (rg_planEntityList.size() > 0) {
                            //从列表中随机获取工序
                            int i = (int) (Math.random() * 50);
                            RG_PlanEntity rg_planEntity = rg_planEntityList.get(i);
                            //根据随机的Plan信息生成工序异常
                            RG_AdjustProcessEntity rg_adjustProcessEntity = new RG_AdjustProcessEntity();
                            rg_adjustProcessEntity.setId(Tools.getUUID());
                            rg_adjustProcessEntity.setReportTime(lastScheduleRollingEndDate);
                            rg_adjustProcessEntity.setIdTask(rg_planEntity.getIdTask());
                            rg_adjustProcessEntity.setIdJob(rg_planEntity.getIdJob());
                            rg_adjustProcessEntity.setIdOrder(rg_planEntity.getOrderByIdOrder().getId());
                            rg_adjustProcessEntity.setOriginalResource(rg_planEntity.getResourceByIdResource().getIdR());
                            rg_adjustProcessEntity.setAppointResource(rg_planEntity.getResourceByIdResource().getIdR());
                            rg_adjustProcessEntity.setOriginalStartTime(Tools.parseStandTextDate(rg_planEntity.getT1Task()));
                            rg_adjustProcessEntity.setOrigin("自动模拟");
                            rg_adjustProcessEntity.setState(ErrorState.ERROR_UNSOLVED);
                            //生成指定拖期时间
                            SimpleDateFormat simpleDateFormatWithClock = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            // 工序时间长度
                            long length = 82740000 - (Tools.parseStandTextDate(rg_planEntity.getT2Task()).getTime() - Tools.parseStandTextDate(rg_planEntity.getT1Task()).getTime());
                            long t1 = Tools.stringConvertToDate(rg_planEntity.getT1Task()).getTime();
                            long sum = t1 + length - 10000;
                            Date dateTime = simpleDateFormatWithClock.parse(simpleDateFormatWithClock.format(sum));
                            rg_adjustProcessEntity.setAppointStartTime(dateTime);
                            rg_adjustProcessEntityList.add(rg_adjustProcessEntity);
                            DAOFactory.getAdjustProcessDAOImplInstance().save(rg_adjustProcessEntity);
                        }
                    }
                    //异常处理
                    System.out.println("待处理异常数量：" + rg_adjustProcessEntityList.size());
                    for (RG_AdjustProcessEntity rg_adjustProcessEntity : rg_adjustProcessEntityList) {
                        System.out.println("当前APS服务器是否在进行计算：" + ApsTools.isRunning);
                        //处理拖期异常
                        ApsTools.instance().executeCommand(ApsTools.getAdjustProcessHandlingURL(rg_adjustProcessEntity));
                        while (ApsTools.isRunning) {
                            try {
                                System.out.println("APS计算中...");
                                Thread.sleep(10000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                //开始滚动排程
                ScheduleAction.beginScheduleHandler(createScheduleInfo());
            } else {
                //未获取到最后一次排程信息
                System.out.println("未获取到最后一次排程信息");
                WebSocketNotification.broadcast(Tools.creatNotificationMessage("未发现最后一次排程信息", "alert"));
            }
        } else {
            //未获取到最后一次排程信息
            System.out.println("未获取到最后一次排程信息");
            WebSocketNotification.broadcast(Tools.creatNotificationMessage("未发现最后一次排程信息", "alert"));
        }
    }

    private boolean random() {
        return (int) (Math.random() * 100) % 2 == 0;
    }
}