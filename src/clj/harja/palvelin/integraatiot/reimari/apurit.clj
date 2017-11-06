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
            [harja.palvelin.tyokalut.lukot :as lukko]
            [harja.kyselyt.reimari-meta :as metatiedot-q]))


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


(defn hakuvali [db integraation-nimi]
  (first (metatiedot-q/hae-hakuvali db {:integraatio integraation-nimi})))

(defn kutsu-reimari-integraatiota* [{:keys [db pohja-url kayttajatunnus salasana alkuaika loppuaika muutosaika] :as hakuparametrit} konteksti]
  (let [otsikot {"Content-Type" "text/xml"
                 "SOAPAction" (:soap-action hakuparametrit)}
        http-asetukset {:metodi :POST
                        :url pohja-url
                        :otsikot otsikot
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana
                        :muutosaika muutosaika}
        _ (log/debug "aikaparametrit: " (or muutosaika [alkuaika loppuaika]))
        {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset ((:sanoma-fn hakuparametrit) (or muutosaika [alkuaika loppuaika])))]
    (integraatiotapahtuma/lisaa-tietoja konteksti (str "Haku: " (:haun-nimi hakuparametrit) " ajalta: " (or muutosaika [alkuaika loppuaika])))

    ((:vastaus-fn hakuparametrit) db body)
    (metatiedot-q/paivita-aikakursori! db {:integraatio (:haun-nimi hakuparametrit)
                                           :aika loppuaika})))

(defn ajat-5min-sisalla? [a b]
  (let [ero-millisekunteina (Math/abs (- (pvm/millisekunteina a) (pvm/millisekunteina b)))]
    (>= (* 5 60 1000) ero-millisekunteina)))

(defn kutsu-reimari-integraatiota
  [{:keys [db integraatioloki haun-nimi] :as hakuparametrit}]
  (let [{:keys [alku loppu]} (hakuvali db haun-nimi)
        vali-ok? (and alku loppu)
        vasta-haettu? (and vali-ok? (ajat-5min-sisalla? alku (pvm/nyt)))]
    (log/debug "vasta-haettu?" vasta-haettu? "vali-ok?" vali-ok?)
    (log/debug "ajat oli" alku (pvm/nyt))
    (if (or (not vali-ok?) vasta-haettu?)
      (if (not vali-ok?)
        (log/warn "Reimari-integraatio: ei löytynyt hakuvälitietoja" haun-nimi "-tapahtumalle, hakua ei tehdä.")
        (log/warn "Reimari-integraatio: ajettu alle 5 min sitten " haun-nimi "-tapahtumalle, hakua ei tehdä."))
      (lukko/yrita-ajaa-lukon-kanssa
       db (str haun-nimi)
       (fn yrita-ajaa-lukon-kanssa-callback []
         (integraatiotapahtuma/suorita-integraatio
          db integraatioloki "reimari" haun-nimi
          (fn suorita-intergraatio-callback [konteksti]
            (kutsu-reimari-integraatiota* (assoc hakuparametrit :alkuaika alku :loppuaika loppu) konteksti))))))))



;; käyttö:
;; (kutsu-interaktiivisesti hae-viat harja.palvelin.main/harja-jarjestelma #inst "2017-08-01T00:00:00" #inst "2017-09-01T00:00:00")
;; tai
;; (kutsu-interaktiivisesti hae-viat (assoc-in harja.palvelin.main/harja-jarjestelma  [:reimari :salasana] "asdf") #inst "2017-08-01T00:00:00" #inst "2017-09-01T00:00:00")
(defn kutsu-interaktiivisesti [fn j alkuaika loppuaika]
  (let [[db il rk] (as-> j x
                     (select-keys x [:db :integraatioloki :reimari])
                     (map second x))
        [kt ss pu] (as-> rk x
                     (select-keys x [:kayttajatunnus :salasana :pohja-url])
                     (map second x))]
    (with-redefs [hakuvali (constantly {:alku alkuaika :loppu loppuaika})]
      (fn db il pu kt ss))))


(defn kysely-sanoma [tyyppi attribuutit]

  (let [;; esim tyyppi = HaeKomponenttiTyypit -> :HaeKomponenttiTyypit ja :HaeKomponenttiTyypitRequest
        tyyppi-kw (keyword tyyppi)
        tyyppi-request-kw (keyword (str tyyppi "Request"))
        sanoma (xml/tee-xml-sanoma
                [:soap:Envelope {:xmlns:soap "http://schemas.xmlsoap.org/soap/envelope/"}
                 [:soap:Body
                  [tyyppi-kw {:xmlns "http://www.liikennevirasto.fi/xsd/harja/reimari"}
                   [tyyppi-request-kw attribuutit]]]])]
    sanoma))

(defn kysely-sanoma-aikavali [tyyppi [alkuaika loppuaika]]
  (kysely-sanoma tyyppi {:alkuaika (formatoi-aika alkuaika)
                         :loppuaika (formatoi-aika loppuaika)}))

(defn kysely-sanoma-muutosaika [tyyppi [alkuaika loppuaika]]
  (kysely-sanoma tyyppi {:muutosaika (formatoi-aika alkuaika)}))
