(ns harja.palvelin.integraatiot.api.sanomat.ilmoitus-sanomat)

(defn rakenna-tierekisteriosoite [ilmoitus tierekisteriosoite]
  (if (and (:numero tierekisteriosoite)
           (:alkuetaisyys tierekisteriosoite)
           (:alkuosa tierekisteriosoite)
           (:loppuetaisyys tierekisteriosoite)
           (:loppuosa tierekisteriosoite))
    (assoc-in ilmoitus [:sijainti :tie]
              {:numero (:numero tierekisteriosoite)
               :aet    (:alkuetaisyys tierekisteriosoite)
               :aosa   (:alkuosa tierekisteriosoite)
               :let    (:loppuetaisyys tierekisteriosoite)
               :losa   (:loppuosa tierekisteriosoite)})
    ilmoitus))

(defn rakenna-sijanti [ilmoitus]
  (let [koordinaatit (:coordinates (harja.geo/pg->clj (:sijainti ilmoitus)))
        tierekisteriosoite (:tr ilmoitus)]
    (-> ilmoitus
        (dissoc :sijainti)
        (dissoc :tr)
        (assoc :tienumero (:numero tierekisteriosoite))
        (assoc-in [:sijainti :koordinaatit]
                  {:x (first koordinaatit)
                   :y (second koordinaatit)})
        (rakenna-tierekisteriosoite tierekisteriosoite))))

(defn rakenna-selitteet [ilmoitus]
  (let [selitteet-kannassa (vec (.getArray (:selitteet ilmoitus)))
        selitteet-vastauksessa (mapv (fn [selite] {:selite selite}) selitteet-kannassa)]
    (update ilmoitus :selitteet (constantly selitteet-vastauksessa))))

(defn rakenna-henkilo [ilmoitus henkiloavain]
  (let [henkilo (henkiloavain ilmoitus)]
    (-> ilmoitus
        (update-in [henkiloavain] dissoc :matkapuhelin)
        (update-in [henkiloavain] dissoc :tyopuhelin)
        (update-in [henkiloavain] dissoc :sahkoposti)
        (assoc-in [henkiloavain :matkapuhelinnumero] (:matkapuhelin henkilo))
        (assoc-in [henkiloavain :tyopuhelinnumero] (:tyopuhelin henkilo))
        (assoc-in [henkiloavain :email] (:sahkoposti henkilo)))))

(defn rakenna-ilmoitus [ilmoitus]
  {:ilmoitus (-> ilmoitus
                 rakenna-selitteet
                 (rakenna-henkilo :ilmoittaja)
                 (rakenna-henkilo :lahettaja)
                 rakenna-sijanti)})