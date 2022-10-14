alter table erilliskustannus add column kasittelytapa laatupoikkeaman_kasittelytapa, add column laskutuskuukausi date;
create table erilliskustannus_liite (
       bonus integer references erilliskustannus(id),
       liite integer references liite(id));
       
comment on table erilliskustannus_liite is 'Bonuksien ja sanktioiden näkymässä bonuksiin liitetyt liitteet';
