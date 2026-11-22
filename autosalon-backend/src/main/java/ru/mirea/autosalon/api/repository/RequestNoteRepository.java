package ru.mirea.autosalon.api.repository;
import org.springframework.data.jpa.repository.JpaRepository; import ru.mirea.autosalon.api.domain.RequestNote; import java.util.*;
public interface RequestNoteRepository extends JpaRepository<RequestNote,UUID> {List<RequestNote> findAllByRequestPublicIdOrderByPinnedDescCreatedAtDesc(UUID requestId); Optional<RequestNote> findByIdAndRequestPublicId(UUID id,UUID requestId);}
