(ns harja.palvelin.integraatiot.reimari.apurit
  (:require [clojure.string :as s]
            [specql.core :as specql]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.tyokalut.lukot :as lukko]))

(defn aikaleima [text]
  (when-not (str/blank? text)
    (.toDate (xml/parsi-xsd-datetime-ms-aikaleimalla text))))

(defn paivamaara [text]
  (when-not (str/blank? text)
    (xml/parsi-paivamaara text)))

(defn formatoi-aika [muutosaika]
  (let [aika-ilman-vyohyketta (xml/formatoi-xsd-datetime muutosaika)]
    (if (s/ends-with? aika-ilman-vyohyketta "Z")
      aika-ilman-vyohyketta
      (str aika-ilman-vyohyketta "Z"))))

(defn kutsu-reimari-integraatiota* [hakuparametrit konteksti db pohja-url kayttajatunnus salasana muutosaika]
  (let [otsikot {"Content-Type" "text/xml"
                 "SOAPAction" (:soap-action hakuparametrit)}
        http-asetukset {:metodi :POST
                        :url pohja-url
                        :otsikot otsikot
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana
                        :muutosaika muutosaika}
        {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset ((:sanoma-fn hakuparametrit) muutosaika))]
    (integraatiotapahtuma/lisaa-tietoja konteksti (str "Haku: " (:haun-nimi hakuparametrit) " alken: " muutosaika))
    ((:vastaus-fn hakuparametrit) db body)))

(defn kutsu-reimari-integraatiota [nimi haku-fn db integraatioloki pohja-url kayttajatunnus salasana]
  (let [muutosaika (edellisen-integraatiotapahtuman-alkuaika db "reimari" haun-nimi)]
    (if-not muutosaika
      (log/info "Reimarin vikahaku: ei löytynyt edellistä onnistunutta" haun-nimi "-tapahtumaa")
      (lukko/yrita-ajaa-lukon-kanssa
       db (str"reimari-" haun-nimi)
       (fn []
         (integraatiotapahtuma/suorita-integraatio
          db integraatioloki "reimari" haun-nimi
          (fn [konteksti] (haku-fn konteksti db pohja-url kayttajatunnus salasana muutosaika))))))))

(defn edellisen-integraatiotapahtuman-alkuaika [db jarjestelma nimi]
  (::integraatiotapahtuma/alkanut
   (last (sort-by ::integraatiotapahtuma/alkanut
                  (specql/fetch db ::integraatiotapahtuma/tapahtuma
                                #{::integraatiotapahtuma/id ::integraatiotapahtuma/alkanut
                                  [::integraatiotapahtuma/integraatio #{:harja.palvelin.integraatiot/nimi
                                                                        :harja.palvelin.integraatiot/jarjestelma}] }
                                {::integraatiotapahtuma/integraatio {:harja.palvelin.integraatiot/jarjestelma jarjestelma
                                                                     :harja.palvelin.integraatiot/nimi nimi}
                                 ::integraatiotapahtuma/onnistunut true })))))
