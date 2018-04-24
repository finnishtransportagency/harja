(ns harja.tiedot.urakka.paikkaukset-yhteinen
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.viesti :as viesti]
            [harja.domain.paikkaus :as paikkaus])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tila (atom {:paikkauskohde-id nil
                     :valinnat {:aikavali (pvm/aikavali-nyt-miinus 7)}}))

(defn alku-parametrit
  [{:keys [nakyma palvelukutsu-onnistui-fn]}]
  (let [tilan-alustus (case nakyma
                        :toteumat {:itemit-avain :paikkaukset
                                   :aikavali-otsikko "Alkuaika"
                                   :voi-valita-trn-kartalta? true
                                   :palvelukutsu :hae-urakan-paikkauskohteet
                                   :palvelukutsu-tunniste :hae-paikkaukset-toteumat-nakymaan
                                   :paikkauskohde-idn-polku [::paikkaus/paikkauskohde ::paikkaus/id]}
                        :kustannukset {:itemit-avain :kustannukset
                                       :aikavali-otsikko "Kirjausaika"
                                       :voi-valita-trn-kartalta? false
                                       :palvelukutsu :hae-paikkausurakan-kustannukset
                                       :palvelukutsu-tunniste :hae-paikkaukset-kustannukset-nakymaan
                                       :paikkauskohde-idn-polku [:paikkauskohde-id]})]
    (swap! tila #(merge % tilan-alustus {:palvelukutsu-onnistui-fn palvelukutsu-onnistui-fn}))))

;; Muokkaukset
(defrecord PaikkausValittu [paikkauskohde valittu?])
(defrecord PaivitaValinnat [uudet])
(defrecord Nakymaan [])
;; Haut
(defrecord HaeItemit [uudet-valinnat])
(defrecord ItemitHaettu [tulos])
(defrecord ItemitEiHaettu [])
(defrecord HaeItemitKutsuLahetetty [])
(defrecord EnsimmainenHaku [tulos])

(extend-protocol tuck/Event
  Nakymaan
  (process-event [_ {:keys [palvelukutsu valinnat haku-kaynnissa?] :as app}]
    (-> app
        (tt/post! palvelukutsu
                  {::paikkaus/urakka-id @nav/valittu-urakka-id
                   :aikavali (get-in app [:valinnat :aikavali])}
                  {:onnistui ->EnsimmainenHaku
                   :epaonnistui ->ItemitEiHaettu})
        (assoc :paikkauksien-haku-kaynnissa? true)))
  EnsimmainenHaku
  (process-event [{tulos :tulos} {:keys [palvelukutsu-onnistui-fn paikkauskohde-id paikkauskohde-idn-polku itemit-avain] :as app}]
    (let [paikkauskohteet (map #(identity
                                  {:id (::paikkaus/id %)
                                   :nimi (::paikkaus/nimi %)
                                   :valittu? (or (nil? paikkauskohde-id)
                                                 (= paikkauskohde-id
                                                    (::paikkaus/id %)))})
                               (:paikkauskohteet tulos))
          naytettavat-tulokset (filter #(or (nil? paikkauskohde-id)
                                            (= paikkauskohde-id
                                               (get-in % paikkauskohde-idn-polku)))
                                       (itemit-avain tulos))]
      (println "ENSIMMÄINEN TULOS")
      (cljs.pprint/pprint tulos)
      (palvelukutsu-onnistui-fn tulos)
      (-> app
          (assoc :paikkauksien-haku-kaynnissa? false
                 :ensimmainen-haku-tehty? true
                 :paikkauskohde-id nil)
          (update :valinnat (fn [valinnat]
                              (assoc valinnat
                                     :aikavali (pvm/aikavali-nyt-miinus 7)
                                     :urakan-paikkauskohteet paikkauskohteet
                                     :tyomenetelmat (:tyomenetelmat tulos)))))))
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
  HaeItemit
  (process-event [{:keys [uudet-valinnat]} {:keys [palvelukutsu palvelukutsu-tunniste valinnat haku-kaynnissa?] :as app}]
    (if-not haku-kaynnissa?
      (let [paikkauksien-idt (into #{} (keep #(when (:valittu? %)
                                                (:id %))
                                             (:urakan-paikkauskohteet valinnat)))
            params (-> uudet-valinnat
                       (dissoc :urakan-paikkauskohteet)
                       (assoc :paikkaus-idt paikkauksien-idt)
                       (assoc ::paikkaus/urakka-id @nav/valittu-urakka-id))]
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
    (println "TULOS")
    (cljs.pprint/pprint tulos)
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