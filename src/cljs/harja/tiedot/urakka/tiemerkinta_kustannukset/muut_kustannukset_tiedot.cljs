(ns harja.tiedot.urakka.tiemerkinta-kustannukset.muut-kustannukset-tiedot
  "Tiemerkintöjen muut kustannukset välilehti - tiedot"
  (:require [tuck.core :as tuck]
            [reagent.core :refer [atom] :as reagent]

            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.ui.viesti :as viesti]
            [harja.ui.lomake :as lomake]
            [harja.tiedot.navigaatio :as nav]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.raportit :as raporttitiedot]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.views.urakka.tiemerkinta-kustannukset.yhteiset :as yhteiset]))

(defonce ^{:private true} raportti-avain :tiemerkinta-muut-kustannukset)
(defonce ^{:private true} nollatut-valinnat {:rivit nil
                                             :valittu-rivi {}
                                             :muokataan false
                                             :haku-kaynnissa? true
                                             :valinnat {:raportti {}
                                                        :aikavali (pvm/kuukauden-aikavali (pvm/nyt))}})

(def nakymassa? (atom false))


(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord AvaaKustannusModal [rivi])
(defrecord HaeTyypitOnnistui [vastaus])
(defrecord HaeTyypitEpaonnistui [vastaus])
(defrecord MuokkaaRivia [rivi])
(defrecord SuljeMuokkaus [])
(defrecord AsetaToteumanPvm [aika])
(defrecord TallennaRivi [rivi virheita?])
(defrecord TallennusOnnistui [vastaus])
(defrecord TallennusEpaonnistui [vastaus])


(defn- epaonnistui [vastaus app]
  (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
  (viesti/nayta-toast! "Tietojen haku epäonnistui" :varoitus viesti/viestin-nayttoaika-keskipitka)
  (assoc app :haku-kaynnissa? false))


(defn- raporttiparametrit [tyypit rivit]
  (raporttitiedot/urakkaraportin-parametrit @nav/valittu-urakka-id raportti-avain
    {:rivit rivit
     :tyypit tyypit
     :urakkatyyppi (:arvo @nav/urakkatyyppi)
     :alkupvm  (-> @u/valittu-aikavali first)
     :loppupvm (-> @u/valittu-aikavali second)
     :sopimus (-> @u/valittu-sopimusnumero first)}))


(defn hae-tiedot
  [{:keys [_valinnat] :as app}]
  (tuck-apurit/post! app :hae-yllapito-toteumat
    {:urakka  @nav/valittu-urakka-id
     :sopimus (-> @u/valittu-sopimusnumero first)
     :alkupvm  (-> @u/valittu-aikavali first)
     :loppupvm (-> @u/valittu-aikavali second)}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(defn- hae-kustannustyypit [app]
  (tuck-apurit/post! app :hae-tiemerkinta-kustannustyypit
    {:urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeTyypitOnnistui
     :epaonnistui ->HaeTyypitEpaonnistui}))


(defn voi-tallentaa?
  [{:keys [pvm hinta selite tyyppi yllapitoluokka] :as _valittu-rivi} luokat]
  (let [pvm-validi? (pvm/pvm? pvm)
        kustannus-olemassa? (some? hinta)
        tyyppi-validi? (contains? (set (keys yhteiset/tyyppi-valinnat)) (keyword tyyppi))
        luokka-olemassa? (boolean (some #(= (:numero yllapitoluokka) (:numero %)) luokat))
        selite-olemassa? (and
                           (some? selite)
                           (> (count selite) 0))]
    (and
      pvm-validi?
      tyyppi-validi?
      luokka-olemassa?
      selite-olemassa?
      kustannus-olemassa?)))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (hae-tiedot app)
    (->
      (tuck-apurit/nollaa-tuck-tila app nollatut-valinnat)
      (assoc :haku-kaynnissa? true)
      (assoc-in [:valinnat :aikavali] @u/valittu-aikavali)))

  HaeTiedotOnnistui
  (process-event [{:keys [vastaus]} {:keys [_valinnat] :as app}]
    (hae-kustannustyypit (assoc app :rivit vastaus)))

  HaeTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaeTyypitOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :tyypit vastaus :haku-kaynnissa? false)
      (assoc-in [:valinnat :raportti] (raporttiparametrit vastaus (:rivit app)))))

  HaeTyypitEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  TallennaRivi
  (process-event [{:keys [rivi virheita?]} {:keys [valittu-rivi] :as app}]
    ;; Tämä enabloi lomakkeen validoinnin
    ;; Saavutettavuusmielessä, näytetään validointi tallennus painalluksen yhteydessä
    (if (or
          virheita?
          (not (voi-tallentaa? valittu-rivi yllapitokohteet-domain/paallysteen-korjausluokat)))
      ;; Jos ei voida tallentaa, kerro käyttäjälle missä virheet
      (assoc-in app [:valittu-rivi :virheita?] true)

      ;; Voidaan tehdä tallennus 
      (let [rivi (lomake/ilman-lomaketietoja rivi)
            {:keys [pvm hinta id selite tyyppi yllapitoluokka]} rivi
            toteuma {:id id
                     :pvm pvm
                     :hinta hinta
                     :tyyppi tyyppi
                     :selite selite
                     :poistettu false
                     :yllapitoluokka yllapitoluokka}

            parametrit {:toteumat [toteuma]
                        :urakka-id  @nav/valittu-urakka-id
                        :sopimus-id (-> @u/valittu-sopimusnumero first)
                        :alkupvm (-> @u/valittu-aikavali first)
                        :loppupvm (-> @u/valittu-aikavali second)}]

        (tuck-apurit/post! app :tallenna-yllapito-toteumat
          parametrit
          {:onnistui ->TallennusOnnistui
           :epaonnistui ->TallennusEpaonnistui})

        (-> app
          (assoc :rivit nil)
          (assoc :muokataan false)
          (assoc :haku-kaynnissa? true)
          (assoc-in [:valittu-rivi :virheita?] false)))))

  TallennusOnnistui
  (process-event [_ {:keys [valittu-rivi rivit] :as app}]
    (let [valittu-id (:id valittu-rivi)
          valittu-rivi (some #(when (= (:id %) valittu-id) %) rivit)]
      (hae-tiedot app)
      (viesti/nayta-toast! "Toteuma tallennettu onnistuneesti" :onnistui viesti/viestin-nayttoaika-keskipitka)
      (assoc app :valitu-rivi valittu-rivi)))

  TallennusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (epaonnistui vastaus app))

  MuokkaaRivia
  (process-event [{:keys [rivi]} app]
    (update app :valittu-rivi merge rivi))

  AsetaToteumanPvm
  (process-event [{aika :aika} app]
    (assoc-in app [:valittu-rivi :pvm] aika))

  AvaaKustannusModal
  (process-event [{:keys [rivi]} app]
    (-> app
      (assoc :muokataan true)
      (assoc :valittu-rivi rivi)))

  SuljeMuokkaus
  (process-event [_ app]
    (assoc app :muokataan false)))
