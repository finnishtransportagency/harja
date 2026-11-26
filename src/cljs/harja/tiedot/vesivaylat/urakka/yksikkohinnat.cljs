(ns harja.tiedot.vesivaylat.urakka.yksikkohinnat
  "Yksikköhinnat-näkymän Tuck-tila ja tapahtumat."
  (:require [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.tiedot.urakka.suunnittelu :as suunnittelu]))

(defrecord Alusta [urakka])
(defrecord HaeYksikkohintaisetTyot [urakka-id])
(defrecord HaeYksikkohintaisetTyotOnnistui [vastaus])
(defrecord HaeYksikkohintaisetTyotEpaonnistui [vastaus])
(defrecord HaeToimenpiteetJaTehtavat [urakka-id])
(defrecord HaeToimenpiteetJaTehtavatOnnistui [vastaus])
(defrecord HaeToimenpiteetJaTehtavatEpaonnistui [vastaus])
(defrecord PaivitaYksikkohinnatGrid [tehtavat])
(defrecord TallennaYksikkohinnat [yksikkohintaiset-tyot kopioi-tuleville-vuosille?])
(defrecord TallennaYksikkohinnatOnnistui [vastaus])
(defrecord TallennaYksikkohinnatEpaonnistui [vastaus])
(defrecord AsetaKaikkiIndeksille [yksikkohintaiset-tyot])
(defrecord HoitokausiVaihdettu [hoitokausi])
(defrecord HaeUrakanIndeksikertoimetOnnistui [vastaus])
(defrecord HaeUrakanIndeksikertoimetEpaonnistui [vastaus])
(defrecord ToggleTallennusTila [])
(defrecord PeruutaTallennus [])
(defrecord AvaaRivi [valiotsikko])

(defn hae-yksikkohintaiset-tyot [_parametrit]
  (tuck-apurit/post!
    :yksikkohintaiset-tyot
    (:id (-> @tiedot/tila :yleiset :urakka))
    {:onnistui ->HaeYksikkohintaisetTyotOnnistui
     :epaonnistui ->HaeYksikkohintaisetTyotEpaonnistui}))

(defn- muodosta-tallennettavat-tyot [urakka tyot]
  (if (= :hoito (:tyyppi urakka))
    (into []
          (mapcat
            (fn [rivi]
              (suunnittelu/jaa-rivien-hoitokaudet
                [rivi]
                #(-> %
                     (assoc :maara (:maara-kkt-10-12 %))
                     (dissoc :maara-kkt-10-12 :maara-kkt-1-9))
                #(-> %
                     (assoc :maara (:maara-kkt-1-9 %))
                     (dissoc :maara-kkt-10-12 :maara-kkt-1-9)))))
          tyot)
    (into [] tyot)))

(defn- yksikkohinnan-avain [rivi]
  [(:tehtava rivi)
   (:alkupvm rivi)
   (:loppupvm rivi)
   (:sopimus rivi)])

(defn- poista-yksikkohintojen-duplikaatit [rivit]
  (reduce
    (fn [tulos rivi]
      (let [avain (yksikkohinnan-avain rivi)
            indeksi (some (fn [[i olemassa-oleva]]
                            (when (= avain
                                    (yksikkohinnan-avain olemassa-oleva))
                              i))
                      (map-indexed vector tulos))]
        (if (some? indeksi)
          (assoc tulos indeksi rivi)
          (conj tulos rivi))))
    []
    rivit))

(defn- urakkavuosi-nro [urakka hoitokausi]
  (let [urakan-aloitusvuosi (pvm/vuosi (:alkupvm urakka))
        urakkavuoden-alkuvuosi (pvm/vuosi (first hoitokausi))]
    (max 1
      (- (inc urakkavuoden-alkuvuosi)
        urakan-aloitusvuosi))))

(defn- indeksikorjattu-hinta [rivi indeksikerroin urakkavuosi-nro]
  (let [hinta (:yksikkohinta rivi)]
    (when (number? hinta)
      (if (and (true? (:indeksi_kaytossa rivi))
            (> urakkavuosi-nro 1)
            (number? indeksikerroin))
        (* hinta indeksikerroin)
        hinta))))

(extend-protocol tuck/Event

  Alusta
  (process-event [{urakka :urakka} app]
    (do
      (tuck-apurit/post!
        app
        :urakan-indeksikertoimet
        (:id urakka)
        {:onnistui ->HaeUrakanIndeksikertoimetOnnistui
         :epaonnistui ->HaeUrakanIndeksikertoimetEpaonnistui})

      (assoc app
        :urakka urakka
        :urakan-hoitokaudet @urakka-tiedot/valitun-urakan-hoitokaudet
        :valittu-hoitokausi @urakka-tiedot/valittu-hoitokausi
        :yksikkohintaiset-tyot nil
        :kaikki-yksikkohintaiset-tyot nil
        :toimenpiteet-ja-tehtavat nil
        :urakan-indeksikertoimet []
        :haku-kaynnissa? true
        :virhe nil)))

  HaeYksikkohintaisetTyot
  (process-event [{parametrit :parametrit} app]
    (hae-yksikkohintaiset-tyot parametrit)
    (assoc app :haku-kaynnissa? true))

  HaeYksikkohintaisetTyotOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [viimeisin-rivi (first vastaus)
          tyot (suunnittelu/prosessoi-tyorivit
                 (:urakka app)
                 vastaus)
          [alkupvm loppupvm] (:valittu-hoitokausi app)]
      (assoc app
        :kaikki-yksikkohintaiset-tyot tyot
        :yksikkohintaiset-tyot (filterv
                                 #(and (pvm/sama-pvm? (:alkupvm %) alkupvm)
                                    (pvm/sama-pvm? (:loppupvm %) loppupvm))
                                 tyot)
        :haku-kaynnissa? false
        :haku (:haku app)
        :viimeisin-muokkaus (:viimeisin-muokkaus viimeisin-rivi)
        :viimeisin-muokkaaja (:viimeisin-muokkaaja viimeisin-rivi))))

  HaeYksikkohintaisetTyotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast!
      "Yksikköhintaisten töiden haku epäonnistui."
      :varoitus)
    (assoc app
      :haku-kaynnissa? false
      :virhe vastaus))

  HaeToimenpiteetJaTehtavat
  (process-event [{urakka-id :urakka-id} app]
    (tuck-apurit/post!
      app
      :urakan-yksikkohintaiset-toimenpiteet-ja-tehtavat
      urakka-id
      {:onnistui ->HaeToimenpiteetJaTehtavatOnnistui
       :epaonnistui ->HaeToimenpiteetJaTehtavatEpaonnistui}))

  HaeToimenpiteetJaTehtavatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :toimenpiteet-ja-tehtavat vastaus
      :haku-kaynnissa? false))

  HaeToimenpiteetJaTehtavatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast!
      "Toimenpiteiden ja tehtävien haku epäonnistui."
      :varoitus)
    (assoc app
      :haku-kaynnissa? false
      :virhe vastaus))

  PaivitaYksikkohinnatGrid
  (process-event [{:keys [tehtavat]} {:keys [urakka valittu-hoitokausi] :as app}]
    (let [[alkupvm loppupvm] valittu-hoitokausi
          sopimus (first @urakka-tiedot/valittu-sopimusnumero)
          urakkavuosi-nro (urakkavuosi-nro urakka valittu-hoitokausi)
          indeksikerroin (get-in app
                           [:urakan-indeksikertoimet
                            (dec urakkavuosi-nro)
                            :indeksikerroin])
          nykyiset-rivit (vec (:yksikkohintaiset-tyot app))
          muokatut-rivit (->> tehtavat
                           (filter :tehtava)
                           (map (fn [rivi]
                                  (let [rivi (merge rivi
                                               {:urakka (:id urakka)
                                                :sopimus sopimus
                                                :alkupvm (or (:alkupvm rivi) alkupvm)
                                                :loppupvm (or (:loppupvm rivi) loppupvm)})]
                                    (assoc rivi
                                      :indeksikorjattu_yksikkohinta
                                      (indeksikorjattu-hinta
                                        rivi
                                        indeksikerroin
                                        urakkavuosi-nro))))))
          paivitetyt-rivit (reduce
                              (fn [rivit uusi-rivi]
                                (let [avain (yksikkohinnan-avain uusi-rivi)
                                      indeksi (some (fn [[i rivi]]
                                                      (when (= avain
                                                              (yksikkohinnan-avain rivi))
                                                        i))
                                                (map-indexed vector rivit))]
                                  (if (some? indeksi)
                                    (assoc rivit indeksi uusi-rivi)
                                    (conj rivit uusi-rivi))))
                              nykyiset-rivit
                              muokatut-rivit)]
      (-> app
        (assoc :yksikkohintaiset-tyot
          (poista-yksikkohintojen-duplikaatit paivitetyt-rivit)
          :tallentamattomia-muutoksia? true))))

  TallennaYksikkohinnat
  (process-event [{yksikkohintaiset-tyot :yksikkohintaiset-tyot kopioi-tuleville-vuosille? :kopioi-tuleville-vuosille?}
                  {:keys [urakka] :as app}]
    (if (:tallennus-kaynnissa? app)
      app
      (do
        (tuck-apurit/post!
          app
          :tallenna-urakan-yksikkohintaiset-tyot
          {:urakka-id (:id urakka)
           :sopimusnumero (first @urakka-tiedot/valittu-sopimusnumero)
           :tyot (muodosta-tallennettavat-tyot urakka yksikkohintaiset-tyot)
           :kopioi-tuleville-vuosille? kopioi-tuleville-vuosille?}
          {:onnistui ->TallennaYksikkohinnatOnnistui
           :epaonnistui ->TallennaYksikkohinnatEpaonnistui})
        (assoc app :tallennus-kaynnissa? true))))

  TallennaYksikkohinnatOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [viimeisin-rivi (first vastaus)
          kaikki-tyot (suunnittelu/prosessoi-tyorivit
                        (:urakka app)
                        vastaus)]
      (viesti/nayta-toast! "Yksikköhinnat tallennettiin onnistuneesti.")
      (-> app
        (assoc :kaikki-yksikkohintaiset-tyot kaikki-tyot)
        (assoc :tallennus-kaynnissa? false)
        (assoc :tallennustila? false)
        (assoc :tallentamattomia-muutoksia? false)
        (assoc :viimeisin-muokkaus
          (:viimeisin_muokkaus viimeisin-rivi))
        (assoc :viimeisin-muokkaaja
          (:viimeisin_muokkaaja viimeisin-rivi)))))

  TallennaYksikkohinnatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Tietojen tallentaminen epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (-> app
      (assoc :tallennus-kaynnissa? false)))

  AsetaKaikkiIndeksille
  (process-event [{:keys [yksikkohintaiset-tyot]}
                  {:keys [valittu-hoitokausi] :as app}]
    (let [[alkupvm loppupvm] valittu-hoitokausi
          sopimus (first @urakka-tiedot/valittu-sopimusnumero)
          paivitetyt-tyot
          (mapv
            (fn [rivi]
              (-> rivi
                (assoc :alkupvm (or (:alkupvm rivi) alkupvm)
                  :loppupvm (or (:loppupvm rivi) loppupvm)
                  :sopimus sopimus)
                (assoc :indeksi_kaytossa true)))
            yksikkohintaiset-tyot)]
      (assoc app
        :yksikkohintaiset-tyot paivitetyt-tyot
        :tallentamattomia-muutoksia? true)))

  HoitokausiVaihdettu
  (process-event [{hoitokausi :hoitokausi} app]
    (hae-yksikkohintaiset-tyot nil)
    (assoc app
      :valittu-hoitokausi hoitokausi
      :yksikkohintaiset-tyot nil
      :haku-kaynnissa? true))

  HaeUrakanIndeksikertoimetOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :urakan-indeksikertoimet vastaus))

  HaeUrakanIndeksikertoimetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast!
      "Urakan indeksikertoimien haku epäonnistui."
      :varoitus)
    (assoc app :virhe vastaus))

  ToggleTallennusTila
  (process-event [_ app]
    (assoc app :tallennustila? (not (:tallennustila? app))))

  PeruutaTallennus
  (process-event [_ app]
    (hae-yksikkohintaiset-tyot nil)
    (-> app
      (assoc :tallentamattomia-muutoksia? false)
      (assoc :tallennustila? (not (:tallennustila? app)))))

  AvaaRivi
  (process-event [{valiotsikko :valiotsikko} app]
    (let [avatut (or (:avatut-tehtavaryhmat app) #{})]
      (assoc app
        :avatut-tehtavaryhmat
        (if (contains? avatut valiotsikko)
          (disj avatut valiotsikko)
          (conj avatut valiotsikko))))))
