(ns harja.palvelin.integraatiot.api.sanomat.tierekisteri-sanomat
  (:require [harja.tyokalut.xml :as xml]))

(defn luo-varusteen-lisayssanoma [otsikko kirjaaja varustetoteuma]
  {:lisaaja {:henkilo      (if (and (:etunimi kirjaaja) (:sukunimi kirjaaja))
                             (str (:etunimi kirjaaja) " " (:sukunimi kirjaaja))
                             "")
             :jarjestelma  (get-in otsikko [:lahettaja :jarjestelma])
             :organisaatio (get-in otsikko [:lahettaja :organisaatio :nimi])
             :yTunnus      (get-in otsikko [:lahettaja :organisaatio :ytunnus])}
   :tietue  {:tunniste    (get-in varustetoteuma [:varuste :tunniste])
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
   :lisatty (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:toteuma :alkanut]))})

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