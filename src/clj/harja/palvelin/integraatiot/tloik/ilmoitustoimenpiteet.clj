(ns harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet
  (:require [taoensso.timbre :as log]
            [clojure.string :as str]
            [harja.kyselyt.tieliikenneilmoitukset :as ilmoitukset]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitustoimenpide-sanoma :as toimenpide-sanoma]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.util UUID)))

(defn laheta [jms-lahettaja db id]
  (let [viesti-id (str (UUID/randomUUID))
        data (konversio/alaviiva->rakenne (first (ilmoitukset/hae-ilmoitustoimenpide db id)))
        muodosta-xml #(toimenpide-sanoma/muodosta data viesti-id)
        jms-viesti-id (jms-lahettaja muodosta-xml viesti-id)]
    (try
      (when (nil? jms-viesti-id)
        (log/warn (format "Ilmoitustoimenpiteen (id: %s) lähetys T-LOIK:n onnistui. mutta JMS viesti-id on nil!" id)))
      (ilmoitukset/merkitse-ilmoitustoimenpide-odottamaan-vastausta! db viesti-id id)
      (log/debug (format "Ilmoitustoimenpiteen (id: %s) lähetys T-LOIK:n onnistui." id))
      (catch Exception e
        (log/error e (format "Ilmoitustoimenpiteen (id: %s) lähetys T-LOIK:n epäonnistui." id))
        (ilmoitukset/merkitse-ilmoitustoimenpidelle-lahetysvirhe-idlla! db id)))))

(defn laheta-ilmoitustoimenpide [jms-lahettaja db id]
  (log/debug (format "Käynnistetään ilmoitustoimenpiteen (id: %s) lähetys T-LOIK:n." id))
  (try
    (laheta jms-lahettaja db id)
    (log/debug (format "Ilmoitustoimenpide (id: %s) lähetetty T-LOIK:n onnistuneesti!" id))
    (catch Exception e
      (log/error e (format "Ilmoitustoimenpiteen (id: %s) lähetyksessä T-LOIK:n tapahtui poikkeus." id))
      (ilmoitukset/merkitse-ilmoitustoimenpidelle-lahetysvirhe-idlla! db id)
      (throw e))))

(defn laheta-lahettamattomat-ilmoitustoimenpiteet [jms-lahettaja db aikavali-min]
  (lukko/yrita-ajaa-lukon-kanssa
    db
    "tloik-uudelleenlahetys"
    #(do
      (log/debug "Lähetetään lähettämättömät ilmoitustoimenpiteet T-LOIK:n.")
      (let [idt (mapv :id (ilmoitukset/hae-lahettamattomat-ilmoitustoimenpiteet db))]
        (doseq [id idt]
          (try
            (laheta-ilmoitustoimenpide jms-lahettaja db id)
            (catch Exception _))))
      (log/debug "Ilmoitustoimenpiteiden lähetys T-LOIK:n valmis."))
    ;; Minuuttia lyhempi lukko kuin ajastuksen aikaväli
    (* 60 (dec aikavali-min))))

(defn vastaanota-kuittaus [db viesti-id onnistunut]
  (if onnistunut
    (do
      (log/debug (format "Ilmoitustoimenpide kuitattiin T-LOIK:sta onnistuneeksi viesti-id:llä: %s" viesti-id))
      (ilmoitukset/merkitse-ilmoitustoimenpide-lahetetyksi! db viesti-id))

    (do
      (log/error (format "Ilmoitustoimenpide kuitattiin T-LOIK:sta epäonnistuneeksi viesti-id:llä: %s" viesti-id))
      (ilmoitukset/merkitse-ilmoitustoimenpidelle-lahetysvirhe-lahetysidlla! db viesti-id))))

(defn tallenna-ilmoitustoimenpide
  [db ilmoitus ilmoitusid vapaateksti toimenpide paivystaja suunta kanava]
  (:id (ilmoitukset/luo-ilmoitustoimenpide<!
         db
         {:ilmoitus                         ilmoitus
          :ilmoitusid                       ilmoitusid
          :vakiofraasi                      nil
          :vapaateksti                      vapaateksti
          :tila                             (when (= "valitys" toimenpide) "lahetetty")
          :kuittaustyyppi                   toimenpide
          :suunta                           suunta
          :kanava                           kanava
          :kuittaaja_henkilo_etunimi        (:etunimi paivystaja)
          :kuittaaja_henkilo_sukunimi       (:sukunimi paivystaja)
          :kuittaaja_henkilo_tyopuhelin     (:tyopuhelin paivystaja)
          :kuittaaja_henkilo_matkapuhelin   (:matkapuhelin paivystaja)
          :kuittaaja_henkilo_sahkoposti     (:sahkoposti paivystaja)
          :kuittaaja_organisaatio_nimi      (:nimi paivystaja)
          :kuittaaja_organisaatio_ytunnus   (:ytunnus paivystaja)
          :kasittelija_henkilo_etunimi      nil
          :kasittelija_henkilo_sukunimi     nil
          :kasittelija_henkilo_matkapuhelin nil
          :kasittelija_henkilo_tyopuhelin   nil
          :kasittelija_henkilo_sahkoposti   nil
          :kasittelija_organisaatio_nimi    nil
          :kasittelija_organisaatio_ytunnus nil})))

(defn varoita-vastaamattomista-kuittauksista
  "Nostetaan varoitus tai virhe lokille, jos löydetään ilmoitustoimenpide-taulusta viestejä, jotka odottaa vastausta
  T-LOIKilta poikkeuksellisen kauan. Mikäli tämä virhe nousee, on oltava yhteydessä T-LOIKiin ja varmistettava, johtuuko
  viive kuormasta vai siitä, että viestit eivät tule jonoon.
  Taustana tässä on tilanne, jossa HARJA luulee lähettäneensä viestin jonoon onnistuneesti, mutta ei kuitenkaan ole niin tehnyt.
  Tämän kaltainen tapaus tapahtunut ainakin 5.9.2023, jolloin virhe huomattiin vasta kun asiasta saatiin palautetta,
  eikä lokeilta ollut huomattavissa mitään poikkeavaa"
  [db]
  (lukko/yrita-ajaa-lukon-kanssa db "kuittausten-monitorointi"
    (fn []
      (log/debug "Haetaan myöhästyneet ilmoitustoimenpiteet")
      (let [myohastyneet-ilmoitukset (->> (ilmoitukset/hae-myohastyneet-ilmoitustoimenpiteet db)
                                       (map #(konversio/array->vec % :idt))
                                       (map #(konversio/array->vec % :korrelaatioidt)))
            minuutin-myohastyneet (first (filter #(and
                                                    (false? (:halytys-annettava %))
                                                    (nil? (:varoitus-annettu %)))
                                           myohastyneet-ilmoitukset))
            kymmenen-min-myohastyneet (first (filter #(true? (:halytys-annettava %)) myohastyneet-ilmoitukset))]

        (when (and minuutin-myohastyneet (pos? (:maara minuutin-myohastyneet)))
          (log/warn (format "Ilmoitusten kuittauksissa viivettä! Lähetetty %s kuittausviestiä T-LOIK:ille ilman vastausta minuutissa"
                      (:maara minuutin-myohastyneet)))
          (ilmoitukset/merkitse-ilmoitustoimenpide-varoitus-annetuksi! db {:idt (:idt minuutin-myohastyneet)
                                                                          :varoitus "varoitus"}))

        (when (and kymmenen-min-myohastyneet (pos? (:maara kymmenen-min-myohastyneet)))
          (log/error (format "Ilmoitusten kuittauksissa viivettä! Lähetetty %s kuittausviestiä T-LOIK:ille ilman vastausta kymmenessä minuutissa. Korrelaatio-id:t: (%s)"
                       (:maara kymmenen-min-myohastyneet)
                       (str/join ", " (:korrelaatioidt kymmenen-min-myohastyneet))))
          (ilmoitukset/merkitse-ilmoitustoimenpide-varoitus-annetuksi! db {:idt (:idt kymmenen-min-myohastyneet)
                                                                          :varoitus "halytys"}))))
    55))
