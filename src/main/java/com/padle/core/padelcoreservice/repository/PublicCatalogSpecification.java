package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.dto.PublicCatalogFilter;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentVisibility;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;

public final class PublicCatalogSpecification {
    private PublicCatalogSpecification() {}

    public static Specification<Tournament> matching(PublicCatalogFilter filter) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.isTrue(root.get("isActive")));
            predicates.add(builder.or(builder.isNull(root.get("visibilidad")),
                    builder.notEqual(root.get("visibilidad"), TournamentVisibility.SOLO_POR_ENLACE)));
            predicates.add(builder.notEqual(root.get("estado"), TournamentStatus.BORRADOR));
            if (filter.clubId() != null) {
                predicates.add(builder.equal(root.get("clubId"), filter.clubId()));
            }
            if (filter.type() != null) {
                predicates.add(builder.equal(root.get("tipo"), filter.type()));
            }
            if (filter.modality() != null) {
                predicates.add(builder.equal(root.get("modalidad"), filter.modality()));
            }
            if (filter.gender() != null) {
                predicates.add(builder.equal(root.get("generoFormato"), filter.gender()));
            }
            if (filter.level() != null) {
                predicates.add(builder.equal(root.get("categoriaNivel"), filter.level()));
            }
            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("estado"), filter.status()));
            }
            if (filter.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("fechaInicio"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("fechaInicio"), filter.to()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
