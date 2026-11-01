-- M10. Идемпотентность событий терминала (FR-M10-08, NFR-R-03): повторная отправка с тем же
-- ключом натыкается на первичный ключ и не порождает второго движения.

create table terminal_event (
    event_key   uuid        primary key,
    task_id     bigint      not null references task (id),
    step_id     bigint      references task_step (id),
    type        varchar(16) not null,
    username    varchar(32) not null,
    received_at timestamptz not null,
    constraint terminal_event_type_known check (type in ('CONFIRM', 'EXCEPTION'))
);
