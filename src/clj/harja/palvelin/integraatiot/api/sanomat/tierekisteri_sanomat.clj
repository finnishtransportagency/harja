(ns harja.palvelin.integraatiot.api.sanomat.tierekisteri-sanomat
  (:require [harja.tyokalut.xml :as xml]))

;; Muokkaa tietuetta siten, että se vastaa json skemaa
;; Esimerkiksi koordinaatteja ja linkkejä ei ole toistaiseksi tarkoituskaan laittaa eteenpäin,
;; vaan ne ovat 'future prooffausta'. Näiden poistaminen payloadista on kasattu tänne, jotta JOS joskus halutaankin
;; palauttaa esim koordinaatit, ei tarvi kuin poistaa niiden dissoccaaminen täältä.
;; Tietueille ja tietueelle tehdään myös muita samankaltaisia operaatiota, esim :tietue -> :varuste uudelleennimeäminen,
;; mutta näitä operaatioita ei tehdä täällä em. syystä.
(def puhdista-tietue-xf
  #(-> %
       (update-in [:tietue] dissoc :kuntoluokka :urakka :piiri)
       (update-in [:tietue :sijainti] dissoc :koordinaatit :linkki)
       (update-in [:tietue :sijainti :tie] dissoc :puoli :alkupvm :ajr)))

(defn luo-varusteen-lisayssanoma [otsikko kirjaaja data]
  {:lisaaja {:henkilo      (if (and (:etunimi kirjaaja) (:sukunimi kirjaaja))
                             (str (:etunimi kirjaaja) " " (:sukunimi kirjaaja))
                             "")
             :jarjestelma  (get-in otsikko [:lahettaja :jarjestelma])
             :organisaatio (get-in otsikko [:lahettaja :organisaatio :nimi])
             :yTunnus      (get-in otsikko [:lahettaja :organisaatio :ytunnus])}
   :tietue  {:tunniste    (get-in data [:varuste :tunniste])
             :alkupvm     (xml/json-date-time->xml-xs-date (get-in data [:toteuma :alkanut]))
             :loppupvm    (xml/json-date-time->xml-xs-date (get-in data [:toteuma :paattynyt]))
             :karttapvm   (xml/json-date-time->xml-xs-date (get-in data [:varuste :karttapvm]))
             :piiri       (get-in data [:varuste :piiri])
             :kuntoluokka (get-in data [:varuste :kuntoluokitus])
             :urakka      (get-in data [:varuste :tierekisteriurakkakoodi])
             :sijainti    {:tie {:numero   (get-in data [:varuste :sijainti :tie :numero])
                                 :aet      (get-in data [:varuste :sijainti :tie :aet])
                                 :aosa     (get-in data [:varuste :sijainti :tie :aosa])
                                 :let      (get-in data [:varuste :sijainti :tie :let])
                                 :losa     (get-in data [:varuste :sijainti :tie :losa])
                                 :ajr      (get-in data [:varuste :sijainti :tie :ajr])
                                 :puoli    (get-in data [:varuste :sijainti :tie :puoli])
                                 :alkupvm  (xml/json-date-time->xml-xs-date
                                             (get-in data [:varuste :sijainti :tie :alkupvm]))
                                 :loppupvm (xml/json-date-time->xml-xs-date
                                             (get-in data [:varuste :sijainti :tie :loppupvm]))}}
             :tietolaji   {:tietolajitunniste (get-in data [:varuste :tietolaji])
                           :arvot             (get-in data [:varuste :arvot])}}
   :lisatty (xml/json-date-time->xml-xs-date (get-in data [:toteuma :alkanut]))})

(defn luo-varusteen-paivityssanoma [otsikko kirjaaja varustetoteuma]
  {:paivittaja {:henkilo      (if (and (:etunimi kirjaaja) (:sukunimi kirjaaja))
                                (str (:etunimi kirjaaja) " " (:sukunimi kirjaaja))
                                "")
                :jarjestelma  (get-in otsikko [:lahettaja :jarjestelma])
                :organisaatio (get-in otsikko [:lahettaja :organisaatio :nimi])
                :yTunnus      (get-in otsikko [:lahettaja :organisaatio :ytunnus])}
   :tietue     {:tunniste    (get-in varustetoteuma [:varuste :tunniste])
                :alkupvm     (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:toteuma :alkanut]))
                :loppupvm    (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:toteuma :paattynyt]))
                :karttapvm   (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:varuste :karttapvm]))
                :piiri       (get-in varustetoteuma [:varuste :piiri])
                :kuntoluokka (get-in varustetoteuma [:varuste :kuntoluokitus])
                :urakka      (get-in varustetoteuma [:varuste :tierekisteriurakkakoodi])
                :sijainti    {:tie {:numero   (get-in varustetoteuma [:varuste :sijainti :tie :numero])
                                    :aet      (get-in varustetoteuma [:varuste :sijainti :tie :aet])
                                    :aosa     (get-in varustetoteuma [:varuste :sijainti :tie :aosa])
                                    :let      (get-in varustetoteuma [:varuste :sijainti :tie :let])
                                    :losa     (get-in varustetoteuma [:varuste :sijainti :tie :losa])
                                    :ajr      (get-in varustetoteuma [:varuste :sijainti :tie :ajr])
                                    :puoli    (get-in varustetoteuma [:varuste :sijainti :tie :puoli])
                                    :alkupvm  (xml/json-date-time->xml-xs-date
                                                (get-in varustetoteuma [:varuste :sijainti :tie :alkupvm]))
                                    :loppupvm (xml/json-date-time->xml-xs-date
                                                (get-in varustetoteuma [:varuste :sijainti :tie :loppupvm]))}}
                :tietolaji   {:tietolajitunniste (get-in varustetoteuma [:varuste :tietolaji])
                              :arvot             (get-in varustetoteuma [:varuste :arvot])}}

   :paivitetty (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:toteuma :alkanut]))})

(defn luo-varusteen-poistosanoma [otsikko kirjaaja varustetoteuma]
  {:poistaja          {:henkilo      (if (and (:etunimi kirjaaja) (:sukunimi kirjaaja))
                                       (str (:etunimi kirjaaja) " " (:sukunimi kirjaaja))
                                       "")
                       :jarjestelma  (get-in otsikko [:lahettaja :jarjestelma])
                       :organisaatio (get-in otsikko [:lahettaja :organisaatio :nimi])
                       :yTunnus      (get-in otsikko [:lahettaja :organisaatio :ytunnus])}
   :tunniste          (get-in varustetoteuma [:varuste :tunniste])
   :tietolajitunniste (get-in varustetoteuma [:varuste :tietolaji])
   :poistettu         (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:toteuma :alkanut]))})

(defn luo-tietueen-lisayssanoma [data]
  (-> data
      (assoc :tietue (:varuste data))
      (assoc-in [:lisaaja :henkilo] (str (get-in data [:lisaaja :henkilo :etunimi])
                                         " "
                                         (get-in data [:lisaaja :henkilo :sukunimi])))
      (assoc-in [:lisaaja :jarjestelma] (get-in data [:otsikko :lahettaja :jarjestelma]))
      (assoc-in [:lisaaja :yTunnus] (get-in data [:otsikko :lahettaja :organisaatio :ytunnus]))
      (assoc-in [:tietue :alkupvm] (xml/json-date-time->xml-xs-date (get-in data [:varuste :alkupvm])))
      (assoc-in [:tietue :loppupvm] (xml/json-date-time->xml-xs-date (get-in data [:varuste :loppupvm])))
      (assoc-in [:tietue :karttapvm] (xml/json-date-time->xml-xs-date (get-in data [:varuste :karttapvm])))
      (assoc-in [:tietue :sijainti :tie :alkupvm] (xml/json-date-time->xml-xs-date
                                                    (get-in data [:varuste :sijainti :tie :alkupvm])))
      (assoc :lisatty (xml/json-date-time->xml-xs-date (:lisatty data)))
      (dissoc :otsikko)
      (dissoc :varuste)))

(defn luo-tietueen-paivityssanoma [data]
  (-> data
      (assoc :tietue (:varuste data))
      (assoc-in [:paivittaja :henkilo] (str (get-in data [:paivittaja :henkilo :etunimi])
                                            " "
                                            (get-in data [:paivittaja :henkilo :sukunimi])))
      (assoc-in [:paivittaja :jarjestelma] (get-in data [:otsikko :lahettaja :jarjestelma]))
      (assoc-in [:paivittaja :yTunnus] (get-in data [:otsikko :lahettaja :organisaatio :ytunnus]))
      (assoc-in [:tietue :alkupvm] (xml/json-date-time->xml-xs-date (get-in data [:varuste :alkupvm])))
      (assoc-in [:tietue :loppupvm] (xml/json-date-time->xml-xs-date (get-in data [:varuste :loppupvm])))
      (assoc-in [:tietue :karttapvm] (xml/json-date-time->xml-xs-date (get-in data [:varuste :karttapvm])))
      (assoc-in [:tietue :sijainti :tie :alkupvm] (xml/json-date-time->xml-xs-date
                                                    (get-in data [:varuste :sijainti :tie :alkupvm])))
      (assoc :paivitetty (xml/json-date-time->xml-xs-date (:paivitetty data)))
      (dissoc :otsikko)
      (dissoc :varuste)))

(defn luo-tietueen-poistosanoma [data]
  {:poistaja          {:henkilo      (str (get-in data [:poistaja :henkilo :etunimi])
                                          " "
                                          (get-in data [:poistaja :henkilo :sukunimi]))
                       :jarjestelma  (get-in data [:otsikko :lahettaja :jarjestelma])
                       :organisaatio (get-in data [:otsikko :lahettaja :organisaatio :nimi])
                       :yTunnus      (get-in data [:otsikko :lahettaja :organisaatio :ytunnus])}
   :tunniste          (:tunniste data)
   :tietolajitunniste (:tietolajitunniste data)
   :poistettu         (xml/json-date-time->xml-xs-date (:poistettu data))})

(defn luo-tierekisteriosoite [parametrit]
  (into {} (filter val {:numero  (get parametrit "numero")
                        :aet     (get parametrit "aet")
                        :aosa    (get parametrit "aosa")
                        :let     (get parametrit "let")
                        :losa    (get parametrit "losa")
                        :ajr     (get parametrit "ajr")
                        :puoli   (get parametrit "puoli")
                        :alkupvm (get parametrit "alkupvm")})))

(defn muunna-tietolajin-hakuvastaus [vastausdata ominaisuudet]
  (dissoc
    (dissoc (assoc-in vastausdata [:tietolaji :ominaisuudet]
                      (map (fn [o]
                             {:ominaisuus o})
                           ominaisuudet)) :onnistunut)
    :tietueet))

(defn muunna-tietueiden-hakuvastaus [vastausdata]
  (-> vastausdata
      (dissoc :onnistunut)
      (update-in [:tietueet] #(map puhdista-tietue-xf %))
      (update-in [:tietueet] #(into [] (remove nil? (remove empty? %))))
      (update-in [:tietueet] (fn [tietue]
                               (map #(clojure.set/rename-keys % {:tietue :varuste}) tietue)))
      (clojure.set/rename-keys {:tietueet :varusteet})))