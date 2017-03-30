(ns harja.domain.tietyoilmoitukset
  (:require [clojure.string :as str]))

(def kaistajarjestelyt
  {"ajokaistaSuljettu" "Ajokaista suljettu"
   "ajorataSuljettu" "Ajorata suljettu"
   "tieSuljettu" "Tie suljettu"})

(def tyotyyppi-vaihtoehdot-tienrakennus
  [["Alikulkukäytävän rak." "Alikulkukäytävän rakennus"]
   ["Kevyenliik. väylän rak." "Kevyenliikenteenväylän rakennus"]
   ["Tienrakennus" "Tienrakennus"]])

(def tyotyyppi-vaihtoehdot-huolto
  [["Tienvarsilaitteiden huolto" "Tienvarsilaitteiden huolto"]
   ["Vesakonraivaus/niittotyö" "Vesakonraivaus / niittotyö"]
   ["Rakenteen parannus" "Rakenteen parannus"]
   ["Tutkimus/mittaus" "Tutkimus / mittaus"]])

(def tyotyyppi-vaihtoehdot-asennus
  [
   ["Jyrsintä-/stabilointityö" "Jyrsintä- / stabilointityö"]
   ["Kaapelityö" "Kaapelityö"]
   ["Kaidetyö" "Kaidetyö"]
   ["Päällystystyö" "Päällystystyö"]
   ["Räjäytystyö" "Räjäytystyö"]
   ["Siltatyö" "Siltatyö"]
   ["Tasoristeystyö" "Tasoristeystyö"]
   ["Tiemerkintätyö" "Tiemerkintätyö"]
   ["Valaistustyö" "Valaistustyö"]])

(def tyotyyppi-vaihtoehdot-muut [["Liittymä- ja kaistajärj." "Liittymä- ja kaistajärjestely"]
                                 ["Silmukka-anturin asent." "Silmukka-anturin asentaminen"]
                                 ["Viimeistely" "Viimeistely"]
                                 ["Muu, mikä?" "Muu, mikä?"]])

(def tyotyyppi-vaihtoehdot-map (into {} (concat
                                          tyotyyppi-vaihtoehdot-tienrakennus
                                          tyotyyppi-vaihtoehdot-huolto
                                          tyotyyppi-vaihtoehdot-asennus
                                          tyotyyppi-vaihtoehdot-muut)))

(def kaistajarjestelyt-vaihtoehdot-map {"ajokaistaSuljettu" "Yksi ajokaista suljettu"
                                        "ajorataSuljettu" "Yksi ajorata suljettu"
                                        "tieSuljettu" "Tie suljettu"
                                        "muu" "Muu, mikä"})

(def vaikutussuunta-vaihtoehdot-map {"molemmat" "Haittaa molemmissa ajosuunnissa"
                                     "tienumeronKasvusuuntaan" "Tienumeron kasvusuuntaan"
                                     "vastenTienumeronKasvusuuntaa" "Vasten tienumeron kasvusuuntaa"})

(defn kaistajarjestelyt->str [t]
  (->> t
      ::kaistajarjestelyt
      ::jarjestely
      kaistajarjestelyt))

(defn nopeusrajoitukset->str [t]
  (str/join
    ", "
    (map
      (fn [n]
        (str (::nopeusrajoitus n) "km/h "
             (when (::matka n)
               (str " (" (::matka n) " metriä)"))))
      (::nopeusrajoitukset t))))

(defn tyoajat->str [t]
  (str (::tyoajat t)))

(defn tyotyypit->str [t]
  (str/join ", " (map ::tyyppi (::tyotyypit t))))

(defn- pinnat->str [avain t]
  (str/join ", " (map (fn [n]
                        (str (::materiaali n)
                             (when (::matka n)
                               (str " (" (::matka n) " metriä)"))))
                      (avain t))))

(def tienpinnat->str (partial pinnat->str ::tienpinnat))
(def kiertotienpinnat->str (partial pinnat->str ::kiertotienpinnat))

(defn ajoneuvorajoitukset->str [t]
  (str (::ajoneuvorajoitukset t)))

(defn huomautukset->str [t]
  (str (::huomautukset t)))

(defn ajoittaiset-pysatykset->str [t]
  (str (::ajoittaiset-pysatykset t)))

(defn ajoittain-suljettu-tie->str [t]
  (str (::ajoittain-suljettu-tie t)))

(defn- henkilo->str [henkilo]
  (str (::etunimi henkilo) " " (::sukunimi henkilo)))

(defn ilmoittaja->str [t]
  (henkilo->str (::ilmoittaja t)))

(defn- yhteyshenkilo->str [h]
  (str
    (::etunimi h) " "
    (::sukunimi h) ", "
    (::matkapuhelin h) ", "
    (::sahkoposti h)))

(defn urakoitsijayhteyshenkilo->str [t]
  (yhteyshenkilo->str (::urakoitsijayhteyshenkilo t)))

(defn tilaajayhteyshenkilo->str [t]
  (yhteyshenkilo->str (::tilaajayhteyshenkilo t)))