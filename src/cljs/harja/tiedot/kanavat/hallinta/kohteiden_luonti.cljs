(ns harja.tiedot.kanavat.hallinta.kohteiden-luonti
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [cljs.core.async :as async :refer [<!]]
            [clojure.set :as set]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tt]
            [namespacefy.core :refer [namespacefy]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]

            [harja.domain.kanavat.kohdekokonaisuus :as kok]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.urakka :as ur]
            [clojure.string :as str]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.kartta.infopaneelin-tila :as paneelin-tila])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce tila (atom {:nakymassa? false
                     :kohteiden-haku-kaynnissa? false
                     :urakoiden-haku-kaynnissa? false
                     :kohdekokonaisuuslomake-auki? false
                     :liittaminen-kaynnissa? false
                     :kohderivit nil
                     :kohdekokonaisuudet nil
                     :karttataso-nakyvissa? false
                     :valittu-urakka nil
                     :uudet-urakkaliitokset {}})) ; Key on vector, jossa [kohde-id urakka-id] ja arvo on boolean

(defonce karttataso-kohteenosat-kohteen-luonnissa (r/cursor tila [:karttataso-nakyvissa?]))
(def uusi-kohde {})

;; Yleiset

(defrecord Nakymassa? [nakymassa?])

;; Grid

(defrecord AloitaUrakoidenHaku [])
(defrecord UrakatHaettu [urakat])
(defrecord UrakatEiHaettu [virhe])

(defrecord HaeKohteet [])
(defrecord KohteetHaettu [tulos])
(defrecord KohteetEiHaettu [virhe])

(defrecord ValitseUrakka [urakka])

(defrecord AsetaKohteenUrakkaliitos [kohde-id urakka-id liitetty?])
(defrecord PaivitaKohteidenUrakkaliitokset [])
(defrecord LiitoksetPaivitetty [tulos])
(defrecord LiitoksetEiPaivitetty [])

;; Lomake

(defrecord AvaaKohdekokonaisuusLomake [])
(defrecord SuljeKohdekokonaisuusLomake [])
(defrecord LisaaKohdekokonaisuuksia [tiedot])

(defrecord TallennaKohdekokonaisuudet [kokonaisuudet])
(defrecord KohdekokonaisuudetTallennettu [tulos])
(defrecord KohdekokonaisuudetEiTallennettu [virhe])

(defrecord ValitseKohde [kohde])
(defrecord KohdettaMuokattu [kohde])
(defrecord TallennaKohde [kohde])
(defrecord KohdeTallennettu [tulos])
(defrecord KohdeEiTallennettu [virhe])

(defrecord HaeKohteenosat [])
(defrecord KohteenosatHaettu [tulos])
(defrecord KohteenosatEiHaettu [virhe])
(defrecord MuokkaaKohteenKohteenosia [osat])
(defrecord KohteenosaKartallaKlikattu [osa])

(defn hae-kanava-urakat! [tulos! fail!]
  (go
    (let [vastaus (<! (k/post! :hallintayksikot
                               {:liikennemuoto :vesi}))
          hy-id (:id (some
                       (fn [hy] (when (= (:nimi hy)
                                         "Kanavat ja avattavat sillat")
                                  hy))
                       vastaus))]
      (if (or (k/virhe? vastaus) (not hy-id))
        (fail! vastaus)

        (let [vastaus (<! (k/post! :hallintayksikon-urakat
                                   hy-id))]
          (if (k/virhe? vastaus)
            (fail! vastaus)

            (tulos! vastaus)))))))

(defn kohderivit [tulos]
  (mapcat
    (fn [kohdekokonaisuus]
      (map
        (fn [kohde]
          ;; Liitetään kohteelle kohdekokonaisuuden (kanavan) tiedot
          ;; Tarvitaan mm. ryhmittelyä varten.
          (-> kohde
              (assoc-in [::kohde/kohdekokonaisuus ::kok/id] (::kok/id kohdekokonaisuus))
              (assoc-in [::kohde/kohdekokonaisuus ::kok/nimi] (::kok/nimi kohdekokonaisuus))))
        (::kok/kohteet kohdekokonaisuus)))
    tulos))

(defn ryhmittele-kohderivit-kanavalla [kohderivit otsikko-fn]
  (let [ryhmitelty (group-by
                     (comp ::kok/nimi ::kohde/kohdekokonaisuus)
                     kohderivit)
        ryhmat (into
                 (sorted-map)
                 ryhmitelty)]
    (mapcat
      (fn [kokonaisuus-nimi]
        (let [ryhman-sisalto (get ryhmat kokonaisuus-nimi)]
          (concat
            [(otsikko-fn kokonaisuus-nimi)]
            (sort-by ::kohde/nimi ryhman-sisalto))))
      (keys ryhmat))))

(defn kohdekokonaisuudet [tulos]
  (sort-by ::kok/nimi
           (-> (map #(select-keys % #{::kok/id ::kok/nimi}) tulos)
               set
               vec)))

(defn kohdekokonaisuudet-voi-tallentaa? [kokonaisuudet]
  (boolean
    (and (every? #(not-empty (::kok/nimi %))
                 (remove :poistettu kokonaisuudet)))))

(defn kohteen-voi-tallentaa? [kohde]
  (boolean
    (and (some? (::kohde/kohdekokonaisuus kohde))
         (not-empty (::kohde/nimi kohde))
         (not-empty (::kohde/kohteenosat kohde)))))

(defn kohdekokonaisuudet-tallennusparametrit [kok]
  (as-> kok $
        (remove :koskematon $)
        (map #(set/rename-keys % {:id ::kok/id
                                  :poistettu ::m/poistettu?})
             $)))

(defn tallennusparametrit-kohde [kohde]
  (-> kohde
      (assoc ::kohde/kohdekokonaisuus-id (get-in kohde [::kohde/kohdekokonaisuus ::kok/id]))
      (dissoc ::kohde/kohdekokonaisuus
              ::kohde/urakat)
      (update ::kohde/kohteenosat
              (fn [osat]
                (map
                  #(-> %
                       (set/rename-keys {:poistettu ::m/poistettu?})
                       (dissoc ::osa/sijainti
                               ::osa/kohde
                               :vanha-kohde
                               ;; kartan tiedot
                               :sijainti
                               :type
                               :nimi
                               :selite
                               :alue
                               :tyyppi-kartalla))
                  osat)))))

(defn kohteen-urakat [kohde]
  (str/join ", " (sort (map ::ur/nimi (::kohde/urakat kohde)))))

(defn kohde-kuuluu-urakkaan? [app kohde urakka]
  (let [kohteella-ui-urakkaliitos? (contains? (:uudet-urakkaliitokset app)
                                              [(::kohde/id kohde) (::ur/id urakka)])
        kohteen-ui-urakkaliitos (get (:uudet-urakkaliitokset app) [(::kohde/id kohde) (::ur/id urakka)])]
    ;; Ensisijaisesti tutkitaan käyttäjän asettamat, tallentamattomat linkit.
    ;; Sen jälkeen tutkitaan, kuuluuko kohde urakkaan kannan palauttaman datan perusteella
    (if kohteella-ui-urakkaliitos?
      kohteen-ui-urakkaliitos
      (boolean
        ((set (map ::ur/id (::kohde/urakat kohde))) (::ur/id urakka))))))

(defn kohteet-haettu [app tulos]
  (-> app
      (assoc :kohderivit (kohderivit tulos))
      (assoc :kohdekokonaisuudet (kohdekokonaisuudet tulos))))

(defn kohteiden-lkm-kokonaisuudessa [{:keys [kohderivit] :as app} kokonaisuus]
  (let [kokonaisuus-id (::kok/id kokonaisuus)]
    (count (filter #(= (get-in % [::kohde/kohdekokonaisuus ::kok/id]) kokonaisuus-id) kohderivit))))

(defn kokonaisuuden-voi-poistaa? [app kokonaisuus]
  (= 0 (kohteiden-lkm-kokonaisuudessa app kokonaisuus)))

(defn osa-kuuluu-valittuun-kohteeseen? [osa {:keys [valittu-kohde]}]
  (boolean
    ((set (map ::osa/id (remove :poistettu (::kohde/kohteenosat valittu-kohde))))
      (::osa/id osa))))

(defonce kohteenosat-kartalla
  (reaction
    (when (:valittu-kohde @tila)
      (kartalla-esitettavaan-muotoon
        (map #(-> %
                  (set/rename-keys {::osa/sijainti :sijainti})
                  (assoc :tyyppi-kartalla :kohteenosa))
             (:haetut-kohteenosat @tila))
        #(osa-kuuluu-valittuun-kohteeseen? % @tila)))))

(defn kohteenosan-infopaneeli-otsikko [app osa]
  (cond
    (and
      (some? (get-in app [:valittu-kohde ::kohde/id]))
      (some? (get-in osa [::osa/kohde ::kohde/id]))
      (= (get-in app [:valittu-kohde ::kohde/id])
         (get-in osa [::osa/kohde ::kohde/id])))
    "Irroita"

    (some? (get-in osa [::osa/kohde ::kohde/id]))
    (str "Irroita kohteesta " (get-in osa [::osa/kohde ::kohde/nimi]) " & Liitä")

    :default
    "Liitä kohteeseen"))

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (let [uudet-urakkaliitokset (if (false? nakymassa?)
                                  {}
                                  (:uudet-urakkaliitokset app))]
      (assoc app :nakymassa? nakymassa?
                 :uudet-urakkaliitokset uudet-urakkaliitokset
                 :karttataso-nakyvissa? (if-not nakymassa?
                                          false
                                          (some? (:valittu-kohde app))))))

  AloitaUrakoidenHaku
  (process-event [_ app]
    (hae-kanava-urakat! (tuck/send-async! ->UrakatHaettu)
                        (tuck/send-async! ->UrakatEiHaettu))
    (assoc app :urakoiden-haku-kaynnissa? true))

  UrakatHaettu
  (process-event [{ur :urakat} app]
    (-> app
        (assoc :urakoiden-haku-kaynnissa? false)
        (assoc :urakat (as-> ur $
                             (map #(select-keys % [:id :nimi]) $)
                             (namespacefy $ {:ns :harja.domain.urakka})))))

  UrakatEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Virhe urakoiden haussa!" :danger)
    (assoc app :urakoiden-haku-kaynnissa? false))

  HaeKohteet
  (process-event [_ app]
    (if-not (:kohteiden-haku-kaynnissa? app)
      (-> app
          (tt/get! :hae-kohdekokonaisuudet-ja-kohteet
                   {:onnistui ->KohteetHaettu
                    :epaonnistui ->KohteetEiHaettu})
          (assoc :kohteiden-haku-kaynnissa? true))

      app))

  KohteetHaettu
  (process-event [{tulos :tulos} app]
    (-> app
        (kohteet-haettu tulos)
        (assoc :kohteiden-haku-kaynnissa? false)))

  KohteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Kohteiden haku epäonnistui!" :danger)
    (-> app
        (assoc :kohteiden-haku-kaynnissa? false)))

  ValitseUrakka
  (process-event [{ur :urakka} app]
    (assoc app :valittu-urakka ur))

  AsetaKohteenUrakkaliitos
  (process-event [{kohde-id :kohde-id
                   urakka-id :urakka-id
                   liitetty? :liitetty?}
                  app]
    (let [liitokset (:uudet-urakkaliitokset app)]
      (assoc app :uudet-urakkaliitokset
                 (assoc liitokset
                   [kohde-id urakka-id]
                   liitetty?))))

  PaivitaKohteidenUrakkaliitokset
  (process-event [_ app]
    (tt/post! :liita-kohteet-urakkaan
              {:liitokset (:uudet-urakkaliitokset app)}
              {:onnistui ->LiitoksetPaivitetty
               :epaonnistui ->LiitoksetEiPaivitetty})
    (assoc app :liittaminen-kaynnissa? true))

  LiitoksetPaivitetty
  (process-event [{tulos :tulos} app]
    (viesti/nayta! "Kohteiden urakkaliitokset tallennettu." :success)
    (assoc app :liittaminen-kaynnissa? false
               :uudet-urakkaliitokset {}
               :kohderivit (kohderivit tulos)))


  LiitoksetEiPaivitetty
  (process-event [{kohde-id :kohde urakka-id :urakka} app]
    (viesti/nayta! "Virhe urakkaliitoksien tallennuksessa!" :danger)
    (assoc app :liittaminen-kaynnissa? false))

  AvaaKohdekokonaisuusLomake
  (process-event [_ app]
    (assoc app :kohdekokonaisuuslomake-auki? true))

  SuljeKohdekokonaisuusLomake
  (process-event [_ app]
    (-> app
        (update :kohdekokonaisuudet #(filter (comp id-olemassa? ::kok/id) %))
        (update :kohdekokonaisuudet (fn [k] (map #(dissoc % :poistettu) k)))
        (assoc :kohdekokonaisuuslomake-auki? false)))

  LisaaKohdekokonaisuuksia
  (process-event [{tiedot :tiedot} app]
    (assoc-in app [:kohdekokonaisuudet] tiedot))

  TallennaKohdekokonaisuudet
  (process-event [{kokonaisuudet :kokonaisuudet} app]
    (if-not (:kohdekokonaisuuksien-tallennus-kaynnissa? app)
      (-> app
          (tt/post! :tallenna-kohdekokonaisuudet
                    (kohdekokonaisuudet-tallennusparametrit kokonaisuudet)
                    {:onnistui ->KohdekokonaisuudetTallennettu
                     :epaonnistui ->KohdekokonaisuudetEiTallennettu})

          (assoc :kohdekokonaisuuksien-tallennus-kaynnissa? true))

      app))

  KohdekokonaisuudetTallennettu
  (process-event [{tulos :tulos} app]
    (-> app
        (kohteet-haettu tulos)
        (assoc :kohdekokonaisuuslomake-auki? false)
        (assoc :kohdekokonaisuuksien-tallennus-kaynnissa? false)))

  KohdekokonaisuudetEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Kohdekokonaisuuksien tallennus epäonnistui!" :danger)
    (-> app
        (assoc :kohdekokonaisuuksien-tallennus-kaynnissa? false)))

  ValitseKohde
  (process-event [{kohde :kohde} app]
    (if (some? kohde)
      (nav/vaihda-kartan-koko! :M)
      (nav/vaihda-kartan-koko! :S))
    (-> app
        (assoc :valittu-kohde kohde)
        (assoc :karttataso-nakyvissa? (if (some? kohde) true false))))

  KohdettaMuokattu
  (process-event [{kohde :kohde} app]
    (assoc app :valittu-kohde kohde))

  TallennaKohde
  (process-event [{kohde :kohde} app]
    (if-not (:kohteen-tallennus-kaynnissa? app)
      (-> app
          (tt/post! :tallenna-kohde
                    (tallennusparametrit-kohde kohde)
                    {:onnistui ->KohdeTallennettu
                     :epaonnistui ->KohdeEiTallennettu})

          (assoc :kohteen-tallennus-kaynnissa? true))

      app))

  KohdeTallennettu
  (process-event [{tulos :tulos} app]
    (-> app
        (kohteet-haettu tulos)
        (assoc :valittu-kohde nil)
        (assoc :kohteen-tallennus-kaynnissa? false)))

  KohdeEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Kohteen epäonnistui!" :danger)
    (-> app
        (assoc :kohteen-tallennus-kaynnissa? false)))

  HaeKohteenosat
  (process-event [_ app]
    (if-not (:kohteenosien-haku-kaynnissa? app)
      (-> app
          (tt/get! :hae-kohteenosat
                   {:onnistui ->KohteenosatHaettu
                    :epaonnistui ->KohteenosatEiHaettu})

          (assoc :kohteenosien-haku-kaynnissa? true))

      app))

  KohteenosatHaettu
  (process-event [{tulos :tulos} app]
    (-> app
        (assoc :kohteenosien-haku-kaynnissa? false)
        (assoc :haetut-kohteenosat tulos)))

  KohteenosatEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Kohteenosien haku epäonnistui!" :danger)
    (-> app
        (assoc :kohteenosien-haku-kaynnissa? false)))

  MuokkaaKohteenKohteenosia
  (process-event [{osat :osat} app]
    (assoc-in app [:valittu-kohde ::kohde/kohteenosat] osat))

  KohteenosaKartallaKlikattu
  (process-event [{osa :osa} app]
    (paneelin-tila/piilota-infopaneeli!)
    (if (osa-kuuluu-valittuun-kohteeseen? osa app)
      ;; Irrota kohteesta
      (-> app
          (update-in [:valittu-kohde ::kohde/kohteenosat]
                     (fn [kohteen-osat]
                       (map #(if (= (::osa/id %) (::osa/id osa))
                               (assoc % :poistettu true)
                               %)
                            kohteen-osat)))
          (update :haetut-kohteenosat
                  (fn [haetut-osat]
                    (map #(if (= (::osa/id %) (::osa/id osa))
                            ;; Estetään virheklikkaukset - lomakkeella
                            ;; ollessa osalle tallennetaan :vanha-kohde, eli jos
                            ;; osa kuului ennen virheellistä liittämistä toiseen kohteeseen,
                            ;; liitetään se irrottaessa siihen takaisin.
                            (assoc % ::osa/kohde (:vanha-kohde %))
                            %)
                         haetut-osat))))

      ;; Liitä kohteeseen
      (-> app
          (update-in [:valittu-kohde ::kohde/kohteenosat]
                     (fn [kohteen-osat]
                       (conj kohteen-osat (assoc osa ::osa/kohde (:valittu-kohde app)
                                                     :vanha-kohde (::osa/kohde osa)))))
          (update :haetut-kohteenosat
                  (fn [haetut-osat]
                    (map #(if (= (::osa/id %) (::osa/id osa))
                            (assoc % ::osa/kohde (:valittu-kohde app)
                                     :vanha-kohde (::osa/kohde osa))
                            %)
                         haetut-osat)))))))
