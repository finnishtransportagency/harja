(ns harja.palvelin.integraatiot.api.yhteystiedot
  "Urakan yhteystietojen hallinta"
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [compojure.core :refer [GET]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu-async tee-kirjausvastauksen-body]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-timestamp]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-kirjausvastauksen-body]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.kyselyt.urakat :as urakat]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.kyselyt.kayttajat :as kayttajat]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.sanomat.yhteystiedot :as yhteyshenkilot-vastaus])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn tarkista-kutsu [db kayttaja urakkanro]
  (when-not (kayttajat/liikenneviraston-jarjestelma? db (:kayttajanimi kayttaja))
    (log/error (format "Kayttajatunnuksela: %s ei ole oikeutta hakea urakan yhteystietoja." (:kayttajatunnus kayttaja)))
    (throw+ {:type virheet/+kayttajalla-puutteelliset-oikeudet+
             :virheet [{:koodi virheet/+kayttajalla-puutteelliset-oikeudet+
                        :viesti (format "Käyttäjällä ei oikeuksia urakkaan: %s" urakkanro)}]}))
  (when-not (urakat/onko-kaynnissa-urakkanro? db urakkanro)
    (log/warn (format "Yritettiin hakea yhteystiedot tuntemattomalle tai päättyneelle urakalle: %s." urakkanro))
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+tuntematon-urakka-koodi+
                        :viesti (format "Urakkanumerolla: %s ei löydy voimassa olevaa urakkaa Harjassa." urakkanro)}]})))

(defn hae-urakan-yhteystiedot [db fim {urakkanro :urakkanro} kayttaja]
  (log/debug (format "Haetaan urakan (urakkanro: %s) tiedot käyttäjälle: %s." urakkanro kayttaja))
  (tarkista-kutsu db kayttaja urakkanro)
  (let [urakan-tiedot (first (urakat/hae-kaynnissaoleva-urakka-urakkanumerolla db urakkanro))
        urakka-id (:id urakan-tiedot)
        harja-yhteyshenkilot (yhteyshenkilot/hae-urakan-yhteyshenkilot db urakka-id)
        harja-vastuuhenkilot (map #(if (:ensisijainen %)
                                     (assoc % :vastuuhenkilo true)
                                     (assoc % :varahenkilo true))
                                  (yhteyshenkilot/hae-urakan-vastuuhenkilot db urakka-id))

        fim-yhteyshenkilot (fim/hae-urakan-kayttajat fim (:sampoid urakan-tiedot))
        fim-yhteyshenkilot (map
                             (fn [fy]
                               (let [hy (first (filter (fn [hy] (= (:kayttajatunnus hy) (:kayttajatunnus fy)))
                                                       harja-vastuuhenkilot))]
                                 (assoc fy :vastuuhenkilo (:vastuuhenkilo hy)
                                           :varahenkilo (:varahenkilo hy))))
                             fim-yhteyshenkilot)]
    (yhteyshenkilot-vastaus/urakan-yhteystiedot urakan-tiedot fim-yhteyshenkilot harja-yhteyshenkilot)))

(defrecord Yhteystiedot []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki fim :fim :as this}]
    (julkaise-reitti
      http :hae-yhteystiedot
      (GET "/api/urakat/yhteystiedot/:urakkanro" request
        (kasittele-kutsu-async
          db
          integraatioloki
          :hae-yhteystiedot
          request
          nil
          json-skeemat/urakan-yhteystietojen-haku-vastaus
          (fn [parametit _ kayttaja db]
            (hae-urakan-yhteystiedot db fim parametit kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-yhteystiedot)
    this))
