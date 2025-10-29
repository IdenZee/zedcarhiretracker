package com.zedcarhire.zedcarhiretracker.service;


import com.zedcarhire.zedcarhiretracker.model.RawMessage;
import com.zedcarhire.zedcarhiretracker.model.TrackerData;
import com.zedcarhire.zedcarhiretracker.repo.TrackerDataRepository;
import com.zedcarhire.zedcarhiretracker.repo.RawMessageRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TrackerService {
    private final TrackerDataRepository repo;
    private final RawMessageRepository rawRepo;

    public TrackerService(TrackerDataRepository repo, RawMessageRepository rawRepo) {
        this.repo = repo;
        this.rawRepo = rawRepo;
    }

    public TrackerData save(TrackerData td) {
        return repo.save(td);
    }

    public List<TrackerData> search(String imei, LocalDateTime from, LocalDateTime to) {
        return repo.search(imei, from, to);
    }

    public List<TrackerData> last(List<String> imeis) {
        return repo.lastForImeis(imeis);
    }

    public void saveRaw(String hex) {
        RawMessage m = new RawMessage();
        m.setRawHex(hex);
        rawRepo.save(m);
    }
}

