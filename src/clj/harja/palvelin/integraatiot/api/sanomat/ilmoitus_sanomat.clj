(ns harja.palvelin.integraatiot.api.sanomat.ilmoitus-sanomat
  (:require [harja.geo :as geo]
            [harja.tyokalut.spec-apurit :as apurit]))

(defn rakenna-tierekisteriosoite [ilmoitus tierekisteriosoite]
  (if (and (:numero tierekisteriosoite)
           (:alkuetaisyys tierekisteriosoite)
           (:alkuosa tierekisteriosoite)
           (:loppuetaisyys tierekisteriosoite)
           (:loppuosa tierekisteriosoite))
    (assoc-in ilmoitus [:sijainti :tie]
              {:numero (:numero tierekisteriosoite)
               :aet (:alkuetaisyys tierekisteriosoite)
               :aosa (:alkuosa tierekisteriosoite)
               :let (:loppuetaisyys tierekisteriosoite)
               :losa (:loppuosa tierekisteriosoite)})
    ilmoitus))

(defn rakenna-sijanti [ilmoitus]
  (let [koordinaatit (:coordinates (geo/pg->clj (:sijainti ilmoitus)))
        tierekisteriosoite (:tr ilmoitus)]
    (-> ilmoitus
        (dissoc :sijainti)
        (dissoc :tr)
        (assoc :tienumero (:numero tierekisteriosoite))
        (assoc-in [:sijainti :koordinaatit]
                  {:x (first koordinaatit)
                   :y (second koordinaatit)})
        (rakenna-tierekisteriosoite tierekisteriosoite))))

(defn rakenna-ilmoituksen-data [ilmoitus vectorin-avain elementin-avain]
  (if (vectorin-avain ilmoitus)
    (let [kannassa (vec (.getArray (vectorin-avain ilmoitus)))
          vastauksessa (mapv (fn [arvo] {elementin-avain arvo}) kannassa)]
      (update ilmoitus vectorin-avain (constantly vastauksessa)))
    ilmoitus))

(defn rakenna-henkilo [ilmoitus henkiloavain]
  (let [henkilo (henkiloavain ilmoitus)]
    (-> ilmoitus
        (update-in [henkiloavain] dissoc :puhelinnumero)
        (update-in [henkiloavain] dissoc :matkapuhelin)
        (update-in [henkiloavain] dissoc :tyopuhelin)
        (update-in [henkiloavain] dissoc :sahkoposti)
        (assoc-in [henkiloavain :matkapuhelin] (:matkapuhelin henkilo))
        (assoc-in [henkiloavain :tyopuhelin] (:tyopuhelin henkilo))
        (assoc-in [henkiloavain :email] (:sahkoposti henkilo)))))

(defn rakenna-kuittaukset [ilmoitus]
  (let [kuittaukset
        (mapv (fn [kuittaus]
                {:kuittaus (-> kuittaus
                             (assoc-in [:kuittaaja :etunimi] (:kuittaaja_henkilo_etunimi kuittaus))
                             (assoc-in [:kuittaaja :sukunimi] (:kuittaaja_henkilo_sukunimi kuittaus))
                             (assoc-in [:kuittaajaorganisaatio :nimi] (:kuittaaja_organisaatio_nimi kuittaus))
                             (assoc-in [:kuittaajaorganisaatio :ytunnus] (:kuittaaja_organisaatio_ytunnus kuittaus))
                             (dissoc :kuittaaja_henkilo_sukunimi)
                             (dissoc :kuittaaja_henkilo_etunimi)
                             (dissoc :kuittaaja_organisaatio_nimi)
                             (dissoc :kuittaaja_organisaatio_ytunnus))})
          (:kuittaukset ilmoitus))]
    ;; Muokkaa kuittauksia ainoastaan, jos niitä on
    (if (and kuittaukset (not (empty? kuittaukset)))
      (-> ilmoitus
        (dissoc :kuittaukset)
        (assoc :kuittaukset kuittaukset))
      ilmoitus)))

(defn- poista-tyhja-lista
  "Tyhjät arrayt eivät mene validaatiosta läpi.
  Poistetaan avain kokonaan, jos sen arvo on tyhjä lista."
  [ilmoitus avain]
  (println "Poistetaan tyhjä lista")
  (if (empty? (avain ilmoitus))
    (dissoc ilmoitus avain)
    ilmoitus))

(defn rakenna-ilmoitus [ilmoitus]
  {:ilmoitus (-> ilmoitus
               (rakenna-ilmoituksen-data :selitteet :selite)
               (rakenna-ilmoituksen-data :kuvat :url)
               (poista-tyhja-lista :kuvat)
               (rakenna-henkilo :ilmoittaja)
               (rakenna-henkilo :lahettaja)
               rakenna-sijanti
               rakenna-kuittaukset)})
