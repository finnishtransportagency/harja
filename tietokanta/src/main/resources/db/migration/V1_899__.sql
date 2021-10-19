alter table if exists lasku rename to kulu;

alter table if exists lasku_kohdistus rename to kulu_kohdistus;

alter table if exists lasku_liite rename to kulu_liite;

alter table if exists kulu_kohdistus rename column lasku to kulu;
alter table if exists kulu_liite rename column lasku to kulu;
