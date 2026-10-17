package com.feetfit.server.repository.drug;

import com.feetfit.server.domain.drug.DrugProduct;
import com.feetfit.server.domain.enums.DrugCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DrugProductRepository extends JpaRepository<DrugProduct, String> {

    @Query("""
            select d from DrugProduct d join d.categories category
            where category = :category
              and (:keyword = '' or lower(d.name) like :pattern escape '!'
                   or lower(d.company) like :pattern escape '!'
                   or lower(d.description) like :pattern escape '!'
                   or lower(d.categoryLabel) like :pattern escape '!')
            order by d.online desc, d.displayOrder, d.id
            """)
    Page<DrugProduct> search(@Param("category") DrugCategory category,
                             @Param("keyword") String keyword,
                             @Param("pattern") String pattern, Pageable pageable);

    @Query("select count(d) > 0 from DrugProduct d join d.categories category where category = :category")
    boolean existsInCategory(@Param("category") DrugCategory category);

    @Query("select distinct d from DrugProduct d left join fetch d.categories where d.id in :ids")
    List<DrugProduct> findForImport(@Param("ids") Collection<String> ids);

    @Query(value = """
            select d from DrugProduct d
            where d.recommendationEligible = true
              and (:category is null or :category member of d.categories)
              and (:keyword = '' or lower(d.name) like :pattern escape '!'
                   or lower(d.company) like :pattern escape '!'
                   or lower(d.description) like :pattern escape '!')
            order by case when :first member of d.categories then 0
                          when :second member of d.categories then 1 else 2 end,
                     d.online desc, d.displayOrder, d.id
            """, countQuery = """
            select count(d) from DrugProduct d
            where d.recommendationEligible = true
              and (:category is null or :category member of d.categories)
              and (:keyword = '' or lower(d.name) like :pattern escape '!'
                   or lower(d.company) like :pattern escape '!'
                   or lower(d.description) like :pattern escape '!')
            """)
    Page<DrugProduct> recommend(@Param("first") DrugCategory first, @Param("second") DrugCategory second,
                                @Param("category") DrugCategory category, @Param("keyword") String keyword,
                                @Param("pattern") String pattern, Pageable pageable);
}
