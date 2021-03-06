package com.rengu.DAO.impl;

import com.rengu.DAO.SiteDAO;
import com.rengu.entity.RG_DistanceEntity;
import com.rengu.entity.RG_GroupresourceEntity;
import com.rengu.entity.RG_PlanEntity;
import com.rengu.entity.RG_SiteEntity;
import com.rengu.util.DAOFactory;
import com.rengu.util.MySessionFactory;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.List;

/**
 * Created by hanchangming on 2017/5/31.
 */
public class SiteDAOImpl extends SuperDAOImpl implements SiteDAO<RG_SiteEntity> {
    @Override
    public List<RG_SiteEntity> findAll() {
        MySessionFactory.getSessionFactory().getCurrentSession().close();
        Session session = MySessionFactory.getSessionFactory().getCurrentSession();
        Transaction transaction = session.getTransaction();
        if (!transaction.isActive()) {
            session.beginTransaction();
        }
        String hql = "from RG_SiteEntity rg_siteEntity";
        Query query = session.createQuery(hql);
        List list = query.list();
        return list;
    }

    @Override
    public RG_SiteEntity findAllById(String id) {
        try {
            MySessionFactory.getSessionFactory().getCurrentSession().close();
            Session session = MySessionFactory.getSessionFactory().getCurrentSession();
            Transaction transaction = session.getTransaction();
            if (!transaction.isActive()) {
                session.beginTransaction();
            }
            String hql = "from RG_SiteEntity rg_siteEntity where rg_siteEntity.id =:id";
            Query query = session.createQuery(hql);
            query.setParameter("id", id);
            if (!query.list().isEmpty()) {
                RG_SiteEntity rg_siteEntity = (RG_SiteEntity) query.list().get(0);
                return rg_siteEntity;
            } else {
                return null;
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public boolean delete(Object object) {
        RG_SiteEntity rg_siteEntity;

        if (object instanceof RG_SiteEntity) {
            rg_siteEntity = (RG_SiteEntity) object;
            String siteId = rg_siteEntity.getId();
            rg_siteEntity = findAllById(siteId);

            //plan
            List<RG_PlanEntity> rg_PlanEntity = DAOFactory.getPlanDAOImplInstance().findAllBySiteId(siteId);

            //distance
            List<RG_DistanceEntity> rg_distanceEntity = DAOFactory.getDistanceDAOImplInstance().findAllBySiteId(siteId);

            if (rg_PlanEntity .size() > 0 || rg_distanceEntity .size() > 0) {
                if (((rg_PlanEntity .size() > 0 && DAOFactory.getPlanDAOImplInstance().deleteByBySiteId(siteId)) ||
                        (rg_distanceEntity .size() > 0 && DAOFactory.getDistanceDAOImplInstance().deleteBySiteId(siteId)))
                        && super.delete(rg_siteEntity)) {
                    return true;
                }  else {
                    return false;
                }

            } else {
                //直接删除
                return super.delete(rg_siteEntity);
            }

        } else {
            //参数错误
            return false;
        }
    }
}
