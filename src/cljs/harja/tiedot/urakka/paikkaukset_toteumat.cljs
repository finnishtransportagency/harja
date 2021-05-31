(ns harja.tiedot.urakka.paikkaukset-toteumat
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.loki :refer [log]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [clojure.string :as str])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def tyhja-lomake {:kopio-itselle? true
                   :saate nil
                   :muut-vastaanottajat nil})

(defonce app (atom {:lomakedata tyhja-lomake
                :filtterit {}}))

(defonce filtterit (r/cursor app [:filtterit]))

(def taso-nakyvissa? (atom false))

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

(defn kiinnostavat-tiedot-grid [{tierekisteriosoite ::paikkaus/tierekisteriosoite
                                 paikkauskohde ::paikkaus/paikkauskohde
                                 :as paikkaus} teiden-pituudet]
  (let [sellaisenaan-naytettavat-arvot (select-keys
                                        paikkaus
                                        #{::paikkaus/tyomenetelma ::paikkaus/alkuaika
                                          ::paikkaus/loppuaika ::paikkaus/massatyyppi
                                          ::paikkaus/leveys ::paikkaus/massamenekki
                                          ::paikkaus/raekoko ::paikkaus/kuulamylly ::paikkaus/id
                                          ::paikkaus/paikkauskohde ::paikkaus/sijainti
                                          ::paikkaus/massamaara ::paikkaus/tienkohdat
                                          ::paikkaus/lahde})
        suirun-pituus (suirun-pituus teiden-pituudet tierekisteriosoite)
        suirun-pinta-ala (* suirun-pituus (::paikkaus/leveys paikkaus))
        suirun-tiedot {:suirun-pituus    suirun-pituus
                       :suirun-pinta-ala suirun-pinta-ala}
        massamaara {:massamaara (massan-maara
                                  suirun-pinta-ala
                                  (::paikkaus/massamenekki paikkaus))}
        sijainti {::paikkaus/sijainti (-> (::paikkaus/sijainti paikkaus)
                                          (assoc :type :moniviiva
                                                 :viivat asioiden-ulkoasu/paikkaukset))}
        nimi (select-keys paikkauskohde #{::paikkaus/nimi})]
    (merge sellaisenaan-naytettavat-arvot tierekisteriosoite nimi sijainti suirun-tiedot massamaara)))

(defn kiinnostavat-tiedot-vetolaatikko
  [paikkaus teiden-pituudet]
  (let [sellaisenaan-naytettavat-arvot (select-keys paikkaus
                                                    [::paikkaus/tienkohdat
                                                     ::paikkaus/materiaalit
                                                     ::paikkaus/id])]
    sellaisenaan-naytettavat-arvot))

(def tilatut-ja-valmiit-filtteri
  (filter
   (fn [kohde]
     (case (::paikkaus/paikkauskohteen-tila kohde)
       ("valmis", "tilattu") true
       nil true
       false))))

(defn- filttereilla-rajatut
  [{{:keys [tyomenetelmat]} :valinnat}]
  (filter
   (fn [kohde]
     (println "tm " (::paikkaus/tyomenetelma kohde) tyomenetelmat)
     (contains? tyomenetelmat (::paikkaus/tyomenetelma kohde)))))

(defn kasittele-haettu-tulos
  [tulos paikkauskohteet tyomenetelmat {teiden-pituudet :teiden-pituudet filtterit :filtterit}]
  (println "tttt" tyomenetelmat (update filtterit :valinnat merge (when tyomenetelmat
                                                 {:tyomenetelmat tyomenetelmat})))
  (let [kiinnostavat-tiedot (map #(kiinnostavat-tiedot-grid % teiden-pituudet)
                                 tulos)
        tilatut-valmiit-filttereilla-rajatut (into []
                                                   (comp tilatut-ja-valmiit-filtteri
                                                         (filttereilla-rajatut
                                                          (update filtterit
                                                                  :valinnat
                                                                  merge
                                                                  (when tyomenetelmat
                                                                    {:tyomenetelmat tyomenetelmat}))))
                                                   paikkauskohteet)
        paikkaukset-kohteen-idn-mukaan (group-by #(get-in % [::paikkaus/paikkauskohde ::paikkaus/id])
                                                 kiinnostavat-tiedot)
        paikkauskohteet-paikkauksilla (map
                                       (fn [paikkauskohde]
                                         (assoc-in paikkauskohde
                                                   [::paikkaus/paikkaukset]
                                                   (get paikkaukset-kohteen-idn-mukaan
                                                        (::paikkaus/id paikkauskohde))))
                                       tilatut-valmiit-filttereilla-rajatut)
        paikkauket-vetolaatikko (map #(kiinnostavat-tiedot-vetolaatikko % teiden-pituudet)
                                     tulos)]
    {:paikkaukset-grid paikkauskohteet-paikkauksilla ;kiinnostavat-tiedot
     :paikkauskohteet paikkauskohteet
     :paikkauket-vetolaatikko paikkauket-vetolaatikko}))

(def toteumat-kartalla
  (reaction (let [paikkaukset (remove #(= (type %) harja.ui.grid.protokollat/Otsikko)
                                      (:paikkaukset-grid @app))
                  paikkauksien-kohdat (:paikkauket-vetolaatikko @app)
                  infopaneelin-tiedot-fn #(merge (select-keys % #{::tierekisteri/tie ::tierekisteri/aosa ::tierekisteri/aet
                                                                  ::tierekisteri/losa ::tierekisteri/let ::paikkaus/alkuaika
                                                                  ::paikkaus/loppuaika ::paikkaus/massatyyppi ::paikkaus/leveys
                                                                  ::paikkaus/massamenekki ::paikkaus/raekoko ::paikkaus/kuulamylly})
                                                 {::paikkaus/nimi (get-in % [::paikkaus/paikkauskohde ::paikkaus/nimi])}
                                                 (some (fn [paikkaus-kohta]
                                                         (when (= (::paikkaus/id paikkaus-kohta) (::paikkaus/id %))
                                                           (select-keys (first (::paikkaus/tienkohdat paikkaus-kohta)) #{::paikkaus/ajorata ::paikkaus/ajourat
                                                                                                                         ::paikkaus/ajouravalit ::paikkaus/reunat})))
                                                       paikkauksien-kohdat))]
              (when (and (not-empty paikkaukset) @taso-nakyvissa?)
                (with-meta (mapv (fn [paikkaus]
                                   {:alue (::paikkaus/sijainti paikkaus)
                                    :tyyppi-kartalla :paikkaukset-toteumat
                                    :stroke {:width asioiden-ulkoasu/+normaali-leveys+}
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
(defrecord SiirryKustannuksiin [paikkauskohde-id])
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

(extend-protocol tuck/Event
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
  (process-event [{{paikkaukset :paikkaukset
                    tyomenetelmat :tyomenetelmat} :tulos} app]
    (let [;; Käyttöliittymä vaatii sen, että paikkauskohteet on eroteltu paikkauksista
          ;; Paikkauskohteiden tiedot ovat kuitenkin jo paikkaus mäppien sisällä, niin
          ;; ei haeta niitä erikseen vaan erotellaan ne sieltä käyttöön.
          ;; Käytetään settiä #{}, niin saadaan mukaan helposti vain uniikit paikkauskohteet
          paikkauskohteet (reduce (fn [kohteet paikkaus]
                                    (conj kohteet (:harja.domain.paikkaus/paikkauskohde paikkaus)))
                                  #{}
                                  paikkaukset)
          naytettavat-tiedot (kasittele-haettu-tulos paikkaukset paikkauskohteet tyomenetelmat app)]
      (merge app naytettavat-tiedot)))
  AsetaPostPaivitys
  (process-event [_ app]
    (assoc app :post-haku-paivitys-fn (fn [_]
                                        (println "post haku paivitys")
                                        (tt/post! :hae-urakan-paikkauskohteet
                                                  (merge {::paikkaus/urakka-id 
                                                          (get-in app [:filtterit :urakka])}
                                                  (get-in app [:filtterit :valinnat]))
                                                  {:onnistui ->PaikkauksetHaettu}))))
  SiirryKustannuksiin
  (process-event [{paikkauskohde-id :paikkauskohde-id} app]
    (swap! yhteiset-tiedot/tila update :valinnat (fn [valinnat]
                                                   (-> valinnat
                                                       (assoc :aikavali [nil nil]
                                                              :tyomenetelmat #{}
                                                              :tr nil)
                                                       (update :urakan-paikkauskohteet (fn [paikkauskohteet]
                                                                                         (map #(if (= paikkauskohde-id (:id %))
                                                                                                 %
                                                                                                 (assoc % :valittu? false))
                                                                                              paikkauskohteet))))))
    (swap! reitit/url-navigaatio assoc :kohdeluettelo-paikkaukset :kustannukset)
    (assoc app :nakymassa? false))
  AvaaVirheModal
  (process-event [{paikkaus :paikkaus} app]
    (assoc app :modalin-paikkaus paikkaus))
  SuljeVirheModal
  (process-event [_ app]
    (assoc app :modalin-paikkaus nil
               :lomakedata tyhja-lomake))
  VirheIlmoitusOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :modalin-paikkaus nil
               :lomakedata tyhja-lomake))
  MerkitseTarkistetuksiOnnistui
  (process-event [{vastaus :vastaus} app]
    (log "MerkitseTarkistetuksi, vastaus " (pr-str vastaus))
    (assoc app :modalin-paikkaus nil))
  PaivitaLomakedata
  (process-event [{lomakedata :lomakedata} app]
    (assoc app :lomakedata lomakedata))
  PaivitaMuutVastaanottajat
  (process-event [{muut :muut} app]
    (assoc-in app [:lomakedata :muut-vastaanottajat] muut)))


