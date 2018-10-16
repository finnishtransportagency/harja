(ns harja.palvelin.komponentit.sonja-test
  (:require [harja.palvelin.asetukset :as asetukset]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.tloik.tyokalut :as tloik-tk]
            [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.core.async :as a :refer [<!! <! go-loop timeout alts!!]]
            [org.httpkit.client :as http]
            [com.stuartsierra.component :as component]

            [harja.palvelin.main :as sut]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]))

(defonce asetukset {:sonja {:url "tcp://localhost:61616"
                            :kayttaja "harja"
                            :salasana "harjaxx"
                            :tyyppi :activemq}})

(def ^:dynamic *sonja-yhteys* nil)

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        #_#_:http-palvelin (testi-http-palvelin)
                        :sonja (sonja/luo-oikea-sonja (:sonja asetukset))
                        :integraatioloki (component/using (integraatioloki/->Integraatioloki nil)
                                                          [:db])
                        :sonja-sahkoposti (component/using
                                            (sahkoposti/luo-sahkoposti "foo@example.com"
                                                                       {:sahkoposti-sisaan-jono "email-to-harja"
                                                                        :sahkoposti-ulos-jono "harja-to-email"
                                                                        :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                                            [:sonja :db :integraatioloki])
                        #_#_:labyrintti (feikki-labyrintti)
                        :tloik (component/using
                                 (tloik-tk/luo-tloik-komponentti)
                                 [:db :sonja :integraatioloki :klusterin-tapahtumat :sonja-sahkoposti])
                        #_#_:sampo (component/using
                                     (->Sampo +lahetysjono-sisaan+ +kuittausjono-sisaan+ +lahetysjono-ulos+ +kuittausjono-ulos+ nil)
                                     [:db :sonja :integraatioloki])
                        #_#_:db-replica (tietokanta/luo-tietokanta testitietokanta)
                        :klusterin-tapahtumat (component/using
                                                (tapahtumat/luo-tapahtumat)
                                                [:db])
                        #_#_:sonja-jms-yhteysvarmistus (component/using
                                                         (let [{:keys [ajovali-minuutteina jono]} (:sonja-jms-yhteysvarmistus asetukset)]
                                                           (sonja-jms-yhteysvarmistus/->SonjaJmsYhteysvarmistus ajovali-minuutteina jono))
                                                         [:db :pois-kytketyt-ominaisuudet :integraatioloki :sonja :klusterin-tapahtumat])
                        #_#_:sahke (component/using
                                     (sahke/->Sahke +lahetysjono+ nil)
                                     [:db :sonja :integraatioloki])
                        #_#_:status (component/using
                                      (status/luo-status)
                                      [:http-palvelin :db :pois-kytketyt-ominaisuudet :db-replica :sonja])))))
  ;; aloita-sonja palauttaa kanavan.
  (binding [*sonja-yhteys* (sut/aloita-sonja jarjestelma)]
    (testit))
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(defn sonja-laheta [jonon-nimi sanoma]
  (let [options {:timeout 200
                 :basic-auth ["admin" "admin"]
                 :headers {"Content-Type" "application/xml"}
                 :body sanoma}
        {:keys [status error] :as response} @(http/post (str "http://localhost:8161/api/message/" jonon-nimi "?type=queue") options)]))

(defn sonja-laheta-odota [jonon-nimi sanoma]
  (let [kasitellyn-tapahtuman-id (fn []
                                   (not-empty
                                     (first (q (str "SELECT it.id "
                                                    "FROM integraatiotapahtuma it"
                                                    "  JOIN integraatioviesti iv ON iv.integraatiotapahtuma=it.id "
                                                    "WHERE iv.sisalto ILIKE('" (clojure.string/replace sanoma #"ä" "Ã¤") "') AND "
                                                    "it.paattynyt IS NOT NULL")))))]
    (sonja-laheta jonon-nimi sanoma)
    (<!!
      (go-loop [kasitelty? (kasitellyn-tapahtuman-id)
                aika 0]
        (if (or kasitelty? (> aika 10))
          kasitellyn-tapahtuman-id
          (do
            (<! (timeout 1000))
            (recur (kasitellyn-tapahtuman-id)
                   (inc aika))))))))

;;; Sonja komponentin testit
; - Käynnistyksen jälkeen tila-atomi näyttää oklta
; - Käynnistyy vaikka jokin sonjaa käyttävä komponentti feilaa
;   - Nähdään mitkä komponentit ei ole käynnissä
; - Jos Sonja ei käynnisty, sitä käyttävät komponentit käynnistyy
;   - Nähdään, että Sonja ei ole päällä
; - Jonossa näkyy viestit
; - Viestin käsittelijän lopetus
; - Viestin lähetys onnistuu

;;; Sonjaa käyttävien komponenttien testejä
; - Testataan, että ilmoitukset käsitellään

(deftest sonjan-kaynnistys
  (let [alkoiko-yhteys? (alts!! [*sonja-yhteys* (timeout 10000)])
        {sonja-asetukset :asetukset yhteys-future :yhteys-future yhteys-ok? :yhteys-ok? tila :tila} (:sonja jarjestelma)]
    (is alkoiko-yhteys? "Yhteys ei alkanut")
    (is (= (:sonja asetukset) sonja-asetukset))))

#_(deftest main-komponentit-loytyy
  (let [tapahtuma-id (sonja-laheta-odota "tloik-ilmoitusviestijono" (slurp "resources/xsd/tloik/esimerkit/ilmoitus.xml"))]))
