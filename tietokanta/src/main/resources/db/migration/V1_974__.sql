ALTER TABLE urakka_paatos
    ADD erilliskustannus_id integer default null,
    ADD sanktio_id integer default null;

comment on column urakka_paatos.erilliskustannus_id is 'Lupausbonus sidotaan erilliskustannustauluun tällä id:llä.';
comment on column urakka_paatos.sanktio_id is 'Lupaussanktio sanktiotauluun tällä id:llä';

