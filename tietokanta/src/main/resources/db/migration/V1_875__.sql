-- toteutuneet_kustannukset taulun kolumni rivin_tunnistin pakotetaan uniikiksi, jotta vältetään duplikaatit
CREATE UNIQUE INDEX toteutuneet_kustannukset_rivin_tunnistin_uindex
    on toteutuneet_kustannukset (rivin_tunnistin);