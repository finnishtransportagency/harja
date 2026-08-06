(ns harja.palvelin.raportointi.raportit.valitavoiteraportti
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [jeesql.core :refer [defqueries]]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.domain.urakka :as u-domain]
            [harja.domain.valitavoite :as vt-domain]
            [clj-time.core :as t]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]))

(defqueries "harja/palvelin/raportointi/raportit/valitavoitteet.sql")

(defn- ajoissa? [valitavoite alkupvm]
  (and (:takaraja valitavoite)
       (:valmis-pvm valitavoite)
       (pvm/sama-tai-ennen? (c/from-date (:valmis-pvm valitavoite))
                            (c/from-date (:takaraja valitavoite)))
       (pvm/sama-tai-jalkeen? (c/from-date (:takaraja valitavoite))
                              (c/from-date alkupvm))))

(defn- myohassa? [valitavoite alkupvm loppupvm]
  (let [onko-myohassa? (fn [haettava-paiva]
                         (and (:takaraja valitavoite)
                           (:valmis-pvm valitavoite)
                           (pvm/jalkeen? (c/from-date (:valmis-pvm valitavoite))
                             (c/from-date (:takaraja valitavoite)))
                           (pvm/sama-tai-jalkeen? (c/from-date haettava-paiva)
                             (c/from-date alkupvm))
                           (pvm/sama-tai-ennen? (c/from-date haettava-paiva)
                             (c/from-date loppupvm))))]
    ;; Palautetaan ne välitavoitteet, jotka ovat valmistuneet myöhässä ja
    ;; joiden joko takaraja tai valmistumispäivä on valitun aikarajan sisällä
    (or (onko-myohassa? (:takaraja valitavoite))
        (onko-myohassa? (:valmis-pvm valitavoite)))))

(defn- kesken? [valitavoite]
  (and (:takaraja valitavoite)
       (pvm/ennen? (t/now) (c/from-date (:takaraja valitavoite)))
       (not (:valmis-pvm valitavoite))))

(defn- toteutumatta? [valitavoite]
  (and (:takaraja valitavoite)
       (not (:valmis-pvm valitavoite))
       (pvm/jalkeen? (t/now)
                     (c/from-date (:takaraja valitavoite)))))

(defn- kuvaile-valmistunut-valitavoite
  "Palauttaa tekstimuotoisen kuvauksen välitavoitteen valmistumisesta."
  [valitavoite alkupvm loppupvm]
  (cond
    (ajoissa? valitavoite alkupvm)
    (let [paivia-valissa (pvm/paivia-valissa (c/from-date (:valmis-pvm valitavoite))
                                             (c/from-date (:takaraja valitavoite)))]
      (when (pos? paivia-valissa)
        (str (fmt/kuvaile-paivien-maara paivia-valissa) " ennen")))

    (myohassa? valitavoite alkupvm loppupvm)
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

(defn- muodosta-raportin-rivit-urakkakohtaisissa [valitavoitteet alkupvm loppupvm vesivaylaurakka?]
  (let [valitavoiterivi (fn [valitavoite]
                          [(:nimi valitavoite)
                           (when vesivaylaurakka? (if (:aloituspvm valitavoite) (pvm/pvm-opt (:aloituspvm valitavoite)) (str "-")))
                           (let [kuvaus (kuvaile-keskenerainen-valitavoite valitavoite)]
                             (str (pvm/pvm-opt (:takaraja valitavoite))
                                  (when kuvaus
                                    (str " (" kuvaus ")"))))
                           (str (vt-domain/valmiustilan-kuvaus valitavoite))
                           (let [valmispvm (:valmispvm valitavoite)
                                 kuvaus (kuvaile-valmistunut-valitavoite valitavoite alkupvm loppupvm)]
                             (if valmispvm
                               (str (pvm/pvm-opt (:valmispvm valitavoite))
                                    (if kuvaus
                                      (str " (" kuvaus ")")))
                               "-"))
                           (:valmis-kommentti valitavoite)
                           (str (:valmis-merkitsija-etunimi valitavoite) " " (:valmis-merkitsija-sukunimi valitavoite))])]
    (when-not (empty? valitavoitteet)
      (into [] (concat (mapv valitavoiterivi valitavoitteet))))))

(defn- muodosta-raportin-rivit-valtakunnallisissa [valitavoitteet alkupvm loppupvm]
  (let [valitavoiterivi (fn [valitavoite]
                          [(str (:valtakunnallinen-nimi valitavoite))
                           (str (:nimi valitavoite))
                           (let [valtakunnallinen-takaraja (:valtakunnallinen-takaraja valitavoite)
                                 toistopaiva (:valtakunnallinen-takarajan-toistopaiva valitavoite)
                                 toistokuukausi (:valtakunnallinen-takarajan-toistokuukausi valitavoite)]
                             (cond
                               valtakunnallinen-takaraja
                               (pvm/pvm-opt valtakunnallinen-takaraja)

                               (and toistopaiva toistokuukausi)
                               (str "Vuosittain "
                                 toistopaiva
                                 "."
                                 toistokuukausi)

                               :else
                               "Ei takarajaa"))
                           (pvm/pvm-opt (:takaraja valitavoite))
                           (str (vt-domain/valmiustilan-kuvaus valitavoite))
                           (let [valmis-pvm (:valmispvm valitavoite)
                                 kuvaus (kuvaile-valmistunut-valitavoite valitavoite alkupvm loppupvm)]
                             (if valmis-pvm
                               (str (pvm/pvm-opt (:valmispvm valitavoite))
                                 (if kuvaus
                                   (str " (" kuvaus ")")))
                               "-"))
                           (:valmis-kommentti valitavoite)
                           (str (:valmis-merkitsija-etunimi valitavoite) " " (:valmis-merkitsija-sukunimi valitavoite))])]
    (when-not (empty? valitavoitteet)
      (into [] (concat (mapv valitavoiterivi valitavoitteet))))))

(defn- muodosta-urakkakohtaiset-otsikkorivit [vesivaylaurakka?]
  [{:otsikko "Nimi" :leveys 10}
   (when vesivaylaurakka? {:otsikko "Aloituspäivä" :leveys 5})
   {:otsikko "Takaraja" :leveys 5}
   {:otsikko "Tila" :leveys 5}
   {:otsikko "Valmistumispäivä" :leveys 5}
   {:otsikko "Kommentti valmistumisesta" :leveys 10}
   {:otsikko "Valmiiksimerkitsijä" :leveys 5}])

(defn- muodosta-valtakunnalliset-otsikkorivit []
  [{:otsikko "Työn kuvaus" :leveys 8}
   {:otsikko "Urakkakohtaiset tarkennukset" :leveys 8}
   {:otsikko "Valtakunnallinen takaraja" :leveys 5}
   {:otsikko "Takaraja urakassa" :leveys 5}
   {:otsikko "Tila" :leveys 5}
   {:otsikko "Valmistumispäivä" :leveys 5}
   {:otsikko "Kommentti valmistumisesta" :leveys 8}
   {:otsikko "Merkitsijä" :leveys 5}])

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (let [urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        vesivaylaurakka? (u-domain/vesivaylaurakka? urakan-tiedot)
        ;;alkupvm ja loppupvm voivat olla yhden kuukauden alku- ja loppupäivät, jos ollaan työmaakokousraportissa,
        ;;mutta välitavoiteraportti näytetään sielläkin vain koko hoitovuoden ajalta
        alkupvm (first (pvm/paivamaaran-hoitokausi alkupvm))
        loppupvm (second (pvm/paivamaaran-hoitokausi loppupvm))
        valitavoitteet (hae-valitavoitteet db {:urakka urakka-id
                                               :alkupvm alkupvm
                                               :loppupvm loppupvm})
        urakan-valitavoitteet (filter #(not (:valtakunnallinen-id %)) valitavoitteet)
        valtakunnalliset-valitavoitteet (filter :valtakunnallinen-id valitavoitteet)
        hoitourakka? (u-domain/mh-tai-hoitourakka? (keyword (:tyyppi urakan-tiedot)))
        otsikkorivit-urakkakohtaisissa (muodosta-urakkakohtaiset-otsikkorivit vesivaylaurakka?)
        otsikkorivit-valtakunnallisissa (muodosta-valtakunnalliset-otsikkorivit)
        urakkakohtaiset-datarivit (muodosta-raportin-rivit-urakkakohtaisissa urakan-valitavoitteet alkupvm loppupvm vesivaylaurakka?)
        valtakunnalliset-datarivit (muodosta-raportin-rivit-valtakunnallisissa valtakunnalliset-valitavoitteet alkupvm loppupvm)
        raportin-nimi "Välitavoiteraportti"
        otsikko (str (raportin-otsikko (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                        raportin-nimi alkupvm loppupvm) ", suoritettu " (fmt/pvm (pvm/nyt)))]

    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:teksti-paksu otsikko]
     [:tyhja-rivi nil]
     [:taulukko {:otsikko "Urakkakohtaiset määräaikaan mennessä tehtävät työt"
                 :tyhja (when (empty? urakkakohtaiset-datarivit) "Ei raportoitavia määräaikaan mennessä tehtäviä töitä.")
                 :sheet-nimi raportin-nimi}
      otsikkorivit-urakkakohtaisissa
      urakkakohtaiset-datarivit]
     (when hoitourakka? [:taulukko {:otsikko "Kaikissa urakoissa määräaikaan mennessä tehtävät työt"
                                    :tyhja (when (empty? valtakunnalliset-datarivit) "Ei raportoitavia määräaikaan mennessä tehtäviä töitä.")
                                    :sheet-nimi raportin-nimi}
                         otsikkorivit-valtakunnallisissa
                         valtakunnalliset-datarivit])]))
