(ns harja.palvelin.integraatiot.reimari.toimenpidehaku
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.integraatiot.reimari.sanomat.hae-toimenpiteet :as sanoma]
            [harja.pvm :as pvm]
            [clojure.string :as s]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.tyokalut.lukot :as lukko]))

(defn kasittele-vastaus [body]
  (log/debug "kasittele-vastaus" body))

(defn hae-toimenpiteet* [konteksti db pohja-url kayttajatunnus salasana muutosaika]

  (let [otsikot {"Content-Type" "application/xml; charset=utf-8"}
        http-asetukset {:metodi :POST
                        :url (str pohja-url "/hae-toimenpiteet")
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana
                        :muutosaika muutosaika}
        {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
    (integraatiotapahtuma/lisaa-tietoja konteksti (str "Haetaan uudet toimenpiteet alkaen " muutosaika))
    (kasittele-vastaus body headers)))



(defn- formatoi-aika [muutosaika]
  (let [aika-ilman-vyohyketta (xml/formatoi-xsd-datetime muutosaika)]
    (if (s/ends-with? aika-ilman-vyohyketta "Z")
      aika-ilman-vyohyketta
      (str aika-ilman-vyohyketta "Z"))))

(defn kysely-sanoma [muutosaika]
  (xml/tee-xml-sanoma
   [:soap:Envelope {:xmlns:soap "http://schemas.xmlsoap.org/soap/envelope/"}
    [:soap:Body
     [:HaeToimenpiteet {:xmlns "http://www.liikennevirasto.fi/xsd/harja/reimari"}
      [:HaeToimenpiteetRequest {:muutosaika (formatoi-aika muutosaika)}]]
     ]]))

(defn edellisen-integraatiotapahtuman-alkuaika [db]
  ;; tähän kysely joka katsoo integraatiotapahtumat-tauusta edellisen
  ;; haetoimenpiteet-tapahtuman alkuajan
  #inst "2017-05-01")

(defn hae-toimenpiteet [db integraatioloki pohja-url kayttajatunnus salasana]
  (let [muutosaika (edellisen-integraatiotapahtuman-alkuaika db)]
    (lukko/yrita-ajaa-lukon-kanssa
     db "reimari-hae-toimenpiteet"
     (fn []
       (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "reimari" "hae-toimenpiteet"
        #(hae-toimenpiteet* % db pohja-url kayttajatunnus salasana muutosaika))))))
