(ns harja.palvelin.raportointi.raportit.tyomaakokous
  "Työmaakokouksen koosteraportti, joka kutsuu muita raportteja ja yhdistää niiden tiedot"
  (:require [harja.palvelin.raportointi.raportit.laskutusyhteenveto :as laskutusyhteenveto]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot :as yks-hint]
            [harja.palvelin.raportointi.raportit.ymparisto :as ymparisto]))

(defn osat [raportti]
  ;; Pudotetaan pois :raportti keyword ja string tai map optiot.
  ;; Palautetaan vain sen jälkeen tulevat raporttielementit
  (mapcat #(if (and (seq? %) (not (vector? %)))
             %
             [%])
          (drop 2 raportti)))

(defn suorita [db user {:keys [kuukausi urakka-id] :as tiedot}]
  [:raportti "Työmaakokousraportti"
   (when (get tiedot "Laskutusyhteenveto")
       [:otsikko "Laskutusyhteenveto"])
   (when (get tiedot "Laskutusyhteenveto")
     (osat (laskutusyhteenveto/suorita db user tiedot)))
   (when (get tiedot "Yksikköhintaisten töiden raportti")
     (osat (yks-hint/suorita db user tiedot)))
   (when (get tiedot "Ympäristöraportti")
     (osat (ymparisto/suorita db user tiedot)))])
