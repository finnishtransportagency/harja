(ns harja.views.urakka.kustannusten-kirjaus.sakot-bonukset-tiedot
  "Tiemerkintöjen sakot ja bonukset - tiedot"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tr])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defonce tila (atom {:rivit nil
                     :muokataan false
                     :valittu-rivi nil
                     :valittu-laji :kaikki
                     :haku-kaynnissa? false
                     :valinnat {:liitteet {}
                                :uusi-liite {}
                                :aikavali (pvm/kuukauden-aikavali (pvm/nyt))
                                :lajit {:sakko "Sakko"
                                        :bonus "Bonus"}}}))

(def nakymassa? (atom false))

(defonce laji-valinnat
  {:kaikki "Kaikki"
   :sakko "Sakko"
   :bonus "Bonus"})


(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord AvaaModal [rivi])
(defrecord MuokkaaRivia [rivi])
(defrecord SuljeMuokkaus [])
(defrecord ValitseLaji [rivi])
(defrecord PoistaLiite [rivi])
(defrecord TallennaRivi [rivi])
(defrecord TallennusOnnistui [vastaus])
(defrecord TallennusEpaonnistui [vastaus])


(defn hae-tiedot
    ;; TODO 
  [{:keys [valinnat] :as app}]
  (tuck-apurit/post! app :hae-tiemerkinta-muut-kustannukset
    {:aikavali (:aikavali valinnat)
     :urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (hae-tiedot app)
    (assoc app :haku-kaynnissa? true))

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :rivit vastaus
      :haku-kaynnissa? false))

  HaeTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  TallennaRivi
  (process-event [{rivi :rivi} app]
    (let [{:keys [tunniste tie aosa aet losa
                  alkuaika loppuaika maara kustannus]} rivi

          nyt (pvm/nyt)
          default-perintapvm (pvm/luo-pvm-dec-kk (pvm/vuosi nyt) (pvm/kuukausi nyt) 15)]
      #_(tuck-apurit/post! app :tallenna-suorasanktio

          #_{:sanktio        (dissoc s :laatupoikkeama :yllapitokohde)
             :laatupoikkeama (assoc (:laatupoikkeama s) :urakka urakka-id
                               :yllapitokohde (:id (:yllapitokohde s)))
             :hoitokausi     @urakka/valittu-hoitokausi}


          {:luoja-id (:id @istunto/kayttaja)
           :urakka-id  @nav/valittu-urakka-id
           :toteuma rivi}
          {:onnistui ->TallennusOnnistui
           :epaonnistui ->TallennusEpaonnistui})
      (assoc app :muokataan false)))

  TallennusOnnistui
  (process-event [_ {:keys [valittu-rivi rivit] :as app}]
    (let [valittu-id (:id valittu-rivi)
          valittu-rivi (some #(when (= (:id %) valittu-id) %) rivit)]
      (viesti/nayta-toast! "Toteuma tallennettu onnistuneesti" :onnistui viesti/viestin-nayttoaika-keskipitka)
      (hae-tiedot app)
      (assoc app :valitu-rivi valittu-rivi)))

  TallennusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tallennus epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tallennus epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  MuokkaaRivia
  (process-event [{rivi :rivi} app]
    (update app :valittu-rivi merge rivi))

  AvaaModal
  (process-event [{rivi :rivi} app]
    (-> app
      (assoc :muokataan true)
      (assoc :valittu-rivi rivi)))

  PoistaLiite
  (process-event [{rivi :rivi} app]
    ;;(println "r: " rivi)
    app
    #_(-> app
        (assoc :muokataan true)
        (assoc :valittu-rivi rivi)))

  SuljeMuokkaus
  (process-event [_ app]
    (assoc app :muokataan false))

  ValitseLaji
  (process-event [{rivi :rivi} app]
    (assoc app :valittu-laji rivi)))
