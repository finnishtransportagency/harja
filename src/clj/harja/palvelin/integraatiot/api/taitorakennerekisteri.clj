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
(s/def ::silta-oid #(and (string? %)
                     (seq %)
                     (re-matches #"^[0-9.]+$" %)))

(defn- tarkista-api-parametrit [parametrit tyyppi]
  (case tyyppi
    :aikavali (do
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

    :silta-oid (when (not (s/valid? ::silta-oid (:silta-oid parametrit)))
                (virheet/heita-viallinen-apikutsu-poikkeus
                  {:koodi virheet/+puutteelliset-parametrit+
                   :viesti (format "Silta-oid on väärässä muodossa. %s " (:silta-oid parametrit))})) 
    :default nil))

(defn muodosta-urakka-tiedot [tarkastus]
  {:harja-id (:urakka_id tarkastus)
   :tunnus (:urakka_tunnus tarkastus)
   :nimi (:urakka_nimi tarkastus)
   :hallintayksikko (:urakka_hallintayksikko tarkastus)})

(defn muodosta-silta-tiedot [tarkastus]
  {:harja-id (:silta_id tarkastus)
   :tunnus (:siltatunnus tarkastus)
   :oid (:silta_oid tarkastus)
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

(defn muodosta-harja-url [tarkastus ympariston-domain-nimi kehitysmoodi?]
  (when (and (:urakka_hallintayksikko tarkastus)
          (:urakka_id tarkastus)
          (:silta_id tarkastus)
          (:siltatarkastus_id tarkastus))
    (str (when-not kehitysmoodi? "https://")
      ympariston-domain-nimi
      "/#urakat/laadunseuranta/siltatarkastukset?&hy="
      (:urakka_hallintayksikko tarkastus)
      "&u=" (:urakka_id tarkastus)
      "&sil=" (:silta_id tarkastus)
      "&st=" (:siltatarkastus_id tarkastus))))

(defn hae-siltatarkastukset
  "Hakee siltatarkastukset annettujen alku- ja loppuajan puitteissa."
  [db {:keys [alkuaika loppuaika] :as parametrit} _kayttaja ympariston-domain-nimi kehitysmoodi]
  (log/info "Taitorakennerekisteri API, siltatarkastusten haku, parametrit: " (pr-str parametrit))
  (tarkista-api-parametrit parametrit :aikavali)
  
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
                                        :harja-url (muodosta-harja-url tarkastus ympariston-domain-nimi kehitysmoodi)
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

(defn onko-silta-olemassa?
  "Tarkistaa, onko silta olemassa silta_oid:n perusteella."
  [db silta-oid]
  (:exists (first (taitorakennerekisteri-kyselyt/loytyyko-silta-oidilla
                    db {:silta-oid silta-oid}))))

(defn hae-sillan-siltatarkastukset
  "Hakee siltatarkastukset sillalle sillan silta_oid:n perusteella."
  [db {:keys [silta-oid] :as parametrit} _kayttaja ympariston-domain-nimi kehitysmoodi]
  (log/info "Taitorakennerekisteri API, sillan siltatarkastusten haku, parametrit: " (pr-str parametrit))
  (tarkista-api-parametrit parametrit :silta-oid)

  (if-not (onko-silta-olemassa? db silta-oid)
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+tuntematon-silta+
                        :viesti (format "Siltaa ei löydy oid:lla %s" silta-oid)}]})
    (let [siltatarkastukset (taitorakennerekisteri-kyselyt/hae-sillan-siltatarkastukset-taitorakennerekisterille
                              db {:silta-oid silta-oid})
          muunnetut-tarkastukset (map (fn [tarkastus]
                                        {:siltatarkastus
                                         {:harja-id (:siltatarkastus_id tarkastus)
                                          :tarkastusaika (when (:tarkastusaika tarkastus)
                                                           (pvm/aika-iso8601-aikavyohykkeen-kanssa (:tarkastusaika tarkastus)))
                                          :tarkastaja (:tarkastaja tarkastus)
                                          :harja-url (muodosta-harja-url tarkastus ympariston-domain-nimi kehitysmoodi)
                                          :luotu (when (:luotu tarkastus)
                                                   (pvm/aika-iso8601-aikavyohykkeen-kanssa (:luotu tarkastus)))
                                          :muokattu (when (:muokattu tarkastus)
                                                      (pvm/aika-iso8601-aikavyohykkeen-kanssa (:muokattu tarkastus)))
                                          :poistettu (boolean (:poistettu tarkastus))
                                          :urakka (muodosta-urakka-tiedot tarkastus)
                                          :silta (muodosta-silta-tiedot tarkastus)
                                          :tarkastuskohteet (muodosta-tarkastuskohteet (:tarkastuskohteet tarkastus))}})
                                   siltatarkastukset)]
      {:siltatarkastukset muunnetut-tarkastukset})))

(defrecord Taitorakennerekisteri [ympariston-domain-nimi kehitysmoodi]
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :hae-siltatarkastukset
      (GET "/api/taitorakennerekisteri/siltatarkastukset/:alkuaika/:loppuaika" parametrit
        (kasittele-get-kutsu db integraatioloki :hae-siltatarkastukset parametrit
          json-skeemat/+taitorakennerekisteri-siltatarkastukset-haku-vastaus+
          (fn [parametrit kayttaja db]
            (hae-siltatarkastukset db parametrit kayttaja ympariston-domain-nimi kehitysmoodi))
          :taitorakenne "trex")))
    (julkaise-reitti
      http :hae-sillan-siltatarkastukset
      (GET "/api/taitorakennerekisteri/siltatarkastukset/:silta-oid" parametrit
        (kasittele-get-kutsu db integraatioloki :hae-sillan-siltatarkastukset parametrit
          json-skeemat/+taitorakennerekisteri-siltatarkastukset-haku-vastaus+
          (fn [parametrit kayttaja db]
            (hae-sillan-siltatarkastukset db parametrit kayttaja ympariston-domain-nimi kehitysmoodi))
          :taitorakenne "trex")))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http 
      :hae-siltatarkastukset
      :hae-sillan-siltatarkastukset)
    this))
