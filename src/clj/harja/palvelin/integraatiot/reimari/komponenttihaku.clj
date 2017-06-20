(ns harja.palvelin.integraatiot.reimari.komponenttihaku
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.alus :as vv-alus]
            [harja.domain.vesivaylat.komponenttityyppi :as komponenttityyppi]
            [harja.domain.vesivaylat.turvalaitekomponentti :as turvalaitekomponentti]
            [harja.domain.vesivaylat.turvalaite :as turvalaite]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.integraatiot.reimari.sanomat.hae-komponenttityypit :as kt-sanoma]
            [harja.palvelin.integraatiot.reimari.sanomat.hae-turvalaitekomponentit :as tl-sanoma]
            [harja.pvm :as pvm]
            [clojure.java.jdbc :as jdbc]
            [specql.core :as specql]
            [clojure.string :as s]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [clojure.set :refer [rename-keys]]))

(defn kasittele-komponenttityypit-vastaus [db vastaus-xml]
  (log/debug "kasittele-komponenttityypit-vastaus" vastaus-xml)
  (let [sanoman-tiedot (kt-sanoma/lue-hae-komponenttityypit-vastaus vastaus-xml)
        kanta-tiedot (for [toimenpide-tiedot sanoman-tiedot]
                       (specql/upsert! db ::komponenttityyppi/komponenttityyppi toimenpide-tiedot))]
    (vec kanta-tiedot)))

(defn kasittele-turvalaitekomponentit-vastaus [db vastaus-xml]
  (log/debug "kasittele-turvalaitekomponentit-vastaus" vastaus-xml)
  (let [sanoman-tiedot (tl-sanoma/lue-hae-turvalaitekomponentit-vastaus vastaus-xml)
        kanta-tiedot (for [toimenpide-tiedot sanoman-tiedot]
                       (specql/upsert! db ::turvalaitekomponentti/turvalaitekomponentti toimenpide-tiedot))]
    (vec kanta-tiedot)))

(defn- formatoi-aika [muutosaika]
  (let [aika-ilman-vyohyketta (xml/formatoi-xsd-datetime muutosaika)]
    (if (s/ends-with? aika-ilman-vyohyketta "Z")
      aika-ilman-vyohyketta
      (str aika-ilman-vyohyketta "Z"))))

(defn komponenttityypit-kysely-sanoma [muutosaika]
  (xml/tee-xml-sanoma
   [:soap:Envelope {:xmlns:soap "http://schemas.xmlsoap.org/soap/envelope/"}
    [:soap:Body
     [:HaeKomponenttiTyypit {:xmlns "http://www.liikennevirasto.fi/xsd/harja/reimari"}
      [:HaeKomponenttiTyypitRequest {:muutosaika (formatoi-aika muutosaika)}]]
     ]]))

(defn turvalaitekomponentit-kysely-sanoma [muutosaika]
  (xml/tee-xml-sanoma
   [:soap:Envelope {:xmlns:soap "http://schemas.xmlsoap.org/soap/envelope/"}
    [:soap:Body
     [:HaeTurvalaitekomponentit {:xmlns "http://www.liikennevirasto.fi/xsd/harja/reimari"}
      [:HaeTurvalaitekomponentitRequest {:muutosaika (formatoi-aika muutosaika)}]]
     ]]))

(defn hae-komponenttityypit* [konteksti db pohja-url kayttajatunnus salasana muutosaika]
  (let [otsikot {"Content-Type" "text/xml"
                 "SOAPAction" "http://www.liikennevirasto.fi/xsd/harja/reimari/HaeKomponenttiTyypit"}
        http-asetukset {:metodi :POST
                        :url pohja-url
                        :otsikot otsikot
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana
                        :muutosaika muutosaika}
        {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset (komponenttityypit-kysely-sanoma muutosaika))]
    (integraatiotapahtuma/lisaa-tietoja konteksti (str "Haetaan uudet komponenttityypit alkaen " muutosaika))
    (kasittele-komponenttityypit-vastaus db body)))

(defn hae-turvalaitekomponentit* [konteksti db pohja-url kayttajatunnus salasana muutosaika]
  (let [otsikot {"Content-Type" "text/xml"
                 "SOAPAction" "http://www.liikennevirasto.fi/xsd/harja/reimari/HaeTurvalaiteKomponentit"}
        http-asetukset {:metodi :POST
                        :url pohja-url
                        :otsikot otsikot
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana
                        :muutosaika muutosaika}
        {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset (turvalaitekomponentit-kysely-sanoma muutosaika))]
    (integraatiotapahtuma/lisaa-tietoja konteksti (str "Haetaan uudet turvalaitekomponentit alkaen " muutosaika))
    (kasittele-turvalaitekomponentit-vastaus db body)))

(defn edellisen-integraatiotapahtuman-alkuaika [db jarjestelma nimi]
  (last (sort-by ::integraatiotapahtuma/alkanut
                 (specql/fetch db ::integraatiotapahtuma/tapahtuma
                               #{::integraatiotapahtuma/id ::integraatiotapahtuma/alkanut
                                 [::integraatiotapahtuma/integraatio #{:harja.palvelin.integraatiot/nimi
                                                                      :harja.palvelin.integraatiot/jarjestelma}] }
                               {::integraatiotapahtuma/integraatio {:harja.palvelin.integraatiot/jarjestelma jarjestelma
                                                                   :harja.palvelin.integraatiot/nimi nimi}}))))

(defn hae-komponenttityypit [db integraatioloki pohja-url kayttajatunnus salasana]
  (let [muutosaika (edellisen-integraatiotapahtuman-alkuaika db "hae-komponenttityypit" "reimari")]
    (if-not muutosaika
      (log/info "Reimarin toimenpidehaku: ei löytynyt edellistä toimenpiteiden hakuaikaa, hakua ei tehdä")
      (lukko/yrita-ajaa-lukon-kanssa
       db "reimari-hae-komponenttityypit"
       (fn []
         (integraatiotapahtuma/suorita-integraatio
          db integraatioloki "reimari" "hae-komponenttityypit"
          #(hae-komponenttityypit* % db pohja-url kayttajatunnus salasana muutosaika)))))))


(defn hae-turvalaitekomponentit [db integraatioloki pohja-url kayttajatunnus salasana]
  (let [muutosaika (edellisen-integraatiotapahtuman-alkuaika db "reimari" "hae-turvalaitekomponentit")]
    (if-not muutosaika
      (log/info "Reimarin toimenpidehaku: ei löytynyt edellistä toimenpiteiden hakuaikaa, hakua ei tehdä")
      (lukko/yrita-ajaa-lukon-kanssa
       db "reimari-hae-turvalaitekomponentit"
       (fn []
         (integraatiotapahtuma/suorita-integraatio
          db integraatioloki "reimari" "hae-turvalaitekomponentit"
          #(hae-turvalaitekomponentit* % db pohja-url kayttajatunnus salasana muutosaika)))))))
