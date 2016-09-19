(ns harja-laadunseuranta.sovellus
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.projektiot :as p]
            [harja-laadunseuranta.utils :as utils])
  (:require-macros [reagent.ratom :refer [reaction run!]]))

(def sovelluksen-alkutila
  {:tallennus-kaynnissa false
   :tallennustilaa-muutetaan false
   :kayttajanimi nil
   :kayttajatunnus nil
   :sijainti {:nykyinen nil
              :edellinen nil}
   :palautettava-tarkastusajo nil
   :tarkastusajo nil
   :tarkastustyyppi nil
   :kirjaamassa-havaintoa false
   :kirjaamassa-yleishavaintoa false
   :reittipisteet []
   :kirjauspisteet []  ;; ikoneita varten
   :lahettamattomia 0
   :pikavalinta nil
   :kuva nil
   :tr-alku nil
   :tr-loppu nil
   :kitkan-keskiarvo nil
   :lumimaara nil
   :tasaisuus nil
   :kiinteys nil
   :polyavyys nil
   :alustettu false
   :gps-tuettu false
   :nayta-kiinteistorajat false
   :nayta-ortokuva false
   :tr-tiedot-nakyvissa false
   :tr-tiedot {:tr-osoite {:tie 20
                           :aosa 1
                           :aet 1}
               :talvihoitoluokka 2}
   :pikahavainnot {}
   :keskita-ajoneuvoon false
   :virheet []
   :ilmoitukset []
   :vakiohavaintojen-kuvaukset nil
   :idxdb nil})

(defonce sovellus (atom sovelluksen-alkutila))

(def vakiohavaintojen-kuvaukset (reagent/cursor sovellus [:vakiohavaintojen-kuvaukset]))
(def palautettava-tarkastusajo (reagent/cursor sovellus [:palautettava-tarkastusajo]))

(def kitkan-keskiarvo (reagent/cursor sovellus [:kitkan-keskiarvo]))
(def lumimaara (reagent/cursor sovellus [:lumimaara]))
(def tasaisuus (reagent/cursor sovellus [:tasaisuus]))
(def kiinteys (reagent/cursor sovellus [:kiinteys]))
(def polyavyys (reagent/cursor sovellus [:polyavyys]))

(def tr-tiedot-nakyvissa (reagent/cursor sovellus [:tr-tiedot-nakyvissa]))
(def tr-tiedot (reagent/cursor sovellus [:tr-tiedot]))

(def tr-alku (reagent/cursor sovellus [:tr-alku]))
(def tr-loppu (reagent/cursor sovellus [:tr-loppu]))

(def hoitoluokka (reagent/cursor sovellus [:tr-tiedot :talvihoitoluokka]))
(def soratiehoitoluokka (reagent/cursor sovellus [:tr-tiedot :soratiehoitoluokka]))

(def lahettamattomia (reagent/cursor sovellus [:lahettamattomia]))

(def kayttajanimi (reagent/cursor sovellus [:kayttajanimi]))
(def kayttajatunnus (reagent/cursor sovellus [:kayttajatunnus]))

(def kirjaamassa-havaintoa (reagent/cursor sovellus [:kirjaamassa-havaintoa]))
(def kirjaamassa-yleishavaintoa (reagent/cursor sovellus [:kirjaamassa-yleishavaintoa]))

(def gps-tuettu (reagent/cursor sovellus [:gps-tuettu]))

(def tr-osoite (reagent/cursor sovellus [:tr-tiedot :tr-osoite]))

(def pikavalinta (reagent/cursor sovellus [:pikavalinta]))

(def alustus-valmis (reaction (let [sovellus @sovellus]
                                (and (utils/tuettu-selain?)
                                     (:gps-tuettu sovellus)
                                     (:idxdb sovellus)
                                     (:kayttajanimi sovellus)))))

(def sovellus-alustettu (reagent/cursor sovellus [:alustettu]))

(def kirjauspisteet (reagent/cursor sovellus [:kirjauspisteet]))

(def sijainti (reagent/cursor sovellus [:sijainti]))
(def tarkastusajo (reagent/cursor sovellus [:tarkastusajo]))
(def tarkastustyyppi (reagent/cursor sovellus [:tarkastustyyppi]))

(def tyhja-sijainti
  {:lat 0
   :lon 0
   :heading 0
   })

(def ajoneuvon-sijainti (reaction
                         (if (:nykyinen @sijainti)
                           (:nykyinen @sijainti)
                           tyhja-sijainti)))

(def kartan-keskipiste (reaction @ajoneuvon-sijainti))

(def kuva (reagent/cursor sovellus [:kuva]))
(def tallennus-kaynnissa (reagent/cursor sovellus [:tallennus-kaynnissa]))
(def virheet (reagent/cursor sovellus [:virheet]))
(def ilmoitukset (reagent/cursor sovellus [:ilmoitukset]))

(def nayta-kiinteistorajat (reagent/cursor sovellus [:nayta-kiinteistorajat]))
(def nayta-ortokuva (reagent/cursor sovellus [:nayta-ortokuva]))

(def keskita-ajoneuvoon (reagent/cursor sovellus [:keskita-ajoneuvoon]))

(def karttaoptiot (reaction {:seuraa-sijaintia (or @tallennus-kaynnissa @keskita-ajoneuvoon)
                             :nayta-kiinteistorajat @nayta-kiinteistorajat
                             :nayta-ortokuva @nayta-ortokuva}))

(def havainnot (reagent/cursor sovellus [:pikahavainnot]))

(def reittisegmentti (reaction
                      (let [{:keys [nykyinen edellinen]} @sijainti]
                        (when (and nykyinen edellinen)
                          {:segmentti [(p/latlon-vektoriksi edellinen)
                                       (p/latlon-vektoriksi nykyinen)]
                           :vari (let [s @havainnot]
                                   (cond
                                     (:liukasta s) "blue"
                                     (:lumista s) "blue"
                                     (:soratie s) "brown"
                                     (:tasauspuute s) "green"
                                     (:yleishavainto s) "red"
                                     :default "black"))}))))

(def reittipisteet (reagent/cursor sovellus [:reittipisteet]))

(def liukkaus-kaynnissa (reagent/cursor sovellus [:pikahavainnot :liukasta]))
(def lumisuus-kaynnissa (reagent/cursor sovellus [:pikahavainnot :lumista]))
(def tasauspuute-kaynnissa (reagent/cursor sovellus [:pikahavainnot :epatasaista]))
(def yleishavainto-kaynnissa (reagent/cursor sovellus [:pikahavainnot :yleishavainto]))

(def idxdb (reagent/cursor sovellus [:idxdb]))

(def sijainnin-tallennus-mahdollinen (reaction (and @idxdb @tarkastusajo)))

(def tallennustilaa-muutetaan (reagent/cursor sovellus [:tallennustilaa-muutetaan]))

(def tarkastusajo-paattymassa (reaction (and @tallennustilaa-muutetaan
                                             @tarkastusajo
                                             @tarkastustyyppi)))

(def nayta-sivupaneeli (reaction (and @tarkastusajo
                                      @tarkastustyyppi
                                      (not @tarkastusajo-paattymassa)
                                      (not @kirjaamassa-havaintoa))))


(def tarkastusajo-luotava (reaction (and @tallennustilaa-muutetaan
                                         (nil? @tarkastusajo)
                                         (nil? @tarkastustyyppi))))


(defn tarkastusajo-seis [sovellus]
  (assoc sovellus
         :tallennus-kaynnissa false
         :tallennustilaa-muutetaan false
         :tarkastustyyppi nil
         :tarkastusajo nil))

(defn tarkastusajo-kayntiin [sovellus tarkastustyyppi ajo-id]
  (assoc sovellus
         :tallennustilaa-muutetaan false
         :tarkastustyyppi tarkastustyyppi
         :tarkastusajo ajo-id
         :reittipisteet []
         :kirjauspisteet []
         :tallennus-kaynnissa true))

(defn tarkastusajo-kayntiin! [tarkastustyyppi ajo-id]
  (swap! sovellus #(tarkastusajo-kayntiin % tarkastustyyppi ajo-id)))

(defn tarkastusajo-seis! []
  (swap! sovellus tarkastusajo-seis))

(def beta-kayttajat #{"A018983" "K870689"})

(defn kesatarkastus-beta? []
  (beta-kayttajat @kayttajatunnus))
