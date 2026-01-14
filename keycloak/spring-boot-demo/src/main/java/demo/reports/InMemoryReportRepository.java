package demo.reports;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryReportRepository {

  private final Map<String, Report> storage = new ConcurrentHashMap<>();

  public List<Report> listByTenant(String tenantId) {
    List<Report> out = new ArrayList<>();
    for (Report r : storage.values()) {
      if (r.tenantId().equals(tenantId)) {
        out.add(r);
      }
    }
    return out;
  }

  public Optional<Report> findById(String id) {
    return Optional.ofNullable(storage.get(id));
  }

  public Report create(String tenantId, String ownerSub, String title) {
    String id = UUID.randomUUID().toString();
    Report r = new Report(id, tenantId, ownerSub, title);
    storage.put(id, r);
    return r;
  }
}
