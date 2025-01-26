package com.example.mdmspaws.Repository;

import com.example.mdmspaws.Entity.DownloadRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DownloadRequestRepository extends JpaRepository<DownloadRequestEntity, Long> {

}
