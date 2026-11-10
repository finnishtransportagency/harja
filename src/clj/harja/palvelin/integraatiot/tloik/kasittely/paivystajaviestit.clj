(ns harja.palvelin.integraatiot.tloik.kasittely.paivystajaviestit
  (:require [taoensso.timbre :as log]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.tloik.sahkoposti :as tloik-sahkoposti]
            [harja.palvelin.integraatiot.tloik.tekstiviesti :as tloik-tekstiviesti]
            [harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet :as ilmoitustoimenpiteet])
  (:use [slingshot.slingshot :only [try+]]))

(defn normalisoi-sahkoposti [sahkoposti]
  (some-> sahkoposti str/trim str/lower-case))

(defn laheta-sms-paivystajalle?
  "SMS lähetetään vain vastuuhenkilölle ja vain toimenpidepyynnöstä."
  [ilmoitus paivystaja]
  (boolean
    (and (:vastuuhenkilo paivystaja)
         ;; Kannasta haetulla ilmoituksella tyyppi on keyword, T-Loikista tulleella merkkijono
         (= "toimenpidepyynto" (some-> (:ilmoitustyyppi ilmoitus) name)))))

(defn lahetyspaatokset
  "Päättää puhtaasti, mitä viestejä kullekin päivystäjälle lähetetään. Sama sähköpostiosoite
  (trim + pienet kirjaimet) saa ilmoituksen vain kerran, ensimmäiselle päivystäjälle.
  Palauttaa päivystäjät samassa järjestyksessä muodossa
  {:paivystaja .. :laheta-sahkoposti? .. :laheta-sms? .. :duplikaatti-sahkoposti? ..}."
  [ilmoitus paivystajat]
  (:paatokset
    (reduce (fn [{:keys [kasitellyt-sahkopostit] :as tulos} paivystaja]
              (let [sahkoposti (normalisoi-sahkoposti (:sahkoposti paivystaja))
                    duplikaatti? (and (seq sahkoposti) (contains? kasitellyt-sahkopostit sahkoposti))]
                (-> tulos
                    (update :paatokset conj {:paivystaja paivystaja
                                             :laheta-sahkoposti? (not duplikaatti?)
                                             :laheta-sms? (laheta-sms-paivystajalle? ilmoitus paivystaja)
                                             :duplikaatti-sahkoposti? (boolean duplikaatti?)})
                    (cond-> (seq sahkoposti) (update :kasitellyt-sahkopostit conj sahkoposti)))))
      {:kasitellyt-sahkopostit #{} :paatokset []}
      paivystajat)))

(defn laheta-ilmoitus-sahkopostilla [{:keys [email]} db {id :ilmoitus-id :as ilmoitus} {vastaanottaja :sahkoposti :as paivystaja}]
  ;; Lähetetään vain vastaanottajalle, jolla on jonkinlainen emailosoite annettuna
  (if (and vastaanottaja (> (count vastaanottaja) 3))
    (do
      (log/info "Lähetetään ilmoitus (id: " id ") sähköpostilla osoitteeseen: " vastaanottaja)
      (let [lahettaja (sahkoposti/vastausosoite email)
            [otsikko viesti] (tloik-sahkoposti/otsikko-ja-viesti db lahettaja ilmoitus)]
        (try
          (sahkoposti/laheta-viesti! email lahettaja vastaanottaja otsikko viesti {"X-Correlation-ID" id})
          (ilmoitustoimenpiteet/tallenna-ilmoitustoimenpide
            db
            (:id ilmoitus)
            (:ilmoitus-id ilmoitus)
            viesti
            "valitys"
            paivystaja
            "ulos"
            "sahkoposti")
          (catch Exception e
            (log/error (format "Ilmoituksen %s lähettämisessä sähköpostilla tapahtui poikkeus." (:ilmoitus-id ilmoitus)) e)))))
    (log/debug (format "Ilmoitusta %s ei voida lähettää sähköpostilla ilman sähköpostiosoitetta." (:ilmoitus-id ilmoitus)))))

(defn laheta-ilmoitus-tekstiviestilla [sms db ilmoitus paivystaja]
  (tloik-tekstiviesti/laheta-ilmoitus-tekstiviestilla sms db ilmoitus paivystaja))

(defn laheta
  ([ilmoitusasetukset db ilmoitus paivystaja]
   (laheta ilmoitusasetukset db ilmoitus paivystaja true))
  ([{sms :sms :as ilmoitusasetukset} db ilmoitus paivystaja laheta-sahkoposti?]
   (when (and sms (laheta-sms-paivystajalle? ilmoitus paivystaja))
     (laheta-ilmoitus-tekstiviestilla sms db ilmoitus paivystaja))
   (when laheta-sahkoposti?
     (laheta-ilmoitus-sahkopostilla ilmoitusasetukset db ilmoitus paivystaja))))
