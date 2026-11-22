package ru.mirea.autosalon.api.service;
import org.springframework.stereotype.Component; import ru.mirea.autosalon.api.domain.*;
@Component public class RequestRules {
 public RequestPriority priority(RequestStatus status,boolean callback){ if(status==RequestStatus.COMPLETED||status==RequestStatus.CANCELLED||status==RequestStatus.NO_SHOW)return RequestPriority.NONE; if(callback&&status==RequestStatus.NEW)return RequestPriority.HIGH; if(callback||status==RequestStatus.NEW)return RequestPriority.MEDIUM; return RequestPriority.LOW; }
}
