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

(defn luo-varusteen-lisayssanoma [otsikko kirjaaja tunniste toimenpide arvot]
  {:lisaaja {:henkilo (if (and (:etunimi kirjaaja) (:sukunimi kirjaaja))
                        (str (:etunimi kirjaaja) " " (:sukunimi kirjaaja))
                        "")
             :jarjestelma (get-in otsikko [:lahettaja :jarjestelma])
             :organisaatio (get-in otsikko [:lahettaja :organisaatio :nimi])
             :yTunnus (get-in otsikko [:lahettaja :organisaatio :ytunnus])}
   :tietue {:tunniste tunniste
            :alkupvm (xml/json-date-time->xml-xs-date (get-in toimenpide [:varuste :tietue :alkupvm]))
            :sijainti {:tie {:numero (get-in toimenpide [:varuste :tietue :sijainti :tie :numero])
                             :aosa (get-in toimenpide [:varuste :tietue :sijainti :tie :aosa])
                             :aet (get-in toimenpide [:varuste :tietue :sijainti :tie :aet])
                             :let (get-in toimenpide [:varuste :tietue :sijainti :tie :let])
                             :losa (get-in toimenpide [:varuste :tietue :sijainti :tie :losa])
                             :ajr (get-in toimenpide [:varuste :tietue :sijainti :tie :ajr])
                             :puoli (get-in toimenpide [:varuste :tietue :sijainti :tie :puoli])
                             :tilannepvm (xml/json-date-time->xml-xs-date
                                           (get-in toimenpide [:varuste :tilannepvm]))}}
            :tietolaji {:tietolajitunniste (get-in toimenpide [:varuste :tietue :tietolaji :tunniste])
                        :arvot arvot}}
   :lisatty (xml/json-date-time->xml-xs-date (get-in toimenpide [:varuste :tietue :alkupvm]))})

(defn luo-varusteen-paivityssanoma [otsikko kirjaaja toimenpide arvot]
  {:paivittaja {:henkilo      (if (and (:etunimi kirjaaja) (:sukunimi kirjaaja))
                                (str (:etunimi kirjaaja) " " (:sukunimi kirjaaja))
                                "")
                :jarjestelma  (get-in otsikko [:lahettaja :jarjestelma])
                :organisaatio (get-in otsikko [:lahettaja :organisaatio :nimi])
                :yTunnus      (get-in otsikko [:lahettaja :organisaatio :ytunnus])}
   :tietue     {:tunniste    (get-in toimenpide [:varuste :tunniste])
                :alkupvm     (xml/json-date-time->xml-xs-date (get-in toimenpide [:varuste :tietue :alkupvm]))
                :loppupvm    (xml/json-date-time->xml-xs-date (get-in toimenpide [:varuste :tietue :loppupvm]))
                :karttapvm   (xml/json-date-time->xml-xs-date (get-in toimenpide [:varuste :tietue :karttapvm]))
                :piiri       (get-in toimenpide [:varuste :tietue :piiri])
                :kuntoluokka (get-in toimenpide [:varuste :tietue :kuntoluokka])
                :urakka      (get-in toimenpide [:varuste :tietue :tierekisteriurakkakoodi])
                :sijainti    {:tie {:numero   (get-in toimenpide [:varuste :tietue :sijainti :tie :numero])
                                    :aosa     (get-in toimenpide [:varuste :tietue :sijainti :tie :aosa])
                                    :aet      (get-in toimenpide [:varuste :tietue :sijainti :tie :aet])
                                    :let      (get-in toimenpide [:varuste :tietue :sijainti :tie :let])
                                    :losa     (get-in toimenpide [:varuste :tietue :sijainti :tie :losa])
                                    :ajr      (get-in toimenpide [:varuste :tietue :sijainti :tie :ajr])
                                    :puoli    (get-in toimenpide [:varuste :tietue :sijainti :tie :puoli])
                                    :tilannepvm (xml/json-date-time->xml-xs-date
                                                  (get-in toimenpide [:varuste :tilannepvm]))}}
                :tietolaji   {:tietolajitunniste (get-in toimenpide [:varuste :tietue :tietolaji :tunniste])
                              :arvot             arvot}}

   :paivitetty (xml/json-date-time->xml-xs-date (get-in toimenpide [:varuste :tietue :alkupvm]))})

(defn luo-varusteen-poistosanoma [otsikko kirjaaja toimenpide]
  {:poistaja          {:henkilo      (if (and (:etunimi kirjaaja) (:sukunimi kirjaaja))
                                       (str (:etunimi kirjaaja) " " (:sukunimi kirjaaja))
                                       "")
                       :jarjestelma  (get-in otsikko [:lahettaja :jarjestelma])
                       :organisaatio (get-in otsikko [:lahettaja :organisaatio :nimi])
                       :yTunnus      (get-in otsikko [:lahettaja :organisaatio :ytunnus])}
   :tunniste          (:tunniste toimenpide)
   :tietolajitunniste (:tietolajitunniste toimenpide)
   :poistettu         (xml/json-date-time->xml-xs-date (:poistettu toimenpide))})

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
      (assoc-in [:tietue :tilannepvm] (xml/json-date-time->xml-xs-date (get-in data [:varuste :tilannepvm])))
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
      (assoc-in [:tietue :tilannepvm] (xml/json-date-time->xml-xs-date (get-in data [:varuste :tilannepvm])))
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
  (into {} (filter val (zipmap [:numero :aet :aosa :let :losa :ajr :puoli :alkupvm]
                               (map (partial get parametrit) ["numero" "aet" "aosa" "let" "losa" "ajr" "puoli" "alkupvm"])))))

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
