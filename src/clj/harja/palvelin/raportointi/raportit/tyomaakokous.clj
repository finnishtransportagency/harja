(ns harja.palvelin.raportointi.raportit.tyomaakokous
  "Työmaakokouksen koosteraportti, joka kutsuu muita raportteja ja yhdistää niiden tiedot"
  (:require
    [taoensso.timbre :as log]
    [harja.kyselyt.urakat :as urakat-q]
    [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
    [harja.palvelin.raportointi.raportit.erilliskustannukset :as erilliskustannukset]
    [harja.palvelin.raportointi.raportit.laatupoikkeama :as laatupoikkeamat]
    [harja.palvelin.raportointi.raportit.laskutusyhteenveto :as laskutusyhteenveto]
    [harja.palvelin.raportointi.raportit.laskutusyhteenveto-tuotekohtainen :as laskutusyhteenveto-tuotekohtainen]
    [harja.palvelin.raportointi.raportit.muutos-ja-lisatyot :as muutos-ja-lisatyot]
    [harja.palvelin.raportointi.raportit.ilmoitus :as ilmoitus]
    [harja.palvelin.raportointi.raportit.sanktio :as sanktiot]
    [harja.palvelin.raportointi.raportit.kelitarkastus :as kelitarkastukset]
    [harja.palvelin.raportointi.raportit.materiaali :as materiaalit]
    [harja.palvelin.raportointi.raportit.soratietarkastus :as soratietarkastukset]
    [harja.palvelin.raportointi.raportit.tiestotarkastus :as tiestotarkastukset]
    [harja.palvelin.raportointi.raportit.turvallisuuspoikkeamat :as turvallisuus]
    [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-kuukausittain :as yks-hint-kuukausittain]
    [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-paivittain :as yks-hint-paivittain]
    [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-tehtavittain :as yks-hint-tehtavittain]
    [harja.palvelin.raportointi.raportit.ymparisto :as ymparisto]
    [harja.palvelin.raportointi.raportit.laaduntarkastus :as laaduntarkastus]
    [harja.palvelin.raportointi.raportit.toimenpideajat :as toimenpideajat]))

(defn urakkatyypin-laskutusyhteenveto
  "Ohjaa laskutusyhteenvedon oikeaan paikkaan urakkatyypin mukaisesti"
  [db user tiedot]
  (if (= :teiden-hoito (:urakkatyyppi tiedot))
    (laskutusyhteenveto-tuotekohtainen/suorita db user tiedot)
    (laskutusyhteenveto/suorita db user tiedot)))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as tiedot}]
  [:raportti {:nimi (yleinen/raportin-otsikko
                      (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                      "Työmaakokousraportti" alkupvm loppupvm)}
   (mapcat (fn [[aja-parametri otsikko raportti-fn]]
             (when (get tiedot aja-parametri)
               (concat [[:otsikko otsikko]]
                       (yleinen/osat (raportti-fn db user tiedot)))))
           ;; säilytä aakkosjärjestys ellei toisin vaadita
           [[:erilliskustannukset "Erilliskustannukset" erilliskustannukset/suorita]
            [:ilmoitusraportti "Ilmoitukset" ilmoitus/suorita]
            [:kelitarkastusraportti "Kelitarkastusraportti" kelitarkastukset/suorita]
            [:laaduntarkastusraportti "Laaduntarkastusraportti" laaduntarkastus/suorita]
            [:laatupoikkeamaraportti "Laatupoikkeamat" laatupoikkeamat/suorita]
            [:laskutusyhteenveto "Laskutusyhteenveto" urakkatyypin-laskutusyhteenveto]
            [:materiaaliraportti "Materiaaliraportti" materiaalit/suorita]
            [:muutos-ja-lisatyot "Muutos- ja lisätyöt" muutos-ja-lisatyot/suorita]
            [:sanktioraportti "Sanktioiden yhteenveto" sanktiot/suorita]
            [:soratietarkastusraportti "Soratietarkastukset" soratietarkastukset/suorita]
            [:tiestotarkastusraportti "Tiestötarkastukset" tiestotarkastukset/suorita]
            [:toimenpideajat "Toimenpiteiden ajoittuminen" toimenpideajat/suorita]
            [:turvallisuus "Turvallisuusraportti" turvallisuus/suorita]
            [:yks-hint-kuukausiraportti "Yksikköhintaiset työt kuukausittain" yks-hint-kuukausittain/suorita]
            [:yksikkohintaiset-tyot "Yksikköhintaiset työt päivittäin" yks-hint-paivittain/suorita]
            [:yks-hint-tehtavien-summat "Yksikköhintaiset työt tehtävittäin" yks-hint-tehtavittain/suorita]
            [:ymparisto "Ympäristöraportti" ymparisto/suorita]])])
