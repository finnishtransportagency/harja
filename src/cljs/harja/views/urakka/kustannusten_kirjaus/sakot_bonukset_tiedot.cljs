(ns harja.views.urakka.kustannusten-kirjaus.sakot-bonukset-tiedot
  "Tiemerkintöjen sakot ja bonukset - tiedot"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tr]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.raportit :as raporttitiedot])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defonce tila (atom {:rivit nil
                     :kohteet {}
                     :liitteet {}
                     :muokataan false
                     :haku-kaynnissa? false
                     :valittu-laji :kaikki
                     :valittu-rivi {:uusi-liite [{}]}
                     :valinnat {:raportti {}
                                :aikavali {}
                                :uusi-liite {}
                                :lajit {:yllapidon_sakko "Sakko"
                                        :yllapidon_bonus "Bonus"}}}))

(def nakymassa? (atom false))
(defonce ^{:private true} raportti-avain :tiemerkinta-sakot-bonukset)

(defonce laji-valinnat
  {:kaikki "Kaikki"
   :yllapidon_sakko "Sakko"
   :yllapidon_bonus "Bonus"})




(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord HaeLiitteetOnnistui [vastaus])
(defrecord HaeLiitteetEpaonnistui [vastaus])
(defrecord HaeKohteetOnnistui [vastaus])
(defrecord HaeKohteetEpaonnistui [vastaus])
(defrecord AvaaModal [rivi])
(defrecord MuokkaaRivia [rivi])
(defrecord SuljeMuokkaus [])
(defrecord ValitseLaji [rivi])
(defrecord TallennaRivi [rivi uusi-liite])
(defrecord TallennusOnnistui [vastaus])
(defrecord TallennusEpaonnistui [vastaus])
(defrecord UusiLiite [liite])


(defn- epaonnistui [vastaus app]
  (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
  (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
  (assoc app :haku-kaynnissa? false))


(defn- raporttiparametrit []
  (raporttitiedot/urakkaraportin-parametrit @nav/valittu-urakka-id raportti-avain
    {:alkupvm  (-> @u/valittu-aikavali first)
     :loppupvm (-> @u/valittu-aikavali second)
     :urakkatyyppi (:arvo @nav/urakkatyyppi)}))


(defn hae-tiedot
  [{:keys [valinnat] :as app}]
  (tuck-apurit/post! app :hae-urakan-sanktiot-ja-bonukset
    {:urakka-id @nav/valittu-urakka-id
     :alku      (-> @u/valittu-aikavali first)
     :loppu     (-> @u/valittu-aikavali second)}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(defn hae-liitteet [app]
  (tuck-apurit/post! app :hae-urakan-liitteet
    {:urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeLiitteetOnnistui
     :epaonnistui ->HaeLiitteetEpaonnistui}))


(defn hae-kohteet [app]
  (tuck-apurit/post! app :urakan-yllapitokohteet-lomakkeelle
    {:urakka-id @nav/valittu-urakka-id :sopimus-id (-> @u/valittu-sopimusnumero :sopimus-id)}
    {:onnistui ->HaeKohteetOnnistui
     :epaonnistui ->HaeKohteetEpaonnistui}))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (hae-tiedot app)
    (assoc app :haku-kaynnissa? true))

  HaeTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (hae-liitteet (-> app
                    (assoc :rivit vastaus :haku-kaynnissa? false)
                    (assoc-in [:valinnat :raportti] (raporttiparametrit)))))

  HaeTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaeLiitteetOnnistui
  (process-event [{:keys [vastaus]} app]
    (hae-kohteet (assoc app :liitteet vastaus)))

  HaeLiitteetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaeKohteetOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :kohteet vastaus))

  HaeKohteetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  UusiLiite
  (process-event [{:keys [liite]} app]
    (println "Liite:: " liite)
    (assoc-in app [:valittu-rivi :laatupoikkeama :uusi-liite] liite))



  TallennaRivi
  (process-event [{:keys [rivi uusi-liite]} app]
    (let [;; _ (println "Tall: " rivi " liite:  " uusi-liite " \n \n")
          ;;nyt (pvm/nyt)
          ;;default-perintapvm (pvm/luo-pvm-dec-kk (pvm/vuosi nyt) (pvm/kuukausi nyt) 15)
          
          _ (println "Rivi : " rivi)
          _ (println "Rivi id: " (-> rivi :yllapitokohde :id))
          _ (println "Param: " (assoc
                                 (:laatupoikkeama rivi)
                                 :urakka @nav/valittu-urakka-id
                                 :yllapitokohde (-> rivi :yllapitokohde :id)))]
      
      (tuck-apurit/post! app :tallenna-suorasanktio
        {:sanktio        (dissoc rivi :laatupoikkeama :yllapitokohde)
         :laatupoikkeama (assoc
                           (:laatupoikkeama rivi)
                           :urakka @nav/valittu-urakka-id
                           :yllapitokohde (-> rivi :yllapitokohde :id))
         :hoitokausi @u/valittu-hoitokausi}
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
    (epaonnistui vastaus app))

  MuokkaaRivia
  (process-event [{:keys [rivi]} app]
    (-> app
      (update :valittu-rivi merge rivi)))

  AvaaModal
  (process-event [{:keys [rivi]} app]
    (-> app
      (assoc :muokataan true)
      (assoc :valittu-rivi rivi)))

  SuljeMuokkaus
  (process-event [_ app]
    (assoc app :muokataan false))

  ValitseLaji
  (process-event [{:keys [rivi]} app]
    (assoc app :valittu-laji rivi)))
