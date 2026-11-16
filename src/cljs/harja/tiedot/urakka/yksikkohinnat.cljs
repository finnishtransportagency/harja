(ns harja.tiedot.urakka.yksikkohinnat
  "Yksikköhinnat-näkymän Tuck-tila ja tapahtumat."
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.tiedot.urakka.suunnittelu :as suunnittelu]))

(defonce yksikkohinnat-app-tila
  (atom {:urakka nil
         :urakan-hoitokaudet []
         :valittu-hoitokausi nil
         :yksikkohintaiset-tyot nil
         :toimenpiteet-ja-tehtavat nil
         :haku-kaynnissa? false
         :virhe nil}))

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

(extend-protocol tuck/Event

  Alusta
  (process-event [{urakka :urakka} app]
    (assoc app
      :urakka urakka
      :urakan-hoitokaudet @urakka-tiedot/valitun-urakan-hoitokaudet
      :valittu-hoitokausi @urakka-tiedot/valittu-hoitokausi
      :yksikkohintaiset-tyot nil
      :toimenpiteet-ja-tehtavat nil
      :haku-kaynnissa? true
      :virhe nil))

  HaeYksikkohintaisetTyot
  (process-event [{parametrit :parametrit} app]
    (hae-yksikkohintaiset-tyot parametrit)
    (assoc app :haku-kaynnissa? true))

  HaeYksikkohintaisetTyotOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [tyot (suunnittelu/prosessoi-tyorivit
                 (:urakka app)
                 vastaus)
          [alkupvm loppupvm] (:valittu-hoitokausi app)]
      (assoc app
        :yksikkohintaiset-tyot
        (filterv #(and
                    (pvm/sama-pvm? (:alkupvm %) alkupvm)
                    (pvm/sama-pvm? (:loppupvm %) loppupvm))
          tyot)
        :haku-kaynnissa? false
        :haku (:haku app)
        :viimeisin-muokkaus (:viimeisin-muokkaus vastaus)
        :viimeisin-muokkaaja (:viimeisin-muokkaaja vastaus))))

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
          nykyiset-rivit (vec (:yksikkohintaiset-tyot app))
          muokatut-rivit
          (->> tehtavat
            (filter :tehtava)
            (map (fn [rivi]
                   (merge rivi
                     {:urakka (:id urakka)
                      :sopimus sopimus
                      :alkupvm (or (:alkupvm rivi) alkupvm)
                      :loppupvm (or (:loppupvm rivi) loppupvm)}))))]
      (-> app
        (assoc :yksikkohintaiset-tyot
          (reduce
            (fn [rivit uusi-rivi]
              (let [avain [(:tehtava uusi-rivi)
                           (:alkupvm uusi-rivi)
                           (:loppupvm uusi-rivi)]
                    olemassa-oleva-indeksi
                    (some (fn [[indeksi rivi]]
                            (when (= avain
                                    [(:tehtava rivi)
                                     (:alkupvm rivi)
                                     (:loppupvm rivi)])
                              indeksi))
                      (map-indexed vector rivit))]
                (if (some? olemassa-oleva-indeksi)
                  (assoc rivit olemassa-oleva-indeksi uusi-rivi)
                  (conj rivit uusi-rivi))))
            nykyiset-rivit
            muokatut-rivit))
        (assoc :tallentamattomia-muutoksia? true))))

  TallennaYksikkohinnat
  (process-event [{yksikkohintaiset-tyot :yksikkohintaiset-tyot} {:keys [urakka] :as app}]
    (if (:tallennus-kaynnissa? app)
      app
      (do
        (tuck-apurit/post!
          app
          :tallenna-urakan-yksikkohintaiset-tyot
          {:urakka-id (:id urakka)
           :sopimusnumero (first @urakka-tiedot/valittu-sopimusnumero)
           :tyot (muodosta-tallennettavat-tyot urakka yksikkohintaiset-tyot)}
          {:onnistui ->TallennaYksikkohinnatOnnistui
           :epaonnistui ->TallennaYksikkohinnatEpaonnistui})
        (assoc app :tallennus-kaynnissa? true))))

  TallennaYksikkohinnatOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Tiedot tallennettiin onnistuneesti.")
    (-> app
      (assoc :tallennus-kaynnissa? false)
      (assoc :tallennustila? false)
      (assoc :tallentamattomia-muutoksia? false)
      (assoc :viimeisin-muokkaus (:viimeisin-muokkaus vastaus))))

  TallennaYksikkohinnatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Tietojen tallentaminen epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (-> app
      (assoc :tallennus-kaynnissa? false)))

  AsetaKaikkiIndeksille
  (process-event [{yksikkohintaiset-tyot :yksikkohintaiset-tyot} app])

  HoitokausiVaihdettu
  (process-event [{hoitokausi :hoitokausi} app]
    (hae-yksikkohintaiset-tyot nil)
    (assoc app
      :valittu-hoitokausi hoitokausi
      :yksikkohintaiset-tyot nil
      :haku-kaynnissa? true))

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
