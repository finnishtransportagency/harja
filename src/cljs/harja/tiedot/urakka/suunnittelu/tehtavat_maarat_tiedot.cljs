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
(defrecord HaeTehtavaprofiilitOnnistui [vastaus])
(defrecord HaeTehtavaprofiilitEpaonnistui [vastaus])
(defrecord TestiTallennaKaikkiinTehtaviinArvo [])

(defn hae-tehtavat-ja-maarat [_parametrit]
  (tuck-apurit/post! :hae-tehtavat-ja-maarat
    {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))
     :valittu-hoitokausi @u/valittu-hoitokausi}
    {:onnistui ->HaeTehtavatJaMaaratOnnistui
     :epaonnistui ->HaeTehtavatJaMaaratEpaonnistui
     :paasta-virhe-lapi? true}))

(defn filtteroi-tehtavat
  "Palauttaa tehtavat, joiden nimi sisältää hakuehdon (case insensitive)."
  [hakuehto tehtavat]
  (if hakuehto
    (filter (fn [tehtava]
              (str/includes?
                (str/lower-case (:nimi tehtava))
                (str/lower-case hakuehto)))
      tehtavat)
    tehtavat))

(defn validoi-ja-filtteroi-tehtavat
  "Validoi hakuehdon ja filtteröi tehtävät. Näytä virheviesti, jos hakuehto on liian lyhyt (alle 2 merkkiä)."
  [hakuehto kaikki-tehtavat app]
  ;; Kun hakuehto on alle 2 merkkiä, näytetään kaikki tehtävät
  (if (>= (count hakuehto) 2)
    (let [f-tehtavat (filtteroi-tehtavat hakuehto kaikki-tehtavat)]
      (-> app
        (assoc :haku hakuehto)
        (assoc :tehtavat-ja-maarat f-tehtavat)
        (assoc :kaikki-tehtavat kaikki-tehtavat)))
    (-> app
      (assoc :tehtavat-ja-maarat kaikki-tehtavat)
      (assoc :kaikki-tehtavat kaikki-tehtavat)
      (assoc :haku hakuehto))))

(def ^:private puuttuva-tarjousmaara-viesti
  "Syötä määrä tai aseta 0. Tyhjää arvoa ei voi tallentaa.")

(defn- tyhja-tarjous-maara?
  "True jos tehtäväriviltä puuttuu sopimuksen määrä (nil/tyhjä)."
  [{:keys [tarjous_maara]}]
  (or (nil? tarjous_maara)
      (and (string? tarjous_maara) (str/blank? tarjous_maara))))

(defn- tehtavarivi?
  [{:keys [valiotsikko tehtava_id]}]
  (and (nil? valiotsikko)
       (some? tehtava_id)))

(defn- tyhjennettiinko-aiempi-tarjous-maara?
  "True jos rivillä oli aiemmin arvo ja se on nyt tyhjä.

  Periaate: sallitaan tallennus, vaikka osalla riveistä ei ole koskaan ollut arvoa,
  mutta estetään käyttäjää tyhjentämästä aiemmin syötettyä arvoa (syötä tällöin 0)."
  [tehtava-id->alkuperainen-rivi {:keys [tehtava_id] :as rivi}]
  (when (tehtavarivi? rivi)
    (let [alkuperainen (get tehtava-id->alkuperainen-rivi tehtava_id)
          alkuperainen-maara (:tarjous_maara alkuperainen)]
      (and (some? alkuperainen-maara)
           (tyhja-tarjous-maara? rivi)))))

(extend-protocol tuck/Event

  HaeTehtavatJaMaarat
  (process-event [{parametrit :parametrit} app]
    (hae-tehtavat-ja-maarat parametrit)
    (assoc app :haku-kaynnissa? true))

  HaeTehtavatJaMaaratOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (validoi-ja-filtteroi-tehtavat (:haku app) (:tehtavat vastaus) app)]
      (-> app
        (assoc :haku-kaynnissa? false)
        (assoc :haku (:haku app))
        (assoc :viimeisin-muokkaus (:viimeisin-muokkaus vastaus))
        (assoc :viimeisin-muokkaaja (:viimeisin-muokkaaja vastaus)))))

  HaeTehtavatJaMaaratEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Tietojen hakeminen epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (-> app
      (assoc :haku-kaynnissa? false)))

  TallennaTehtavat
  (process-event [{tehtavat :tehtavat kopioi-tuleville-vuosille? :kopioi-tuleville-vuosille?} app]
    (let [tehtava-id->alkuperainen-rivi (into {}
                                          (keep (fn [rivi]
                                                  (when (tehtavarivi? rivi)
                                                    [(:tehtava_id rivi) rivi])))
                                          (:kaikki-tehtavat app))
          tyhjennys-yritys? (and (not kopioi-tuleville-vuosille?)
                                 (some (partial tyhjennettiinko-aiempi-tarjous-maara?
                                                tehtava-id->alkuperainen-rivi)
                                       tehtavat))]
      (if tyhjennys-yritys?
      (do
        (viesti/nayta-toast!
          puuttuva-tarjousmaara-viesti
          :varoitus
          viesti/viestin-nayttoaika-keskipitka)
        app)
      (do
        (tuck-apurit/post! :tallenna-tehtavat-ja-maarat
          {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))
           :tehtavat tehtavat
           :kopioi-tuleville-vuosille? kopioi-tuleville-vuosille?
           :valittu-hoitokausi @u/valittu-hoitokausi}
          {:onnistui ->TallennaTehtavatOnnistui
           :epaonnistui ->TallennaTehtavatEpaonnistui
           :paasta-virhe-lapi? true})
        (assoc app :tallennus-kaynnissa? true)))))

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
    (validoi-ja-filtteroi-tehtavat hakuehto (:kaikki-tehtavat app) app))

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
    (assoc app :tallentamattomia-muutoksia? false))

  HaeTehtavaprofiilitOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [arvolliset-tehtavat (map (fn [tehtava]
                                     (if (nil? (:valiotsikko tehtava))
                                       (assoc tehtava :tarjous_maara (get vastaus (:nimi tehtava) 0))
                                       tehtava))
                                   (:tehtavat-ja-maarat app))]
      (assoc app :tehtavat-ja-maarat arvolliset-tehtavat)))

  HaeTehtavaprofiilitEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Pikatäytön arvojen hakeminen epäonnistui: " (pr-str vastaus))
      :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  TestiTallennaKaikkiinTehtaviinArvo
  (process-event [_ app]
    (tuck-apurit/get! :hae-tehtavaprofiilit
      {:onnistui ->HaeTehtavaprofiilitOnnistui
       :epaonnistui ->HaeTehtavaprofiilitEpaonnistui
       :paasta-virhe-lapi? true})
    app))

