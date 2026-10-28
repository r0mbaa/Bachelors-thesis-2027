package io.github.r0mbaa.wms.core.task;

import java.util.EnumSet;
import java.util.Set;

public enum TaskStatus {
    /** В очереди, сборщик не назначен. */
    QUEUED,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    FAILED;

    public static final Set<TaskStatus> ACTIVE = EnumSet.of(QUEUED, ASSIGNED, IN_PROGRESS);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
