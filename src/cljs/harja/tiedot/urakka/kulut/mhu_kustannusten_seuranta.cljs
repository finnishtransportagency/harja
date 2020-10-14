(ns harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta
  "UI controlleri kustannusten seurantaan"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.toteuma :as t]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as ui-lomake]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.toteumat.maarien-toteumat-kartalla :as maarien-toteumat-kartalla])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(declare hae-kustannukset)
(declare ryhmittele-tehtavat)

(defrecord HaeKustannukset [urakka toimenpide hoitoikauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm])
(defrecord KustannustenHakuOnnistui [vastaus])
(defrecord KustannustenHakuEpaonnistui [vastaus])
(defrecord ValitseToimenpide [urakka toimenpide])
(defrecord HaeToimenpiteet [])
(defrecord ToimenpiteetHakuEpaonnistui [vastaus])
(defrecord ToimenpiteetHakuOnnistui [vastaus])
(defrecord AvaaRivi [avain])
(defrecord ValitseHoitokausi [urakka vuosi])

(defn ryhmittele-tehtavat
  [ryhmiteltavat]
  (let [_ (js/console.log "ryhmiteltävät yksinkertainen: " (pr-str (group-by :toimenpide ryhmiteltavat)))
        ryhmitelty-tr (group-by :toimenpide ryhmiteltavat)]
    #_ (sort-by first
             (into {}
                   (map
                     (fn [[tehtavaryhma tehtavat]]
                       [tehtavaryhma (sort-by first
                                              (group-by :tehtava tehtavat))])
                     ryhmitelty-tr)))
    (group-by :toimenpide ryhmiteltavat)))

(defn hae-kustannukset [urakka-id toimenpide hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm]
  (let [alkupvm (when hoitokauden-alkuvuosi
                  (str hoitokauden-alkuvuosi "-10-01"))
        #_alkupvm #_(if aikavali-alkupvm
                      aikavali-alkupvm alkupvm)
        loppupvm (when hoitokauden-alkuvuosi
                   (str (inc hoitokauden-alkuvuosi) "-09-30"))
        #_loppupvm #_(if aikavali-loppupvm
                       aikavali-loppupvm loppupvm)]
    (tuck-apurit/post! :urakan-kustannusten-seuranta-toimenpideittain
                       {:urakka-id urakka-id
                        :tehtavaryhma (:otsikko toimenpide)
                        :alkupvm alkupvm
                        :loppupvm loppupvm}
                       {:onnistui ->KustannustenHakuOnnistui
                        :epaonnistui ->KustannustenHakuEpaonnistui
                        :paasta-virhe-lapi? true})))

(extend-protocol tuck/Event

  ValitseToimenpide
  (process-event [{urakka :urakka toimenpide :toimenpide} app]
    (do
      (hae-kustannukset urakka toimenpide
                        (:hoitokauden-alkuvuosi app)
                        (:aikavali-alkupvm app)
                        (:aikavali-loppupvm app))
      (-> app
          (assoc :valittu-toimenpide toimenpide))))

  HaeKustannukset
  (process-event [{urakka-id :urakka-id toimenpide :toimenpide hoitokauden-alkuvuosi :hoitokauden-alkuvuosi
                   aikavali-alkupvm :aikavali-alkupvm aikavali-loppupvm :aikavali-loppupvm} app]
    (let [alkupvm (when aikavali-alkupvm
                    (pvm/iso8601 aikavali-alkupvm))
          loppupvm (when aikavali-loppupvm
                     (pvm/iso8601 aikavali-loppupvm))]
      (hae-kustannukset urakka-id toimenpide hoitokauden-alkuvuosi alkupvm loppupvm))
    app)

  KustannustenHakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [ryhmitelty-tehtava (ryhmittele-tehtavat vastaus)
          eka-toimenpide (first ryhmitelty-tehtava)
          ekan-tehtavat (second eka-toimenpide)
          ;_ (js/console.log "ekan-tehtavat" (pr-str ekan-tehtavat))
          _ (doall
              (mapv (fn [rivi]
                      (js/console.log "second" (pr-str (second rivi)))) ekan-tehtavat))
          ;_ (js/console.log "ekan-tehtavat" (pr-str ekan-tehtavat))
          ;_
          #_ (js/console.log "ekan-tehtavat-summat" (apply + (map (fn [rivi]
                                                                 (let [_ (js/console.log "apply rivi" (pr-str rivi))]
                                                                   (:toteutunut_summa (first (second rivi))))) ekan-tehtavat)))
          _ (js/console.log "ryhmitelty-tehtava" (pr-str ryhmitelty-tehtava))
          toimenpiteet (mapv
                         (fn [toimenpide]
                           (let [toimenpiteen-tehtavat (second toimenpide)
                                 _ (js/console.log "toimenpiteen-tehtavat" (pr-str toimenpiteen-tehtavat))]
                             {:toimenpide (first toimenpide)
                              :yht-toteutunut-summa (apply + (map (fn [rivi]
                                                                    (:toteutunut_summa rivi))
                                                                  toimenpiteen-tehtavat))
                              :yht-budjetoitu-summa (apply + (map (fn [rivi]
                                                                    (:budjetoitu_summa rivi))
                                                                  toimenpiteen-tehtavat))
                              :tehtavat toimenpiteen-tehtavat})
                           )
                         ryhmitelty-tehtava)
          _ (js/console.log "toimenpiteet" (pr-str toimenpiteet))
          ]
      (-> app
          (assoc-in [:kustannukset] vastaus)
          (assoc-in [:kustannukset-grouped] ryhmitelty-tehtava)
          (assoc-in [:kustannukset-grouped2] toimenpiteet))))

  KustannustenHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Haku epäonnistui!" :danger)
    app)


  HaeToimenpiteet
  (process-event [_ app]
    (tuck-apurit/post! :urakan-kustannusten-toimenpiteet {}
                       {:onnistui ->ToimenpiteetHakuOnnistui
                        :epaonnistui ->ToimenpiteetHakuEpaonnistui
                        :paasta-virhe-lapi? true})
    app)

  ToimenpiteetHakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc-in app [:toimenpiteet] vastaus))

  ToimenpiteetHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Toimenpiteiden haku epäonnistui!" :danger)
    app)

  ;; Vain yksi rivi voi olla avattuna kerralla, joten tallennetaan avain app-stateen tai poistetaan se, jos se oli jo valittuna
  AvaaRivi
  (process-event [{avain :avain} app]
    (if (= avain (get-in app [:valittu-rivi]))
      (assoc-in app [:valittu-rivi] nil)
      (assoc-in app [:valittu-rivi] avain)))

  ValitseHoitokausi
  (process-event [{urakka :urakka vuosi :vuosi} app]
    (do
      (hae-kustannukset urakka (:valittu-toimenpide app) vuosi nil nil)
      (-> app
          (assoc-in [:hoitokauden-alkuvuosi] vuosi))))

  ;ValitseAikavali
  #_(process-event
      [{:keys [polku arvo]} app]
      (let [arvo (if (nil? arvo)
                   (get-in app [polku])
                   arvo)]
        (-> app
            (assoc-in [:hoitokauden-alkuvuosi] nil)
            (assoc-in [(case polku
                         :alkupvm :aikavali-alkupvm
                         :loppupvm :aikavali-loppupvm)] arvo))))

  )
