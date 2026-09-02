-- Lisätään uudet tyypit ajastettu_tehtava ENUM:iin raportointitehtäviä varten
ALTER TYPE ajastettu_tehtava ADD VALUE 'paivita_raportti_toteutuneet_materiaalit';
ALTER TYPE ajastettu_tehtava ADD VALUE 'paivita_raportti_pohjavesialueiden_suolatoteumat';
ALTER TYPE ajastettu_tehtava ADD VALUE 'paivita_raportti_toteuma_maarat';
