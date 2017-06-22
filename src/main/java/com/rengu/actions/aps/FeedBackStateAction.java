package com.rengu.actions.aps;

import com.opensymphony.xwork2.ActionContext;
import com.rengu.DAO.aps.ApsDao;
import com.rengu.actions.SuperAction;
import com.rengu.entity.RG_ScheduleEntity;
import com.rengu.entity.RG_SnapshotNodeEntity;
import com.rengu.util.*;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * APS回调框架更新计算状态
 * Created by wey580231 on 2017/6/13.
 */
public class FeedBackStateAction extends SuperAction {

    private final String APS_RESULT_SUCCESS = "1";         //APS计算成功结果标识
    private final String APS_RESULT_FAIL = "0";            //APS计算失败结果标识

    private ApsDao apsDao = new ApsDao();

    //根据返回的id号更新对应schedule的状态
    public void update() {
        ActionContext context = ActionContext.getContext();
        Map<String, Object> parameterMap = context.getParameters();

        boolean result = false;
        StringBuilder jsonString = new StringBuilder();

        if (parameterMap.size() == 3) {
            String[] id = (String[]) parameterMap.get("id");
            String[] state = (String[]) parameterMap.get("STATE");
            String[] message = (String[]) parameterMap.get("MESSAGE");
            if (id.length > 0 && state.length > 0 && message.length > 0 && GlobalVariable.RootSnapshotId.length() > 0) {

                Session session = MySessionFactory.getSessionFactory().getCurrentSession();
                session.beginTransaction();

                Query query = session.createQuery("from RG_SnapshotNodeEntity entity where entity.id=:id");
                query.setParameter("id", GlobalVariable.RootSnapshotId);
                List list = query.list();
                if (list.size() > 0 && list.get(0) instanceof RG_SnapshotNodeEntity) {
                    RG_SnapshotNodeEntity rootSnapshot = (RG_SnapshotNodeEntity) list.get(0);

                    //创建快照节点
                    query = session.createQuery("from RG_SnapshotNodeEntity entity where entity.id=:id");
                    query.setParameter("id", GlobalVariable.MiddleSnapshotId);
                    list = query.list();
                    RG_SnapshotNodeEntity middleSnapshot = null;
                    if (list.size() > 0) {
                        middleSnapshot = (RG_SnapshotNodeEntity) list.get(0);
                    }

                    //TODO 查询schedule时会级联查询出其对应的set集合
                    RG_ScheduleEntity schedule = rootSnapshot.getSchedule();

                    String nodeName = null;

                    //不是应急排程
                    if (!GlobalVariable.IsErrorSchedule) {
                        if (state[0].equals(APS_RESULT_SUCCESS)) {
                            if (GlobalVariable.ApsReplyCount == 0) {
                                schedule.setState(RG_ScheduleEntity.APS_SUCCESS);
                                nodeName = "排程结果";
                                WebSocketNotification.broadcast("APS计算完成!");
                            } else {
                                schedule.setState(RG_ScheduleEntity.APS_ADJUST);
                                if (middleSnapshot != null) {
                                    nodeName = "优化调整" + middleSnapshot.getChilds().size();
                                }
                                WebSocketNotification.broadcast("APS优化计算完成!");
                            }
                        }
                        //计算失败
                        else if (state[0].equals(APS_RESULT_FAIL)) {
                            schedule.setState(RG_ScheduleEntity.APS_FAIL);
                            WebSocketNotification.broadcast("APS计算失败!");
                        }
                    }
                    //故障应急排程
                    else {
                        if (state[0].equals(APS_RESULT_SUCCESS)) {
                            if (GlobalVariable.ApsReplyCount == 0) {
                                schedule.setState(RG_ScheduleEntity.ERROR_SUCCESS);
                                nodeName = "应急结果";
                                WebSocketNotification.broadcast("APS应急计算完成!");
                            } else {
                                schedule.setState(RG_ScheduleEntity.ERROR_ADJUST);
                                if (middleSnapshot != null) {
                                    nodeName = "应急优化调整" + middleSnapshot.getChilds().size();
                                }
                                WebSocketNotification.broadcast("APS应急优化计算完成!");
                            }
                        }
                        //计算失败
                        else if (state[0].equals(APS_RESULT_FAIL)) {
                            schedule.setState(RG_ScheduleEntity.ERROR_FAIL);
                            WebSocketNotification.broadcast("APS应急失败!");
                        }
                    }
                    GlobalVariable.ApsReplyCount++;

                    RG_SnapshotNodeEntity bottomSnapshot = null;

                    if (middleSnapshot != null) {
                        bottomSnapshot = new RG_SnapshotNodeEntity();
                        bottomSnapshot.setId(Tools.getUUID());
                        bottomSnapshot.setName(nodeName);
                        bottomSnapshot.setLevel(SnapshotLevel.BOTTOM);

                        bottomSnapshot.setParent(middleSnapshot);
                        bottomSnapshot.setRootParent(rootSnapshot);

                        GlobalVariable.BottomSnapshotId = bottomSnapshot.getId();

                        middleSnapshot.getChilds().add(bottomSnapshot);

                        session.save(middleSnapshot);
                    }

                    //APS_PLAN-->RG_PlanEntity共用一个session,确保在一个实务之中
                    if (state[0].equals(APS_RESULT_SUCCESS)) {
                        try {

                            ApsTools.instance().getScheduleResult(bottomSnapshot);

                            session.getTransaction().commit();

                        } catch (Exception e) {
                            e.printStackTrace();
                            session.getTransaction().rollback();
                            WebSocketNotification.broadcast("APS计算结果转换出错!");
                        }
                    }
                }
            }
        } else {
            WebSocketNotification.broadcast("APS计算出错!");
        }
        Tools.jsonPrint(Tools.apsCode("ok", "1", "recive execute operation"), this.httpServletResponse);
    }

    //每次排程结果成功后，在对应的middle节点下，创建bottom节点
    private void createSnapshotNode(Session session) {
        Query mquery = session.createQuery("from RG_SnapshotNodeEntity mshot where mshot.id=:id");
        List mlist = mquery.list();
        if (mlist.size() > 0 && mlist.get(0) instanceof RG_SnapshotNodeEntity) {
            RG_SnapshotNodeEntity parent = (RG_SnapshotNodeEntity) mlist.get(0);

            RG_SnapshotNodeEntity child = new RG_SnapshotNodeEntity();
            child.setId(Tools.getUUID());

            child.setParent(parent);
            parent.getChilds().add(child);

            GlobalVariable.BottomSnapshotId = child.getId();

            session.save(parent);
        }
    }

    //查询APS状态,在对APS数据库操作之前，都要先执行状态查询操作
    public void queryApsState() {
        StringBuilder result = new StringBuilder();

        if (apsDao.queryCurrState(result)) {
            Tools.jsonPrint(result.toString(), this.httpServletResponse);
        } else {
            Tools.jsonPrint(Tools.resultCode("error", "Can't execute operation"), this.httpServletResponse);
        }
    }
}
