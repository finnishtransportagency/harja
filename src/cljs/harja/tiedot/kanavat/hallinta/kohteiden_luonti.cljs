(ns harja.tiedot.kanavat.hallinta.kohteiden-luonti
  (:require [reagent.core :refer [atom]]
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

            [harja.domain.kanavat.kohdekokonaisuus :as kok]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.urakka :as ur]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tila (atom {:nakymassa? false
                 :kohteiden-haku-kaynnissa? false
                 :urakoiden-haku-kaynnissa? false
                 :kohdelomake-auki? false
                 :liittaminen-kaynnissa {}
                 :kohderivit nil
                 :kanavat nil
                 :lomakkeen-tiedot nil
                 :valittu-urakka nil
                 :uudet-urakkaliitokset {}})) ; Key on vector, jossa [kohde-id urakka-id] ja arvo on boolean

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
(defrecord PaivitaKohteidenUrakkaliitokset [kohde liita? urakka])
(defrecord LiitoksetPaivitetty [tulos kohde urakka])
(defrecord LiitoksetEiPaivitetty [virhe kohde urakka])

;; Lomake

(defrecord AvaaKohdeLomake [])
(defrecord SuljeKohdeLomake [])
(defrecord ValitseKanava [kanava])
(defrecord LisaaKohteita [tiedot])

(defrecord TallennaKohteet [])
(defrecord KohteetTallennettu [tulos])
(defrecord KohteetEiTallennettu [virhe])

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
              (assoc ::kok/id (::kok/id kohdekokonaisuus))
              (assoc ::kok/nimi (::kok/nimi kohdekokonaisuus))))
        (::kok/kohteet kohdekokonaisuus)))
    tulos))

(defn kanavat [tulos]
  (map #(select-keys % #{::kok/id ::kok/nimi}) tulos))

(defn kohteet-voi-tallentaa? [kohteet]
  (boolean
    (and (:kanava kohteet)
         (not-empty (:kohteet kohteet))
         (every?
           (fn [kohde]
             (and (::kohde/tyyppi kohde)))
           (:kohteet kohteet)))))

(defn muokattavat-kohteet [app]
  (get-in app [:lomakkeen-tiedot :kohteet]))

(defn tallennusparametrit [lomake]
  (let [kanava-id (get-in lomake [:kanava ::kok/id])
        params (->> (:kohteet lomake)
                    (map #(assoc % ::kohde/kanava-id kanava-id))
                    (map #(set/rename-keys % {:id ::kohde/id
                                              :poistettu ::m/poistettu?}))
                    (map #(select-keys
                            %
                            [::kohde/nimi
                             ::kohde/id
                             ::kohde/kanava-id
                             ::kohde/tyyppi
                             ::m/poistettu?])))]
    params))

(defn kohteen-urakat [kohde]
  (str/join ", " (sort (map ::ur/nimi (::kohde/urakat kohde)))))

(defn kohde-kuuluu-urakkaan? [app kohde urakka]
  (boolean
    ;; Kohde kuuluu urakkaan, jos se on siihen merkitty, tai jos se on käyttöliittymässä
    ;; asetettu (muttei vielä tallennettu)
    (or ((into #{} (::kohde/urakat kohde)) urakka)
        (get (:uudet-urakkaliitokset app) [(::kohde/id kohde) (::ur/id urakka)]))))

(defn poista-kohde [kohteet kohde]
  (into [] (disj (into #{} kohteet) kohde)))

(defn lisaa-kohteelle-urakka [app muokattava-kohde urakka liita?]
  (update app :kohderivit
          (fn [kohteet]
            (map
              (fn [kohde]
                (if (= (::kohde/id kohde) (::kohde/id muokattava-kohde))
                  (if liita?
                    (update kohde ::kohde/urakat conj urakka)

                    (update kohde ::kohde/urakat
                            (fn [urakat]
                              (into
                                []
                                (disj (into #{} urakat) urakka)))))

                  kohde))
              kohteet))))

(defn kaynnista-liittaminen [app kohde-id urakka-id]
  (update app :liittaminen-kaynnissa
          (fn [kohde-ja-urakat]
            (let [kohde-ja-urakat (or kohde-ja-urakat {})]
              (if (kohde-ja-urakat kohde-id)
                (update kohde-ja-urakat kohde-id conj urakka-id)

                (assoc kohde-ja-urakat kohde-id #{urakka-id}))))))

(defn lopeta-liittaminen [app kohde-id urakka-id]
  (update app :liittaminen-kaynnissa
          (fn [kohde-ja-urakat]
            (when (kohde-ja-urakat kohde-id)
              (update kohde-ja-urakat kohde-id disj urakka-id)))))

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

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
        (assoc :kohderivit (kohderivit tulos))
        (assoc :kanavat (kanavat tulos))
        (assoc :kohteiden-haku-kaynnissa? false)))

  KohteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Kohteiden haku epäonnistui!" :danger)
    (-> app
        (assoc :kohteiden-haku-kaynnissa? false)))

  AvaaKohdeLomake
  (process-event [_ app]
    (assoc app :kohdelomake-auki? true))

  SuljeKohdeLomake
  (process-event [_ app]
    (-> app
        (assoc :kohdelomake-auki? false)
        (assoc :lomakkeen-tiedot nil)))

  ValitseKanava
  (process-event [{kanava :kanava} app]
    (-> app
        (assoc-in [:lomakkeen-tiedot :kanava] kanava)
        (assoc-in [:lomakkeen-tiedot :kohteet]
                  (filter
                    (fn [kohde]
                      (= (::kok/id kohde)
                         (::kok/id kanava)))
                    (:kohderivit app)))))

  LisaaKohteita
  (process-event [{tiedot :tiedot} app]
    (assoc-in app [:lomakkeen-tiedot :kohteet] tiedot))

  TallennaKohteet
  (process-event [_ {tiedot :lomakkeen-tiedot :as app}]
    (if-not (:kohteiden-tallennus-kaynnissa? app)
      (-> app
          (tt/post! :lisaa-kanavalle-kohteita
                    (tallennusparametrit tiedot)
                    {:onnistui ->KohteetTallennettu
                     :epaonnistui ->KohteetEiTallennettu})

          (assoc :kohteiden-tallennus-kaynnissa? true))

      app))

  KohteetTallennettu
  (process-event [{tulos :tulos} app]
    (-> app
        (assoc :kohderivit (kohderivit tulos))
        (assoc :kanavat (kanavat tulos))
        (assoc :kohdelomake-auki? false)
        (assoc :lomakkeen-tiedot nil)
        (assoc :kohteiden-tallennus-kaynnissa? false)))

  KohteetEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Kohteiden tallennus epäonnistui!" :danger)
    (-> app
        (assoc :kohteiden-tallennus-kaynnissa? false)))

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

  ValitseUrakka
  (process-event [{ur :urakka} app]
    (assoc app :valittu-urakka ur))

  AsetaKohteenUrakkaliitos
  (process-event [{kohde-id :kohde-id
                   urakka-id :urakka-id
                   liitetty? :liitetty?}
                  app]
    (log "ASETA " kohde-id urakka-id liitetty?)
    (let [liitokset (:uudet-urakkaliitokset app)]
      (assoc app :uudet-urakkaliitokset
                 (assoc liitokset
                   [kohde-id urakka-id]
                   liitetty?))))

  PaivitaKohteidenUrakkaliitokset
  (process-event [{kohde :kohde
                   liita? :liita?
                   urakka :urakka}
                  app]
    ;; TODO Jatkossa päivitetään usea liitos kerralla
    #_(let [kohde-id (::kohde/id kohde)
            urakka-id (::ur/id urakka)]
        (tt/post! :liita-kohde-urakkaan
                  {:kohde-id kohde-id
                   :urakka-id urakka-id
                   :poistettu? (not liita?)}
                  {:onnistui ->KohdeLiitetty
                   :onnistui-parametrit [kohde-id urakka-id]
                   :epaonnistui ->KohdeEiLiitetty
                   :epaonnistui-parametrit [kohde-id urakka-id]})

        (-> app
            (lisaa-kohteelle-urakka kohde urakka liita?)
            (kaynnista-liittaminen kohde-id urakka-id))))

  LiitoksetPaivitetty
  (process-event [{kohde-id :kohde urakka-id :urakka} app]
    (-> app
        (lopeta-liittaminen kohde-id urakka-id)))

  LiitoksetEiPaivitetty
  (process-event [{kohde-id :kohde urakka-id :urakka} app]
    (viesti/nayta! "Virhe kohteen liittämisessä urakkaan!" :danger)
    (-> app
        (lopeta-liittaminen kohde-id urakka-id))))