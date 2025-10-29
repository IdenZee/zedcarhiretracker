package com.zedcarhire.zedcarhiretracker.repo;


import com.zedcarhire.zedcarhiretracker.model.TrackerData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TrackerDataRepository extends JpaRepository<TrackerData, Long> {

    List<TrackerData> findTop100ByImeiOrderByGpsTimeDesc(String imei);

    @Query("select t from TrackerData t where (:imei is null or t.imei = :imei) " +
            "and (:from is null or t.gpsTime >= :from) and (:to is null or t.gpsTime <= :to) " +
            "order by t.gpsTime desc")
    List<TrackerData> search(String imei, LocalDateTime from, LocalDateTime to);

    @Query("select t from TrackerData t where t.imei in ?1 and t.gpsTime = " +
            "(select max(x.gpsTime) from TrackerData x where x.imei = t.imei)")
    List<TrackerData> lastForImeis(List<String> imeis);


    // Fetch all records for a device (optional use)
    List<TrackerData> findByImeiOrderByGpsTimeDesc(String imei);


    Optional<TrackerData> findTop1ByImeiOrderByGpsTimeDesc(String imei);

    List<TrackerData> findByImeiAndGpsTimeBetweenOrderByGpsTimeAsc(
            String imei,
            LocalDateTime start,
            LocalDateTime end
    );
}
