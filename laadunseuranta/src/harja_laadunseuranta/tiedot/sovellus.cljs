(ns harja-laadunseuranta.tiedot.sovellus
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.projektiot :as p]
            [harja-laadunseuranta.utils :as utils])
  (:require-macros [reagent.ratom :refer [reaction run!]]))

(def sovelluksen-alkutila
  {;; Sovelluksen alustustiedot
   :alustus {:alustettu? false
             :gps-tuettu nil
             :idxdb-tuettu nil
             :ensimmainen-sijainti nil ; Estää sovelluksen käytön jos GPS ei toimi oikein
             :verkkoyhteys? (.-onLine js/navigator)
             :selain-tuettu? (utils/tuettu-selain?)
             :kayttaja-tunnistettu nil
             :selain-vanhentunut? (utils/vanhentunut-selain?)}

   ;; Tarkastusajon perustiedot
   :tarkastusajo-alkamassa? false ; Käynnistysnappia painettu UI:sta
   :valittu-urakka-id nil ; Päättämisdialogissa valittu urakka-id
   :tarkastusajo-id nil ; Palvelinpään id tarkastusajo-taulussa
   :tarkastusajo-kaynnissa? false
   :palautettava-tarkastusajo nil ; TODO REFACTOR dokumentoi tämä
   :tarkastusajo-paattymassa? false ; Jos true, näytetään päättämisdialogi
   :tarkastusajon-paattamisvaihe nil ;; Mikä dialogi näytetään:
                                     ;; :paattamisvarmistus
                                     ;; :urakkavarmistus
                                     ;; :paatetaan
                                     ;; nil

   ;; Käyttäjätiedot
   :kayttaja {:kayttajanimi nil
              :kayttajatunnus nil
              :roolit #{}
              :oikeus-urakoihin nil ;; Urakat, joihin tarkastusoikeus, "sopivimmat" ensimmäisenä. nil kun haetaan, vector kun haettu
              :organisaatio nil}

   ;; Ajonaikaiset tiedot
   :lahettamattomia-merkintoja 0 ; Montako riviä idxdb:ssä on lähettämättä palvelimelle
   ;; GPS sijainti (EUREF-FIN kooordinaatistossa)
   :sijainti {:nykyinen nil
              :edellinen nil}

   ;; Viimeisimmän sijainnin perusteella haetut TR-tiedot
   :tr-tiedot {:tr-osoite {:tie nil
                           :aosa nil
                           :aet nil}
               :talvihoitoluokka nil}

   ;; UI
   :ui {:tr-tiedot-nakyvissa? false
        :paanavigointi {:nakyvissa? true
                        :valilehdet-nakyvissa? true
                        :valilehtiryhmat [] ; Näkyvien välilehtien määritykset {:avain ..., :nimi ... , :sisalto ...}
                        :valittu-valilehtiryhma 0 ;; Indeksi taulukossa valilehtiryhmat
                        :valittu-valilehti nil ; Välilehden avain
                        :hampurilaisvalikon-lista-nakyvissa? false}}

   ;; Havainnot
   :jatkuvat-havainnot #{} ; Tähän tallentuu välikohtaiset havainnot (esim. liukasta, lumista jne.).
                           ; Sama kuin UI:ssa alas painetut havaintonapit.

   ;; Mittaukset
   ;; Mittaustiedot kun kyseessä on "perusnäppäimistö"
   :mittaussyotto {:nykyinen-syotto nil ;; Arvo, jota ollaan syöttämässä (string)
                   :syotot []} ;; Aiemmin syötetyt arvot samassa mittauksessa
   ;; Mittaustiedot kun kyseessä on "erikoisnäppäimistö"
   :soratiemittaussyotto {:tasaisuus 5 ; tarkastuksen alkaessa ei vikoja (arvo 5)
                          :kiinteys 5
                          :polyavyys 5}
   :mittaustyyppi nil ;; Suoritettava mittaustyyppi (esim. :lumista) tai nil jos ei olla mittaamassa mitään

   ;; Lomake
   :havaintolomake-auki? false
   :kuvaa-otetaan? false ; Tulisi olla true silloin kun otetaan kuvaa (valitaan tiedostoa tai käytetään laitteen kameraa)
   :havaintolomakedata {:kayttajanimi nil
                        :aikaleima nil
                        :laadunalitus? false
                        :kuvaus ""
                        :kuva nil
                        :esikatselukuva nil
                        :liittyy-havaintoon nil ;; Jos liittyy johonkin aiempaan havaintoon, tässä on havainnon indexed db id.
                        :liittyy-varmasti-tiettyyn-havaintoon? false ;; Jos tultu esim Ilmoituksen kautta ja liitetään tiettyyn pikahavaintoon kuva/tekstiä
                        :tr-osoite {:tie nil
                                    :aosa nil
                                    :aet nil
                                    :losa nil
                                    :let nil}}

   :liittyvat-havainnot [] ;; Lista viimeisiä havaintoja, joihin lomake voidaan liittää
   ;; Item on map: {:id <indexeddb-id> :havainto-avain :lumista :aikaleima <aika> :tr-osoite <tr-osoite-mappi>}

   ;; Kartta
   :kirjauspisteet [] ; Kartalla näytettäviä ikoneita varten
   :reittipisteet [] ; Kartalle piirrettävä häntä mäppejä {:segmentti [[x1 y1] [x2 y2]] :vari html-vari}

   :kartta {:keskita-ajoneuvoon? false
            :nayta-kiinteistorajat? false
            :nayta-ortokuva? false}

   ;; Muut
   :ilmoitus nil ; Nykyinen näytettävä ilmoitus (jos ei käytetä ilmoitusjonoa)
   :ilmoitukseen-liittyva-havainto-id nil ; tarjoaa mahdollisuuden avata lomake ko. pikahavaintoon sidottuna
   :idxdb nil ; indexed db kahva
   :palvelinvirhe nil ; kuvaus palvelimen virheestä (string)
   })

(defonce sovellus (atom sovelluksen-alkutila))

;; Cursorit helpottamaan tilan muokkausta

(def palautettava-tarkastusajo (reagent/cursor sovellus [:palautettava-tarkastusajo]))

(def tr-tiedot (reagent/cursor sovellus [:tr-tiedot]))
(def tr-osoite (reagent/cursor sovellus [:tr-tiedot :tr-osoite]))
(def hoitoluokka (reagent/cursor sovellus [:tr-tiedot :talvihoitoluokka]))
(def soratiehoitoluokka (reagent/cursor sovellus [:tr-tiedot :soratiehoitoluokka]))

(def lahettamattomia-merkintoja (reagent/cursor sovellus [:lahettamattomia-merkintoja]))

(def kayttaja (reagent/cursor sovellus [:kayttaja]))
(def kayttajanimi (reagent/cursor sovellus [:kayttaja :kayttajanimi]))
(def kayttajatunnus (reagent/cursor sovellus [:kayttaja :kayttajatunnus]))
(def roolit (reagent/cursor sovellus [:kayttaja :roolit]))
(def organisaatio (reagent/cursor sovellus [:kayttaja :organisaatio]))

(def havaintolomake-auki? (reagent/cursor sovellus [:havaintolomake-auki?]))
(def kuvaa-otetaan? (reagent/cursor sovellus [:kuvaa-otetaan?]))
(def havaintolomakedata (reagent/cursor sovellus [:havaintolomakedata]))
(def havaintolomakkeeseen-liittyva-havainto (reagent/cursor sovellus [:havaintolomakedata :liittyy-havaintoon]))
(def liittyy-varmasti-tiettyyn-havaintoon? (reagent/cursor sovellus [:havaintolomakedata :liittyy-varmasti-tiettyyn-havaintoon?]))
(def liittyvat-havainnot (reagent/cursor sovellus [:liittyvat-havainnot]))
(def havaintolomake-kuva (reagent/cursor sovellus [:havaintolomakedata :kuva]))
(def havaintolomake-esikatselukuva (reagent/cursor sovellus [:havaintolomakedata :esikatselukuva]))

(def alustus-valmis? (reaction (let [sovellus @sovellus]
                                (boolean (and (get-in sovellus [:alustus :gps-tuettu])
                                              false ;; TODO POIS
                                              (get-in sovellus [:alustus :ensimmainen-sijainti])
                                              (get-in sovellus [:alustus :verkkoyhteys?])
                                              (get-in sovellus [:alustus :selain-tuettu?])
                                              (not (empty? (get-in sovellus [:kayttaja :oikeus-urakoihin])))
                                              (:idxdb sovellus)
                                              (get-in sovellus [:kayttaja :kayttajanimi]))))))

(def sovellus-alustettu (reagent/cursor sovellus [:alustus :alustettu?]))
(def verkkoyhteys (reagent/cursor sovellus [:alustus :verkkoyhteys?]))
(def selain-tuettu (reagent/cursor sovellus [:alustus :selain-tuettu?]))
(def selain-vanhentunut (reagent/cursor sovellus [:alustus :selain-vanhentunut?]))
(def gps-tuettu (reagent/cursor sovellus [:alustus :gps-tuettu]))
(def kayttaja-tunnistettu (reagent/cursor sovellus [:alustus :kayttaja-tunnistettu]))
(def idxdb-tuettu (reagent/cursor sovellus [:alustus :idxdb-tuettu]))
(def ensimmainen-sijainti (reagent/cursor sovellus [:alustus :ensimmainen-sijainti]))
(def oikeus-urakoihin (reagent/cursor sovellus [:kayttaja :oikeus-urakoihin]))

(def kirjauspisteet (reagent/cursor sovellus [:kirjauspisteet]))

(def sijainti (reagent/cursor sovellus [:sijainti]))
(def valittu-urakka-id (reagent/cursor sovellus [:valittu-urakka-id]))
(def tarkastusajo-id (reagent/cursor sovellus [:tarkastusajo-id]))
(def tarkastusajo-alkamassa? (reagent/cursor sovellus [:tarkastusajo-alkamassa?]))

(def tyhja-sijainti
  {:lat 0
   :lon 0
   :heading 0})

(def ajoneuvon-sijainti (reaction
                          (if (:nykyinen @sijainti)
                            @sijainti
                            tyhja-sijainti)))

(def kartan-keskipiste (reaction (:nykyinen @ajoneuvon-sijainti)))

(def tarkastusajo-kaynnissa? (reagent/cursor sovellus [:tarkastusajo-kaynnissa?]))
(def ilmoitus (reagent/cursor sovellus [:ilmoitus]))
(def ilmoitukseen-liittyva-havainto-id (reagent/cursor sovellus [:ilmoitukseen-liittyva-havainto-id]))

(def nayta-kiinteistorajat? (reagent/cursor sovellus [:kartta :nayta-kiinteistorajat?]))
(def nayta-ortokuva? (reagent/cursor sovellus [:kartta :nayta-ortokuva?]))
(def keskita-ajoneuvoon? (reagent/cursor sovellus [:kartta :keskita-ajoneuvoon?]))
(def karttaoptiot (reaction {:seuraa-sijaintia? (or @tarkastusajo-kaynnissa? @keskita-ajoneuvoon?)
                             :nayta-kiinteistorajat? @nayta-kiinteistorajat?
                             :nayta-ortokuva? @nayta-ortokuva?}))

(def jatkuvat-havainnot (reagent/cursor sovellus [:jatkuvat-havainnot]))
(def mittaustyyppi (reagent/cursor sovellus [:mittaustyyppi]))
(def mittaussyotto (reagent/cursor sovellus [:mittaussyotto]))
(def soratiemittaussyotto (reagent/cursor sovellus [:soratiemittaussyotto]))

;; Kartalle piirtoa varten
(def reittisegmentti
  ;; TODO REFACTOR, tee tästä run! blokki joka suoraan lisää segmentin
  ;; ja poista reitintallennus komponentista
  (reaction
    (let [{:keys [nykyinen edellinen]} @sijainti]
      (when (and nykyinen edellinen)
        {:segmentti [(p/latlon-vektoriksi edellinen)
                     (p/latlon-vektoriksi nykyinen)]
         :vari (let [s @jatkuvat-havainnot]
                 (cond
                   (:liukasta s) "blue"
                   (:lumista s) "blue"
                   (:tasauspuute s) "blue"

                   (:soratie s) "brown"

                   (:vesakko-raivaamatta s) "green"
                   (:niittamatta s) "green"

                   (:yleishavainto s) "red"
                   :default "black"))}))))

(def reittipisteet (reagent/cursor sovellus [:reittipisteet]))

(def idxdb (reagent/cursor sovellus [:idxdb]))
(def palvelinvirhe (reagent/cursor sovellus [:palvelinvirhe]))

(def sijainnin-tallennus-mahdollinen (reaction (and @idxdb @tarkastusajo-id)))

(def tarkastusajo-paattymassa? (reagent/cursor sovellus [:tarkastusajo-paattymassa?]))
(def tarkastusajon-paattamisvaihe (reagent/cursor sovellus [:tarkastusajon-paattamisvaihe]))

(def piirra-paanavigointi?
  (reaction (boolean (and @tarkastusajo-id
                          @tarkastusajo-kaynnissa?
                          (not @tarkastusajo-paattymassa?)
                          (not @havaintolomake-auki?)))))

(def nayta-paanavigointi? (reagent/cursor sovellus [:ui :paanavigointi :nakyvissa?]))
(def nayta-paanavigointi-valilehdet? (reagent/cursor sovellus [:ui :paanavigointi :valilehdet-nakyvissa?]))
(def paanavigoinnin-valilehtiryhmat (reagent/cursor sovellus [:ui :paanavigointi :valilehtiryhmat]))
(def paanavigoinnin-valittu-valilehtiryhma (reagent/cursor sovellus [:ui :paanavigointi :valittu-valilehtiryhma]))
(def paanavigoinnin-valittu-valilehti (reagent/cursor sovellus [:ui :paanavigointi :valittu-valilehti]))
(def paanavigoinnin-hampurilaisvalikon-lista-nakyvissa? (reagent/cursor sovellus [:ui :paanavigointi :hampurilaisvalikon-lista-nakyvissa?]))

;; Yleiset apufunktiot helpottamaan tilan muokkausta
;; Tänne mielellään vain sellaiset, joita tarvitsee käyttää useasta paikasta

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
