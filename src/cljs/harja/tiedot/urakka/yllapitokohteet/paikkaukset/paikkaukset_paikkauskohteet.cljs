(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.modal :as modal]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle :as paikkauskohteet-kartalle]
            [harja.tiedot.urakka.urakka :as tila])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn virhe-modal [virhe otsikko]
  (modal/nayta!
    {:otsikko otsikko
     :otsikko-tyyli :virhe}
    (when virhe
      (doall
        (for [rivi virhe]
          ^{:key (hash rivi)}
          [:div
           [:b (get rivi "paikkauskohde")]
           (if (vector? (get rivi "virhe"))
             (doall
               (for [v (get rivi "virhe")]
                 ^{:key (str (hash v) (hash rivi))}
                 [:p (str v)]
                 ))
             [:p (str (get rivi "virhe"))])
           ]))
      )))

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
(defrecord FiltteriValitseEly [uusi-ely])
(defrecord TiedostoLadattu [vastaus])
(defrecord HaePaikkauskohteet [])
(defrecord HaePaikkauskohteetOnnistui [vastaus])
(defrecord HaePaikkauskohteetEpaonnistui [vastaus])
(defrecord PaivitaLomake [lomake])
(defrecord TallennaPaikkauskohde [paikkauskohde])
(defrecord TallennaPaikkauskohdeOnnistui [vastaus])
(defrecord TallennaPaikkauskohdeEpaonnistui [vastaus])
(defrecord TilaaPaikkauskohde [paikkauskohde])
(defrecord TilaaPaikkauskohdeOnnistui [id])
(defrecord TilaaPaikkauskohdeEpaonnistui [])
(defrecord HylkaaPaikkauskohde [paikkauskohde])
(defrecord HylkaaPaikkauskohdeOnnistui [paikkauskohde])
(defrecord HylkaaPaikkauskohdeEpaonnistui [paikkauskohde])
(defrecord PoistaPaikkauskohde [paikkauskohde])
(defrecord PoistaPaikkauskohdeOnnistui [paikkauskohde])
(defrecord PoistaPaikkauskohdeEpaonnistui [paikkauskohde])
(defrecord PeruPaikkauskohteenTilaus [paikkauskohde])
(defrecord PeruPaikkauskohteenTilausOnnistui [])
(defrecord PeruPaikkauskohteenTilausEpaonnistui [])
(defrecord PeruPaikkauskohteenHylkays [paikkauskohde])
(defrecord PeruPaikkauskohteenHylkaysOnnistui [])
(defrecord PeruPaikkauskohteenHylkaysEpaonnistui [paikkauskohde])

(defn- siivoa-ennen-lahetysta [lomake]
  (dissoc lomake
          :sijainti
          :harja.tiedot.urakka.urakka/validi?
          :harja.tiedot.urakka.urakka/validius))

(defn- hae-paikkauskohteet [urakka-id {:keys [valittu-tila valittu-vuosi valittu-menetelma valittu-ely] :as app}]
  (let [alkupvm (pvm/->pvm (str "1.1." valittu-vuosi))
        loppupvm (pvm/->pvm (str "31.12." valittu-vuosi))]
    (tuck-apurit/post! :paikkauskohteet-urakalle
                       {:urakka-id urakka-id
                        :tila valittu-tila
                        :alkupvm alkupvm
                        :loppupvm loppupvm
                        :tyomenetelmat #{valittu-menetelma}
                        :elyt #{valittu-ely}}
                       {:onnistui ->HaePaikkauskohteetOnnistui
                        :epaonnistui ->HaePaikkauskohteetEpaonnistui
                        :paasta-virhe-lapi? true})))

(defn- tallenna-paikkauskohde [paikkauskohde onnistui epaonnistui]
  (tuck-apurit/post! :tallenna-paikkauskohde-urakalle
                     paikkauskohde
                     {:onnistui onnistui
                      :epaonnistui epaonnistui
                      :paasta-virhe-lapi? true}))

(defn validoinnit
  ([avain lomake]
   (avain {:nimi [tila/ei-nil tila/ei-tyhja]
           :tyomenetelma [tila/ei-nil tila/ei-tyhja]
           :tie [tila/ei-nil tila/ei-tyhja tila/numero]
           :aosa [tila/ei-nil tila/ei-tyhja tila/numero]
           :losa [tila/ei-nil tila/ei-tyhja tila/numero]
           :aet [tila/ei-nil tila/ei-tyhja tila/numero]
           :let [tila/ei-nil tila/ei-tyhja tila/numero]
           :alkupvm [tila/ei-nil tila/ei-tyhja tila/paivamaara]
           :loppupvm [tila/ei-nil tila/ei-tyhja tila/paivamaara
                      (tila/silloin-kun #(not (nil? (:alkupvm lomake)))
                                        (fn [arvo]
                                          ;; Validointi vaatii "nil" vastauksen, kun homma on pielessä ja kentän arvon, kun kaikki on ok
                                          (when (pvm/ennen? (:alkupvm lomake) arvo)
                                            arvo)))]
           :suunniteltu-maara [tila/ei-nil tila/ei-tyhja tila/numero]
           :yksikko [tila/ei-nil tila/ei-tyhja]
           :suunniteltu-hinta [tila/ei-nil tila/ei-tyhja tila/numero]
           }))
  ([avain]
   (validoinnit avain {})))

(defn paikkauskohde-id->nimi [app id]
  (:name (first (filter #(= id (:id %)) (:paikkauskohteet app)))))

(defn lomakkeen-validoinnit [lomake]
  [[:nimi] (validoinnit :nimi lomake)
   [:tyomenetelma] (validoinnit :tyomenetelma lomake)
   [:tie] (validoinnit :tie lomake)
   [:aosa] (validoinnit :aosa lomake)
   [:losa] (validoinnit :losa lomake)
   [:aet] (validoinnit :aet lomake)
   [:let] (validoinnit :let lomake)
   [:alkupvm] (validoinnit :alkupvm lomake)
   [:loppupvm] (validoinnit :loppupvm lomake)
   [:suunniteltu-maara] (validoinnit :suunniteltu-maara lomake)
   [:yksikko] (validoinnit :yksikko lomake)
   [:suunniteltu-hinta] (validoinnit :suunniteltu-hinta lomake)])

(defn- validoi-lomake [lomake]
  (apply tila/luo-validius-tarkistukset [[:nimi] (validoinnit :nimi lomake)
                                         [:tyomenetelma] (validoinnit :tyomenetelma lomake)
                                         [:tie] (validoinnit :tie lomake)
                                         [:aosa] (validoinnit :aosa lomake)
                                         [:losa] (validoinnit :losa lomake)
                                         [:aet] (validoinnit :aet lomake)
                                         [:let] (validoinnit :let lomake)
                                         [:alkupvm] (validoinnit :alkupvm lomake)
                                         [:loppupvm] (validoinnit :loppupvm lomake)
                                         [:suunniteltu-maara] (validoinnit :suunniteltu-maara lomake)
                                         [:yksikko] (validoinnit :yksikko lomake)
                                         [:suunniteltu-hinta] (validoinnit :suunniteltu-hinta lomake)]))

(extend-protocol tuck/Event

  AvaaLomake
  (process-event [{lomake :lomake} app]
    (let [{:keys [validoi] :as validoinnit} (validoi-lomake lomake)
          {:keys [validi? validius]} (validoi validoinnit lomake)]
      (-> app
          (assoc :lomake lomake)
          (assoc-in [:lomake ::tila/validius] validius)
          (assoc-in [:lomake ::tila/validi?] validi?))))

  SuljeLomake
  (process-event [_ app]
    (dissoc app :lomake))

  FiltteriValitseTila
  (process-event [{uusi-tila :uusi-tila} app]
    (let [app (assoc app :valittu-tila uusi-tila)]
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
      app))

  FiltteriValitseVuosi
  (process-event [{uusi-vuosi :uusi-vuosi} app]
    (let [app (assoc app :valittu-vuosi uusi-vuosi)]
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
      app))

  FiltteriValitseTyomenetelma
  (process-event [{uusi-menetelma :uusi-menetelma} app]
    (let [app (assoc app :valittu-tyomenetelma uusi-menetelma)]
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
      app))

  FiltteriValitseEly
  (process-event [{uusi-ely :uusi-ely} app]
    (let [app (assoc app :valittu-ely uusi-ely)]
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
      app))

  TiedostoLadattu
  (process-event [{vastaus :vastaus} app]
    (do
      ;(js/console.log "TiedostoLadattu :: error?" (pr-str (:status vastaus)) (pr-str (get-in vastaus [:response "virheet"])))

      ;; Excelissä voi mahdollisesti olla virheitä, jos näin on, niin avataan modaali, johon virheet kirjoitetaan
      ;; Jos taas kaikki sujui kuten Strömssössä, niin näytetään onnistumistoasti
      (if (not= 200 (:status vastaus))
        (do
          (viesti/nayta-toast! "Ladatun tiedoston käsittelyssä virhe"
                               :varoitus viesti/viestin-nayttoaika-lyhyt)
          (virhe-modal (get-in vastaus [:response "virheet"]) "Virhe ladattaessa kohteita tiedostosta")
          (assoc app :excel-virhe (get-in vastaus [:response "virheet"])))
        (do
          ;; Ladataan uudet paikkauskohteet
          (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          (viesti/nayta-toast! "Paikkauskohteet ladattu onnistuneesti"
                               :onnistui viesti/viestin-nayttoaika-lyhyt)
          (dissoc app :excel-virhe)))))

  HaePaikkauskohteet
  (process-event [_ app]
    (do
      ; (js/console.log "HaePaikkauskohteet -> tehdään serverihaku")
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
      app))

  HaePaikkauskohteetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [
          paikkauskohteet (map (fn [kohde]
                                 (-> kohde
                                     (assoc :formatoitu-aikataulu
                                            (fmt-aikataulu (:alkupvm kohde) (:loppupvm kohde) (:paikkauskohteen-tila kohde)))
                                     (assoc :formatoitu-sijainti
                                            (fmt-sijainti (:tie kohde) (:aosa kohde) (:losa kohde) (:aet kohde) (:let kohde)))))
                               vastaus)
          zoomattavat-geot (into [] (concat (mapv (fn [p]
                                                    (when (and
                                                            (not (nil? (:sijainti p)))
                                                            (not (empty? (:sijainti p))))
                                                      (harja.geo/extent (:sijainti p))))
                                                  paikkauskohteet)))
          _ (js/console.log "HaePaikkauskohteetOnnistui :: zoomattavat-geot" (pr-str zoomattavat-geot))
          ;_ (js/console.log "HaePaikkauskohteetOnnistui :: paikkauskohteet" (pr-str paikkauskohteet))
          ]
      (do
        (when (and (not (nil? paikkauskohteet))
                   (not (empty? paikkauskohteet))
                   (not (nil? zoomattavat-geot))
                   (not (empty? zoomattavat-geot)))
          (js/console.log "reset ja keskitys")
          (reset! paikkauskohteet-kartalle/karttataso-paikkauskohteet paikkauskohteet)
          (kartta-tiedot/keskita-kartta-alueeseen! zoomattavat-geot))
        (-> app
            (assoc :lomake nil) ;; Sulje mahdollinen lomake
            (assoc :paikkauskohteet paikkauskohteet)))))

  HaePaikkauskohteetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "Haku epäonnistui, vastaus " (pr-str vastaus))
      app))

  PaivitaLomake
  (process-event [{lomake :lomake} app]
    (let [{:keys [validoi] :as validoinnit} (validoi-lomake lomake)
          {:keys [validi? validius]} (validoi validoinnit lomake)]
      (-> app
          (assoc :lomake lomake)
          (assoc-in [:lomake ::tila/validius] validius)
          (assoc-in [:lomake ::tila/validi?] validi?))))

  TallennaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [;; Muutetaan paikkauskohteen tilaa vain, jos sitä ei ole asetettu
          paikkauskohde (if (nil? (:paikkauskohteen-tila paikkauskohde))
                          (assoc paikkauskohde :paikkauskohteen-tila "ehdotettu")
                          paikkauskohde)
          paikkauskohde (-> paikkauskohde
                            (siivoa-ennen-lahetysta)
                            (assoc :urakka-id (-> @tila/tila :yleiset :urakka :id)))]
      (do
        (js/console.log "Tallennetaan paikkauskohde" (pr-str paikkauskohde))
        (tallenna-paikkauskohde paikkauskohde ->TallennaPaikkauskohdeOnnistui ->TallennaPaikkauskohdeEpaonnistui)
        app)))

  TallennaPaikkauskohdeOnnistui
  (process-event [{id :id} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohde " (paikkauskohde-id->nimi app id) " lisätty")
                           :success viesti/viestin-nayttoaika-pitka)
      (dissoc app :lomake)))

  TallennaPaikkauskohdeEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "Paikkauskohteen tallennus epäonnistui" (pr-str vastaus))
      (viesti/nayta-toast! "Paikkauskohteen tallennus epäonnistui" :varoitus viesti/viestin-nayttaika-aareton)
      app))

  TilaaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [paikkauskohde (assoc paikkauskohde :paikkauskohteen-tila "tilattu")]
      (do
        (println "Merkitään paikkauskohde [" (:nimi paikkauskohde) "] tilatuksi")
        (tallenna-paikkauskohde paikkauskohde ->TilaaPaikkauskohdeOnnistui ->TilaaPaikkauskohdeEpaonnistui)
        app)))

  TilaaPaikkauskohdeOnnistui
  (process-event [{id :id} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohde " (paikkauskohde-id->nimi app (:id id)) " tilattu"))
      (dissoc app :lomake)))

  TilaaPaikkauskohdeEpaonnistui
  (process-event [{id :id} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:id id)) " tilaamisessa tapahtui virhe!")
                           :varoitus viesti/viestin-nayttaika-aareton)
      (dissoc app :lomake)))

  HylkaaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [paikkauskohde (assoc paikkauskohde :paikkauskohteen-tila "hylatty")]
      (do
        (println "Merkitään paikkauskohde [" (:nimi paikkauskohde) "] hylätyksi")
        (tallenna-paikkauskohde paikkauskohde ->HylkaaPaikkauskohdeOnnistui ->HylkaaPaikkauskohdeEpaonnistui)
        app)))

  HylkaaPaikkauskohdeOnnistui
  (process-event [{id :id} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohde " (paikkauskohde-id->nimi app (:id id)) " hylätty"))
      (dissoc app :lomake)))

  HylkaaPaikkauskohdeEpaonnistui
  (process-event [{id :id} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:id id)) " hylkäämisessä tapahtui virhe!")
                           :varoitus viesti/viestin-nayttaika-aareton)
      (dissoc app :lomake)))

  PoistaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [paikkauskohde (assoc paikkauskohde :poistettu true)]
      (do
        (js/console.log "Merkitään paikkauskohde " (:nimi paikkauskohde) "poistetuksi")
        (tallenna-paikkauskohde paikkauskohde ->PoistaPaikkauskohdeOnnistui ->PoistaPaikkauskohdeEpaonnistui)
        app)))

  PoistaPaikkauskohdeOnnistui
  (process-event [{id :id} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohde " (paikkauskohde-id->nimi app (:id id)) " poistettu"))
      (dissoc app :lomake)))

  PoistaPaikkauskohdeEpaonnistui
  (process-event [{id :id} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:id id)) " poistamisessa tapahtui virhe!")
                           :varoitus viesti/viestin-nayttaika-aareton)
      (dissoc app :lomake)))

  ;; TODO: Mieti siistimisen yhteydessä, yhdistetäänkö nämä kaksi yhdeksi. Ainoa ero on logitus tällä hetkellä
  PeruPaikkauskohteenTilaus
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [paikkauskohde (assoc paikkauskohde :paikkauskohteen-tila "ehdotettu")]
      (do
        (println "Merkitään paikkauskohde [" (:nimi paikkauskohde) "] tilatusta ehdotetuksi")
        (tallenna-paikkauskohde paikkauskohde ->PeruPaikkauskohteenTilausOnnistui ->PeruPaikkauskohteenTilausEpaonnistui)
        app)))

  PeruPaikkauskohteenTilausOnnistui
  (process-event [{id :id} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:id id)) " tilaus peruttu"))
      (dissoc app :lomake)))

  PeruPaikkauskohteenTilausEpaonnistui
  (process-event [{id :id} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:id id)) " tilauksen perumisessa tapahtui virhe!")
                           :varoitus viesti/viestin-nayttaika-aareton)
      (dissoc app :lomake)))

  PeruPaikkauskohteenHylkays
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [paikkauskohde (assoc paikkauskohde :paikkauskohteen-tila "ehdotettu")]
      (do
        (println "Merkitään paikkauskohde [" (:nimi paikkauskohde) "] hylätystä ehdotetuksi")
        (tallenna-paikkauskohde paikkauskohde ->PeruPaikkauskohteenHylkaysOnnistui ->PeruPaikkauskohteenHylkaysEpaonnistui)
        app)))

  PeruPaikkauskohteenHylkaysOnnistui
  (process-event [{id :id} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:id id)) " hylkäys peruttu"))
      (dissoc app :lomake)))

  PeruPaikkauskohteenHylkaysEpaonnistui
  (process-event [{id :id} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:id id)) " hylkäyksen perumisessa tapahtui virhe!")
                           :varoitus viesti/viestin-nayttaika-aareton)
      (dissoc app :lomake)))

  )

(def tyomenetelmat
  ["Kaikki" "MPA" "KTVA" "SIPA" "SIPU" "REPA" "UREM" "Muu"])

