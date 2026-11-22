package ru.mirea.autosalon.api.filter;
import org.springframework.data.jpa.domain.Specification; import ru.mirea.autosalon.api.domain.*; import java.util.*; import jakarta.persistence.criteria.Predicate;
public final class RequestFilters { private RequestFilters(){}
 public record Filter(String text,UUID carId,RequestStatus status,Boolean callback,RequestPriority priority,String tag,Boolean archived,Integer durationMin,Integer durationMax){}
 public static Specification<TestDriveRequest> from(Filter f){return (r,q,cb)->{
  List<Predicate> p=new ArrayList<>();q.distinct(true);
  if(f.archived()!=null)p.add(cb.equal(r.get("archived"),f.archived()));
  if(f.carId()!=null)p.add(cb.equal(r.get("car").get("publicId"),f.carId()));
  if(f.status()!=null)p.add(cb.equal(r.get("status"),f.status())); if(f.callback()!=null)p.add(cb.equal(r.get("callbackRequested"),f.callback()));
  if(f.priority()!=null)p.add(cb.equal(r.get("priority"),f.priority()));
  if(f.durationMin()!=null)p.add(cb.ge(r.get("durationMinutes"),f.durationMin()));if(f.durationMax()!=null)p.add(cb.le(r.get("durationMinutes"),f.durationMax()));
  if(f.tag()!=null&&!f.tag().isBlank())p.add(cb.equal(cb.lower(r.joinSet("tags")),f.tag().strip().toLowerCase(Locale.ROOT)));
  if(f.text()!=null&&!f.text().isBlank()){String s="%"+f.text().strip().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
   p.add(cb.or(cb.like(cb.lower(r.get("externalId")),s,'\\'),cb.like(cb.lower(r.get("client").get("fullName")),s,'\\'),cb.like(cb.lower(r.get("client").get("phone")),s,'\\'),cb.like(cb.lower(r.get("car").get("brand")),s,'\\'),cb.like(cb.lower(r.get("car").get("model")),s,'\\'),cb.like(cb.lower(r.get("car").get("vin")),s,'\\')));}
  return cb.and(p.toArray(Predicate[]::new));};}
}
