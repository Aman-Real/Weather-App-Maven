package com.skycast.weather.repository;

import com.skycast.weather.domain.Location;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;

import java.util.ArrayList;
import java.util.List;

class LocationRepositoryCustomImpl implements LocationRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    /** Criteria API: builds the WHERE clause only from the filters that were supplied. */
    @Override
    public List<Location> search(String name, String country, Double minLatitude, Double maxLatitude) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Location> query = cb.createQuery(Location.class);
        Root<Location> root = query.from(Location.class);

        List<Predicate> filters = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            filters.add(cb.like(cb.lower(root.<String>get("name")), "%" + name.toLowerCase() + "%"));
        }
        if (country != null && !country.isBlank()) {
            filters.add(cb.equal(cb.lower(root.<String>get("country")), country.toLowerCase()));
        }
        if (minLatitude != null) {
            filters.add(cb.ge(root.<Double>get("latitude"), minLatitude));
        }
        if (maxLatitude != null) {
            filters.add(cb.le(root.<Double>get("latitude"), maxLatitude));
        }
        query.select(root).where(filters.toArray(Predicate[]::new)).orderBy(cb.asc(root.get("name")));
        return em.createQuery(query).setMaxResults(50).getResultList();
    }

    /** Hibernate Session (unwrapped from the JPA EntityManager) running an HQL query. */
    @Override
    public List<Location> recentlyUpdated(int limit) {
        Session session = em.unwrap(Session.class);
        return session.createQuery("from Location l order by l.updatedAt desc", Location.class)
                .setMaxResults(limit)
                .getResultList();
    }
}
