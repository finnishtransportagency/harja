(ns harja.tiedot.urakka.suunnittelu.tehtavat-maarat-tiedot
  (:require [clojure.string :as str]
            [harja.tiedot.urakka :as u]
            [harja.ui.viesti :as viesti]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tiedot]))

(defonce nakymassa? (atom false))

;; Muutosten seuranta
(defonce tallentamattomia-muutoksia (atom false))

(defn synkronoi-muutokset-muutokset-atomiin!
  "Synkronoi app-staten :tallentamattomia-muutoksia? atomiin navigaatiota varten.
   Kutsutaan automaattisesti kaikissa eventeissä, jotka muuttavat tilaa."
  [app]
  (reset! tallentamattomia-muutoksia (boolean (get app :tallentamattomia-muutoksia? false)))
  app)

(defrecord HaeTehtavatJaMaarat [parametrit])
(defrecord HaeTehtavatJaMaaratOnnistui [vastaus parametrit])
(defrecord HaeTehtavatJaMaaratEpaonnistui [vastaus parametrit])

(defrecord TallennaTehtavat [tehtavat kopioi-tuleville-vuosille?])
(defrecord TallennaTehtavatOnnistui [vastaus])
(defrecord TallennaTehtavatEpaonnistui [vastaus])

(defrecord ToggleTallennusTila [])
(defrecord FiltteroiTehtavat [hakuehto])
(defrecord PeruutaTallennus [])
(defrecord PaivitaTehtavatGrid [tehtavat])
(defrecord AvaaRivi [valiotsikko])
(defrecord NollaaTehtavatJaMaaratMuutokset [])

(defn hae-tehtavat-ja-maarat [parametrit]
  (tuck-apurit/post! :hae-tehtavat-ja-maarat
    {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))
     :valittu-hoitokausi @u/valittu-hoitokausi}
    {:onnistui ->HaeTehtavatJaMaaratOnnistui
     :epaonnistui ->HaeTehtavatJaMaaratEpaonnistui
     :paasta-virhe-lapi? true}))

(defn filtteroi-tehtavat
  "Palauttaa tehtavat, joiden nimi sisältää hakuehdon (case insensitive)."
  [hakuehto tehtavat]
  (filter (fn [tehtava]
            (str/includes?
              (str/lower-case (:nimi tehtava))
              (str/lower-case hakuehto)))
    tehtavat))

(extend-protocol tuck/Event

  HaeTehtavatJaMaarat
  (process-event [{parametrit :parametrit} app]
    (js/console.log "HaeTehtavatJaMaarat :: parametrit " (pr-str parametrit))
    (hae-tehtavat-ja-maarat parametrit)
    (assoc app :haku-kaynnissa? true))

  HaeTehtavatJaMaaratOnnistui
  (process-event [{vastaus :vastaus parametrit :parametrit} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tehtavat-ja-maarat (:tehtavat vastaus))
      (assoc :kaikki-tehtavat (:tehtavat vastaus))
      (assoc :viimeisin-muokkaus (:viimeisin-muokkaus vastaus))
      (assoc :viimeisin-muokkaaja (:viimeisin-muokkaaja vastaus))))

  HaeTehtavatJaMaaratEpaonnistui
  (process-event [{vastaus :vastaus parametrit :parametrit} app]
    (viesti/nayta-toast! (str "Tietojen hakeminen epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (-> app
      (assoc :haku-kaynnissa? false)))

  TallennaTehtavat
  (process-event [{tehtavat :tehtavat kopioi-tuleville-vuosille? :kopioi-tuleville-vuosille?} app]
    (tuck-apurit/post! :tallenna-tehtavat-ja-maarat
      {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))
       :tehtavat tehtavat
       :kopioi-tuleville-vuosille? kopioi-tuleville-vuosille?
       :valittu-hoitokausi @u/valittu-hoitokausi}
      {:onnistui ->TallennaTehtavatOnnistui
       :epaonnistui ->TallennaTehtavatEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kaynnissa? true))

  TallennaTehtavatOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Tiedot tallennettiin onnistuneesti.")

    (-> app
      (assoc :tallennus-kaynnissa? false)
      (assoc :tallennustila? false)
      (assoc :tallentamattomia-muutoksia? false)
      (assoc :tehtavat-ja-maarat (filtteroi-tehtavat (:haku app) (:tehtavat vastaus)))
      (assoc :kaikki-tehtavat (:tehtavat vastaus))
      (assoc :viimeisin-muokkaus (:viimeisin-muokkaus vastaus))
      (synkronoi-muutokset-muutokset-atomiin!)))

  TallennaTehtavatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Tietojen tallentaminen epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (-> app
      (assoc :tallennus-kaynnissa? false)))

  PaivitaTehtavatGrid
  (process-event [{tehtavat :tehtavat} app]
    (let [muokatut-tehtavat tehtavat
          yhdistetyt-tehtavat (reduce (fn [acc alkuperainen-tehtava]
                                        (let [muokattu-tehtava (first (filter #(= (:nimi %) (:nimi alkuperainen-tehtava)) muokatut-tehtavat))]
                                          (if muokattu-tehtava
                                            (conj acc muokattu-tehtava)
                                            (conj acc alkuperainen-tehtava))))
                                [] (:tehtavat-ja-maarat app))]
      (-> app
        (assoc :tallentamattomia-muutoksia? true)
        (assoc :tehtavat-ja-maarat (sort-by :jarjestys yhdistetyt-tehtavat))
        (synkronoi-muutokset-muutokset-atomiin!))))

  ToggleTallennusTila
  (process-event [_ app]
    (assoc app :tallennustila? (not (:tallennustila? app))))

  FiltteroiTehtavat
  (process-event [{hakuehto :hakuehto} app]
    ;; Kun hakuehto on alle 2 merkkiä, näytetään kaikki tehtävät
    (if (>= (count hakuehto) 2)
      (let [tehtavat (:tehtavat-ja-maarat app)
            f-tehtavat (filtteroi-tehtavat hakuehto tehtavat)]
        (-> app
          (assoc :haku hakuehto)
          (assoc :tehtavat-ja-maarat f-tehtavat)))
      (-> app
        (assoc :tehtavat-ja-maarat (:kaikki-tehtavat app))
        (assoc :haku hakuehto))))

  PeruutaTallennus
  (process-event [_ app]
    (hae-tehtavat-ja-maarat nil)
    (-> app
      (assoc :tallentamattomia-muutoksia? false)
      (assoc :tallennustila? (not (:tallennustila? app)))))

  AvaaRivi
  (process-event [{valiotsikko :valiotsikko} app]
    (let [app (if (nil? (:avatut-tehtavaryhmat app))
                (assoc app :avatut-tehtavaryhmat #{})
                app)]
      (if (contains? (:avatut-tehtavaryhmat app) valiotsikko)
        (assoc app :avatut-tehtavaryhmat (disj (:avatut-tehtavaryhmat app) valiotsikko))
        (assoc app :avatut-tehtavaryhmat (merge (:avatut-tehtavaryhmat app) valiotsikko)))))

  NollaaTehtavatJaMaaratMuutokset
  (process-event [_ app]
    (assoc app :tallentamattomia-muutoksia? false)))
