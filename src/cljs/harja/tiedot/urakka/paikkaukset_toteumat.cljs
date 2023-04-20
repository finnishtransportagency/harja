(ns harja.tiedot.urakka.paikkaukset-toteumat
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [clojure.string :as str]
            [harja.ui.viesti :as viesti])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def tyhja-lomake {:kopio-itselle? true
                   :saate nil
                   :muut-vastaanottajat nil})

(defonce paikkaustoteumat-kartalla (atom []))
(defonce valitut-kohteet-atom (atom #{}))
(def taso-nakyvissa? (atom true))

(defn suirun-pituus
  [teiden-pituudet tierekisteriosoite]
  (tierekisteri/laske-tien-pituus (get teiden-pituudet (::tierekisteri/tie tierekisteriosoite))
                                  {:tr-alkuosa (::tierekisteri/aosa tierekisteriosoite)
                                   :tr-alkuetaisyys (::tierekisteri/aet tierekisteriosoite)
                                   :tr-loppuosa (::tierekisteri/losa tierekisteriosoite)
                                   :tr-loppuetaisyys (::tierekisteri/let tierekisteriosoite)}))

(defn massan-maara
      "Massamäärä tonneina (t). Massamenekki on massan määrä kiloina neliömetrillä. Pinta-ala on alue neliömetreinä."
      [pinta-ala massamenekki]
      (/ (* massamenekki pinta-ala) 1000))

(def toteumat-kartalla
  (reaction (let [;; Näytä vain valittu kohde kartalla
                  valitut-kohteet (if (= (count @valitut-kohteet-atom) 0)
                                    nil
                                    @valitut-kohteet-atom)
                  ;; Näytetään kartalla pelkät paikkaukset, ei paikkauskohteita
                  paikkaukset (flatten (mapcat
                                         (fn [kohde]
                                           [(::paikkaus/paikkaukset kohde)])
                                         @paikkaustoteumat-kartalla))
                  ;; Poistetaan listasta kaikki joilla ei ole geometriaa
                  paikkaukset (keep
                                (fn [p]
                                  (when (and
                                          (not (nil? (get-in p [::paikkaus/sijainti])))
                                          (not (nil? (get-in p [::paikkaus/sijainti :type]))))
                                    p))
                                paikkaukset)
                  ;; Jätetään paikkauslistaan vain valitut, jos valittuja on
                  paikkaukset (if (not (empty? valitut-kohteet))
                                (keep (fn [p]
                                        (when
                                          (and (or (nil? valitut-kohteet)
                                                   (contains? valitut-kohteet (::paikkaus/id p)))
                                               (::paikkaus/sijainti p))
                                          p))
                                      paikkaukset)
                                paikkaukset)
                  infopaneelin-tiedot-fn #(merge (select-keys % #{::tierekisteri/tie ::tierekisteri/aosa ::tierekisteri/aet
                                                                  ::tierekisteri/losa ::tierekisteri/let ::paikkaus/alkuaika
                                                                  ::paikkaus/loppuaika ::paikkaus/massatyyppi ::paikkaus/leveys
                                                                  ::paikkaus/massamenekki ::paikkaus/raekoko ::paikkaus/kuulamylly})
                                                 {::paikkaus/nimi (get-in % [::paikkaus/paikkauskohde ::paikkaus/nimi])}
                                                 (some (fn [paikkaus-kohta]
                                                         (when (= (::paikkaus/id paikkaus-kohta) (::paikkaus/id %))
                                                           (select-keys (first (::paikkaus/tienkohdat paikkaus-kohta)) #{::paikkaus/ajorata ::paikkaus/ajourat
                                                                                                                         ::paikkaus/kaista
                                                                                                                         ::paikkaus/ajouravalit ::paikkaus/reunat})))
                                                       paikkaukset))]
              (when (and (not (empty? paikkaukset)) @taso-nakyvissa?)
                (with-meta (mapv (fn [paikkaus]
                                   {:alue (merge {:tyyppi-kartalla :paikkaukset-toteumat
                                                  :stroke {:width 8
                                                           :color (asioiden-ulkoasu/tilan-vari "valmis")}}
                                                 (::paikkaus/sijainti paikkaus))
                                    :tyyppi-kartalla :paikkaukset-toteumat
                                    :stroke {:width 8 #_asioiden-ulkoasu/+normaali-leveys+}
                                    :infopaneelin-tiedot (infopaneelin-tiedot-fn paikkaus)})
                                 paikkaukset)
                           {:selitteet [{:vari (map :color asioiden-ulkoasu/paikkaukset)
                                         :teksti "Paikkaukset"}]})))))

(defn ilmoita-virheesta-paikkaustiedoissa [paikkaus]
  (k/post! :ilmoita-virheesta-paikkaustiedoissa
           (merge paikkaus
                  {::paikkaus/urakka-id (:id @nav/valittu-urakka)})))

;; Muokkaukset
(defrecord Nakymaan [])
(defrecord NakymastaPois [])
(defrecord AsetaPostPaivitys [])
(defrecord HaePaikkauskohteet [])
;; Haut
(defrecord PaikkauksetHaettu [tulos])
(defrecord PaikkauskohdeTarkistettu [paikkaus])
;; Modal (sähköpostin lähetys paikkaustoteumassa olevasta virheestä)
(defrecord AvaaVirheModal [paikkaus])
(defrecord SuljeVirheModal [])
(defrecord VirheIlmoitusOnnistui [vastaus])
(defrecord MerkitseTarkistetuksiOnnistui [vastaus])
(defrecord PaivitaLomakedata [lomakedata])
(defrecord PaivitaMuutVastaanottajat [muut])
;; Urapaikkausten excel-tuonti
(defrecord UremPaikkausLatausOnnistui [vastaus])
(defrecord UremPaikkausLatausEpaonnistui [vastaus])
(defrecord SuljeUremLatausVirhe [])

(defn hae-paikkauskohteet [urakka-id {:keys [valinnat] :as app}]
  (tuck-apurit/post! :hae-urakan-paikkaukset
                     {:harja.domain.paikkaus/urakka-id urakka-id
                      :aikavali (:aikavali valinnat)
                      :tyomenetelmat (:valitut-tyomenetelmat valinnat)}
                     {:onnistui ->PaikkauksetHaettu
                      :epaonnistui ->PaikkauksetHaettu
                      :paasta-virhe-lapi? true}))

(extend-protocol tuck/Event
  HaePaikkauskohteet
  (process-event [_ app]
    (do
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
      app))

  PaikkauskohdeTarkistettu
  (process-event [{paikkaus :paikkaus} {{:keys [valinnat]} :filtterit :as app}]
    (log "merkitse-paikkaus-tarkistetuksi, " (pr-str paikkaus))
    (tt/post! app 
              :merkitse-paikkauskohde-tarkistetuksi
              (merge paikkaus
                     {::paikkaus/urakka-id (:id @nav/valittu-urakka)
                      ::paikkaus/hakuparametrit (yhteiset-tiedot/filtterin-valinnat->kysely-params valinnat)})
              {:onnistui ->PaikkauksetHaettu}))
  Nakymaan
  (process-event [_ app]
    (assoc app :nakymassa? true))
  NakymastaPois
  (process-event [_ app]
    (-> app
        (assoc :ensimmainen-haku-tehty? false)
        (assoc :nakymassa? false)))

  PaikkauksetHaettu
  (process-event [ {tulos :tulos} app]
    (let [_ (reset! paikkaustoteumat-kartalla tulos)]
      (merge app {:ensimmainen-haku-tehty? true
                  :paikkaukset-grid tulos
                  :paikkauskohteet tulos
                  :paikkauket-vetolaatikko tulos})))

  AsetaPostPaivitys
  (process-event [_ app]
    (assoc app :post-haku-paivitys-fn (fn [_]
                                        (hae-paikkauskohteet (:id @nav/valittu-urakka) app))))

  AvaaVirheModal
  (process-event [{paikkaus :paikkaus} app]
    (-> app
        (assoc :modalin-paikkauskohde paikkaus)
        (assoc-in [:lomakedata :kopio-itselle?] true)))
  SuljeVirheModal
  (process-event [_ app]
    (assoc app :modalin-paikkauskohde nil
               :lomakedata tyhja-lomake))
  VirheIlmoitusOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :modalin-paikkauskohde nil
               :lomakedata tyhja-lomake))
  MerkitseTarkistetuksiOnnistui
  (process-event [{vastaus :vastaus} app]
    (log "MerkitseTarkistetuksi, vastaus " (pr-str vastaus))
    (assoc app :modalin-paikkauskohde nil))
  PaivitaLomakedata
  (process-event [{lomakedata :lomakedata} app]
    (assoc app :lomakedata lomakedata))
  PaivitaMuutVastaanottajat
  (process-event [{muut :muut} app]
    (assoc-in app [:lomakedata :muut-vastaanottajat] muut))

  UremPaikkausLatausOnnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Paikkaukset tuotu excelistä onnistuneesti")
    (hae-paikkauskohteet (get-in @tila/yleiset [:urakka :id]) app)
    app)

  UremPaikkausLatausEpaonnistui
  (process-event [{{response :response} :vastaus} app]
    (assoc app :excel-tuontivirhe (get response "virheet")))

  SuljeUremLatausVirhe
  (process-event [_ app]
    (dissoc app :excel-tuontivirhe)))


