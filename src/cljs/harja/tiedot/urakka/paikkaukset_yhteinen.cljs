(ns harja.tiedot.urakka.paikkaukset-yhteinen
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.viesti :as viesti]
            [harja.domain.paikkaus :as paikkaus])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tila (atom nil))

(defn alku-parametrit
  [{:keys [nakyma palvelukutsu-onnistui-fn]}]
  (let [tilan-alustus (case nakyma
                        :toteumat {:itemit-avain :paikkaukset
                                   :aikavali-otsikko "Alkuaika"
                                   :voi-valita-trn-kartalta? true
                                   :palvelukutsu :hae-urakan-paikkauskohteet
                                   :palvelukutsu-tunniste :hae-paikkaukset-toteumat-nakymaan}
                        :kustannukset {:itemit-avain :kustannukset
                                       :aikavali-otsikko "Kirjausaika"
                                       :voi-valita-trn-kartalta? false
                                       :palvelukutsu :hae-paikkausurakan-kustannukset
                                       :palvelukutsu-tunniste :hae-paikkaukset-kustannukset-nakymaan})]
    (swap! tila #(merge % tilan-alustus {:palvelukutsu-onnistui-fn palvelukutsu-onnistui-fn}))))

(defn nakyman-urakka
  "Saman urakan sisällä kun vaihdetaan toteumista kustannuksiin, ei resetoida hakuehtoja. Mutta jos urakka
   vaihtuu, tulee hakuehdot resetoida."
  [ur]
  (when (not= (:urakka @tila) ur)
    (reset! tila {:valinnat {:aikavali (pvm/aikavali-nyt-miinus 7)
                             :tyomenetelmat #{}}
                  :urakka ur})))

;; Muokkaukset
(defrecord PaikkausValittu [paikkauskohde valittu?])
(defrecord TyomenetelmaValittu [tyomenetelma valittu?])
(defrecord PaivitaValinnat [uudet])
(defrecord Nakymaan [])
;; Haut
(defrecord HaeItemit [uudet-valinnat])
(defrecord ItemitHaettu [tulos])
(defrecord ItemitEiHaettu [])
(defrecord HaeItemitKutsuLahetetty [])
(defrecord EnsimmainenHaku [tulos])

(defn filtterin-valinnat->kysely-params
  [valinnat]
  (let [paikkauksien-idt (when (:urakan-paikkauskohteet valinnat)
                           (into #{} (keep #(when (:valittu? %)
                                              (:id %))
                                           (:urakan-paikkauskohteet valinnat))))]
    (-> valinnat
        (dissoc :urakan-paikkauskohteet)
        (assoc :paikkaus-idt paikkauksien-idt)
        (assoc ::paikkaus/urakka-id @nav/valittu-urakka-id))))

(extend-protocol tuck/Event
  Nakymaan
  (process-event [_ {:keys [palvelukutsu valinnat haku-kaynnissa?] :as app}]
    (-> app
        (tt/post! palvelukutsu
                  (merge (filtterin-valinnat->kysely-params valinnat)
                         {:ensimmainen-haku? true})
                  {:onnistui ->EnsimmainenHaku
                   :epaonnistui ->ItemitEiHaettu})
        (assoc :paikkauksien-haku-kaynnissa? true)))
  EnsimmainenHaku
  (process-event [{tulos :tulos} {:keys [palvelukutsu-onnistui-fn itemit-avain valinnat] :as app}]
    (let [paikkauskohteet (or (:urakan-paikkauskohteet valinnat)
                              (map #(identity
                                      {:id (::paikkaus/id %)
                                       :nimi (::paikkaus/nimi %)
                                       :valittu? true})
                                   (:paikkauskohteet tulos)))
          naytettavat-tulokset (itemit-avain tulos)]
      (palvelukutsu-onnistui-fn tulos)
      (-> app
          (assoc :paikkauksien-haku-kaynnissa? false
                 :ensimmainen-haku-tehty? true
                 :urakan-tyomenetelmat (:tyomenetelmat tulos))
          (update :valinnat (fn [valinnat]
                              (assoc valinnat
                                     :urakan-paikkauskohteet paikkauskohteet))))))
  PaivitaValinnat
  (process-event [{u :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                u)
          haku (tuck/send-async! ->HaeItemit)]
      (go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))
  PaikkausValittu
  (process-event [{{:keys [id]} :paikkauskohde valittu? :valittu?} app]
    (let [uudet-paikkausvalinnat (map #(if (= (:id %) id)
                                         (assoc % :valittu? valittu?)
                                         %)
                                      (get-in app [:valinnat :urakan-paikkauskohteet]))]
      (tuck/process-event (->PaivitaValinnat {:urakan-paikkauskohteet uudet-paikkausvalinnat}) app)
      (assoc-in app [:valinnat :urakan-paikkauskohteet] uudet-paikkausvalinnat)))
  TyomenetelmaValittu
  (process-event [{{:keys [nimi]} :tyomenetelma valittu? :valittu?} app]
      (update-in app [:valinnat :tyomenetelmat] (fn [valitut-tyomenetelmat]
                                                  (if valittu?
                                                    (conj valitut-tyomenetelmat nimi)
                                                    (disj valitut-tyomenetelmat nimi)
                                                    ))))
  HaeItemit
  (process-event [{:keys [uudet-valinnat]} {:keys [palvelukutsu palvelukutsu-tunniste valinnat haku-kaynnissa?] :as app}]
    (if-not haku-kaynnissa?
      (let [params (filtterin-valinnat->kysely-params uudet-valinnat)]
        (-> app
            (tt/post! palvelukutsu
                      params
                      {:viive 1000
                       :tunniste palvelukutsu-tunniste
                       :lahetetty ->HaeItemitKutsuLahetetty
                       :onnistui ->ItemitHaettu
                       :epaonnistui ->ItemitEiHaettu})
            (assoc :paikkauksien-haku-tulee-olemaan-kaynnissa? true)))
      app))
  HaeItemitKutsuLahetetty
  (process-event [_ app]
    (assoc app :paikkauksien-haku-kaynnissa? true))
  ItemitHaettu
  (process-event [{tulos :tulos} {palvelukutsu-onnistui-fn :palvelukutsu-onnistui-fn :as app}]
    (palvelukutsu-onnistui-fn tulos)
    (assoc app
           :paikkauksien-haku-kaynnissa? false
           :paikkauksien-haku-tulee-olemaan-kaynnissa? false))
  ItemitEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Paikkauksien haku epäonnistui! " :danger)
    (assoc app
           :paikkauksien-haku-kaynnissa? false
           :paikkauksien-haku-tulee-olemaan-kaynnissa? false)))
