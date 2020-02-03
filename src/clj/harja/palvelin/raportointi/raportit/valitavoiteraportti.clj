(ns harja.palvelin.raportointi.raportit.valitavoiteraportti
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [clj-time.core :as t]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [harja.math :as math]))

(defqueries "harja/palvelin/raportointi/raportit/valitavoitteet.sql")

(defn- ajoissa? [valitavoite]
  (and (:takaraja valitavoite)
       (:valmis-pvm valitavoite)
       (pvm/sama-tai-ennen? (c/from-date (:valmis-pvm valitavoite))
                            (c/from-date (:takaraja valitavoite)))))

(defn- suodata-ajoissa [valitavoitteet]
  (filter
    (fn [valitavoite]
      (ajoissa? valitavoite))
    valitavoitteet))

(defn- myohassa? [valitavoite]
  (and (:takaraja valitavoite)
       (:valmis-pvm valitavoite)
       (pvm/jalkeen? (c/from-date (:valmis-pvm valitavoite))
                     (c/from-date (:takaraja valitavoite)))))

(defn- suodata-myohassa [valitavoitteet]
  (filter
    (fn [valitavoite]
      (myohassa? valitavoite))
    valitavoitteet))

(defn- kesken? [valitavoite]
  (and (:takaraja valitavoite)
       (pvm/ennen? (t/now) (c/from-date (:takaraja valitavoite)))
       (not (:valmis-pvm valitavoite))))

(defn- suodata-kesken [valitavoitteet]
  (filter
    (fn [valitavoite]
      (kesken? valitavoite))
    valitavoitteet))

(defn- toteutumatta? [valitavoite]
  (and (:takaraja valitavoite)
       (not (:valmis-pvm valitavoite))
       (pvm/jalkeen? (t/now)
                     (c/from-date (:takaraja valitavoite)))))

(defn- suodata-toteumatta [valitavoitteet]
  (filter
    (fn [valitavoite]
      (toteutumatta? valitavoite))
    valitavoitteet))

(defn- kuvaile-valmistunut-valitavoite
  "Palauttaa tekstimuotoisen kuvauksen välitavoitteen valmistumisesta."
  [valitavoite]
  (cond
    (ajoissa? valitavoite)
    (let [paivia-valissa (pvm/paivia-valissa (c/from-date (:valmis-pvm valitavoite))
                                             (c/from-date (:takaraja valitavoite)))]
      (when (pos? paivia-valissa)
        (str (fmt/kuvaile-paivien-maara paivia-valissa) " ennen")))

    (myohassa? valitavoite)
    (let [paivia-valissa (pvm/paivia-valissa (c/from-date (:takaraja valitavoite))
                                             (c/from-date (:valmis-pvm valitavoite)))]
      (when (pos? paivia-valissa)
        (str (fmt/kuvaile-paivien-maara paivia-valissa) " myöhässä")))
    :default
    nil))

(defn- kuvaile-keskenerainen-valitavoite
  "Palauttaa tekstimuotoisen kuvauksen siitä kuinka kauan välitavoitteen
   takarajaan on aikaa jäljellä tai kauanko tavoitteesta ollaan myöhässä."
  [valitavoite]
  (cond
    (kesken? valitavoite)
    (let [paivia-valissa (t/in-days (t/interval (t/now)
                                                (c/from-date (:takaraja valitavoite))))]
      (when (pos? paivia-valissa)
        (str (fmt/kuvaile-paivien-maara paivia-valissa) " jäljellä")))

    (toteutumatta? valitavoite)
    (let [paivia-valissa (t/in-days (t/interval
                                      (c/from-date (:takaraja valitavoite))
                                      (t/now)))]
      (when (pos? paivia-valissa)
        (str (fmt/kuvaile-paivien-maara paivia-valissa) " myöhässä")))
    :default
    nil))

(defn- muodosta-raportin-rivit [valitavoitteet]
  (let [ajoissa (suodata-ajoissa valitavoitteet)
        myohassa (suodata-myohassa valitavoitteet)
        kesken (suodata-kesken valitavoitteet)
        toteutumatta (suodata-toteumatta valitavoitteet)
        valitavoiterivi (fn [valitavoite]
                          [(:nimi valitavoite)
                           (let [kuvaus (kuvaile-keskenerainen-valitavoite valitavoite)]
                             (str (pvm/pvm-opt (:takaraja valitavoite))
                                  (when kuvaus
                                    (str " (" kuvaus ")"))))
                           (let [valmis-pvm (:valmis-pvm valitavoite)
                                 kuvaus (kuvaile-valmistunut-valitavoite valitavoite)]
                             (if valmis-pvm
                               (str (pvm/pvm-opt (:valmis-pvm valitavoite))
                                    (if kuvaus
                                      (str " (" kuvaus ")")))
                               "-"))
                           (:valmis-kommentti valitavoite)])]
    (when-not (empty? valitavoitteet)
      (into [] (concat
                 [{:otsikko (str "Ajoissa valmistuneet ("
                                 (fmt/prosentti
                                   (math/osuus-prosentteina (count ajoissa) (count valitavoitteet)) 0)
                                 ")")}]
                 (mapv valitavoiterivi ajoissa)
                 [{:otsikko (str "Myöhässä valmistuneet ("
                                 (fmt/prosentti
                                   (math/osuus-prosentteina (count myohassa) (count valitavoitteet)) 0)
                                 ")")}]
                 (mapv valitavoiterivi myohassa)
                 [{:otsikko (str "Kesken ("
                                 (fmt/prosentti
                                   (math/osuus-prosentteina (count kesken) (count valitavoitteet)) 0)
                                 ")")}]
                 (mapv valitavoiterivi kesken)
                 [{:otsikko (str "Valmistumatta ("
                                 (fmt/prosentti
                                   (math/osuus-prosentteina (count toteutumatta) (count valitavoitteet)) 0)
                                 ")")}]
                 (mapv valitavoiterivi toteutumatta))))))

(defn- muodosta-otsikkorivit []
  [{:otsikko "Työn kuvaus" :leveys 10}
   {:otsikko "Takaraja" :leveys 5}
   {:otsikko "Valmistunut" :leveys 10}
   {:otsikko "Kommentti" :leveys 10}])

(defn suorita [db user {:keys [urakka-id] :as parametrit}]
  (let [konteksti :urakka
        valitavoitteet (hae-valitavoitteet db {:urakka urakka-id})
        otsikkorivit (muodosta-otsikkorivit)
        datarivit (muodosta-raportin-rivit valitavoitteet)
        raportin-nimi "Määräaikaan mennessä tehtävät työt"
        otsikko (str (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                     ", " raportin-nimi ", suoritettu " (fmt/pvm (pvm/nyt)))]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja (when (empty? datarivit) "Ei raportoitavia määräaikaan mennessä tehtäviä töitä.")
                 :sheet-nimi raportin-nimi}
      otsikkorivit
      datarivit]]))
