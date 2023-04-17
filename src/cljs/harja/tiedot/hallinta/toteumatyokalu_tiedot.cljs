(ns harja.tiedot.hallinta.toteumatyokalu-tiedot
  "Toteumatyokalun ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [cljs.core.async :refer [<! >! chan close!]]
            [cljs-http.client :as http]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def +mahdolliset-urakat+
  [
   {:id 34 :nimi "Ivalon MHU testiurakka (uusi) (aseta sopimusid 19)"}
   {:id 35 :nimi "Oulun MHU 2019-2024 (Aseta sopimusid 42)"}
   ])

(def alkutila {:toteumatiedot {:tierekisteriosoite nil
                               :valittu-jarjestelma "Autoyksikkö Kolehmainen"
                               :valittu-urakka nil
                               :valittu-hallintayksikko nil
                               :lahetysaika (pvm/jsondate (pvm/nyt))
                               :ulkoinen-id 123
                               :suorittaja-nimi "Urakoitsija Oy"
                               :sopimusid 19
                               :valittu-materiaali nil
                               :yksikko "t"
                               :materiaalimaara 123
                               :nakymassa? false}
               :lahetys-kaynnissa? false
               :mahdolliset-urakat +mahdolliset-urakat+})
(def data (atom alkutila))
(def nakymassa? (atom false))

(def +mahdolliset-materiaalit+
  [{:id 1 :nimi "Talvisuolaliuos NaCl" :yksikko "t"}
   {:id 6 :nimi "Kaliumformiaattiliuos" :yksikko "t"}
   {:id 10 :nimi "Kesäsuola sorateiden kevätkunnostus" :yksikko "t"}
   {:id 11 :nimi "Kesäsuola sorateiden pölynsidonta" :yksikko "t"}])

(defn muodosta-reittipiste [x y app pisteiden-maara]
  {:reittipiste
   {:aika (get-in app [:toteumatiedot :lahetysaika])
    :koordinaatit {:x x
                   :y y}
    :tehtavat []
    :materiaalit [{
                   :materiaali (:nimi (get-in app [:toteumatiedot :valittu-materiaali])),
                   :maara {:yksikko (get-in app [:toteumatiedot :yksikko])
                           :maara (/ (js/parseFloat (get-in app [:toteumatiedot :materiaalimaara])) pisteiden-maara)}}]}})

(defn koostettu-data [app]
  {:otsikko {:lahettaja
             {:jarjestelma (get-in app [:toteumatiedot :valittu-jarjestelma])
              :organisaatio {:nimi "Urakoitsija1"
                             :ytunnus "1234567-8"}}
             :viestintunniste {:id 234}
             :lahetysaika (get-in app [:toteumatiedot :lahetysaika])}
   :reittitoteuma {:toteuma {:tunniste {:id (get-in app [:toteumatiedot :ulkoinen-id])}
                             :suorittaja {:nimi (get-in app [:toteumatiedot :suorittaja-nimi])
                                          :ytunnus "1234567-8"}
                             :sopimusId (get-in app [:toteumatiedot :sopimusid])
                             :alkanut (get-in app [:toteumatiedot :lahetysaika])
                             :paattynyt (get-in app [:toteumatiedot :lahetysaika])
                             :toteumatyyppi "kokonaishintainen"
                             :lisatieto "Normisuolaus"
                             ;; Tähän väliin tehtävät, jos ovat pakollisia
                             :materiaalit [{:materiaali (:nimi (get-in app [:toteumatiedot :valittu-materiaali])),
                                            :maara {:yksikko (get-in app [:toteumatiedot :yksikko])
                                                    :maara (js/parseFloat (get-in app [:toteumatiedot :materiaalimaara]))}}]}
                   :reitti (mapv #(muodosta-reittipiste (first %) (second %) app (count (:koordinaatit app))) (:koordinaatit app))}})

(def karttataso-tierekisteri (atom []))
(defonce tierekisteri-kartalla-nakyvissa? (atom true))
(defn tierekisteri-kartalla []
  (reaction
    (let [_ (js/console.log "@@karttataso-tierekisteri" (pr-str @karttataso-tierekisteri))
          muokattu-toteumaksi {:reitti @karttataso-tierekisteri}]
      (when @tierekisteri-kartalla-nakyvissa?
        (kartalla-esitettavaan-muotoon
          muokattu-toteumaksi
          true
          (map
            #(assoc % :tyyppi-kartalla :toteuma)))))))
(def valittu-urakka (atom nil))
(def valittu-hallintayksikko (atom nil))

(defrecord Muokkaa [toteumatiedot])

;; TR osoitteelle koordinaatit
(defrecord HaeTROsoitteelleKoordinaatit [toteumatiedot])
(defrecord HaeTROsoitteelleKoordinaatitOnnistui [vastaus])
(defrecord HaeTROsoitteelleKoordinaatitEpaonnistui [vastaus])

(defrecord Laheta [toteumatiedot])
(defrecord LahetysOnnistui [vastaus])
(defrecord LahetysEpaonnistui [vastaus])

(defrecord HaeHallintayksikonUrakatOnnistui [vastaus])
(defrecord HaeHallintayksikonUrakatEpaonnistui [vastaus])
(defrecord HaeKayttajanOikeuksiaOnnistui [vastaus])
(defrecord HaeKayttajanOikeuksiaEpaonnistui [vastaus])

;; Lisätään oikeudet urakkaan
(defrecord LisaaOikeudetUrakkaan [urakka-id])
(defrecord LisaaOikeudetUrakkaanOnnistui [vastaus])
(defrecord LisaaOikeudetUrakkaanEpaonnistui [vastaus])

(defrecord HaeSeuraavaVapaaUlkoinenId [])
(defrecord HaeSeuraavaVapaaUlkoinenIdOnnistui [vastaus])
(defrecord HaeSeuraavaVapaaUlkoinenIdEpaonnistui [vastaus])

(defrecord HaeUrakanTierekisteriosoitteita [urakka-id])
(defrecord HaeUrakanTierekisteriosoitteitaOnnistui [vastaus])
(defrecord HaeUrakanTierekisteriosoitteitaEpaonnistui [vastaus])

(defrecord PaivitaRaportit [])
(defrecord PaivitaRaportitOnnistui [vastaus])
(defrecord PaivitaRaportitEpaonnistui [vastaus])

(defn hae-hallintayksikon-urakat [hal]
  (let [_ (tuck-apurit/post! :hallintayksikon-urakat
            (:id hal)
            {:onnistui ->HaeHallintayksikonUrakatOnnistui
             :epaonnistui ->HaeHallintayksikonUrakatEpaonnistui
             :paasta-virhe-lapi? true})]))

(defn hae-kayttajan-kaytto-oikeudet []
  (tuck-apurit/post! :hae-jarjestelmatunnuksen-lisaoikeudet {:kayttaja-id (:id @istunto/kayttaja)}
    {:onnistui ->HaeKayttajanOikeuksiaOnnistui
     :epaonnistui ->HaeKayttajanOikeuksiaEpaonnistui
     :paasta-virhe-lapi? true}))

(extend-protocol tuck/Event

  Muokkaa
  (process-event [{toteumatiedot :toteumatiedot} app]
    (let [;; Tarkista hallintayksikko
          toteumatiedot (if (and (not= (get-in app [:toteumatiedot :valittu-hallintayksikko]) (:valittu-hallintayksikko toteumatiedot)))
                          (do
                            (hae-hallintayksikon-urakat (:valittu-hallintayksikko toteumatiedot))
                            ;; Haetaan urakkahaun yhteydessä aina myös tiedot, että onko käyttäjällä oikeudet lisätä urakkaan API:n kautta toteumia :hae-urakat-lisaoikeusvalintaan
                            (hae-kayttajan-kaytto-oikeudet)
                            (reset! valittu-urakka nil)
                            (-> toteumatiedot
                              (assoc :sopimusid nil)
                              (assoc :valittu-urakka nil)))
                          toteumatiedot)
          ;; Aseta sopimus
          toteumatiedot (if (and (:valittu-urakka toteumatiedot) (:sopimukset (:valittu-urakka toteumatiedot)))
                          (assoc toteumatiedot :sopimusid (first (keys (:sopimukset (:valittu-urakka toteumatiedot)))))
                          toteumatiedot)
          ;; Aseta urakoitsija
          toteumatiedot (if (and (:valittu-urakka toteumatiedot) (get-in toteumatiedot [:valittu-urakka :urakoitsija]))
                          (assoc toteumatiedot :suorittaja-nimi (get-in toteumatiedot [:valittu-urakka :urakoitsija :nimi]))
                          toteumatiedot)]
      (-> app
        (assoc :mahdolliset-urakat (if (and
                                         @nav/hallintayksikon-urakkalista
                                         (not= +mahdolliset-urakat+ @nav/hallintayksikon-urakkalista))
                                     @nav/hallintayksikon-urakkalista
                                     +mahdolliset-urakat+))
        (assoc :toteumatiedot toteumatiedot))))

  Laheta
  (process-event [{toteumatiedot :toteumatiedot} app]
    (let [_ (js/console.log "(clj->js koostettu-data)" (.stringify js/JSON (clj->js (koostettu-data app))))
          tulos! (tuck/send-async! ->LahetysOnnistui)
          virhe! (tuck/send-async! ->LahetysEpaonnistui)
          urakkaid (:id (get-in app [:toteumatiedot :valittu-urakka]))]
      (if urakkaid
        (go
          (let [vastaus (<! (http/post (str "api/urakat/"
                                         urakkaid
                                         "/toteumat/reitti")
                              {:body (.stringify js/JSON (clj->js (koostettu-data app)))
                               :content-type :json
                               :accect :json
                               ;:headers {"OAM_REMOTE_USER" "ivalo-api"}
                               }))]
            (if (k/virhe? vastaus)
              (virhe!)
              (tulos!))))
        (js/console.log "Urakkaa ei valittu! ei lähetetä (käyttöliittymä on vaikea, eli yritä uusiksi)"))
      (assoc app :lahetys-kaynnissa? true)))

  LahetysOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Toteuma lähetetty" :onnistui)
      (js/console.log "Vastaus: " (pr-str vastaus))
      (assoc app :lahetys-kaynnissa? false)))

  LahetysEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Toteuman lähetys epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (js/console.log "Virhe: " (pr-str vastaus))
      (assoc app :lahetys-kaynnissa? false)))

  HaeTROsoitteelleKoordinaatit
  (process-event [{toteumatiedot :toteumatiedot} app]
    (let [_ (js/console.log "HaeTROsoitteelleKoordinaatit :: toteumatiedot" (pr-str (dissoc toteumatiedot :harja.ui.lomake/skeema :valittu-urakka)))
          _ (tuck-apurit/post! :hae-tr-viivaksi
              (:tierekisteriosoite toteumatiedot)
              {:onnistui ->HaeTROsoitteelleKoordinaatitOnnistui
               :epaonnistui ->HaeTROsoitteelleKoordinaatitEpaonnistui
               :paasta-virhe-lapi? true})]
      (assoc app :trhaku-kaynnissa? true)))

  HaeTROsoitteelleKoordinaatitOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "HaeTROsoitteelleKoordinaatitOnnistui" :onnistui)
    (let [koordinaatit (first (mapcat (fn [coordinates]
                                        (concat (map :points (:lines coordinates)))) vastaus))
          _ (js/console.log "HaeTROsoitteelleKoordinaatitOnnistui :: vastaus" (pr-str vastaus))
          _ (js/console.log "HaeTROsoitteelleKoordinaatitOnnistui :: koordinaatit" (pr-str koordinaatit))
          _ (js/console.log "koordinaateista reitti: " (pr-str (mapv
                                                                 #(muodosta-reittipiste (first %) (second %) app (count koordinaatit))
                                                                 koordinaatit)))
          _ (reset! karttataso-tierekisteri koordinaatit)]
      (-> app
        (assoc :koordinaatit koordinaatit)
        (assoc :trhaku-kaynnissa? false))))

  HaeTROsoitteelleKoordinaatitEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (reset! karttataso-tierekisteri [])
      (viesti/nayta-toast! "Koordinaattien haku epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (js/console.log "HaeTROsoitteelleKoordinaatitOnnistui :: vastaus" (pr-str vastaus))
      (-> app
        (assoc :koordinaatit nil)
        (assoc :trhaku-kaynnissa? false))))

  HaeHallintayksikonUrakatOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "HaeTROsoitteelleKoordinaatitOnnistui" :onnistui)
    (assoc app :mahdolliset-urakat vastaus))

  HaeHallintayksikonUrakatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "HaeHallintayksikonUrakatEpaonnistui :: vastaus" (pr-str vastaus))
    (-> app
      (assoc :koordinaatit nil)
      (assoc :trhaku-kaynnissa? false)))

  HaeKayttajanOikeuksiaOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "HaeKayttajanOikeuksiaOnnistui :: vastaus" (pr-str vastaus))
      (viesti/nayta-toast! "HaeKayttajanOikeuksiaOnnistui" :onnistui)
      (assoc app :oikeudet-urakoihin vastaus)))

  HaeKayttajanOikeuksiaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "HaeKayttajanOikeuksiaEpaonnistui :: vastaus" (pr-str vastaus))
    app)

  LisaaOikeudetUrakkaan
  (process-event [{urakka-id :urakka-id} app]
    (let [payload {:oikeudet (conj [] {:urakka-id urakka-id
                                       :poistettu false})
                   :kayttaja-id (:id @istunto/kayttaja)}
          _ (tuck-apurit/post! :tallenna-jarjestelmatunnuksen-lisaoikeudet
              payload
              {:onnistui ->LisaaOikeudetUrakkaanOnnistui
               :epaonnistui ->LisaaOikeudetUrakkaanEpaonnistui
               :paasta-virhe-lapi? true})]
      app))

  LisaaOikeudetUrakkaanOnnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "LisaaOikeudetUrakkaanOnnistui :: vastaus" (pr-str vastaus))
    (viesti/nayta-toast! "LisaaOikeudetUrakkaanOnnistui" :onnistui)
    (hae-kayttajan-kaytto-oikeudet)
    app)

  LisaaOikeudetUrakkaanEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "LisaaOikeudetUrakkaanEpaonnistui :: vastaus" (pr-str vastaus))
    app)

  HaeSeuraavaVapaaUlkoinenId
  (process-event [_ app]
    (let [_ (tuck-apurit/post! :debug-hae-seuraava-vapaa-ulkoinen-id
              {}
              {:onnistui ->HaeSeuraavaVapaaUlkoinenIdOnnistui
               :epaonnistui ->HaeSeuraavaVapaaUlkoinenIdEpaonnistui
               :paasta-virhe-lapi? true})]
      app))

  HaeSeuraavaVapaaUlkoinenIdOnnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "LisaaOikeudetUrakkaanOnnistui :: vastaus" (pr-str vastaus))
    (viesti/nayta-toast! "HaeSeuraavaVapaaUlkoinenIdOnnistui" :onnistui)
    (assoc-in app [:toteumatiedot :ulkoinen-id] vastaus))

  HaeSeuraavaVapaaUlkoinenIdEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "HaeSeuraavaVapaaUlkoinenIdEpaonnistui :: vastaus" (pr-str vastaus))
    (viesti/nayta-toast! "HaeSeuraavaVapaaUlkoinenIdEpaonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
    app)

  HaeUrakanTierekisteriosoitteita
  (process-event [{urakka-id :urakka-id} app]
    (let [_ (tuck-apurit/post! :debug-hae-urakan-tierekisteriosoitteita
              {:urakka-id urakka-id}
              {:onnistui ->HaeUrakanTierekisteriosoitteitaOnnistui
               :epaonnistui ->HaeUrakanTierekisteriosoitteitaEpaonnistui
               :paasta-virhe-lapi? true})]
      app))

  HaeUrakanTierekisteriosoitteitaOnnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "HaeUrakanTierekisteriosoitteitaOnnistui :: vastaus" (pr-str vastaus))
    (viesti/nayta-toast! "HaeUrakanTierekisteriosoitteitaOnnistui" :onnistui)
    (assoc app :tierekisteriosoitteita vastaus))

  HaeUrakanTierekisteriosoitteitaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "HaeUrakanTierekisteriosoitteitaEpaonnistui :: vastaus" (pr-str vastaus))
    (viesti/nayta-toast! "HaeUrakanTierekisteriosoitteitaEpaonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
    app)

  PaivitaRaportit
  (process-event [_ app]
    (let [_ (tuck-apurit/post! :debug-paivita-raportit
              {:urakka-id (get-in app [:toteumatiedot :valittu-urakka :id])
               :alkupvm (str (pvm/hoitokauden-alkuvuosi (get-in app [:toteumatiedot :lahetysaika])) "-10-01")
               :loppupvm (str (inc (pvm/hoitokauden-alkuvuosi (get-in app [:toteumatiedot :lahetysaika]))) "-09-30")}
              {:onnistui ->PaivitaRaportitOnnistui
               :epaonnistui ->PaivitaRaportitEpaonnistui
               :paasta-virhe-lapi? true})]
      app))

  PaivitaRaportitOnnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "PaivitaRaportitOnnistui :: vastaus" (pr-str vastaus))
    (viesti/nayta-toast! "PaivitaRaportitOnnistui" :onnistui)
    app)

  PaivitaRaportitEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "PaivitaRaportitEpaonnistui :: vastaus" (pr-str vastaus))
    (viesti/nayta-toast! "PaivitaRaportitEpaonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
    app)
  )
