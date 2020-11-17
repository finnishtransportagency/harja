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

(defrecord HaeKustannukset [toimenpide hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm])
(defrecord KustannustenHakuOnnistui [vastaus])
(defrecord KustannustenHakuEpaonnistui [vastaus])
(defrecord ValitseToimenpide [urakka toimenpide])
(defrecord HaeToimenpiteet [])
(defrecord ToimenpiteetHakuEpaonnistui [vastaus])
(defrecord ToimenpiteetHakuOnnistui [vastaus])
(defrecord AvaaRivi [avain])
(defrecord ValitseHoitokausi [urakka vuosi])

(defn hae-kustannukset [urakka-id toimenpide hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm]
  (let [_ (js/console.log "hae-kustannukset ! " (pr-str hoitokauden-alkuvuosi) " toimenpide" (pr-str toimenpide))
        alkupvm (when hoitokauden-alkuvuosi
                  (str hoitokauden-alkuvuosi "-10-01"))
        #_alkupvm #_(if aikavali-alkupvm
                      aikavali-alkupvm alkupvm)
        loppupvm (when hoitokauden-alkuvuosi
                   (str (inc hoitokauden-alkuvuosi) "-09-30"))
        #_loppupvm #_(if aikavali-loppupvm
                       aikavali-loppupvm loppupvm)]
    (tuck-apurit/post! :urakan-kustannusten-seuranta-toimenpideittain
                       {:urakka-id urakka-id
                        :toimenpidekoodi (:koodi toimenpide)
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
  (process-event [{toimenpide :toimenpide hoitokauden-alkuvuosi :hoitokauden-alkuvuosi
                   aikavali-alkupvm :aikavali-alkupvm aikavali-loppupvm :aikavali-loppupvm} app]
    (let [alkupvm (when aikavali-alkupvm
                    (pvm/iso8601 aikavali-alkupvm))
          loppupvm (when aikavali-loppupvm
                     (pvm/iso8601 aikavali-loppupvm))
          urakka-id (-> @tila/yleiset :urakka :id)]
      (hae-kustannukset urakka-id toimenpide hoitokauden-alkuvuosi alkupvm loppupvm))
    app)

  KustannustenHakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [ryhmitelty-tehtava (group-by :toimenpide vastaus)
          toimenpiteet (mapv
                         (fn [toimenpide]
                           (let [toimenpiteen-tehtavat (second toimenpide)
                                 ;; Toimenpiteet mäpissä on budjetoidut ja toteutuneet toimenpiteet
                                 ;; UI:lla budjetointi lasketaan yhteen (yllä yhteensä kohdassa) ja toteutuneet kustannukset näytetään
                                 ;; rivikohtaisesti.
                                 ;; Poistetaan siis budjetointiin liittyvät tehtävät :toteutunut = budjetoitu tai hth
                                 toteutuneet-tehtavat (filter
                                                        (fn [tehtava]
                                                          (when (and
                                                                  (not= "hjh" (:toteutunut tehtava))
                                                                  (not= "budjetointi" (:toteutunut tehtava)))
                                                            tehtava))
                                                        toimenpiteen-tehtavat)
                                 ;_ (js/console.log "toimenpiteen-tehtavat1" (pr-str toimenpiteen-tehtavat))
                                 _ (js/console.log "toimenpiteen: " (pr-str (first toimenpide)) "tehtavat" (pr-str (map (juxt :tehtava_nimi :kustannustyyppi :jarjestys) toimenpiteen-tehtavat)))
                                 jarjestys (some #(:jarjestys %) toimenpiteen-tehtavat)
                                 _ (js/console.log "jarjestys" (pr-str jarjestys))]
                             {:toimenpide (first toimenpide)
                              :jarjestys jarjestys
                              :toimenpide-toteutunut-summa (apply + (map (fn [rivi]
                                                                           (:toteutunut_summa rivi))
                                                                         toimenpiteen-tehtavat))
                              :toimenpide-budjetoitu-summa (apply + (map (fn [rivi]
                                                                           (:budjetoitu_summa rivi))
                                                                         toimenpiteen-tehtavat))
                              :tehtavat toteutuneet-tehtavat}))
                         ryhmitelty-tehtava)
          _ (js/console.log "toimenpiteet 1" (pr-str (map (juxt :toimenpide :jarjestys) toimenpiteet)))
          toimenpiteet (sort-by :jarjestys toimenpiteet)
          _ (js/console.log "toimenpiteet 2" (pr-str (map (juxt :toimenpide :jarjestys) toimenpiteet)))
          yhteensa {:toimenpide "Tavoitehinta/Yhteensä"
                    :yht-toteutunut-summa (apply + (map (fn [rivi]
                                                          (:toimenpide-toteutunut-summa rivi))
                                                        toimenpiteet))
                    :yht-budjetoitu-summa (apply + (map (fn [rivi]
                                                          (:toimenpide-budjetoitu-summa rivi))
                                                        toimenpiteet))}
          ]
      (-> app
          (assoc-in [:kustannukset-yhteensa] yhteensa)
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
