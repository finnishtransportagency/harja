(ns harja.palvelin.raportointi.raportit.tyomaakokous
  "Työmaakokouksen koosteraportti, joka kutsuu muita raportteja ja yhdistää niiden tiedot"
  (:require
    [taoensso.timbre :as log]
    [harja.palvelin.raportointi.raportit.laskutusyhteenveto :as laskutusyhteenveto]
    [harja.palvelin.raportointi.raportit.ilmoitus :as ilmoitus]
    [harja.palvelin.raportointi.raportit.turvallisuuspoikkeamat :as turvallisuus]
    [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-paivittain :as yks-hint]
    [harja.palvelin.raportointi.raportit.ymparisto :as ymparisto]))

(defn osat [raportti]
  ;; Pudotetaan pois :raportti keyword ja string tai map optiot.
  ;; Palautetaan vain sen jälkeen tulevat raporttielementit
  (mapcat #(if (and (seq? %) (not (vector? %)))
             %
             [%])
          (drop 2 raportti)))

(defn suorita [db user {:keys [kuukausi urakka-id] :as tiedot}]
  [:raportti {:nimi (str "Työmaakokousraportti" kuukausi)}
   (mapcat (fn [[aja-parametri otsikko raportti-fn]]
             (do
               (log/debug "aja parametri " aja-parametri)
               (log/debug "otsikko " otsikko)
               (log/debug "raportti-fn " raportti-fn)
               (when (get tiedot aja-parametri)
                (concat [[:otsikko otsikko]]
                        (osat (raportti-fn db user tiedot))))))
           [[:laskutusyhteenveto "Laskutusyhteenveto" laskutusyhteenveto/suorita]
            [:ilmoitusraportti "Ilmoitusraportti" ilmoitus/suorita]
            [:yksikkohintaiset-tyot "Yksikköhintaisten töiden raportti" yks-hint/suorita]
            [:turvallisuus "Turvallisuusraportti" turvallisuus/suorita]
            [:ymparisto "Ympäristöraportti" ymparisto/suorita]])])
