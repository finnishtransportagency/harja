(ns harja.tiedot.hallinta.tyokalut.talvihoitoreitti-tyokalu
  "Toteumatyokalun ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tyokalut.yleiset :as yleiset]
            [cljs.core.async :refer [<! >! chan close!]]
            [cljs-http.client :as http]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def +mahdolliset-urakat+
  [
   {:id 34 :nimi "Ivalon MHU testiurakka (uusi) (aseta sopimusid 19)"}
   {:id 35 :nimi "Oulun MHU 2019-2024 (Aseta sopimusid 42)"}
   ])

(defn- luo-aika-paivamaarasta [paivamaara aika]
  (str paivamaara aika))

(def alkutila {:talvihoitoreitti {:lahetysaika (pvm/jsondate (pvm/nyt))
                                  :valittu-jarjestelma "Autoyksikkö Kolehmainen"
                                  :valittu-urakka nil
                                  :valittu-hallintayksikko nil
                                  :suorittaja-nimi "Urakoitsija Oy"
                                  :reittinimi "Nelostien kaaos"
                                  :reitti [{:tie 4 :aosa 101 :aet 0 :losa 101 :let 3000 :pituus 3000
                                            :hoitoluokka 2, :kalusto-lkm 7
                                            :kalustotyyppi "KA"}]}
               :lahetys-kaynnissa? false
               :mahdolliset-urakat +mahdolliset-urakat+})
(def data (atom alkutila))


(defn koostettu-data [app]
  {:ulkoinen-id (get-in app [:talvihoitoreitti :ulkoinen-id])
   :reittinimi (get-in app [:talvihoitoreitti :reittinimi])
   ;; Reitti koostuu oikeasti useammasta tieosoitteesta
   :reitti (conj [
                  {:tie (get-in app [:talvihoitoreitti :tierekisteriosoite :numero])
                   :aosa (get-in app [:talvihoitoreitti :tierekisteriosoite :alkuosa])
                   :aet (get-in app [:talvihoitoreitti :tierekisteriosoite :alkuetaisyys])
                   :losa (get-in app [:talvihoitoreitti :tierekisteriosoite :loppuosa])
                   :let (get-in app [:talvihoitoreitti :tierekisteriosoite :loppuetaisyys])
                   :hoitoluokka (yleiset/random-luku-valilta 1 9)
                   :pituus (yleiset/random-luku-valilta 1 30000)
                   :kalusto-lkm (get-in app [:talvihoitoreitti :kalusto-lkm])
                   :kalustotyyppi (get-in app [:talvihoitoreitti :kalustotyyppi])}
                  ]
             ;; Koska ollaan kiireessä, niin varmistetaan, että pelkkä tien numero on lisätty ja oletetaan että muutkin on
             (when (get-in app [:talvihoitoreitti :tierekisteriosoite2 :numero])
               {:tie (get-in app [:talvihoitoreitti :tierekisteriosoite2 :numero])
                :aosa (get-in app [:talvihoitoreitti :tierekisteriosoite2 :alkuosa])
                :aet (get-in app [:talvihoitoreitti :tierekisteriosoite2 :alkuetaisyys])
                :losa (get-in app [:talvihoitoreitti :tierekisteriosoite2 :loppuosa])
                :let (get-in app [:talvihoitoreitti :tierekisteriosoite2 :loppuetaisyys])
                :hoitoluokka (yleiset/random-luku-valilta 1 9)
                :pituus (yleiset/random-luku-valilta 1 30000)
                :kalusto-lkm (get-in app [:talvihoitoreitti :kalusto-lkm2])
                :kalustotyyppi (get-in app [:talvihoitoreitti :kalustotyyppi2])}))})



(def valittu-urakka (atom nil))
(def valittu-hallintayksikko (atom nil))
(defrecord Muokkaa [talvihoitoreitti])
(defrecord Laheta [talvihoitoreitti])
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
(defrecord LisaaKirjoitusOikeusOnnistui [vastaus])
(defrecord LisaaKirjoitusOikeusEpaonnistui [vastaus])

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
  (process-event [{talvihoitoreitti :talvihoitoreitti} app]
    (let [;; Tarkista hallintayksikko
          talvihoitoreitti (if (and (not= (get-in app [:talvihoitoreitti :valittu-hallintayksikko]) (:valittu-hallintayksikko talvihoitoreitti)))
                             (do
                               (hae-hallintayksikon-urakat (:valittu-hallintayksikko talvihoitoreitti))
                               ;; Haetaan urakkahaun yhteydessä aina myös tiedot, että onko käyttäjällä oikeudet lisätä urakkaan API:n kautta toteumia :hae-urakat-lisaoikeusvalintaan
                               (hae-kayttajan-kaytto-oikeudet)
                               (reset! valittu-urakka nil)
                               (-> talvihoitoreitti
                                 (assoc :valittu-urakka nil)))
                             talvihoitoreitti)
          ;; Aseta urakoitsija
          talvihoitoreitti (if (and (:valittu-urakka talvihoitoreitti) (get-in talvihoitoreitti [:valittu-urakka :urakoitsija]))
                             (assoc talvihoitoreitti :suorittaja-nimi (get-in talvihoitoreitti [:valittu-urakka :urakoitsija :nimi]))
                             talvihoitoreitti)]
      (-> app
        (assoc :mahdolliset-urakat (if (and
                                         @nav/hallintayksikon-urakkalista
                                         (not= +mahdolliset-urakat+ @nav/hallintayksikon-urakkalista))
                                     @nav/hallintayksikon-urakkalista
                                     +mahdolliset-urakat+))
        (assoc :talvihoitoreitti talvihoitoreitti))))

  Laheta
  (process-event [{talvihoitoreitti :talvihoitoreitti} app]
    (let [tulos! (tuck/send-async! ->LahetysOnnistui)
          virhe! (tuck/send-async! ->LahetysEpaonnistui)
          urakkaid (:id (get-in app [:talvihoitoreitti :valittu-urakka]))]

      (.setTimeout js/window
        #(let [data (koostettu-data app)]
           (if urakkaid
             (go
               (let [params {:body (.stringify js/JSON (clj->js data))
                             :content-type :json
                             :accept :json}
                     ;; Lähetä talvihoitoreitti rajapintaan
                     vastaus (<! (http/put (str "api/urakat/" urakkaid "/talvihoitoreitti") params))
                     _ (println "Laheta :: vastaus" (pr-str vastaus))]
                 (if (or (k/virhe? vastaus) (= 400 (:status vastaus)))
                   (virhe!)
                   (tulos!))))
             (js/console.log "Urakkaa ei valittu! ei lähetetä (käyttöliittymä on vaikea, eli yritä uusiksi)")))

        500)

      (assoc app :lahetys-kaynnissa? true)))

  LahetysOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Talvihoitoreitti lähetetty" :onnistui)
      (assoc app :lahetys-kaynnissa? false)))

  LahetysEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Talvihoitoreitin lähetys epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (assoc app :lahetys-kaynnissa? false)))

  HaeHallintayksikonUrakatOnnistui
  (process-event [{vastaus :vastaus} app]
    ;; Filtteröidään muut, kuin teiden-hoito tyyppiset urakat pois. Koska talvihoitoreittejä ei voi muille lisätä
    (let [vastaus (filter #(= :teiden-hoito (:tyyppi %)) vastaus)]
      (viesti/nayta-toast! "HaeHallintayksikonUrakatOnnistui" :onnistui)
      (assoc app :mahdolliset-urakat vastaus)))

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
          ;; Käyttäjä tarvitsee myös kirjoitusoikeudet, joten lisätty tällainen koodipalikka
          _ (tuck-apurit/post! :lisaa-kayttajalle-kirjoitusoikeus
              {:oikeus "kirjoitus"
               :kayttajanimi (:kayttajanimi @istunto/kayttaja)}
              {:onnistui ->LisaaKirjoitusOikeusOnnistui
               :epaonnistui ->LisaaKirjoitusOikeusEpaonnistui
               :paasta-virhe-lapi? true})

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

  LisaaKirjoitusOikeusOnnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "LisaaKirjoitusOikeusOnnistui :: vastaus" (pr-str vastaus))
    (viesti/nayta-toast! "LisaaKirjoitusOikeusOnnistui" :onnistui)
    (hae-kayttajan-kaytto-oikeudet)
    app)

  LisaaKirjoitusOikeusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "LisaaKirjoitusOikeusEpaonnistui :: vastaus" (pr-str vastaus))
    app))
