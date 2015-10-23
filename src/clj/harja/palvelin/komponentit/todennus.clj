(ns harja.palvelin.komponentit.todennus
  "Tämä namespace määrittelee käyttäjäidentiteetin todentamisen. Käyttäjän todentaminen WWW-palvelussa tehdään KOKA ympäristön antamilla header tiedoilla. Tämä komponentti ei huolehdi käyttöoikeuksista, vaan pelkästään tarkistaa käyttäjän identiteetin."
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.cache :as cache]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.kayttajat :as q]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.tapahtumat :refer [kuuntele!]]
            ))


;; Pidetään käyttäjätietoja muistissa vartti, jotta ei tarvitse koko ajan hakea tietokannasta uudestaan.
;; KOKA->käyttäjätiedot pitää hakea joka ikiselle HTTP pyynnölle.
(def kayttajatiedot (atom (cache/ttl-cache-factory {} :ttl (* 15 60 1000))))

(defn koka-remote-id->kayttajatiedot [db koka-remote-id]
  (get (swap! kayttajatiedot
              #(cache/through (fn [id]
                                (let [kt (first  (q/hae-kirjautumistiedot db id))]
                                  (if (nil? kt)
                                    nil
                                    (-> kt
                                        (assoc :organisaatio {:id (:org_id kt)
                                                              :nimi (:org_nimi kt)
                                                              :tyyppi (:org_tyyppi kt)}
                                               :roolit (into #{} (:roolit (konv/array->vec (first (q/hae-kayttajan-roolit db (:id kt))) :roolit)))
                                               :urakkaroolit (map konv/alaviiva->rakenne (q/hae-kayttajan-urakka-roolit db (:id kt))))
                                        (dissoc :org_id :org_nimi :org_tyyppi)))))
                              %
                              koka-remote-id))
       koka-remote-id))

  
(defprotocol Todennus
  "Protokolla HTTP pyyntöjen käyttäjäidentiteetin todentamiseen."
  (todenna-pyynto [this req] "Todenna annetun HTTP-pyynnön käyttäjätiedot, palauttaa uuden req mäpin, jossa käyttäjän tiedot on lisätty avaimella :kayttaja."))

(def todennusvirhe {:virhe :todennusvirhe})

(defrecord HttpTodennus []
  component/Lifecycle
  (start [this]
    (log/info "Todennetaan HTTP käyttäjä KOKA headereista.")
    (kuuntele! (:klusterin-tapahtumat this)
               :kayttaja-muokattu #(swap! kayttajatiedot cache/evict %))
    this)
  (stop [this]
    this)

  Todennus
  (todenna-pyynto [this req]
    (let [headerit (:headers req)
          kayttaja-id (headerit "oam_remote_user")]
      
      ;;(log/info "KOKA: " kayttaja-id)
      (if (nil? kayttaja-id)
        (throw+ todennusvirhe)
        (if-let [kayttajatiedot (koka-remote-id->kayttajatiedot (:db this) kayttaja-id)]
          (assoc req :kayttaja kayttajatiedot)
          (throw+ todennusvirhe))))))

(defrecord FeikkiHttpTodennus [kayttaja]
  component/Lifecycle
  (start [this]
    (log/warn "Käytetään FEIKKI käyttäjätodennusta, käyttäjä = " (pr-str kayttaja))
    this)
  (stop [this]
    this)

  Todennus
  (todenna-pyynto [this req]
    (assoc req
      :kayttaja kayttaja)))

(defn http-todennus []
  (->HttpTodennus))

(defn feikki-http-todennus [kayttaja]
  (->FeikkiHttpTodennus kayttaja))


