(ns harja.domain.tietyoilmoitukset
  (:require [clojure.string :as str]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.roolit :as roolit]
    #?(:cljs [harja.tiedot.istunto :as istunto])))

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

(def kaistajarjestelyt-vaihtoehdot ["ajokaistaSuljettu" "ajorataSuljettu" "tieSuljettu" "muu"])
(def kaistajarjestelyt-vaihtoehdot-map {"ajokaistaSuljettu" "Yksi ajokaista suljettu"
                                        "ajorataSuljettu" "Yksi ajorata suljettu"
                                        "tieSuljettu" "Tie suljettu"
                                        "muu" "Muu, mikä"})

(def vaikutussuunta-vaihtoehdot-map {"molemmat" "Haittaa molemmissa ajosuunnissa"
                                     "tienumeronKasvusuuntaan" "Tienumeron kasvusuuntaan"
                                     "vastenTienumeronKasvusuuntaa" "Vasten tienumeron kasvusuuntaa"})

(def nopeusrajoitukset ["30" "40" "50" "60" "70" "80" "90" "100"])

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
        (str (::rajoitus n) "km/h "
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

(defn ajoittaiset-pysaytykset->str [t]
  (str (::ajoittaiset-pysaytykset t)))

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

(defn voi-tallentaa?
  #?(:cljs ([kayttajan-urakat ilmoitus] (voi-tallentaa? @istunto/kayttaja kayttajan-urakat ilmoitus)))
  ([user kayttajan-urakat ilmoitus]
   (or

     ;; Uuden luonti mahdollista, jos tilaaja tai urakka on oma (tai ei määritelty)
     (and (nil? (::id ilmoitus))
          (or (nil? (::urakka-id ilmoitus))
              (roolit/tilaajan-kayttaja? user)
              (kayttajan-urakat (::urakka-id ilmoitus))))

     ;; Muokkaaminen mahdollista, jos ilmoitus on itse luoma tai oman organisaatio
     ;; tai omaan urakkaan kuuluva
     (and (::id ilmoitus)
          (or (= (::muokkaustiedot/luoja-id ilmoitus) (:id user))
              (= (::urakoitsija-id ilmoitus) (get-in user [:organisaatio :id]))
              (= (::tilaaja-id ilmoitus) (get-in user [:organisaatio :id]))
              (kayttajan-urakat (::urakka-id ilmoitus)))))))
