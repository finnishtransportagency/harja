ALTER TABLE urakka_paatos
    ADD taulu_id integer default null;

comment on column urakka_paatos.taulu_id is 'Lupausbonus sidotaan erilliskustannustauluun ja Lupaussanktio sanktiotauluun tällä id:llä';
