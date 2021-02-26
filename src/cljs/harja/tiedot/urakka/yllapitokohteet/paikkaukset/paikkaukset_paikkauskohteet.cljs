(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.modal :as modal]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle :as paikkauskohteet-kartalle]
            [harja.tiedot.urakka.urakka :as tila])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- fmt-aikataulu [alkupvm loppupvm tila]
  (str
    (pvm/fmt-kuukausi-ja-vuosi-lyhyt alkupvm)
    " - "
    (pvm/fmt-p-k-v-lyhyt loppupvm)
    (when-not (= "valmis" tila)
      " (arv.)")))

(defn fmt-sijainti [tie alkuosa loppuosa alkuet loppuet]
  (str tie " - " alkuosa "/" alkuet " - " loppuosa "/" loppuet))

(defrecord AvaaLomake [lomake])
(defrecord SuljeLomake [])
(defrecord FiltteriValitseTila [uusi-tila])
(defrecord FiltteriValitseVuosi [uusi-vuosi])
(defrecord FiltteriValitseTyomenetelma [uusi-menetelma])
(defrecord HaePaikkauskohteet [])
(defrecord HaePaikkauskohteetOnnistui [vastaus])
(defrecord HaePaikkauskohteetEpaonnistui [vastaus])
(defrecord PaivitaLomake [lomake])
(defrecord TallennaPaikkauskohde [paikkauskohde])
(defrecord TallennaPaikkauskohdeOnnistui [vastaus])
(defrecord TallennaPaikkauskohdeEpaonnistui [vastaus])
(defrecord TilaaPaikkauskohde [paikkauskohde])
(defrecord HylkaaPaikkauskohde [paikkauskohde])
(defrecord PoistaPaikkauskohde [paikkauskohde])
(defrecord PeruPaikkauskohteenTilaus [paikkauskohde])
(defrecord PeruPaikkauskohteenHylkays [paikkauskohde])

(defn- hae-paikkauskohteet [urakka-id tila vuosi menetelma]
  (let [alkupvm (pvm/->pvm (str "1.1." vuosi))
        loppupvm (pvm/->pvm (str "31.12." vuosi))
        _ (js/console.log "hae-paikkauskohteet" (pr-str alkupvm) (pr-str loppupvm) )]
    (tuck-apurit/post! :paikkauskohteet-urakalle
                       {:urakka-id urakka-id
                        :tila tila
                        :alkupvm alkupvm
                        :loppupvm loppupvm
                        :tyomenetelmat #{menetelma}}
                       {:onnistui ->HaePaikkauskohteetOnnistui
                        :epaonnistui ->HaePaikkauskohteetEpaonnistui
                        :paasta-virhe-lapi? true})))

(defn- tallenna-paikkauskohde [paikkauskohde]
  (tuck-apurit/post! :tallenna-paikkauskohde-urakalle
                     paikkauskohde
                     {:onnistui ->TallennaPaikkauskohdeOnnistui
                      :epaonnistui ->TallennaPaikkauskohdeEpaonnistui
                      :paasta-virhe-lapi? true}))

(extend-protocol tuck/Event
  AvaaLomake
  (process-event [{lomake :lomake} app]
    (-> app
        (assoc :lomake lomake)))

  SuljeLomake
  (process-event [_ app]
    (dissoc app :lomake))

  FiltteriValitseTila
  (process-event [{uusi-tila :uusi-tila} app]
    (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) uusi-tila (:valittu-vuosi app) (:valittu-tyomenetelma app))
    (assoc app :valittu-tila uusi-tila))

  FiltteriValitseVuosi
  (process-event [{uusi-vuosi :uusi-vuosi} app]
    (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) (:valittu-tila app) uusi-vuosi (:valittu-tyomenetelma app))
    (assoc app :valittu-vuosi uusi-vuosi))

  FiltteriValitseTyomenetelma
  (process-event [{uusi-menetelma :uusi-menetelma} app]
    (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) (:valittu-tila app) (:valittu-vuosi app) uusi-menetelma)
    (assoc app :valittu-tyomenetelma uusi-menetelma))

  HaePaikkauskohteet
  (process-event [_ app]
    (do
      (js/console.log "HaePaikkauskohteet -> tehdään serverihaku")
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) (:valittu-tila app) (:valittu-vuosi app) (:valittu-tyomenetelma app))
      app))

  HaePaikkauskohteetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [paikkauskohteet (map (fn [kohde]
                                 (-> kohde
                                     (assoc :formatoitu-aikataulu
                                            (fmt-aikataulu (:alkupvm kohde) (:loppupvm kohde) (:paikkauskohteen-tila kohde)))
                                     (assoc :formatoitu-sijainti
                                            (fmt-sijainti (:tie kohde) (:aosa kohde) (:losa kohde) (:aet kohde) (:let kohde)))))
                               vastaus)
          zoomattavat-geot (into [] (concat (mapv #(harja.geo/extent (:sijainti %)) paikkauskohteet)))]
      (do
        (reset! paikkauskohteet-kartalle/karttataso-paikkauskohteet paikkauskohteet)
        (kartta-tiedot/keskita-kartta-alueeseen! zoomattavat-geot)
        (-> app
            (assoc :lomake nil) ;; Sulje mahdollinen lomake
            (assoc :paikkauskohteet paikkauskohteet)))))

  HaePaikkauskohteetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "Haku epäonnistui, vastaus " vastaus)
      app))

  PaivitaLomake
  (process-event [{lomake :lomake} app]
    (assoc app :lomake lomake))

  TallennaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [;; Muutetaan paikkauskohteen tilaa vain, jos sitä ei ole asetettu
          paikkauskohde (if (nil? (:paikkauskohteen-tila paikkauskohde))
                          (assoc paikkauskohde :paikkauskohteen-tila "ehdotettu")
                          paikkauskohde)
          paikkauskohde (-> paikkauskohde
                            (dissoc :sijainti)
                            (assoc :urakka-id (-> @tila/tila :yleiset :urakka :id)))]
      (do
        (js/console.log "Lähetetään paikkauskohde" (pr-str paikkauskohde))
        (tallenna-paikkauskohde paikkauskohde)
        app)))

  TilaaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [paikkauskohde (assoc paikkauskohde :paikkauskohteen-tila "tilattu")]
      (do
        (println "Merkitään paikkauskohde [" (:nimi paikkauskohde) "] tilatuksi")
        (tallenna-paikkauskohde paikkauskohde)
        app)))

  HylkaaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [paikkauskohde (assoc paikkauskohde :paikkauskohteen-tila "hylatty")]
      (do
        (println "Merkitään paikkauskohde [" (:nimi paikkauskohde) "] hylätyksi")
        (tallenna-paikkauskohde paikkauskohde)
        app)))

  PoistaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [paikkauskohde (assoc paikkauskohde :poistettu true)]
      (do
        (js/console.log "Merkitään paikkauskohde " (:nimi paikkauskohde) "poistetuksi")
        (tallenna-paikkauskohde paikkauskohde)
        app)))

  TallennaPaikkauskohdeOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "Paikkauskohteen tallennus onnistui" vastaus)
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) (:valittu-tila app) (:valittu-vuosi app) (:valittu-tyomenetelma app))
      (dissoc app :lomake)
      (modal/piilota!)))

  TallennaPaikkauskohdeEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "Paikkauskohteen tallennus epäonnistui" (pr-str vastaus))
      ;;TODO: tämä antaa warningin
      ;(harja.ui.yleiset/virheviesti-sailio "Paikkauskohteen tallennus epäonnistui")
      #_(dissoc app :lomake)
      app))

  ;; TODO: Mieti siistimisen yhteydessä, yhdistetäänkö nämä kaksi yhdeksi. Ainoa ero on logitus tällä hetkellä
  PeruPaikkauskohteenTilaus
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [paikkauskohde (assoc paikkauskohde :paikkauskohteen-tila "ehdotettu")]
      (do
        (println "Merkitään paikkauskohde [" (:nimi paikkauskohde) "] tilatusta ehdotetuksi")
        (tallenna-paikkauskohde paikkauskohde)
        app)))

  PeruPaikkauskohteenHylkays
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [paikkauskohde (assoc paikkauskohde :paikkauskohteen-tila "ehdotettu")]
      (do
        (println "Merkitään paikkauskohde [" (:nimi paikkauskohde) "] hylätystä ehdotetuksi")
        (tallenna-paikkauskohde paikkauskohde)
        app)))
  )

(def tyomenetelmat
  ["Kaikki" "MPA" "KTVA" "SIPA" "SIPU" "REPA" "UREM" "Muu"])

