ALTER TRIGGER search_operationalpoint__ins_trig ON infra_object_operational_point
RENAME TO search_operational_point__ins_trig;

ALTER TRIGGER search_operationalpoint__upd_trig ON infra_object_operational_point
RENAME TO search_operational_point__upd_trig;

ALTER FUNCTION search_operationalpoint__ins_trig_fun ()
RENAME TO search_operational_point__ins_trig_fun;

ALTER FUNCTION search_operationalpoint__upd_trig_fun ()
RENAME TO search_operational_point__upd_trig_fun;
