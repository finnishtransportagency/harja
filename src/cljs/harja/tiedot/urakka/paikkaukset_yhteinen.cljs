(ns harja.tiedot.urakka.paikkaukset-yhteinen
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.viesti :as viesti]
            [harja.domain.paikkaus :as paikkaus]
            [harja.tiedot.urakka.urakka :as tila])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn nakyman-urakka
  "Saman urakan sisällä kun vaihdetaan toteumista kustannuksiin, ei resetoida hakuehtoja. Mutta jos urakka
   vaihtuu, tulee hakuehdot resetoida."
  [tila ur]
  (when (not= (:urakka @tila) ur)
    (swap! tila #(merge % {:valinnat {:aikavali (pvm/aikavali-nyt-miinus 28)
                                      :tyomenetelmat #{}}
                           :urakka ur}))))

;; Muokkaukset
(defrecord PaivitaValinnat [uudet])
;; Haut
(defrecord HaeItemit [])
(defrecord ItemitHaettu [tulos])
(defrecord ItemitEiHaettu [])
(defrecord HaeItemitKutsuLahetetty [])
(defrecord PaikkauksetHaettu [tulos])
;; Työmenetelmät
(defrecord HaeTyomenetelmat [])
(defrecord HaeTyomenetelmatOnnistui [vastaus])
(defrecord HaeTyomenetelmatEpaonnistui [vastaus])
(defrecord ValitseTyomenetelma [tyomenetelma valittu?])
(defrecord AvaaToteumaOtsikko [avain])

(defn filtterin-valinnat->kysely-params
  [valinnat]
  (-> valinnat
      (assoc :tyomenetelmat (:valitut-tyomenetelmat valinnat))
      (dissoc :valitut-tyomenetelmat)
      (assoc ::paikkaus/urakka-id @nav/valittu-urakka-id)))

(extend-protocol tuck/Event

  PaikkauksetHaettu
  (process-event [{tulos :tulos} app]
    (let [app (assoc app :ensimmainen-haku-tehty? true
                         :paikkauksien-haku-tulee-olemaan-kaynnissa? false
                         :paikkauksien-haku-kaynnissa? false
                         :paikkaukset-grid tulos
                         :paikkauskohteet tulos
                         :paikkauket-vetolaatikko tulos)]
      app))

  PaivitaValinnat
  (process-event [{u :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                u)
          ;haku (tuck/send-async! ->HaeItemit)
          ]
      #_(go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))

  ;; TODO: Tämä on hyvin samanlainen kuin paikkauskohteissa. Voisi yhdistää
  ValitseTyomenetelma
  (process-event [{tyomenetelma :tyomenetelma valittu? :valittu?} app]
    (let [valitut-tyomenetelmat (get-in app [:valinnat :valitut-tyomenetelmat])
          menetelmat (cond
                       ;; Valitaan joku muu kuin "kaikki"
                       (and valittu? (not= "Kaikki" (:nimi tyomenetelma)))
                       (-> valitut-tyomenetelmat
                           (conj (:id tyomenetelma))
                           (disj "Kaikki"))

                       ;; Valitaan "kaikki"
                       (and valittu? (= "Kaikki" (:nimi tyomenetelma)))
                       #{"Kaikki"} ;; Palautetaan kaikki valinnalla

                       ;; Poistetaan "kaikki" valinta
                       (and (not valittu?) (= "Kaikki" (:nimi tyomenetelma)))
                       (disj valitut-tyomenetelmat "Kaikki")

                       ;; Poistetaan joku muu kuin "kaikki" valinta
                       (and (not valittu?) (not= "Kaikki" (:nimi tyomenetelma)))
                       (disj valitut-tyomenetelmat (:id tyomenetelma)))
          ;haku (tuck/send-async! ->HaeItemit) -- Testataan hakunapin toimintaa käyttäjillä, joten ei haeta vaihdon yhteydessä
          app (assoc-in app [:valinnat :valitut-tyomenetelmat] menetelmat)]
      ;(go (haku (:valinnat app)))
      app))

  HaeItemit
  (process-event [_ {:keys [palvelukutsu palvelukutsu-tunniste valinnat haku-kaynnissa?] :as app}]
    (if-not haku-kaynnissa?
      (let [params (filtterin-valinnat->kysely-params valinnat)]
        (-> app
            (tt/post! palvelukutsu
                      params
                      {:viive 1000
                       :tunniste palvelukutsu-tunniste
                       :lahetetty ->HaeItemitKutsuLahetetty
                       :onnistui ->ItemitHaettu
                       :epaonnistui ->ItemitEiHaettu})
            (assoc :paikkauksien-haku-tulee-olemaan-kaynnissa? true)))
      app))

  HaeItemitKutsuLahetetty
  (process-event [_ app]
    (assoc app :paikkauksien-haku-kaynnissa? true))

  ItemitHaettu
  (process-event [{tulos :tulos} {palvelukutsu-onnistui-fn :palvelukutsu-onnistui-fn :as app}]
    (assoc app :ensimmainen-haku-tehty? true
               :paikkauksien-haku-tulee-olemaan-kaynnissa? false
               :paikkauksien-haku-kaynnissa? false
               :paikkaukset-grid tulos
               :paikkauskohteet tulos
               :paikkauket-vetolaatikko tulos))

  ItemitEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Paikkauksien haku epäonnistui! " :danger)
    (-> app
        (dissoc :paikkaukset-grid
                :paikkauskohteet
                :paikkauket-vetolaatikko)
        (assoc :paikkauksien-haku-kaynnissa? false)
        (assoc :paikkauksien-haku-tulee-olemaan-kaynnissa? false)))

  HaeTyomenetelmat
  (process-event [_ app]
    (do
      (tt/post! app
                :hae-paikkauskohteiden-tyomenetelmat
                {}
                {:onnistui ->HaeTyomenetelmatOnnistui
                 :epaonnistui ->HaeTyomenetelmatEpaonnistui
                 :paasta-virhe-lapi? true})
      app))

  HaeTyomenetelmatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc-in app [:valinnat :tyomenetelmat] vastaus))

  HaeTyomenetelmatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Paikkauskohteiden tyomenetelmien haku epäonnistui" :varoitus viesti/viestin-nayttoaika-aareton)
      app))

  AvaaToteumaOtsikko
  (process-event [{avain :avain} app]
    (let [avoimet-otsikot (if (::paikkaus/toteumataulukon-tilat app)
                            (into #{} (::paikkaus/toteumataulukon-tilat app))
                            #{})
          avoimet-otsikot (if (contains? avoimet-otsikot avain)
                            (into #{} (disj avoimet-otsikot avain))
                            (into #{} (cons avain avoimet-otsikot)))]
      (assoc app ::paikkaus/toteumataulukon-tilat avoimet-otsikot)))
  )
