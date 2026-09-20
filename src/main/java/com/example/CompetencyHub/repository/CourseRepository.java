package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    // ---------- Derived queries: Spring Data writes the SQL from the method name ----------

    /**
     * Spring Data parses the method name into a query at startup: findBy + property +
     * keyword. A typo in a property name fails when the context loads, not at runtime —
     * one of the quieter advantages over hand-written strings.
     */
    Optional<Course> findByCode(String code);

    /** Exists checks compile to SELECT 1 ... LIMIT 1, cheaper than loading the entity. */
    boolean existsByCode(String code);

    /** Containing → LIKE %?%, IgnoreCase → LOWER() on both sides. */
    List<Course> findByTitleContainingIgnoreCase(String fragment);

    /**
     * Returning Page triggers a second COUNT query so the caller knows the total.
     * Use Slice instead when you only need "is there a next page" and want to skip it.
     */
    Page<Course> findByCapacityGreaterThanEqual(int minCapacity, Pageable pageable);


    // ---------- JPQL: for anything the method-name grammar can't express ----------

    /**
     * JPQL queries entities and fields (Course, c.capacity), not tables and columns.
     * Hibernate translates to SQL for whichever database is configured.
     *
     * <p>Named parameters (:minCapacity) over positional (?1) — they survive being
     * reordered and read better.
     */
    @Query("select c from Course c where c.capacity >= :minCapacity order by c.code")
    List<Course> findWithCapacityAtLeast(@Param("minCapacity") int minCapacity);

    // ---------- The fix for N+1 ----------

    /**
     * Loads every course together with its competencies in ONE query.
     *
     * <p>{@code join fetch} is not the same as {@code join}: a plain join filters rows,
     * a fetch join also populates the association so it is already in memory when you
     * touch it. Without this, reading competencies on 10 courses fires 10 extra
     * queries — see CatalogDiagnosticsService.
     *
     * <p>{@code distinct} is needed because the join produces one row per competency
     * (4 rows per course), and without it Hibernate would return the same Course
     * instance four times.
     *
     * <p><b>Cannot be paginated.</b> Add a Pageable here and Hibernate logs HHH000104
     * and applies the limit in memory after loading everything — technically correct,
     * catastrophic on a large table. Paginate ids first, then fetch, when you need both.
     */
    @Query("select distinct c from Course c left join fetch c.competencies")
    List<Course> findAllWithCompetencies();

    // ---------- Native SQL: when you need database features JPQL has no words for ----------

    /**
     * Enrollment counts per course. Aggregation with a LEFT JOIN and a filtered ON
     * clause is clearer in SQL than in JPQL, so this one drops to native.
     *
     * <p>Returns a projection interface rather than an entity — the result is a report
     * row, not a Course, and pretending otherwise would mean loading entities nobody
     * needs. Column aliases must match the getter names.
     *
     * <p>Cost of going native: this SQL is now PostgreSQL-specific.
     */
    @Query(value = """
            SELECT c.id          AS id,
                   c.code        AS code,
                   c.title       AS title,
                   COUNT(e.id)   AS enrolled
            FROM course c
            LEFT JOIN enrollment e ON e.course_id = c.id AND e.status = 'ACTIVE'
            GROUP BY c.id, c.code, c.title
            ORDER BY enrolled DESC, c.code
            """, nativeQuery = true)
    List<CourseEnrollmentCount> findEnrollmentCounts();

    // ---------- Bulk update ----------

    /**
     * Raises capacity on undersubscribed courses in a single UPDATE.
     *
     * <p>{@code @Modifying} is required for anything that isn't a SELECT — without it
     * Spring Data tries to run it as a query and fails.
     *
     * <p><b>Bulk operations bypass the persistence context.</b> Hibernate sends the SQL
     * straight to the database; entities already loaded in the current session keep
     * their old values and would overwrite this update on flush. {@code clearAutomatically}
     * empties the session afterwards so the next read comes from the database.
     *
     * @return the number of rows updated
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Course c set c.capacity = c.capacity + :extraSeats where c.capacity < :threshold")
    int addSeatsToSmallCourses(@Param("extraSeats") int extraSeats,
                               @Param("threshold") int threshold);
}
