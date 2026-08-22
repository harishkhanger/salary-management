-- V5: a bulk raise can target hand-picked employees instead of a country /
-- department cohort. The ids are persisted on the run row (like excluded_ids)
-- so a crashed run resumes against exactly the same cohort.

ALTER TABLE bulk_raise_runs ADD COLUMN employee_ids JSON NULL;
