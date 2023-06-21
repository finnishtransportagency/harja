(ns harja.tiedot.hallinta.raporttien-suoritustieto
  "Raporttien suoritustietojen hakemiseen liittyvät tiedot."
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.tiedot.hallinta.yhteiset :as yhteiset])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))


(def valittu-raportti (atom nil))
(def valittu-rooli (atom nil))
(def valittu-formaatti (atom nil))
(def nakymassa? (atom false))

(defn hae-raporttitiedot [{:keys [alkupvm loppupvm raportti rooli formaatti]}]
  (k/post! :hae-raporttien-suoritustiedot
           {:alkupvm alkupvm
            :loppupvm loppupvm
            :raportti raportti
            :rooli rooli
            :formaatti formaatti}))

(def raporttitiedot (reaction<!
                      [alkupvm (first @yhteiset/valittu-aikavali)
                       loppupvm (second @yhteiset/valittu-aikavali)
                       raportti @valittu-raportti
                       rooli @valittu-rooli
                       formaatti @valittu-formaatti
                       nakymassa? @nakymassa?]
                      (when nakymassa?
                        (go (<! (hae-raporttitiedot {:alkupvm alkupvm
                                                     :loppupvm loppupvm
                                                     :raportti raportti
                                                     :rooli rooli
                                                     :formaatti formaatti}))))))


;; järjestetty tuotantodatan yleisyyden mukaan
(def +mahdolliset-raportit+
  [[nil "Kaikki"]
   ["laskutusyhteenveto-tuotekohtainen" "Laskutusyhteenveto, tuotekohtainen"]
   ["laskutusyhteenveto-tyomaa" "Laskutusyhteenveto, työmaakokous"]
   ["laskutusyhteenveto-mhu" "Laskutusyhteenveto MHU"]
   ["laskutusyhteenveto" "Laskutusyhteenveto alueurakat"]
   ["suolatoteumat-rajoitusalueilla" "Suolatoteumat rajoitusalueilla"]
   ["ymparistoraportti" "Ympäristöraportti"]
   ["ilmoitusraportti" "Ilmoitusraportti"]
   ["ilmoitukset-raportti" "Ilmoitukset-raportti"]
   ["tyomaakokous" "Työmaakokousraportti"]
   ["muutos-ja-lisatyot" "Muutos- ja lisätyot"]
   ["indeksitarkistus" "Indeksitarkistus"]
   ["sanktioraportti" "Sanktioraportti"]
   ["materiaaliraportti" "Materiaaliraportti"]
   ["yks-hint-tehtavien-summat" "Yksikköhintaisten tehtävien summat"]
   ["pohjavesialueiden-suolatoteumat" "Suolatoteumat (kaikki pohjavesialueet)"]
   ["laatupoikkeamaraportti" "Laatupoikkeamaraportti"]
   ["yks-hint-kuukausiraportti" "Yksikköhintaisten töiden kuukausiraportti"]
   ["tehtavamaarat" "Tehtävämäärät"]
   ["suolasakko" "Suolasakkoraportti"]
   ["siltatarkastus" "Siltatarkastusraportti"]
   ["kulut-tehtavaryhmittain" "Kulut tehtäväryhmittäin"]
   ["erilliskustannukset" "Erilliskustannukset"]
   ["toimenpidekilometrit" "Toimenpidekilometrit"]
   ["turvallisuus" "Turvallisuusraportti"]
   ["tiestotarkastusraportti" "Tiestötarkastusraportti"]
   ["valitavoiteraportti" "Välitavoiteraportti"]
   ["yllapidon-aikataulu" "Ylläpidon aikatauluraportti"]
   ["vemtr" "Valtakunnalliset tehtävämäärät"]
   ["kelitarkastusraportti" "Kelitarkastusraportti"]
   ["laaduntarkastusraportti" "Laaduntarkastusraportti"]
   ["tiemerkinnan-kustannusyhteenveto" "Tiemerkinnän kustannusyhteenveto"]
   ["toimenpidepaivat" "Toimenpidepäivät"]
   ["toimenpideajat" "Toimenpideajat"]
   ["yks-hint-tyot" "Yksikköhintaiset työt"]
   ["soratietarkastusraportti" "Soratietarkastusraportti"]
   ["vesivaylien-laskutusyhteenveto" "Vesiväylien laskutusyhteenveto"]
   ["sanktioraportti-yllapito" "Sanktioraportti yllapito"]
   ["vastaanottotarkastusraportti" "Vastaanottotarkastusraportti"]
   ["kanavien-muutos-ja-lisatyot" "Kanavien muutos-ja lisätyöt"]
   ["kanavien-laskutusyhteenveto" "Kanavien laskutusyhteenveto"]
   ["kanavien-liikennetapahtumat" "Kanavien liikennetapahtumat"]])

(defn raportin-nimi-fmt [vaihtoehdot tunniste]
  (first (keep #(when (= (first %)
                         tunniste)
                  (second %))
               vaihtoehdot)))


(def +mahdolliset-formaatit+
  [[nil "Kaikki"]
   [:selain "Selain (Harja)"]
   [:pdf "PDF"]
   [:excel "Excel"]])
