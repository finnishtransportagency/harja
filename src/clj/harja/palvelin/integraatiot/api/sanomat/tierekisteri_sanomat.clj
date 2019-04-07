(ns harja.palvelin.integraatiot.api.sanomat.tierekisteri-sanomat
  (:require [harja.tyokalut.xml :as xml]))

(defn luo-tietueen-lisayssanoma [otsikko tunniste toimenpide arvot]
  {:lisaaja {:henkilo (str (get-in toimenpide [:lisaaja :henkilo :etunimi])
                           " "
                           (get-in toimenpide [:lisaaja :henkilo :sukunimi]))
             :jarjestelma (get-in otsikko [:lahettaja :jarjestelma])
             :organisaatio (get-in otsikko [:lahettaja :organisaatio :nimi])
             :yTunnus (get-in otsikko [:lahettaja :organisaatio :ytunnus])}
   :tietue {:tunniste tunniste
            :alkupvm (xml/json-date-time->xml-xs-date (get-in toimenpide [:varuste :tietue :alkupvm]))
            :sijainti {:tie (merge
                              (get-in toimenpide [:varuste :tietue :sijainti :tie])
                              {:tilannepvm (xml/json-date-time->xml-xs-date
                                             (get-in toimenpide [:varuste :tilannepvm]))})}
            :tietolaji {:tietolajitunniste (get-in toimenpide [:varuste :tietue :tietolaji :tunniste])
                        :arvot arvot}}
   :lisatty (xml/json-date-time->xml-xs-date (get-in toimenpide [:varuste :tietue :alkupvm]))})

(defn luo-tietueen-paivityssanoma [otsikko toimenpide arvot]
  {:paivittaja {:henkilo (str (get-in toimenpide [:paivittaja :henkilo :etunimi])
                              " "
                              (get-in toimenpide [:paivittaja :henkilo :sukunimi]))
                :jarjestelma (get-in otsikko [:lahettaja :jarjestelma])
                :organisaatio (get-in otsikko [:lahettaja :organisaatio :nimi])
                :yTunnus (get-in otsikko [:lahettaja :organisaatio :ytunnus])}
   :tietue {:tunniste (get-in toimenpide [:varuste :tunniste])
            :alkupvm (xml/json-date-time->xml-xs-date (get-in toimenpide [:varuste :tietue :alkupvm]))
            :loppupvm (xml/json-date-time->xml-xs-date (get-in toimenpide [:varuste :tietue :loppupvm]))
            :karttapvm (xml/json-date-time->xml-xs-date (get-in toimenpide [:varuste :tietue :karttapvm]))
            :piiri (get-in toimenpide [:varuste :tietue :piiri])
            :kuntoluokka (get-in toimenpide [:varuste :tietue :kuntoluokka])
            :urakka (get-in toimenpide [:varuste :tietue :tierekisteriurakkakoodi])
            :sijainti {:tie (merge
                              (get-in toimenpide [:varuste :tietue :sijainti :tie])
                              {:tilannepvm (xml/json-date-time->xml-xs-date
                                             (get-in toimenpide [:varuste :tilannepvm]))})}
            :tietolaji {:tietolajitunniste (get-in toimenpide [:varuste :tietue :tietolaji :tunniste])
                        :arvot arvot}}

   :paivitetty (xml/json-date-time->xml-xs-date (get-in toimenpide [:varuste :tietue :alkupvm]))})

(defn luo-tietueen-poistosanoma [otsikko toimenpide]
  {:poistaja {:henkilo (str (get-in toimenpide [:poistaja :henkilo :etunimi])
                            " "
                            (get-in toimenpide [:poistaja :henkilo :sukunimi]))
              :jarjestelma (get-in otsikko [:lahettaja :jarjestelma])
              :organisaatio (get-in otsikko [:lahettaja :organisaatio :nimi])
              :yTunnus (get-in otsikko [:lahettaja :organisaatio :ytunnus])}
   :tunniste (:tunniste toimenpide)
   :tietolajitunniste (:tietolajitunniste toimenpide)
   :poistettu (xml/json-date-time->xml-xs-date (:poistettu toimenpide))})

(defn luo-tierekisteriosoite [parametrit]
  (into {} (filter val (zipmap [:numero :aet :aosa :let :losa :ajr :puoli :alkupvm]
                               (map (partial get parametrit) ["numero" "aet" "aosa" "let" "losa" "ajr" "puoli" "alkupvm"])))))

(defn muunna-tietolajin-hakuvastaus [vastausdata ominaisuudet]
  {:tietolajit [(dissoc
                  (dissoc (assoc-in vastausdata [:tietolaji :ominaisuudet]
                                    (map (fn [o]
                                           {:ominaisuus o})
                                         ominaisuudet)) :onnistunut)
                  :tietueet)]})

(defn muunna-tietolajien-hakuvastaus [vastausdata]
  {:tietolajit (remove #(empty? (get-in % [:tietolaji :ominaisuudet]))
                       (map (fn [t] {:tietolaji (assoc (:tietolaji t)
                                                  :ominaisuudet
                                                  (map (fn [o] {:ominaisuus o})
                                                       (get-in t [:tietolaji :ominaisuudet])))}) vastausdata))})
