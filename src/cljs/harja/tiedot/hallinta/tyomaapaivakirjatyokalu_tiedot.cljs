(ns harja.tiedot.hallinta.tyomaapaivakirjatyokalu-tiedot
  "Toteumatyokalun ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [cljs-time.format :as df]
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

(def alkutila {:paivakirja {:tierekisteriosoite nil
                            :valittu-jarjestelma "Autoyksikkö Kolehmainen"
                            :valittu-urakka nil
                            :valittu-hallintayksikko nil
                            :lahetysaika (pvm/jsondate (pvm/nyt))
                            :ulkoinen-id 123
                            :suorittaja-nimi "Urakoitsija Oy"
                            :paivystaja "Pekka Päivystäjä"
                            :tyonjohtaja "Teppo Työnjohtaja"
                            :tyokoneiden-lkm 7
                            :lisakaluston-lkm 3
                            :saa-asematietojen-lkm 1}
               :lahetys-kaynnissa? false
               :mahdolliset-urakat +mahdolliset-urakat+})
(def data (atom alkutila))

(def +mahdolliset-materiaalit+
  [{:id 1 :nimi "Talvisuolaliuos NaCl" :yksikko "t"}
   {:id 6 :nimi "Kaliumformiaattiliuos" :yksikko "t"}
   {:id 10 :nimi "Kesäsuola sorateiden kevätkunnostus" :yksikko "t"}
   {:id 11 :nimi "Kesäsuola sorateiden pölynsidonta" :yksikko "t"}])

(defn- generoi-saaasematiedot [montako lahetysaika-str]
  (let [aika (df/parse (df/formatter "yyyy-MM-dd'T'HH:mm:ss'Z'") lahetysaika-str)
        anna-aseman-tiedot (fn [indeksi]
                             {:saatieto {:havaintoaika lahetysaika-str,
                                         :aseman-tunniste (str (rand-int 999999)),
                                         :aseman-tietojen-paivityshetki (pvm/jsondate (pvm/pvm-plus-tuntia aika indeksi)),
                                         :ilman-lampotila 20.0,
                                         :tien-lampotila 5.0,
                                         :keskituuli 16,
                                         :sateen-olomuoto 23.0,
                                         :sadesumma 5}})]
    (vec (for [i (range 1 (inc montako))]
           (anna-aseman-tiedot i)))))

(defn- generoi-muut-toimenpiteet [montako lahetysaika-str]
  (let [aika (df/parse (df/formatter "yyyy-MM-dd'T'HH:mm:ss'Z'") lahetysaika-str)
        anna-toimenpide (fn [indeksi]
                          {:tieston-muu-toimenpide {:aloitus (pvm/jsondate (pvm/pvm-plus-tuntia aika indeksi)),
                                                    :lopetus (pvm/jsondate (pvm/pvm-plus-tuntia aika (inc indeksi))),
                                                    :tehtavat [{:tehtava {:kuvaus "Esimerkki kuvaus"}}]}})]
    (vec (for [i (range 1 (inc montako))]
           (anna-toimenpide i)))))

(defn koostettu-data [app]
  {:otsikko {:lahettaja {:jarjestelma (get-in app [:paivakirja :valittu-jarjestelma]),
                         :organisaatio {:nimi "YIT Rakennus Oy",
                                        :ytunnus "1565583-5"}},
             :viestintunniste {:id 8139298},
             :lahetysaika (get-in app [:paivakirja :lahetysaika])}
   :tyomaapaivakirja {:kaluston-kaytto [{:kalusto {:aloitus (get-in app [:paivakirja :lahetysaika]),
                                                   :lopetus (get-in app [:paivakirja :lahetysaika]),
                                                   :tyokoneiden-lkm (get-in app [:paivakirja :tyokoneiden-lkm]),
                                                   :lisakaluston-lkm (get-in app [:paivakirja :lisakaluston-lkm])}}],
                      :muut-kirjaukset {:kuvaus "Huomatus vielä että kiire tuli hommissa"},
                      :saatiedot (generoi-saaasematiedot
                                   (get-in app [:paivakirja :saa-asematietojen-lkm])
                                   (get-in app [:paivakirja :lahetysaika])),
                      :tyonjohtajan-tiedot [{:tyonjohtaja {:aloitus (get-in app [:paivakirja :lahetysaika]),
                                                           :lopetus (get-in app [:paivakirja :lahetysaika]),
                                                           :nimi (get-in app [:paivakirja :tyonjohtaja])}}],
                      :paivystajan-tiedot [{:paivystaja {:aloitus (get-in app [:paivakirja :lahetysaika]),
                                                         :lopetus (get-in app [:paivakirja :lahetysaika]),
                                                         :nimi (get-in app [:paivakirja :paivystaja])}}],
                      :tieston-muut-toimenpiteet (generoi-muut-toimenpiteet 3 (get-in app [:paivakirja :lahetysaika])),
                      :tieston-toimenpiteet [{:tieston-toimenpide {:aloitus (get-in app [:paivakirja :lahetysaika]),
                                                                   :lopetus (get-in app [:paivakirja :lahetysaika]),
                                                                   :tehtavat [{:tehtava {:id 1368}}]}}],
                      :onnettomuudet [{:onnettomuus {:kuvaus "Onnettomuus tiellä 4"}}],
                      :liikenteenohjaus-muutokset [{:liikenteenohjaus-muutos {:kuvaus "Paljon muutoksia"}}],
                      :tunniste {:id "123456",
                                 :versio 1,
                                 :paivamaara (pvm/iso8601-timestamp-str->iso8601-str (get-in app [:paivakirja :lahetysaika]))},
                      :poikkeukselliset-saahavainnot [{:poikkeuksellinen-saahavainto {:havaintoaika (get-in app [:paivakirja :lahetysaika]),
                                                                                      :paikka "Kauhava",
                                                                                      :kuvaus "Jäätävä sade"}}],
                      :palautteet [{:palaute {:kuvaus "Esimerkki palaute"}}],
                      :viranomaisen-avustaminen [{:viranomaisen-avustus {:kuvaus "Katselmointi pidetty hyvässä ymmärryksessä", :tunnit 4.75}}],
                      :tilaajan-yhteydenotot [{:tilaajan-yhteydenotto {:kuvaus "Tilaajan huomautukset työmaapäiväkirjaan", :nimi "Tiia Tilaaja"}}]}})


(def valittu-urakka (atom nil))
(def valittu-hallintayksikko (atom nil))
(defrecord Muokkaa [paivakirja])
(defrecord Laheta [paivakirja])
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
  (process-event [{paivakirja :paivakirja} app]
    (let [;; Tarkista hallintayksikko
          paivakirja (if (and (not= (get-in app [:paivakirja :valittu-hallintayksikko]) (:valittu-hallintayksikko paivakirja)))
                       (do
                         (hae-hallintayksikon-urakat (:valittu-hallintayksikko paivakirja))
                         ;; Haetaan urakkahaun yhteydessä aina myös tiedot, että onko käyttäjällä oikeudet lisätä urakkaan API:n kautta toteumia :hae-urakat-lisaoikeusvalintaan
                         (hae-kayttajan-kaytto-oikeudet)
                         (reset! valittu-urakka nil)
                         (-> paivakirja
                           (assoc :valittu-urakka nil)))
                       paivakirja)
          ;; Aseta urakoitsija
          paivakirja (if (and (:valittu-urakka paivakirja) (get-in paivakirja [:valittu-urakka :urakoitsija]))
                       (assoc paivakirja :suorittaja-nimi (get-in paivakirja [:valittu-urakka :urakoitsija :nimi]))
                       paivakirja)]
      (-> app
        (assoc :mahdolliset-urakat (if (and
                                         @nav/hallintayksikon-urakkalista
                                         (not= +mahdolliset-urakat+ @nav/hallintayksikon-urakkalista))
                                     @nav/hallintayksikon-urakkalista
                                     +mahdolliset-urakat+))
        (assoc :paivakirja paivakirja))))

  Laheta
  (process-event [{paivakirja :paivakirja} app]
    (let [tulos! (tuck/send-async! ->LahetysOnnistui)
          virhe! (tuck/send-async! ->LahetysEpaonnistui)
          urakkaid (:id (get-in app [:paivakirja :valittu-urakka]))]
      (if urakkaid
        (go
          (let [vastaus (<! (http/post (str "api/urakat/" urakkaid "/tyomaapaivakirja")
                              {:body (.stringify js/JSON (clj->js (koostettu-data app)))
                               :content-type :json
                               :accect :json}))]
            (if (k/virhe? vastaus)
              (virhe!)
              (tulos!))))
        (js/console.log "Urakkaa ei valittu! ei lähetetä (käyttöliittymä on vaikea, eli yritä uusiksi)"))
      (assoc app :lahetys-kaynnissa? true)))

  LahetysOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Päiväkirja lähetetty" :onnistui)
      (assoc app :lahetys-kaynnissa? false)))

  LahetysEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Päiväkirjan lähetys epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (assoc app :lahetys-kaynnissa? false)))

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
    (assoc-in app [:paivakirja :ulkoinen-id] vastaus))

  HaeSeuraavaVapaaUlkoinenIdEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "HaeSeuraavaVapaaUlkoinenIdEpaonnistui :: vastaus" (pr-str vastaus))
    (viesti/nayta-toast! "HaeSeuraavaVapaaUlkoinenIdEpaonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
    app))
