package com.oncf.hypervisor.repository;

import com.oncf.hypervisor.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {
    Optional<Zone> findByName(String name);

    @Query(value = """
            SELECT * FROM zones z
            WHERE z.geom_3d IS NOT NULL
              AND ST_3DIntersects(
                z.geom_3d,
                ST_SetSRID(ST_MakePoint(:lon, :lat, :elevation), 4326)
              )
            """, nativeQuery = true)
    List<Zone> findIntersecting3d(@Param("lat") double lat,
                                  @Param("lon") double lon,
                                  @Param("elevation") double elevation);

    @Query(value = """
            SELECT * FROM zones z
            WHERE z.geom_3d IS NOT NULL
              AND ST_3DDWithin(
                z.geom_3d,
                ST_SetSRID(ST_MakePoint(:lon, :lat, :elevation), 4326),
                :tolerance
              )
            """, nativeQuery = true)
    List<Zone> findMatching3d(@Param("lat") double lat,
                              @Param("lon") double lon,
                              @Param("elevation") double elevation,
                              @Param("tolerance") double tolerance);
}
