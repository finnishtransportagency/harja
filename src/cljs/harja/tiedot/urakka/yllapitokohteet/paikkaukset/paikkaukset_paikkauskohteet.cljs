(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [reagent.core :refer [atom] :as r]
            [clojure.data :refer [diff]]
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
            [harja.tiedot.urakka.urakka :as tila]
            [harja.domain.paikkaus :as paikkaus])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def lomakkeen-pituuskentat (atom {:pituus nil :tie nil :aosa nil :aet nil :losa nil :let nil}))

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
               [:ul
                (for [v (get rivi "virhe")]
                  ^{:key (str (hash v) (hash rivi))}
                  [:li (str v)]
                  )])
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

(defn- fmt-valmistuminen
  "Tiemerkkarille näytetään arvioitu valmistumispäivämäärä"
  [loppupvm]
  (str (pvm/pvm-opt loppupvm) " (arv.) "))

(defn fmt-sijainti [tie alkuosa loppuosa alkuet loppuet]
  (str tie " - " alkuosa "/" alkuet " - " loppuosa "/" loppuet))

(defrecord AvaaLomake [lomake])
(defrecord SuljeLomake [])
(defrecord FiltteriValitseTila [uusi-tila valittu?])
(defrecord FiltteriValitseVuosi [uusi-vuosi])
(defrecord FiltteriValitseTyomenetelma [uusi-menetelma valittu?])
(defrecord FiltteriValitseEly [uusi-ely valittu?])
(defrecord TiedostoLadattu [vastaus])
(defrecord HaePaikkauskohteet [])
(defrecord HaePaikkauskohteetOnnistui [vastaus])
(defrecord HaePaikkauskohteetEpaonnistui [vastaus])
(defrecord PaivitaLomake [lomake])
(defrecord TallennaPaikkauskohde [paikkauskohde])
(defrecord TallennaPaikkauskohdeOnnistui [paikkauskohde muokattu])
(defrecord TallennaPaikkauskohdeEpaonnistui [paikkauskohde muokattu])
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
(defrecord LaskePituusOnnistui [vastaus])
(defrecord LaskePituusEpaonnistui [vastaus])
(defrecord JarjestaPaikkauskohteet [jarjestys])

(defn- tilat-hakuun [tilat]
  (let [sql-tilat {"Kaikki" "kaikki",
                   "Ehdotettu" "ehdotettu",
                   "Hylätty" "hylatty",
                   "Tilattu" "tilattu",
                   "Valmis" "valmis",
                   "Tarkistettu" "tarkistettu"}
        tilat (set (mapv #(sql-tilat %) tilat))]
    tilat))

(defn- siivoa-ennen-lahetysta [lomake]
  (-> lomake
      (update :ajorata (fn [nykyinen-arvo]
                         (if (= "Ei ajorataa" nykyinen-arvo)
                           nil
                           nykyinen-arvo)))
      (dissoc :sijainti
              :pituus
              :harja.tiedot.urakka.urakka/validi?
              :harja.tiedot.urakka.urakka/validius)))

(defn- laske-paikkauskohteen-pituus [lomake]
  (let [;; Tarkistetaan ensin, että onko pituuskentät muuttuneet, jos ei niin ei lasketa pituutta
        nykyiset-pituuskentat {:pituus (:pituus lomake)
                               :tie (:tie lomake)
                               :aosa (:aosa lomake)
                               :aet (:aet lomake)
                               :losa (:losa lomake)
                               :let (:let lomake)}
        pituus-diff (diff @lomakkeen-pituuskentat nykyiset-pituuskentat)
        _ (when (or (not (nil? (first pituus-diff)))
                    (not (nil? (second pituus-diff))))
            (do
              (reset! lomakkeen-pituuskentat nykyiset-pituuskentat)
              (tuck-apurit/post! :laske-paikkauskohteen-pituus
                                 nykyiset-pituuskentat
                                 {:onnistui ->LaskePituusOnnistui
                                  :epaonnistui ->LaskePituusEpaonnistui
                                  :paasta-virhe-lapi? true})))]
    lomake))
;;	926 - 9/3364 - 12/3964
(defn- hae-paikkauskohteet [urakka-id {:keys [valitut-tilat valittu-vuosi valitut-tyomenetelmat valitut-elyt hae-aluekohtaiset-paikkauskohteet?] :as app}]
  (let [alkupvm (pvm/->pvm (str "1.1." valittu-vuosi))
        loppupvm (pvm/->pvm (str "31.12." valittu-vuosi))]
    (tuck-apurit/post! :paikkauskohteet-urakalle
                       {:urakka-id urakka-id
                        :tilat (tilat-hakuun valitut-tilat)
                        :alkupvm alkupvm
                        :loppupvm loppupvm
                        :tyomenetelmat valitut-tyomenetelmat
                        :elyt valitut-elyt
                        :hae-alueen-kohteet? hae-aluekohtaiset-paikkauskohteet?}
                       {:onnistui ->HaePaikkauskohteetOnnistui
                        :epaonnistui ->HaePaikkauskohteetEpaonnistui
                        :paasta-virhe-lapi? true})))

(defn- tallenna-paikkauskohde
  ([paikkauskohde onnistui epaonnistui]
   (tallenna-paikkauskohde paikkauskohde onnistui epaonnistui nil))
  ([paikkauskohde onnistui epaonnistui parametrit]
   (let [paikkauskohde (siivoa-ennen-lahetysta paikkauskohde)]
     (tuck-apurit/post! :tallenna-paikkauskohde-urakalle
                        paikkauskohde
                        {:onnistui onnistui
                         :onnistui-parametrit parametrit
                         :epaonnistui epaonnistui
                         :epaonnistui-parametrit parametrit
                         :paasta-virhe-lapi? true}))))

(defn validoinnit
  ([avain lomake]
   (avain {:nimi [tila/ei-nil tila/ei-tyhja]
           :tyomenetelma [tila/ei-nil tila/ei-tyhja]
           :tie [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
           :aosa [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
           :losa [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
           :aet [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
           :let [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
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

(defn- kaanteinen-jarjestaja [a b]
  (compare b a))

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
  (process-event [{uusi-tila :uusi-tila valittu? :valittu?} app]
    (let [valitut-tilat (:valitut-tilat app)
          tilat (cond
                  ;; Valitaan joku muu kuin "kaikki"
                  (and valittu? (not= "Kaikki" (:nimi uusi-tila)))
                  (-> valitut-tilat
                      (conj (:nimi uusi-tila))
                      (disj "Kaikki"))

                  ;; Valitaan "kaikki"
                  (and valittu? (= "Kaikki" (:nimi uusi-tila)))
                  #{"Kaikki"} ;; Palautetaan kaikki valinnalla

                  ;; Poistetaan "kaikki" valinta
                  (and (not valittu?) (= "Kaikki" (:nimi uusi-tila)))
                  (disj valitut-tilat "Kaikki")

                  ;; Poistetaan joku muu kuin "kaikki" valinta
                  (and (not valittu?) (not= "Kaikki" (:nimi uusi-tila)))
                  (disj valitut-tilat (:nimi uusi-tila)))
          app (assoc app :valitut-tilat tilat)]
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
      app))

  FiltteriValitseVuosi
  (process-event [{uusi-vuosi :uusi-vuosi} app]
    (let [app (assoc app :valittu-vuosi uusi-vuosi)]
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
      app))

  FiltteriValitseTyomenetelma
  (process-event [{uusi-menetelma :uusi-menetelma valittu? :valittu?} app]
    (let [valitut-tyomenetelmat (:valitut-tyomenetelmat app)
          menetelmat (cond
                       ;; Valitaan joku muu kuin "kaikki"
                       (and valittu? (not= "Kaikki" (:nimi uusi-menetelma)))
                       (-> valitut-tyomenetelmat
                           (conj (:nimi uusi-menetelma))
                           (disj "Kaikki"))

                       ;; Valitaan "kaikki"
                       (and valittu? (= "Kaikki" (:nimi uusi-menetelma)))
                       #{"Kaikki"} ;; Palautetaan kaikki valinnalla

                       ;; Poistetaan "kaikki" valinta
                       (and (not valittu?) (= "Kaikki" (:nimi uusi-menetelma)))
                       (disj valitut-tyomenetelmat "Kaikki")

                       ;; Poistetaan joku muu kuin "kaikki" valinta
                       (and (not valittu?) (not= "Kaikki" (:nimi uusi-menetelma)))
                       (disj valitut-tyomenetelmat (:nimi uusi-menetelma)))
          app (assoc app :valitut-tyomenetelmat menetelmat)]
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
      app))

  FiltteriValitseEly
  (process-event [{uusi-ely :uusi-ely valittu? :valittu?} app]
    (let [valitut-elyt (:valitut-elyt app)
          elyt (cond
                 ;; Valitaan joku muu kuin "kaikki"
                 (and valittu? (not= 0 (:id uusi-ely)))
                 (-> valitut-elyt
                     (conj (:id uusi-ely))
                     (disj 0))

                 ;; Valitaan "kaikki"
                 (and valittu? (= 0 (:id uusi-ely)))
                 #{0} ;; Palautetaan kaikki valinnalla

                 ;; Poistetaan "kaikki" valinta
                 (and (not valittu?) (= 0 (:id uusi-ely)))
                 (disj valitut-elyt 0)

                 ;; Poistetaan joku muu kuin "kaikki" valinta
                 (and (not valittu?) (not= 0 (:id uusi-ely)))
                 (disj valitut-elyt (:id uusi-ely)))
          app (assoc app :valitut-elyt elyt)]
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
      app))

  TiedostoLadattu
  (process-event [{vastaus :vastaus} app]
    (let [_ (js/console.log "TiedostoLadattu :: vastaus" (pr-str vastaus))]
      (do
        ;(js/console.log "TiedostoLadattu :: error?" (pr-str (:status vastaus)) (pr-str (get-in vastaus [:response "virheet"])))

        ;; Excelissä voi mahdollisesti olla virheitä, jos näin on, niin avataan modaali, johon virheet kirjoitetaan
        ;; Jos taas kaikki sujui kuten Strömssössä, niin näytetään onnistumistoasti
        (if (and (not (nil? (:status vastaus))) (not= 200 (:status vastaus)))
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
            (dissoc app :excel-virhe))))))

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
                                            (fmt-sijainti (:tie kohde) (:aosa kohde) (:losa kohde) (:aet kohde) (:let kohde)))
                                     (assoc :loppupvm-arvio (fmt-valmistuminen (:loppupvm kohde)))
                                     (assoc :paivays (or (:muokattu kohde) (:luotu kohde)))))
                               vastaus)
          zoomattavat-geot (into [] (concat (mapv (fn [p]
                                                    (when (and
                                                            (not (nil? (:sijainti p)))
                                                            (not (empty? (:sijainti p))))
                                                      (harja.geo/extent (:sijainti p))))
                                                  paikkauskohteet)))
          ;_ (js/console.log "HaePaikkauskohteetOnnistui :: zoomattavat-geot" (pr-str zoomattavat-geot))
          _ (js/console.log "HaePaikkauskohteetOnnistui :: paikkauskohteet" (pr-str (set (mapv :id paikkauskohteet))) #_(pr-str paikkauskohteet))
          ]
      (do
        (when (and (not (nil? paikkauskohteet))
                   (not (empty? paikkauskohteet))
                   (not (nil? zoomattavat-geot))
                   (not (empty? zoomattavat-geot)))
          ;(js/console.log "reset ja keskitys")
          (reset! paikkauskohteet-kartalle/karttataso-paikkauskohteet paikkauskohteet)
          #_(kartta-tiedot/keskita-kartta-alueeseen! zoomattavat-geot)
          (reset! paikkauskohteet-kartalle/valitut-kohteet-atom (set (mapv :id paikkauskohteet))))
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
    (let [lomake (laske-paikkauskohteen-pituus lomake)
          {:keys [validoi] :as validoinnit} (validoi-lomake lomake)
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
        (tallenna-paikkauskohde paikkauskohde
                                ->TallennaPaikkauskohdeOnnistui
                                ->TallennaPaikkauskohdeEpaonnistui
                                [(not (nil? (:id paikkauskohde)))])
        app)))

  TallennaPaikkauskohdeOnnistui
  (process-event [{muokattu :muokattu paikkauskohde :paikkauskohde} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast!
        (if muokattu
          "Muutokset tallennettu"
          (str "Kohde " (paikkauskohde-id->nimi app (:id paikkauskohde)) " lisätty")))
      (dissoc app :lomake)))

  TallennaPaikkauskohdeEpaonnistui
  (process-event [{muokattu :muokattu paikkauskohde :paikkauskohde} app]
    (do
      (js/console.log "Paikkauskohteen tallennus epäonnistui" (pr-str paikkauskohde))
      (if muokattu
        (viesti/nayta-toast! "Paikkauskohteen muokkaus epäonnistui" :varoitus viesti/viestin-nayttoaika-aareton)
        (viesti/nayta-toast! "Paikkauskohteen tallennus epäonnistui" :varoitus viesti/viestin-nayttoaika-aareton))
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
                           :varoitus viesti/viestin-nayttoaika-aareton)
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
                           :varoitus viesti/viestin-nayttoaika-aareton)
      (dissoc app :lomake)))

  PoistaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (do
      (tuck-apurit/post! :poista-paikkauskohde
                         (siivoa-ennen-lahetysta paikkauskohde)
                         {:onnistui ->PoistaPaikkauskohdeOnnistui
                          :epaonnistui ->PoistaPaikkauskohdeEpaonnistui
                          :paasta-virhe-lapi? true})
      app))

  PoistaPaikkauskohdeOnnistui
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohde " (:nimi paikkauskohde) " poistettu"))
      (dissoc app :lomake)))

  PoistaPaikkauskohdeEpaonnistui
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [_ (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (:nimi paikkauskohde) " poistamisessa tapahtui virhe!")
                           :varoitus viesti/viestin-nayttoaika-aareton)
      (dissoc app :lomake)))

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
                           :varoitus viesti/viestin-nayttoaika-aareton)
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
                           :varoitus viesti/viestin-nayttoaika-aareton)
      (dissoc app :lomake)))

  LaskePituusOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (assoc-in app [:lomake :pituus] (:pituus vastaus))
          pituuskentat (merge @lomakkeen-pituuskentat
                              {:pituus (:pituus vastaus)})
          _ (reset! lomakkeen-pituuskentat pituuskentat)]
      app))

  LaskePituusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    ;; Kohteen pituutta ei voitu laskea
    (do
      (js/console.log "Pituutta ei voitu laskea: vastaus" (pr-str vastaus))
      app))

  JarjestaPaikkauskohteet
  (process-event [{jarjestys :jarjestys} app]
    (let [vanha-jarjestys (get-in app [:jarjestys :nimi])
          kaanteinen? (if (= jarjestys vanha-jarjestys)
                        (not (get-in app [:jarjestys :kaanteinen?]))
                        false)]
      (-> app
          (assoc-in [:jarjestys :nimi] jarjestys)
          (assoc-in [:jarjestys :kaanteinen?] kaanteinen?)
          (assoc :paikkauskohteet (sort-by jarjestys (if kaanteinen? kaanteinen-jarjestaja compare) (:paikkauskohteet app))))))
  )

(def tyomenetelmat
  (into ["Kaikki"] paikkaus/kuvaillut-tyomenetelmat))

