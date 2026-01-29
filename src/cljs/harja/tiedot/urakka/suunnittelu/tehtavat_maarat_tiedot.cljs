(ns harja.tiedot.urakka.suunnittelu.tehtavat-maarat-tiedot
  (:require [clojure.string :as str]
            [harja.tiedot.urakka :as u]
            [harja.ui.viesti :as viesti]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tiedot]))

(def ^:private puuttuva-tarjousmaara-virheviesti
  "Syötä määrä. Jos tehtävälle ei ole määrää, syötä 0")

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
(defrecord ToggleNaytaVainPuuttuvat [])
(defrecord AsetaPuuttuvatNollaksi [tehtava-idt])

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

(defn puuttuuko-tarjous-maara?
  [{:keys [valiotsikko tehtava_id tarjous_maara]}]
  (and (nil? valiotsikko)
    (some? tehtava_id)
    (or (nil? tarjous_maara)
      (and (string? tarjous_maara) (str/blank? tarjous_maara)))))

(defn puuttuvat-tarjous-maarat
  "Palauttaa setin tehtävä_id:itä, joilta puuttuu sopimuksen määrä (nil/tyhjä).
   Käytetään näkymässä korostamaan puuttuvat kentät."
  [tehtavat]
  (into #{}
    (keep (fn [tehtava]
            (when (puuttuuko-tarjous-maara? tehtava)
              (:tehtava_id tehtava))))
    tehtavat))

(defn- onko-puuttuvia-tarjous-maaria?
  [tehtavat]
  (boolean (some puuttuuko-tarjous-maara? tehtavat)))

(defn- suodata-vain-puuttuvat
  "Suodattaa listan niin, että mukaan jäävät puuttuvat tehtävät ja niiden väliotsikot.
   Huom: Tämä suodatin ei muokkaa tallennettavaa payloadia, vain näkymää."
  [tehtavat lukitut-puuttuvat-tehtava-idt]
  (let [lukitus-kaytossa? (seq lukitut-puuttuvat-tehtava-idt)
        puuttuu? (fn [rivi]
                   (if lukitus-kaytossa?
                     (contains? lukitut-puuttuvat-tehtava-idt (:tehtava_id rivi))
                     (puuttuuko-tarjous-maara? rivi)))
        puuttuvat (filter (fn [rivi]
                            (and (nil? (:valiotsikko rivi))
                                 (some? (:tehtava_id rivi))
                                 (puuttuu? rivi)))
                    tehtavat)
        ryhmat (into #{} (keep :tehtavaryhmaotsikko) puuttuvat)]
    (filter (fn [{:keys [valiotsikko] :as rivi}]
              (or (and (some? valiotsikko)
                       (contains? ryhmat valiotsikko))
                  (and (nil? valiotsikko)
                       (some? (:tehtava_id rivi))
                       (puuttuu? rivi))))
            tehtavat)))

(defn- paivita-naytettavat-tehtavat
  "Päivittää app-staten näkymässä näytettävät tehtävät kaikken tehtävien perusteella.
  Haku (>= 2 merkkiä) ja Näytä vain puuttuvat -suodatin vaikuttavat vain tähän listaan."
  [app]
  (let [kaikki (or (:kaikki-tehtavat app) [])
        haku (:haku app)
        haku-aktiivinen? (and (some? haku) (>= (count haku) 2))
        tehtavat (if haku-aktiivinen?
                   (filtteroi-tehtavat haku kaikki)
                   kaikki)
        tehtavat (if (true? (:nayta-vain-puuttuvat? app))
                   (suodata-vain-puuttuvat tehtavat (:lukitut-puuttuvat-tehtava-idt app))
                   tehtavat)]
    (assoc app :tehtavat-ja-maarat tehtavat)))

(extend-protocol tuck/Event

  HaeTehtavatJaMaarat
  (process-event [{_parametrit :parametrit} app]
    (hae-tehtavat-ja-maarat nil)
    (assoc app :haku-kaynnissa? true))

  HaeTehtavatJaMaaratOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [kaikki-tehtavat (:tehtavat vastaus)
          lukitut-puuttuvat-tehtava-idt (when (true? (:nayta-vain-puuttuvat? app))
                                         (puuttuvat-tarjous-maarat kaikki-tehtavat))]
      (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tallennus-yritetty? false)
      (assoc :kaikki-tehtavat kaikki-tehtavat)
      (cond-> (true? (:nayta-vain-puuttuvat? app))
        (assoc :lukitut-puuttuvat-tehtava-idt lukitut-puuttuvat-tehtava-idt))
      (assoc :tulevat-hoitovuodet-yhteenveto (:tulevat-hoitovuodet-yhteenveto vastaus))
      (assoc :menneet-hoitovuodet-yhteenveto (:menneet-hoitovuodet-yhteenveto vastaus))
      (assoc :kopiointi-tuleville-tehty? false)
      (paivita-naytettavat-tehtavat)
      (assoc :viimeisin-muokkaus (:viimeisin-muokkaus vastaus))
      (assoc :viimeisin-muokkaaja (:viimeisin-muokkaaja vastaus)))))

  HaeTehtavatJaMaaratEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Tietojen hakeminen epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (-> app
      (assoc :haku-kaynnissa? false)))

  TallennaTehtavat
  (process-event [{tehtavat :tehtavat kopioi-tuleville-vuosille? :kopioi-tuleville-vuosille?} app]
    (if (onko-puuttuvia-tarjous-maaria? tehtavat)
      (do
        (viesti/nayta-toast! puuttuva-tarjousmaara-virheviesti :varoitus viesti/viestin-nayttoaika-keskipitka)
        (assoc app :tallennus-yritetty? true))
      (do
        (tuck-apurit/post! :tallenna-tehtavat-ja-maarat
          {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))
           :tehtavat tehtavat
           :kopioi-tuleville-vuosille? kopioi-tuleville-vuosille?
           :valittu-hoitokausi @u/valittu-hoitokausi}
          {:onnistui ->TallennaTehtavatOnnistui
           :epaonnistui ->TallennaTehtavatEpaonnistui
           :paasta-virhe-lapi? true})
        (-> app
          (assoc :tallennus-kaynnissa? true)
          (assoc :viimeisin-tallennus-kopioi-tuleville? (boolean kopioi-tuleville-vuosille?))))))

  TallennaTehtavatOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Tiedot tallennettiin onnistuneesti.")

    (let [kaikki-tehtavat (:tehtavat vastaus)
          lukitut-puuttuvat-tehtava-idt (when (true? (:nayta-vain-puuttuvat? app))
                                         (puuttuvat-tarjous-maarat kaikki-tehtavat))]
      (-> app
      (assoc :tallennus-kaynnissa? false)
      (assoc :tallennustila? false)
      (assoc :tallennus-yritetty? false)
      (assoc :tallentamattomia-muutoksia? false)
      (assoc :kaikki-tehtavat kaikki-tehtavat)
      (cond-> (true? (:nayta-vain-puuttuvat? app))
        (assoc :lukitut-puuttuvat-tehtava-idt lukitut-puuttuvat-tehtava-idt))
      (assoc :kopiointi-tuleville-tehty? (boolean (:viimeisin-tallennus-kopioi-tuleville? app)))
      (paivita-naytettavat-tehtavat)
      (assoc :viimeisin-muokkaus (:viimeisin-muokkaus vastaus))
      (synkronoi-muutokset-muutokset-atomiin!))))

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
                                [] (:kaikki-tehtavat app))]
      (-> app
        (assoc :tallentamattomia-muutoksia? true)
        (assoc :kaikki-tehtavat (sort-by :jarjestys yhdistetyt-tehtavat))
        (paivita-naytettavat-tehtavat)
        (synkronoi-muutokset-muutokset-atomiin!))))

  ToggleTallennusTila
  (process-event [_ app]
    (-> app
      (assoc :tallennustila? (not (:tallennustila? app)))
      (assoc :tallennus-yritetty? false)))

  FiltteroiTehtavat
  (process-event [{hakuehto :hakuehto} app]
    (-> app
      (assoc :haku hakuehto)
      (paivita-naytettavat-tehtavat)))

  PeruutaTallennus
  (process-event [_ app]
    (hae-tehtavat-ja-maarat nil)
    (-> app
      (assoc :tallentamattomia-muutoksia? false)
      (assoc :tallennus-yritetty? false)
      (assoc :tallennustila? (not (:tallennustila? app)))))

  AvaaRivi
  (process-event [{valiotsikko :valiotsikko} app]
    (let [app (if (nil? (:avatut-tehtavaryhmat app))
                (assoc app :avatut-tehtavaryhmat #{})
                app)]
      (if (contains? (:avatut-tehtavaryhmat app) valiotsikko)
        (assoc app :avatut-tehtavaryhmat (disj (:avatut-tehtavaryhmat app) valiotsikko))
        (assoc app :avatut-tehtavaryhmat (conj (:avatut-tehtavaryhmat app) valiotsikko)))))

  ToggleNaytaVainPuuttuvat
  (process-event [_ app]
    (let [uusi-arvo (not (boolean (:nayta-vain-puuttuvat? app)))
          lukitut-puuttuvat-tehtava-idt (when uusi-arvo
                                         (puuttuvat-tarjous-maarat (:kaikki-tehtavat app)))
          puuttuvat-ryhmat (when uusi-arvo
                             (into #{}
                               (keep :tehtavaryhmaotsikko)
                               (filter puuttuuko-tarjous-maara? (:kaikki-tehtavat app))))]
      (-> app
        (assoc :nayta-vain-puuttuvat? uusi-arvo)
        (cond-> uusi-arvo
          (assoc :lukitut-puuttuvat-tehtava-idt lukitut-puuttuvat-tehtava-idt))
        (cond-> (not uusi-arvo)
          (dissoc :lukitut-puuttuvat-tehtava-idt))
        (cond-> puuttuvat-ryhmat
          (update :avatut-tehtavaryhmat (fnil into #{}) puuttuvat-ryhmat))
        (paivita-naytettavat-tehtavat))))

  AsetaPuuttuvatNollaksi
  (process-event [{tehtava-idt :tehtava-idt} app]
    (let [tehtava-idt (or tehtava-idt #{})
          paivitetty (mapv (fn [rivi]
                             (if (and (contains? tehtava-idt (:tehtava_id rivi))
                                   (puuttuuko-tarjous-maara? rivi))
                               (assoc rivi :tarjous_maara 0)
                               rivi))
                       (:kaikki-tehtavat app))
          n (count tehtava-idt)
          nayta-vain-puuttuvat? (true? (:nayta-vain-puuttuvat? app))
          puuttuvia-jaljella? (onko-puuttuvia-tarjous-maaria? paivitetty)
          palaa-kaikkiin? (and nayta-vain-puuttuvat? (not puuttuvia-jaljella?))]
      (when (pos? n)
        (viesti/nayta-toast!
          (if palaa-kaikkiin?
            (str "Asetettiin 0 arvo " n " tehtävälle. Näytetään kaikki tehtävät.")
            (str "Asetettiin 0 arvo " n " tehtävälle."))
          :onnistunut))
      (-> app
        (assoc :tallentamattomia-muutoksia? (or (pos? n) (:tallentamattomia-muutoksia? app)))
        (assoc :kaikki-tehtavat paivitetty)
        (assoc :tallennus-yritetty? false)
        (cond-> palaa-kaikkiin?
          (assoc :nayta-vain-puuttuvat? false)
          palaa-kaikkiin?
          (dissoc :lukitut-puuttuvat-tehtava-idt))
        (paivita-naytettavat-tehtavat)
        (synkronoi-muutokset-muutokset-atomiin!))))

  NollaaTehtavatJaMaaratMuutokset
  (process-event [_ app]
    (-> app
      (assoc :tallentamattomia-muutoksia? false)
      (assoc :tallennus-yritetty? false))))
