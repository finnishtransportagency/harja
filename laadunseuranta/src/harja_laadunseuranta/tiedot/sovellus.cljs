(ns harja-laadunseuranta.tiedot.sovellus
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.projektiot :as p]
            [harja-laadunseuranta.utils :as utils])
  (:require-macros [reagent.ratom :refer [reaction run!]]))

(def sovelluksen-alkutila
  {;; Sovelluksen alustustiedot
   :alustus {:alustettu false
             :gps-tuettu false
             :ensimmainen-sijainti nil ; alustusta varten
             :verkkoyhteys (.-onLine js/navigator)
             :selain-tuettu (utils/tuettu-selain?)}

   ;; Tarkastusajon perustiedot
   :valittu-urakka nil ; Urakka valitaan tietyntyyppisiin ajoihin (päällystys), muuten päätellään automaattisesti kun tarkastus päättyy
   :tarkastusajo-id nil
   :tallennus-kaynnissa false
   :palautettava-tarkastusajo nil
   :tarkastusajo-paattymassa nil ; Jos true, näytetään päättämisdialogi

   ;; Käyttäjätiedot
   :kayttaja {:kayttajanimi nil
              :kayttajatunnus nil}

   ;; Ajonaikaiset tiedot
   :lahettamattomia-merkintoja 0
   :sijainti {:nykyinen nil
              :edellinen nil}
   :reittipisteet []
   :tr-tiedot {:tr-osoite {:tie 20 ;; TODO Älä harkoodaa tätä?
                           :aosa 1
                           :aet 1}
               :talvihoitoluokka 2}

   ;; UI
   :tr-tiedot-nakyvissa false

   ;; Havainnot
   :jatkuvat-havainnot #{} ; Tähän tallentuu välikohtaiset havainnot (esim. liukasta, lumista jne.)

   ;; Mittaukset
   :mittaussyotto {:nykyinen-syotto nil ;; Arvo, jota ollaan syöttämässä (string)
                   :syotot []} ;; Aiemmin syötetyt arvot samassa mittauksessa
   :soratiemittaussyotto {:tasaisuus 5
                          :kiinteys 5
                          :polyavyys 5}
   :mitttaustyyppi nil ;; Suoritettava mittaustyyppi (esim. :lumista) tai nil jos ei olla mittaamassa mitään

   ;; Lomake

   :havaintolomake-auki? false
   :havaintolomake-data {}

   ;; Kartta
   :kirjauspisteet [] ; Kartalla näytettäviä ikoneita varten
   :kartta {:keskita-ajoneuvoon false
            :nayta-kiinteistorajat false
            :nayta-ortokuva false}

   ;; Muut
   :vakiohavaintojen-kuvaukset nil ; Serveriltä saadut tiedot vakiohavainnoista

   :ilmoitukset [] ;; Sisältää jonossa olevat ajastetut ilmoitukset
   :idxdb nil
   :palvelinvirhe nil})

(defonce sovellus (atom sovelluksen-alkutila))

;; Cursorit helpottamaan tilan muokkausta

(def vakiohavaintojen-kuvaukset (reagent/cursor sovellus [:vakiohavaintojen-kuvaukset]))
(def palautettava-tarkastusajo (reagent/cursor sovellus [:palautettava-tarkastusajo]))

(def tr-tiedot (reagent/cursor sovellus [:tr-tiedot]))
(def tr-tiedot-nakyvissa (reagent/cursor sovellus [:tr-tiedot-nakyvissa]))
(def tr-osoite (reagent/cursor sovellus [:tr-tiedot :tr-osoite]))
(def hoitoluokka (reagent/cursor sovellus [:tr-tiedot :talvihoitoluokka]))
(def soratiehoitoluokka (reagent/cursor sovellus [:tr-tiedot :soratiehoitoluokka]))

(def lahettamattomia-merkintoja (reagent/cursor sovellus [:lahettamattomia-merkintoja]))

(def kayttajanimi (reagent/cursor sovellus [:kayttaja :kayttajanimi]))
(def kayttajatunnus (reagent/cursor sovellus [:kayttaja :kayttajatunnus]))

(def havaintolomake-auki (reagent/cursor sovellus [:havaintolomake-auki?]))


(def alustus-valmis (reaction (let [sovellus @sovellus]
                                (and (get-in sovellus [:alustus :gps-tuettu])
                                     (get-in sovellus [:alustus :ensimmainen-sijainti])
                                     (get-in sovellus [:alustus :verkkoyhteys])
                                     (get-in sovellus [:alustus :selain-tuettu])
                                     (:idxdb sovellus)
                                     (get-in sovellus [:kayttaja :kayttajanimi])))))

(def sovellus-alustettu (reagent/cursor sovellus [:alustus :alustettu]))
(def verkkoyhteys (reagent/cursor sovellus [:alustus :verkkoyhteys]))
(def selain-tuettu (reagent/cursor sovellus [:alustus :selain-tuettu]))
(def gps-tuettu (reagent/cursor sovellus [:alustus :gps-tuettu]))
(def ensimmainen-sijainti (reagent/cursor sovellus [:alustus :ensimmainen-sijainti]))

(def kirjauspisteet (reagent/cursor sovellus [:kirjauspisteet]))

(def sijainti (reagent/cursor sovellus [:sijainti]))
(def valittu-urakka (reagent/cursor sovellus [:valittu-urakka]))
(def tarkastusajo-id (reagent/cursor sovellus [:tarkastusajo-id]))

(def tyhja-sijainti
  {:lat 0
   :lon 0
   :heading 0})

(def ajoneuvon-sijainti (reaction
                          (if (:nykyinen @sijainti)
                            (:nykyinen @sijainti)
                            tyhja-sijainti)))

(def kartan-keskipiste (reaction @ajoneuvon-sijainti))

(def tallennus-kaynnissa (reagent/cursor sovellus [:tallennus-kaynnissa]))
(def ilmoitukset (reagent/cursor sovellus [:ilmoitukset]))

(def nayta-kiinteistorajat (reagent/cursor sovellus [:kartta :nayta-kiinteistorajat]))
(def nayta-ortokuva (reagent/cursor sovellus [:kartta :nayta-ortokuva]))
(def keskita-ajoneuvoon (reagent/cursor sovellus [:kartta :keskita-ajoneuvoon]))
(def karttaoptiot (reaction {:seuraa-sijaintia (or @tallennus-kaynnissa @keskita-ajoneuvoon)
                             :nayta-kiinteistorajat @nayta-kiinteistorajat
                             :nayta-ortokuva @nayta-ortokuva}))

(def jatkuvat-havainnot (reagent/cursor sovellus [:jatkuvat-havainnot]))
(def mittaustyyppi (reagent/cursor sovellus [:mittaustyyppi]))
(def mittaussyotto (reagent/cursor sovellus [:mittaussyotto]))
(def soratiemittaussyotto (reagent/cursor sovellus [:soratiemittaussyotto]))

(def reittisegmentti (reaction
                       (let [{:keys [nykyinen edellinen]} @sijainti]
                         (when (and nykyinen edellinen)
                           {:segmentti [(p/latlon-vektoriksi edellinen)
                                        (p/latlon-vektoriksi nykyinen)]
                            :vari (let [s @jatkuvat-havainnot]
                                    (cond
                                      (:liukasta s) "blue"
                                      (:lumista s) "blue" ;; TODO Onko tämä oikein?
                                      (:soratie s) "brown"
                                      (:tasauspuute s) "green"
                                      (:yleishavainto s) "red"
                                      :default "black"))}))))

(def reittipisteet (reagent/cursor sovellus [:reittipisteet]))

(def idxdb (reagent/cursor sovellus [:idxdb]))
(def palvelinvirhe (reagent/cursor sovellus [:palvelinvirhe]))

(def sijainnin-tallennus-mahdollinen (reaction (and @idxdb @tarkastusajo-id)))

(def tarkastusajo-paattymassa (reagent/cursor sovellus [:tarkastusajo-paattymassa]))

(def nayta-paanavigointi? (reaction (boolean (and @tarkastusajo-id
                                                  @tallennus-kaynnissa
                                                  (not @tarkastusajo-paattymassa)
                                                  (not @havaintolomake-auki)))))

;; Apufunktiot helpottamaan tilan muokkausta

(defn aseta-mittaus-paalle! [uusi-mittaustyyppi]
  (reset! mittaustyyppi uusi-mittaustyyppi))

(defn aseta-mittaus-pois! []
  (.log js/console "Asetetaan mittaus pois")
  (reset! mittaussyotto {:nykyinen-syotto nil
                         :syotot []})
  (reset! soratiemittaussyotto {:tasaisuus 5
                                :kiinteys 5
                                :polyavyys 5})
  (reset! mittaustyyppi nil))

(defn valitse-urakka! [urakka]
  (reset! valittu-urakka urakka))

(defn lisaa-jatkuva-havainto! [avain]
  (reset! jatkuvat-havainnot (conj @jatkuvat-havainnot avain)))

(defn poista-jatkuva-havainto! [avain]
  (reset! jatkuvat-havainnot (into #{} (remove #(= avain %) @jatkuvat-havainnot))))

(defn poista-kaikki-jatkuvat-havainnot! []
  (aseta-mittaus-pois!)
  (reset! jatkuvat-havainnot #{}))

(defn togglaa-jatkuva-havainto!
  "Lisää jatkuvan havainnon, jos sitä ei ole. Jos on, poistaa sen."
  [avain]
  (if (avain @jatkuvat-havainnot)
    (poista-jatkuva-havainto! avain)
    (lisaa-jatkuva-havainto! avain)))

(defn lopeta-jatkuvan-havainnon-mittaus! [avain]
  (poista-jatkuva-havainto! avain)
  (aseta-mittaus-pois!))