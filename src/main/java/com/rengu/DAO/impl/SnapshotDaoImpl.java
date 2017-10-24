package com.rengu.DAO.impl;

import com.rengu.entity.RG_SnapshotNodeEntity;
import com.rengu.util.MySessionFactory;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.List;

/**
 * Created by hanch on 2017/6/29.
 */
public class SnapshotDaoImpl extends SuperDAOImpl {
    public List<RG_SnapshotNodeEntity> findAllByLevel(String level) {
        MySessionFactory.getSessionFactory().getCurrentSession().close();
        Session session = MySessionFactory.getSessionFactory().getCurrentSession();
        Transaction transaction = session.getTransaction();
        if (!transaction.isActive()) {
            session.beginTransaction();
        }
        String hql = "from RG_SnapshotNodeEntity entity where entity.level =:level order by entity.nodeCreateTime desc";
        Query query = session.createQuery(hql);
        query.setParameter("level", level);
        List list = query.list();
        transaction.commit();
        return list;
    }

    public RG_SnapshotNodeEntity findAllById(String id) {
        try {
            MySessionFactory.getSessionFactory().getCurrentSession().close();
            Session session = MySessionFactory.getSessionFactory().getCurrentSession();
            Transaction transaction = session.getTransaction();
            if (!transaction.isActive()) {
                session.beginTransaction();
            }
            String hql = "from RG_SnapshotNodeEntity rg_snapshotNodeEntity where rg_snapshotNodeEntity.id =:id";
            Query query = session.createQuery(hql);
            query.setParameter("id", id);
            if (!query.list().isEmpty()) {
                RG_SnapshotNodeEntity rg_snapshotNodeEntity = (RG_SnapshotNodeEntity) query.list().get(0);
                return rg_snapshotNodeEntity;
            } else {
                return null;
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }
    }

    /*public RG_SnapshotNodeEntity findBottomNodeById(String id) {
        try {
            MySessionFactory.getSessionFactory().getCurrentSession().close();
            Session session = MySessionFactory.getSessionFactory().getCurrentSession();
            Transaction transaction = session.getTransaction();
            if (!transaction.isActive()) {
                session.beginTransaction();
            }
            String hql = "from RG_SnapshotNodeEntity rg_snapshotNodeEntity where rg_snapshotNodeEntity.id =:id";
            Query query = session.createQuery(hql);
            query.setParameter("id", id);
            if (!query.list().isEmpty()) {
                RG_SnapshotNodeEntity rg_snapshotNodeEntity = (RG_SnapshotNodeEntity) query.list().get(0);
                return rg_snapshotNodeEntity;
            } else {
                return null;
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }
    }*/
}
