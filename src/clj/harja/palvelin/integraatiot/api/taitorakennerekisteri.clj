(ns harja.palvelin.integraatiot.api.taitorakennerekisteri
  "Taitorakennerekisterin endpointit"
  (:require [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [GET]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-get-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.parametrit :as parametrit]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.kyselyt.taitorakennerekisteri :as taitorakennerekisteri-kyselyt]
            [harja.kyselyt.konversio :as konversio]
            [harja.pvm :as pvm]
            [harja.domain.siltatarkastus :as siltadomain]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [throw+]])
  (:import (java.text SimpleDateFormat)))

(s/def ::alkuaika #(and (string? %) (>= (count %) 20) (or
                                                        (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muoto) %))
                                                        (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muotoZ) %)))))
(s/def ::loppuaika #(and (string? %) (>= (count %) 20) (or
                                                         (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muoto) %))
                                                         (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muotoZ) %)))))

(defn- tarkista-haun-parametrit [parametrit]
  (try
    (s/valid? ::loppuaika (:loppuaika parametrit))
    (s/valid? ::alkuaika (:alkuaika parametrit))
    (parametrivalidointi/tarkista-parametrit
      parametrit
      {:alkuaika "Alkuaika puuttuu"
       :loppuaika "Loppuaika puuttuu"})
    (catch Exception e
      (log/error "Virhe Taitorakennerekisteri-api kutsussa:" e)
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+puutteelliset-parametrit+
                          :viesti "Poikkeus annetuissa parametreissa. Anna päivämäärät muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-01T00:00:00+03"}]})))
  (when (not (s/valid? ::alkuaika (:alkuaika parametrit)))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Alkuaika väärässä muodossa: %s Anna muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-01T00:00:00+03" (:alkuaika parametrit))}))
  (when (not (s/valid? ::loppuaika (:loppuaika parametrit)))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Loppuaika väärässä muodossa: %s Anna muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-01T00:00:00+03" (:loppuaika parametrit))})))

(defn muodosta-urakka-tiedot [tarkastus]
  {:harja-id (:urakka_id tarkastus)
   :tunnus (:urakka_tunnus tarkastus)
   :nimi (:urakka_nimi tarkastus)})

(defn muodosta-silta-tiedot [tarkastus]
  {:harja-id (:silta_id tarkastus)
   :tunnus (:siltatunnus tarkastus)
   :oid (:trex_oid tarkastus)
   :siltaid (:siltaid tarkastus)})

(defn muodosta-havainnot [kohde]
  (when-let [tulos (:tulos kohde)]
    [{:toimenpidetarpeet (map (fn [koodi]
                                {:toimenpidetarve
                                 {:koodi koodi
                                  :arvo (siltadomain/koodi->arvo koodi)
                                  :kuvaus (siltadomain/koodi->kuvaus koodi)}})
                           tulos)
      :lisatieto (:lisatieto kohde)
      :kuvat (when-let [liitteet (:liitteet kohde)]
               (map (fn [liite]
                      {:kuva {:harja-id (:liite_id liite)
                              :liite-oid (:liite_oid liite)
                              :nimi (:nimi liite)
                              :tiedostotyyppi (:tyyppi liite)
                              :koko (:koko liite)
                              :kuvaus (:kuvaus liite)
                              :url "/url/placeholder/"
                              :pikkukuva nil}})
                 liitteet))}]))

(defn muodosta-tarkastuskohteet [kohteet-json]
  (when kohteet-json
    (let [kohteet (konversio/jsonb->clojuremap kohteet-json)]
      (map (fn [kohde]
             {:kohde
              {:tyyppi-id (:kohde_id kohde)
               :tyyppi (siltadomain/siltatarkastuskohteen-tyyppi (:kohde_id kohde))
               :nimi (siltadomain/siltatarkastuskohteen-nimi (:kohde_id kohde))
               :paarakenneosa (siltadomain/kohde-id->paarakenneosa (:kohde_id kohde))
               :havainnot (muodosta-havainnot kohde)}})
        kohteet))))

(defn hae-siltatarkastukset
  "Hakee siltatarkastukset annettujen alku- ja loppuajan puitteissa."
  [db {:keys [alkuaika loppuaika] :as parametrit} _kayttaja]
  (log/info "Taitorakennerekisteri API, siltatarkastusten haku, parametrit: " (pr-str parametrit))
  (tarkista-haun-parametrit parametrit)
  
  (let [alku-timestamp (pvm/rajapinta-str-aika->sql-timestamp alkuaika)
        loppu-timestamp (pvm/rajapinta-str-aika->sql-timestamp loppuaika)
        
        siltatarkastukset (taitorakennerekisteri-kyselyt/hae-siltatarkastukset-taitorakennerekisterille
                           db {:alkuaika alku-timestamp
                               :loppuaika loppu-timestamp})
          
        muunnetut-tarkastukset (map (fn [tarkastus]
                                      {:siltatarkastus
                                       {:harja-id (:siltatarkastus_id tarkastus)
                                        :tarkastusaika (when (:tarkastusaika tarkastus)
                                                         (pvm/aika-iso8601-aikavyohykkeen-kanssa (:tarkastusaika tarkastus)))
                                        :tarkastaja (:tarkastaja tarkastus)
                                        :luotu (when (:luotu tarkastus)
                                                 (pvm/aika-iso8601-aikavyohykkeen-kanssa (:luotu tarkastus)))
                                        :muokattu (when (:muokattu tarkastus)
                                                    (pvm/aika-iso8601-aikavyohykkeen-kanssa (:muokattu tarkastus)))
                                        :poistettu (boolean (:poistettu tarkastus))
                                        :urakka (muodosta-urakka-tiedot tarkastus)
                                        :silta (muodosta-silta-tiedot tarkastus)
                                        :tarkastuskohteet (muodosta-tarkastuskohteet (:tarkastuskohteet tarkastus))}})
                                 siltatarkastukset)]
    
    {:siltatarkastukset muunnetut-tarkastukset}))

(defrecord Taitorakennerekisteri []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :hae-siltatarkastukset
      (GET "/api/taitorakennerekisteri/siltatarkastukset/:alkuaika/:loppuaika" parametrit
        (kasittele-get-kutsu db integraatioloki :hae-siltatarkastukset parametrit
          json-skeemat/+taitorakennerekisteri-siltatarkastukset-haku-vastaus+
          (fn [parametrit kayttaja db]
            (hae-siltatarkastukset db parametrit kayttaja))
          :taitorakenne "trex")))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-siltatarkastukset)
    this))
