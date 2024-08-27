(ns harja.palvelin.integraatiot.api.talvihoitoreitit
  "Talvihoitoreittien lisäys API:n kautta"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE]]
            [clojure.string :refer [join]]
            [harja.kyselyt.konversio :as konv]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-tarkastukselle]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.sanomat.tielupa-sanoma :as tielupa-sanoma]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as liitteet]
            [harja.domain.tielupa :as tielupa]
            [harja.kyselyt.tielupa-kyselyt :as tielupa-q]
            [harja.kyselyt.kayttajat :as kayttajat-q]
            [harja.kyselyt.tieverkko :as tieverkko-q]
            [harja.kyselyt.talvihoitoreitit :as talvihoitoreitit-q]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]])
  (:import (java.util Date)))

(defn hae-sijainti [db sijainti]
  (let [parametrit {:tie (::tielupa/tie sijainti)
                    :aosa (::tielupa/aosa sijainti)
                    :aet (::tielupa/aet sijainti)
                    :losa (::tielupa/losa sijainti)
                    :loppuet (::tielupa/let sijainti)}
        geometria (if (and (:losa parametrit) (:loppuet parametrit))
                    (tieverkko-q/tierekisteriosoite-viivaksi db parametrit)
                    (tieverkko-q/tierekisteriosoite-pisteeksi db parametrit))]
    (assoc sijainti ::tielupa/geometria geometria)))

(defn- tarkista-datan-oikeellisuus
  [tielupa]
  (cond
    (some #(= (::tielupa/laite %) "Muuntamo") (::tielupa/kaapeliasennukset tielupa))
    (if (pvm/jalkeen? (java.util.Date. (.getTime (::tielupa/myontamispvm tielupa)))
          (pvm/luo-pvm 2015 0 1))
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+tieluvan-data-vaarin+
                          :viesti (str "Kaapeliasennusluvalle merkattu laitteeksi 'Muuntamo' vaikka myöntämispäivämäärä (" (pvm/pvm (java.util.Date. (.getTime (::tielupa/myontamispvm tielupa)))) ") on merkattu 1.1.2015 jälkeen")}]})
      tielupa)
    :else tielupa))

(defn hae-tieluvan-sijainnit [db tielupa]
  (let [sijainnit (::tielupa/sijainnit tielupa)]
    (if (empty? sijainnit)
      tielupa
      (assoc tielupa ::tielupa/sijainnit (map #(hae-sijainti db %) sijainnit)))))

(defn hae-sijainnit-avaimella [db avain tielupa]
  (assoc tielupa avain (mapv #(hae-sijainti db %) (get tielupa avain))))

;; Tieluvilla on enemmän ely-keskuksia kuin Harjassa muuten
(defn hae-ely [db ely tielupa]
  (let [ely-numero (case ely
                     "Uusimaa" 1
                     "Keski-Suomi" 9
                     "Lappi" 14
                     "Etelä-Pohjanmaa" 10
                     "Pohjois-Pohjanmaa" 12
                     "Kaakkois-Suomi" 3
                     "Varsinais-Suomi" 2
                     "Pohjois-Savo" 8
                     "Pirkanmaa" 4
                     "Ahvenanmaa" 16
                     "Etelä-Savo" 7
                     "Häme" 4
                     "Kainuu" 14
                     "Pohjanmaa" 12
                     "Pohjois-Karjala" 9
                     "Satakunta" 3
                     (throw+ {:type virheet/+viallinen-kutsu+
                              :virheet [{:koodi virheet/+tuntematon-ely+
                                         :viesti (str "Tuntematon ELY " ely)}]}))
        ely-id (:id (first (kayttajat-q/hae-ely-numerolla-tielupaa-varten db ely-numero)))]
    (assoc tielupa ::tielupa/ely ely-id)))

(defn lisaa-talvihoitoreitti [db data kayttaja parametrit]
  (println "lisaa-talvihoitoreitti :: parametrit" (pr-str parametrit))
  (println "lisaa-talvihoitoreitti :: data" (pr-str data))
  (println "lisaa-talvihoitoreitti :: kayttaja" (pr-str kayttaja))
  (validointi/tarkista-urakka-ja-kayttaja db (konv/konvertoi->int (:id parametrit)) kayttaja)
  (let [urakka_id (konv/konvertoi->int (:id parametrit))
        kayttaja_id (konv/konvertoi->int (:id kayttaja))
        _ (println "lisaa-talvihoitoreitti :: parametrit" (pr-str parametrit))
        _ (println "lisaa-talvihoitoreitti :: data" (pr-str data))
        ;; Tallenna talvihoitoreitin perustiedot
        talvihoitoreitti-id (:id (talvihoitoreitit-q/lisaa-talvihoitoreitti<! db {:nimi (:reittinimi data)
                                                                                  :urakka_id urakka_id
                                                                                  :kayttaja_id kayttaja_id}))
        ;; Lisää kalustot
        _ (doseq [kalusto (:kalusto data)]
          (talvihoitoreitit-q/lisaa-kalusto-talvihoitoreitille<! db
            {:talvihoitoreitti_id talvihoitoreitti-id
             :maara (:kalusto-lkm kalusto)
             :kalustotyyppi (:kalustotyyppi kalusto)}))
        ;; Lisää reitit
        _ (doseq [reitti (:reitti data)]
          (talvihoitoreitit-q/lisaa-reitti-talvihoitoreitille<! db
            {:talvihoitoreitti_id talvihoitoreitti-id
             :tie (:tie reitti)
             :alkuosa (:aosa reitti)
             :alkuetaisyys (:aet reitti)
             :loppuosa (:losa reitti)
             :loppuetaisyys (:let reitti)
             :pituus (:pituus reitti)
             :hoitoluokka (:hoitoluokka reitti)}))
        _ (println "lisaa-talvihoitoreitti :: luodun reitin id" (pr-str talvihoitoreitti-id))]

    )

  (tee-kirjausvastauksen-body {:ilmoitukset "Talvihoitoreitti kirjattu onnistuneesti"}))

(defrecord Talvihoitoreitit []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-talvihoitoreitti
      (POST "/api/urakat/:id/talvihoitoreitti" request
        (kasittele-kutsu db integraatioloki :lisaa-talvihoitoreitti request
          json-skeemat/talvihoitoreitti-kirjaus-request
          json-skeemat/kirjausvastaus
          (fn [parametrit data kayttaja db]
            (lisaa-talvihoitoreitti db data kayttaja parametrit))
          :kirjoitus)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-talvihoitoreitti)
    this))
