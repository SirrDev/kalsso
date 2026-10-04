package com.footbooking.search;

import com.footbooking.field.Field;
import com.footbooking.field.FieldFormat;
import com.footbooking.field.FieldSurface;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/** Lecture seule : etend Repository (et non JpaRepository) pour n'exposer aucune ecriture. */
public interface SearchRepository extends Repository<Field, UUID> {

    @Query(value = """
            select new com.footbooking.search.FieldSummary(
                f.id, f.name, f.format, f.surface, f.slotMinutes, v.id, v.name, v.city, v.address)
            from Field f join Venue v on v.id = f.venueId
            where f.active = true
              and v.status = com.footbooking.venue.VenueStatus.ACTIVE
              and (:city is null or lower(v.city) = :city)
              and (:format is null or f.format = :format)
              and (:surface is null or f.surface = :surface)
            order by v.name, f.name
            """,
            countQuery = """
            select count(f)
            from Field f join Venue v on v.id = f.venueId
            where f.active = true
              and v.status = com.footbooking.venue.VenueStatus.ACTIVE
              and (:city is null or lower(v.city) = :city)
              and (:format is null or f.format = :format)
              and (:surface is null or f.surface = :surface)
            """)
    Page<FieldSummary> search(@Param("city") String city,
                              @Param("format") FieldFormat format,
                              @Param("surface") FieldSurface surface,
                              Pageable pageable);
}